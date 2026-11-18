package com.move60.workout.architecture

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/*
 * MY tab data (Room v6, docs/v64). ADDITIVE: two new tables, no existing table or user record is touched.
 * Nothing is seeded: a space or equipment exists only because the user saved it (the "3종" in Figma is a sample value).
 */

enum class SpaceKind { HOME, GYM }

/** A place the user works out. Equipment is optional per space; the default space feeds the recommendation/compose screens. */
@Entity(tableName = "workout_spaces")
data class WorkoutSpaceEntity(
    @PrimaryKey val spaceId: String,
    /** [SpaceKind] name. */
    val kind: String,
    val name: String,
    val isDefault: Boolean,
    val createdAtEpochMs: Long
)

/** One piece of equipment the user said is available in a space. brand/model are optional free text (no search provider yet). */
@Entity(
    tableName = "space_equipment",
    primaryKeys = ["spaceId", "equipmentKey"],
    foreignKeys = [ForeignKey(WorkoutSpaceEntity::class, ["spaceId"], ["spaceId"], onDelete = ForeignKey.CASCADE)]
)
data class SpaceEquipmentEntity(
    val spaceId: String,
    val equipmentKey: String,
    val brand: String?,
    val model: String?,
    val updatedAtEpochMs: Long
)

/**
 * Equipment choices per space kind (Figma 62 gym / 73 home, widened by the PO decision of 2026-11-17: a gym can register all of its
 * equipment and a home can register its home-training equipment). Keys are stored; labels are UI text. Keys are never renamed or removed:
 * saved space equipment must keep resolving. Rope attachments and mini bands are part of CABLE_MACHINE / RESISTANCE_BAND, and body-weight
 * exercises need no registration at all (see docs/v67).
 */
enum class Equipment(val label: String, val cardio: Boolean) {
    INDOOR_CYCLE("실내 사이클", true), ELLIPTICAL("일립티컬", true),
    LEG_PRESS("레그프레스", false), LAT_PULLDOWN("랫풀다운", false), CABLE_MACHINE("케이블 머신", false),
    DUMBBELL("덤벨", false), YOGA_MAT("요가 매트", false), RESISTANCE_BAND("저항 밴드", false),
    HIP_THRUST_MACHINE("힙쓰러스트 머신", false), HIP_ABDUCTION_MACHINE("힙 어브덕션 머신", false), LEG_CURL_MACHINE("레그컬 머신", false),
    SEATED_ROW_MACHINE("시티드 로우 머신", false), CHEST_PRESS_MACHINE("체스트프레스 머신", false),
    BENCH("벤치", false), STEP_BOX("스텝박스", false);

    companion object {
        /** Display order of the checklist. The first entries of each list keep their earlier order; new ones are appended. */
        fun choices(kind: SpaceKind): List<Equipment> = when (kind) {
            SpaceKind.GYM -> listOf(INDOOR_CYCLE, LEG_PRESS, LAT_PULLDOWN, ELLIPTICAL, CABLE_MACHINE,
                HIP_THRUST_MACHINE, HIP_ABDUCTION_MACHINE, LEG_CURL_MACHINE, SEATED_ROW_MACHINE, CHEST_PRESS_MACHINE,
                DUMBBELL, BENCH, STEP_BOX, YOGA_MAT, RESISTANCE_BAND)
            SpaceKind.HOME -> listOf(INDOOR_CYCLE, DUMBBELL, YOGA_MAT, RESISTANCE_BAND, ELLIPTICAL, BENCH, STEP_BOX)
        }
        fun fromKey(key: String): Equipment? = entries.firstOrNull { it.name == key }
    }
}

