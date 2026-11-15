package com.move60.workout.architecture

/**
 * Calendar sources for one date. The legacy `daily_workouts` total and the new-engine session seconds are returned
 * SEPARATELY and never summed (C-20261115-04.4): when both exist, the device cannot tell whether they describe the
 * same time, so the caller must show them by source instead of adding them up.
 * Legacy totals are never turned into sessions and never feed challenge credit.
 */
data class DayHistorySources(val date: String, val legacyActiveSeconds: Long?, val sessionActiveSeconds: Long) {
    val hasLegacy: Boolean get() = legacyActiveSeconds != null
    val hasSessions: Boolean get() = sessionActiveSeconds > 0L
}

class DayHistoryReader(private val db: WorkoutDatabase) {
    suspend fun day(date: String): DayHistorySources =
        DayHistorySources(date, db.workoutDao().day(date)?.activeSeconds, db.routiveDao().sessionSecondsOn(date))
}
