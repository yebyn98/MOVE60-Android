package com.move60.workout

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/**
 * Per-day, per-stage measured active time (calendar day detail).
 * Separate preferences file: legacy history, energy and set records keep their format.
 * Only measured active seconds are stored; days recorded before v43 simply have no entries.
 */
class StageLog(context: Context) {
    private val prefs = context.getSharedPreferences("move60_workout_stages_v1", Context.MODE_PRIVATE)

    /** [metSeconds] = measured seconds weighted by the stage MET estimate (used to split the day's kcal). */
    data class Entry(val stage: Int, val seconds: Long, val metSeconds: Double)

    private fun read(date: LocalDate): JSONObject = try {
        JSONObject(prefs.getString(date.toString(), "{}") ?: "{}")
    } catch (_: Exception) { JSONObject() }

    @Synchronized
    fun add(date: LocalDate, stage: Int, seconds: Long, met: Double) {
        if (seconds <= 0L || stage !in 0..LAST_STAGE) return
        val record = read(date)
        val old = record.optJSONObject(stage.toString()) ?: JSONObject()
        record.put(stage.toString(), JSONObject()
            .put("s", old.optLong("s", 0L) + seconds)
            .put("m", old.optDouble("m", 0.0) + seconds * met.coerceAtLeast(0.0)))
        prefs.edit().putString(date.toString(), record.toString()).apply()
    }

    /** Stages with recorded time, in plan order. Empty for days without stage data. */
    fun entries(date: LocalDate): List<Entry> {
        val record = read(date)
        return (0..LAST_STAGE).mapNotNull { stage ->
            val item = record.optJSONObject(stage.toString()) ?: return@mapNotNull null
            val seconds = item.optLong("s", 0L)
            if (seconds <= 0L) null else Entry(stage, seconds, item.optDouble("m", 0.0))
        }
    }
}
