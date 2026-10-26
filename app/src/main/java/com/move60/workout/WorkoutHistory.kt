package com.move60.workout

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Isolated persistence for workout calendar data.
 * This does not mutate the legacy SessionStore or its preferences.
 * Call addActiveSeconds only with measured active time, never wall-clock elapsed time.
 */
class WorkoutHistory(context: Context) {
    private val prefs = context.getSharedPreferences("move60_workout_history_v1", Context.MODE_PRIVATE)
    private val zone = ZoneId.of("Asia/Seoul")

    data class DayRecord(val date: LocalDate, val activeSeconds: Long) {
        val activeMinutes: Long get() = activeSeconds / 60
    }

    fun record(date: LocalDate): DayRecord {
        val seconds = prefs.getLong(date.toString(), 0L).coerceAtLeast(0L)
        return DayRecord(date, seconds)
    }

    @Synchronized
    fun addActiveSeconds(date: LocalDate, seconds: Long): DayRecord {
        require(seconds >= 0L) { "Active seconds cannot be negative" }
        val previous = record(date).activeSeconds
        val updated = if (Long.MAX_VALUE - previous < seconds) Long.MAX_VALUE else previous + seconds
        check(prefs.edit().putLong(date.toString(), updated).commit()) {
            "Failed to persist workout history"
        }
        return DayRecord(date, updated)
    }

    fun today(): DayRecord = record(LocalDate.now(zone))

    fun month(month: YearMonth): List<DayRecord> =
        (1..month.lengthOfMonth()).map { record(month.atDay(it)) }

    fun trainedDays(month: YearMonth): Int = month(month).count { it.activeSeconds > 0L }

    fun totalActiveSeconds(month: YearMonth): Long =
        month(month).fold(0L) { total, day ->
            if (Long.MAX_VALUE - total < day.activeSeconds) Long.MAX_VALUE
            else total + day.activeSeconds
        }
}
