package com.move60.watch

import android.content.Context
import com.google.android.gms.wearable.DataMap
import org.json.JSONObject
import kotlin.math.ceil

/** Five stages: 준비운동, 유산소 ①, 근력운동, 유산소 ②, 스트레칭 (phone protocol additive: older phones send 0..3). */
const val STAGE_COUNT = 5
const val LAST_STAGE = STAGE_COUNT - 1
/** LEGACY_5: cardio stages and the stretch stage carry timed intervals. */
fun isTimedStage(stageIndex: Int) = stageIndex == 0 || stageIndex == 1 || stageIndex == 3 || stageIndex == 4

const val STRUCTURE_LEGACY_5 = "LEGACY_5"
const val STRUCTURE_ROUTIVE_4 = "ROUTIVE_4"
/** ROUTIVE_4 order: 준비운동(0) → 근력(1) → 유산소(2) → 마무리(3). */
const val ROUTIVE_STAGE_COUNT = 4

data class WatchWorkoutState(
    val running: Boolean = false, val finished: Boolean = false,
    val hasSession: Boolean = false, val stageIndex: Int = 0,
    val stageName: String = "준비운동", val detail: String = "",
    val remaining: Int = 300, val deadline: Long = 0L,
    val protocol: Int = 0, val sequence: Long = 0L, val session: Long = 0L,
    val controlVersion: Long = 0L, val restDeadline: Long = 0L,
    val restPaused: Int = 0, val restId: Long = 0L,
    val exerciseId: String = "", val setNumber: Int = 0,
    val setLabel: String = "", val canComplete: Boolean = false, val day: Int = 1,
    val activeSeconds: Int = 0, val completedSets: Int = 0, val totalSets: Int = 0,
    val estimatedKcal: Double = 0.0, val exerciseName: String = "", val setTotal: Int = 0,
    val targetReps: Int = 0, val setWeight: String = "", val bodyFocus: String = "",
    val intervalIndex: Int = 0, val intervalTotal: Int = 0, val upcomingInterval: String = "",
    /** Seconds past the stage target while paused (published by the phone); running overtime is derived from [deadline]. */
    val overtime: Int = 0,
    /** Which workout structure the phone is running. Older phones send nothing = LEGACY_5 (five stages). */
    val structure: String = STRUCTURE_LEGACY_5, val stageCount: Int = STAGE_COUNT,
    /** false = the stage has no planned time: show elapsed time ([stageElapsed] measured at [stageElapsedAt]), never a countdown. */
    val plannedKnown: Boolean = true, val stageElapsed: Int = 0, val stageElapsedAt: Long = 0L
) {
    val isRoutive4: Boolean get() = structure == STRUCTURE_ROUTIVE_4
    val lastStageIndex: Int get() = stageCount - 1
    /** The strength stage: index 2 in LEGACY_5, index 1 in ROUTIVE_4. */
    val isStrength: Boolean get() = stageIndex == (if (isRoutive4) 1 else 2)
    /** Stages that count down a planned time and can run past it. */
    val isTimed: Boolean get() = if (isRoutive4) plannedKnown else isTimedStage(stageIndex)
    /** Elapsed seconds of the current stage when it has no plan; grows only while running. */
    fun elapsedSeconds(now: Long = System.currentTimeMillis()): Int =
        stageElapsed + if (running && stageElapsedAt > 0L) ((now - stageElapsedAt) / 1000L).toInt().coerceAtLeast(0) else 0
    /** "+mm:ss" source: seconds spent beyond the target of a timed stage, 0 otherwise. */
    fun overtimeSeconds(now: Long = System.currentTimeMillis()): Int = when {
        finished || !hasSession || !isTimed -> 0
        running && deadline > 0L -> if (now > deadline) ((now - deadline) / 1000L).toInt() else 0
        else -> overtime.coerceAtLeast(0)
    }
    val stageLabel: String get() = (stageIndex+1).toString()+"/$stageCount · "+stageName
    val ongoing: Boolean get() = hasSession && !finished
    fun secondsRemaining(now: Long = System.currentTimeMillis()): Int =
        if (running) ceil((deadline - now) / 1000.0).toInt().coerceAtLeast(0) else remaining
    fun restSeconds(now: Long = System.currentTimeMillis()): Int =
        if (restDeadline > 0L) ceil((restDeadline-now)/1000.0).toInt().coerceAtLeast(0) else restPaused
    fun save(context: Context): Boolean {
        val prefs = context.getSharedPreferences("watch_workout", Context.MODE_PRIVATE)
        val old = try { JSONObject(prefs.getString("state","{}")!!) } catch (_: Exception) { JSONObject() }
        val oldSequence = old.optLong("sequence")
        if (sequence < oldSequence) return false
        val json = JSONObject().put("running",running).put("finished",finished).put("hasSession",hasSession)
            .put("stageIndex",stageIndex).put("stageName",stageName).put("detail",detail)
            .put("remaining",remaining).put("deadline",deadline).put("protocol",protocol).put("sequence",sequence)
            .put("session",session).put("controlVersion",controlVersion).put("restDeadline",restDeadline)
            .put("restPaused",restPaused).put("restId",restId).put("exerciseId",exerciseId)
            .put("day",day).put("setNumber",setNumber).put("setLabel",setLabel).put("canComplete",canComplete)
            .put("activeSeconds",activeSeconds).put("completedSets",completedSets).put("totalSets",totalSets)
            .put("estimatedKcal",estimatedKcal).put("exerciseName",exerciseName).put("setTotal",setTotal)
            .put("targetReps",targetReps).put("setWeight",setWeight).put("bodyFocus",bodyFocus).put("intervalIndex",intervalIndex)
            .put("intervalTotal",intervalTotal).put("upcomingInterval",upcomingInterval).put("overtime",overtime)
            .put("structure",structure).put("stageCount",stageCount).put("plannedKnown",plannedKnown)
            .put("stageElapsed",stageElapsed).put("stageElapsedAt",stageElapsedAt)
        prefs.edit().putString("state",json.toString()).apply()
        // Changes within a stage (new exercise/cue or session) must also invalidate the tile.
        if (old.optInt("stageIndex") != stageIndex || old.optString("setLabel") != setLabel ||
            old.optString("exerciseName") != exerciseName || old.optString("detail") != detail ||
            old.optLong("session") != session || old.optBoolean("hasSession") != hasSession ||
            old.optBoolean("running") != running || old.optBoolean("finished") != finished ||
            old.optLong("restId") != restId || old.optString("structure") != structure)
            androidx.wear.tiles.TileService.getUpdater(context).requestUpdate(WorkoutTileService::class.java)
        return true
    }
    companion object {
        /** Unknown structure strings fall back to the five-stage layout so an unexpected value can never hide the legacy flow. */
        private fun structureOf(raw: String?) = if (raw == STRUCTURE_ROUTIVE_4) STRUCTURE_ROUTIVE_4 else STRUCTURE_LEGACY_5
        private fun countOf(structure: String) = if (structure == STRUCTURE_ROUTIVE_4) ROUTIVE_STAGE_COUNT else STAGE_COUNT
        fun fromData(d: DataMap): WatchWorkoutState {
            val structure = structureOf(d.getString("structure"))
            return fromDataWith(d, structure, countOf(structure))
        }
        private fun fromDataWith(d: DataMap, structure: String, count: Int) = WatchWorkoutState(
            running=d.getBoolean("running"),finished=d.getBoolean("finished"),
            hasSession=d.getBoolean("hasSession") || d.getBoolean("running"),
            stageIndex=d.getInt("stageIndex").coerceIn(0,count-1),
            stageName=d.getString("stageName") ?: d.getString("stage") ?: "준비운동",
            detail=d.getString("detail") ?: d.getString("stage") ?: "",
            remaining=d.getInt("remaining",300).coerceAtLeast(0),deadline=d.getLong("deadline"),
            protocol=d.getInt("protocol"),sequence=d.getLong("sequence"),session=d.getLong("session"),
            controlVersion=d.getLong("controlVersion"),restDeadline=d.getLong("restDeadline"),
            restPaused=d.getInt("restPaused"),restId=d.getLong("restId"),
            exerciseId=d.getString("exerciseId") ?: "",setNumber=d.getInt("setNumber"),
            setLabel=d.getString("setLabel") ?: "",canComplete=d.getBoolean("canComplete"),day=d.getInt("day",1),
            activeSeconds=d.getInt("activeSeconds"),completedSets=d.getInt("completedSets"),totalSets=d.getInt("totalSets"),
            estimatedKcal=d.getDouble("estimatedKcal"),exerciseName=d.getString("exerciseName") ?: "",setTotal=d.getInt("setTotal"),
            targetReps=d.getInt("targetReps"),setWeight=d.getString("setWeight") ?: "",bodyFocus=d.getString("bodyFocus") ?: "",
            intervalIndex=d.getInt("intervalIndex"),intervalTotal=d.getInt("intervalTotal"),
            upcomingInterval=d.getString("upcomingInterval") ?: "",overtime=d.getInt("overtime"),
            structure=structure, stageCount=count, plannedKnown=if (d.containsKey("plannedKnown")) d.getBoolean("plannedKnown") else true,
            stageElapsed=d.getInt("stageElapsed"), stageElapsedAt=d.getLong("stageElapsedAt"))
        fun load(context: Context): WatchWorkoutState = try {
            val j = JSONObject(context.getSharedPreferences("watch_workout", Context.MODE_PRIVATE).getString("state","{}")!!)
            val structure = structureOf(j.optString("structure"))
            val count = countOf(structure)
            WatchWorkoutState(
                running=j.optBoolean("running"),finished=j.optBoolean("finished"),hasSession=j.optBoolean("hasSession"),
                stageIndex=j.optInt("stageIndex").coerceIn(0,count-1),stageName=j.optString("stageName","준비운동"),
                detail=j.optString("detail"),remaining=j.optInt("remaining",300).coerceAtLeast(0),deadline=j.optLong("deadline"),
                protocol=j.optInt("protocol"),sequence=j.optLong("sequence"),session=j.optLong("session"),
                controlVersion=j.optLong("controlVersion"),restDeadline=j.optLong("restDeadline"),
                restPaused=j.optInt("restPaused"),restId=j.optLong("restId"),exerciseId=j.optString("exerciseId"),
                setNumber=j.optInt("setNumber"),setLabel=j.optString("setLabel"),canComplete=j.optBoolean("canComplete"),day=j.optInt("day",1),
                activeSeconds=j.optInt("activeSeconds"),completedSets=j.optInt("completedSets"),totalSets=j.optInt("totalSets"),
                estimatedKcal=j.optDouble("estimatedKcal"),exerciseName=j.optString("exerciseName"),setTotal=j.optInt("setTotal"),
                targetReps=j.optInt("targetReps"),setWeight=j.optString("setWeight"),bodyFocus=j.optString("bodyFocus"),intervalIndex=j.optInt("intervalIndex"),
                intervalTotal=j.optInt("intervalTotal"),upcomingInterval=j.optString("upcomingInterval"),overtime=j.optInt("overtime"),
                structure=structure, stageCount=count, plannedKnown=j.optBoolean("plannedKnown", true),
                stageElapsed=j.optInt("stageElapsed"), stageElapsedAt=j.optLong("stageElapsedAt"))
        } catch (_: Exception) { WatchWorkoutState() }
    }
}
