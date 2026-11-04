package com.move60.workout.data

import android.content.Context
import com.move60.workout.EnergyLedger
import com.move60.workout.SessionStore
import com.move60.workout.WorkoutHistory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Non-destructive migration adapter.
 * The foreground WorkoutService still owns workout time, set records and phone/watch sync.
 * Room is a reactive read model for Compose and never overwrites the legacy source.
 */
@Singleton
class WorkoutDashboardRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DailyWorkoutDao,
    private val history: WorkoutHistory,
    private val energy: EnergyLedger,
    private val session: SessionStore
) {
    private val zone = ZoneId.of("Asia/Seoul")
    private val preferences by lazy {
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    }

    fun observeRecent(): Flow<List<DailyWorkoutEntity>> = dao.observeRecent()
    fun currentDate(): LocalDate = LocalDate.now(zone)

    fun soundEnabled(): Boolean = preferences.getBoolean("sound", true)
    fun weightKg(): Float = preferences.getFloat("calorie_weight_kg", 63f)

    suspend fun setSoundEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        check(preferences.edit().putBoolean("sound", enabled).commit()) {
            "Unable to save notification sound setting"
        }
    }

    suspend fun setWeightKg(weight: Float) = withContext(Dispatchers.IO) {
        require(weight in 25f..300f) { "체중은 25~300kg 범위로 입력하세요." }
        check(preferences.edit().putFloat("calorie_weight_kg", weight).commit()) {
            "Unable to save calorie estimate weight"
        }
    }

    private fun snapshotFor(date: LocalDate): JSONObject? {
        if (date != currentDate()) return null
        session.reloadIfChanged()
        return JSONObject(session.snapshot())
    }

    private fun summary(date: LocalDate, now: Long, activeSnapshot: JSONObject?): DailyWorkoutEntity =
        DailyWorkoutEntity(
            date = date.toString(),
            activeSeconds = history.record(date).activeSeconds,
            estimatedKcal = energy.estimate(date, activeSnapshot).coerceAtLeast(0.0),
            updatedAtMillis = now
        )

    /** Refresh only today's observable row while the visible screen is STARTED. */
    suspend fun refreshToday() = withContext(Dispatchers.IO) {
        val today = currentDate()
        dao.upsert(summary(today, System.currentTimeMillis(), snapshotFor(today)))
    }

    /** Safe, idempotent cache import; scheduled in WorkManager even when UI closes. */
    suspend fun importHistory(days: Long = 45L) = withContext(Dispatchers.IO) {
        val today = currentDate()
        val now = System.currentTimeMillis()
        val current = snapshotFor(today)
        val records = (0L until days).map { i ->
            val date = today.minusDays(i)
            summary(date, now, if (date == today) current else null)
        }
        dao.upsertAll(records)
    }
}