@Dao
interface SpaceDao {
    @Query("SELECT * FROM workout_spaces ORDER BY isDefault DESC, createdAtEpochMs, spaceId") fun observeSpaces(): Flow<List<WorkoutSpaceEntity>>
    @Query("SELECT * FROM workout_spaces ORDER BY isDefault DESC, createdAtEpochMs, spaceId") suspend fun spaces(): List<WorkoutSpaceEntity>
    @Query("SELECT * FROM workout_spaces WHERE spaceId = :id") suspend fun space(id: String): WorkoutSpaceEntity?
    @Query("SELECT * FROM space_equipment ORDER BY spaceId, equipmentKey") fun observeEquipment(): Flow<List<SpaceEquipmentEntity>>
    @Query("SELECT * FROM space_equipment WHERE spaceId = :id ORDER BY equipmentKey") suspend fun equipment(id: String): List<SpaceEquipmentEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSpace(space: WorkoutSpaceEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEquipment(rows: List<SpaceEquipmentEntity>)
    @Query("DELETE FROM space_equipment WHERE spaceId = :id") suspend fun clearEquipment(id: String)
    @Query("UPDATE workout_spaces SET isDefault = (spaceId = :id)") suspend fun setOnlyDefault(id: String)
    @Query("UPDATE workout_spaces SET name = :name WHERE spaceId = :id") suspend fun rename(id: String, name: String)
}

sealed interface SpaceResult {
    data class Saved(val spaceId: String) : SpaceResult
    data class Rejected(val reason: String) : SpaceResult
}

data class EquipmentChoice(val key: String, val brand: String? = null, val model: String? = null)

/** Validation and all-or-nothing writes for MY > 운동 공간. Equipment edits replace one space's set in a single transaction. */
class SpaceService(private val db: WorkoutDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao get() = db.spaceDao()

    /** The first space created becomes the default. The id is caller-supplied so a retried tap cannot create a duplicate. */
    suspend fun create(spaceId: String, kind: SpaceKind, name: String): SpaceResult {
        val n = name.trim()
        if (spaceId.isBlank()) return SpaceResult.Rejected("spaceId is blank")
        if (n.isEmpty() || n.length > MAX_NAME) return SpaceResult.Rejected("name must be 1..$MAX_NAME chars")
        return db.withTransaction {
            val all = dao.spaces()
            when {
                all.any { it.spaceId == spaceId } -> SpaceResult.Saved(spaceId)   // idempotent retry
                all.size >= MAX_SPACES -> SpaceResult.Rejected("too many spaces")
                all.any { it.name.equals(n, ignoreCase = true) } -> SpaceResult.Rejected("name already used")
                else -> {
                    dao.insertSpace(WorkoutSpaceEntity(spaceId, kind.name, n, all.isEmpty(), clock()))
                    SpaceResult.Saved(spaceId)
                }
            }
        }
    }

    /**
     * PO decision 2: a registered gym's name can be changed. Same rules as creating (1..[MAX_NAME] characters after trimming, no
     * duplicate); only gyms are renamable (the home space has a fixed name). Equipment and records of the space are untouched.
     */
    suspend fun rename(spaceId: String, name: String): SpaceResult {
        val n = name.trim()
        if (n.isEmpty() || n.length > MAX_NAME) return SpaceResult.Rejected("name must be 1..$MAX_NAME chars")
        return db.withTransaction {
            val space = dao.space(spaceId) ?: return@withTransaction SpaceResult.Rejected("unknown space")
            when {
                space.kind != SpaceKind.GYM.name -> SpaceResult.Rejected("only gyms can be renamed")
                dao.spaces().any { it.spaceId != spaceId && it.name.equals(n, ignoreCase = true) } -> SpaceResult.Rejected("name already used")
                else -> { dao.rename(spaceId, n); SpaceResult.Saved(spaceId) }
            }
        }
    }

    suspend fun setDefault(spaceId: String): SpaceResult = db.withTransaction {
        if (dao.space(spaceId) == null) SpaceResult.Rejected("unknown space") else { dao.setOnlyDefault(spaceId); SpaceResult.Saved(spaceId) }
    }

    /**
     * Replaces the equipment of one space with [choices]. Only catalog keys valid for the space's kind are accepted, brand/model
     * only on cardio equipment (<= [MAX_TEXT] chars, blank = unset). Any invalid entry writes nothing.
     */
    suspend fun saveEquipment(spaceId: String, choices: List<EquipmentChoice>): SpaceResult = db.withTransaction {
        val space = dao.space(spaceId) ?: return@withTransaction SpaceResult.Rejected("unknown space")
        val allowed = Equipment.choices(SpaceKind.valueOf(space.kind)).map { it.name }.toSet()
        if (choices.map { it.key }.toSet().size != choices.size) return@withTransaction SpaceResult.Rejected("duplicate equipment")
        val now = clock()
        val rows = choices.map { c ->
            val eq = Equipment.fromKey(c.key)
            if (eq == null || c.key !in allowed) return@withTransaction SpaceResult.Rejected("equipment not available here: ${c.key}")
            val brand = c.brand?.trim()?.ifEmpty { null }; val model = c.model?.trim()?.ifEmpty { null }
            if ((brand != null || model != null) && !eq.cardio) return@withTransaction SpaceResult.Rejected("model is for cardio equipment only")
            if ((brand?.length ?: 0) > MAX_TEXT || (model?.length ?: 0) > MAX_TEXT) return@withTransaction SpaceResult.Rejected("text too long")
            SpaceEquipmentEntity(spaceId, c.key, brand, model, now)
        }
        dao.clearEquipment(spaceId)
        if (rows.isNotEmpty()) dao.insertEquipment(rows)
        SpaceResult.Saved(spaceId)
    }

    companion object { const val MAX_NAME = 20; const val MAX_SPACES = 10; const val MAX_TEXT = 40 }
}
