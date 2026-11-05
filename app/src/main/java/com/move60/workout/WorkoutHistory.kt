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
    // Body part (upper/lower) trained on a day, stored separately so existing history keeps its format.
    private val focusPrefs = context.getSharedPreferences("move60_workout_focus_v1", Context.MODE_PRIVATE)
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

    /** Remember which body part was trained that day (only real upper/lower selections are stored). */
    fun recordFocus(date: LocalDate, focus: String?) {
        if (focus == "upper" || focus == "lower") focusPrefs.edit().putString(date.toString(), focus).apply()
    }

    /** "upper", "lower" or null when the day has no recorded body part (e.g. data from older versions). */
    fun focus(date: LocalDate): String? = focusPrefs.getString(date.toString(), null)

    /** Trained days in the Monday-first week containing [today], counting only up to today. */
    fun weekTrainedDays(today: LocalDate): Int {
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return (0..6).count { val d = monday.plusDays(it.toLong()); !d.isAfter(today) && isTrained(d) }
    }

    fun today(): DayRecord = record(LocalDate.now(zone))

    fun month(month: YearMonth): List<DayRecord> =
        (1..month.lengthOfMonth()).map { record(month.atDay(it)) }

    /** A day counts as trained only with at least one full minute of measured active time. */
    fun isTrained(date: LocalDate): Boolean = record(date).activeMinutes >= 1L

    fun trainedDays(month: YearMonth): Int = month(month).count { it.activeMinutes >= 1L }

    fun totalActiveSeconds(month: YearMonth): Long =
        month(month).fold(0L) { total, day ->
            if (Long.MAX_VALUE - total < day.activeSeconds) Long.MAX_VALUE
            else total + day.activeSeconds
        }

    companion object {
        /** The weekly plan covers Monday to Friday. */
        const val WEEKLY_GOAL_DAYS = 5
        /** Default monthly goal (about four plan weeks); not user-configurable yet. */
        const val MONTHLY_GOAL_DAYS = 20
    }
}
