package com.move60.workout

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min

/** Gross MET-based estimates. External load is volume, never a direct kcal conversion. */
class EnergyLedger(private val context: Context) {
    private val prefs = context.getSharedPreferences("move60_energy_v1", Context.MODE_PRIVATE)
    private val zone = ZoneId.of("Asia/Seoul")
    // Keep current-workout estimates independent of the historical daily energy ledger.
    // The first timestamp of a session is the identity; pauses and app restarts reuse it.
    private val sessionPrefs = context.getSharedPreferences("move60_session_energy_v1", Context.MODE_PRIVATE)
    private fun readSession(token: Long) = try {
        JSONObject(sessionPrefs.getString(token.toString(), "{}") ?: "{}")
    } catch (_: Exception) { JSONObject() }
    private fun weight() = context.getSharedPreferences("preferences", Context.MODE_PRIVATE).getFloat("calorie_weight_kg", 63f).toDouble()
    private fun read(date: LocalDate) = try { JSONObject(prefs.getString(date.toString(), "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    @Synchronized fun add(date: LocalDate, from: Long, until: Long, snapshot: JSONObject) {
        val record = read(date)
        val start = maxOf(from, record.optLong("last_end", from))
        if (until <= start) return
        if (!record.has("seconds")) record.put("legacy_met_seconds", WorkoutHistory(context).record(date).activeSeconds * 4.5)
        append(record, (until - start) / 1000.0, snapshot, start + (until - start) / 2)
        record.put("last_end", until).put("weight", weight())
        val token = snapshot.optLong("sessionStartedAt", 0L)
        if (token > 0L) {
            val sessionRecord = readSession(token)
            val sessionFrom = maxOf(start, sessionRecord.optLong("last_end", token), token)
            if (until > sessionFrom) {
                append(sessionRecord, (until - sessionFrom) / 1000.0, snapshot,
                    sessionFrom + (until - sessionFrom) / 2)
                sessionRecord.put("last_end", until).put("weight", weight())
                check(sessionPrefs.edit().putString(token.toString(), sessionRecord.toString()).commit()) {
                    "Failed to persist session calorie estimate"
                }
            }
        }
        check(prefs.edit().putString(date.toString(), record.toString()).commit())
    }
    private fun append(record: JSONObject, seconds: Double, snapshot: JSONObject, now: Long) {
        if (snapshot.optInt("stage") == 2) record.put("strength_seconds", record.optDouble("strength_seconds", 0.0) + seconds)
        else record.put("met_seconds", record.optDouble("met_seconds", 0.0) + seconds * CalorieEstimator.snapshotMet(snapshot, now))
        record.put("seconds", record.optDouble("seconds", 0.0) + seconds)
    }
    fun estimate(date: LocalDate, pending: JSONObject? = null): Double {
        val record = read(date)
        if (!record.has("seconds")) record.put("legacy_met_seconds", WorkoutHistory(context).record(date).activeSeconds * 4.5)
        if (pending != null && pending.optBoolean("running") && !pending.optBoolean("finished")) {
            val now = System.currentTimeMillis()
            val checkpoint = context.getSharedPreferences("timer", Context.MODE_PRIVATE).getLong("history_checkpoint_ms", 0L)
            val midnight = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val from = maxOf(checkpoint, record.optLong("last_end"), midnight)
            if (checkpoint > 0L && now > from) append(record, (now-from)/1000.0, pending, now)
        }
        val total = record.optDouble("seconds", 0.0)
        if (total <= 0.0) return WorkoutHistory(context).record(date).activeSeconds / 60.0 * 4.5 * 3.5 * weight() / 200.0
        val kg = if (date == LocalDate.now(zone)) weight() else record.optDouble("weight", weight())
        return calculate(record, SetRecordStore(context).completed(date), kg)
    }

    /** The active workout starts at 0 kcal regardless of earlier workouts that day. */
    fun estimateSession(snapshot: JSONObject): Double {
        val token = snapshot.optLong("sessionStartedAt", 0L)
        if (token <= 0L) return 0.0
        val record = readSession(token)
        if (snapshot.optBoolean("running") && !snapshot.optBoolean("finished")) {
            val checkpoint = context.getSharedPreferences("timer", Context.MODE_PRIVATE)
                .getLong("history_checkpoint_ms", 0L)
            // Live preview only: do not mutate preferences until the service checkpoints time.
            val from = maxOf(checkpoint, record.optLong("last_end", token), token)
            val now = System.currentTimeMillis()
            if (checkpoint > 0L && now > from)
                append(record, (now - from) / 1000.0, snapshot, from + (now - from) / 2)
        }
        return calculate(record, SetRecordStore(context).completedForSession(token),
            record.optDouble("weight", weight()))
    }

    private fun calculate(record: JSONObject, sets: List<SetRecordStore.Record>, kg: Double): Double {
        val strength = record.optDouble("strength_seconds", 0.0)
        val strengthMetSeconds = if (sets.isEmpty()) strength * 3.5 else {
            // As with the daily ledger, strength is estimated using actual active seconds.
            val requestedWork = sets.sumOf { it.reps.toInt() * 3.0 }
            val work = min(strength, requestedWork)
            val meanMet = sets.sumOf { it.reps.toInt() * if (it.rpe.toInt() >= 7) 6.0 else 3.5 } /
                sets.sumOf { it.reps.toInt() }.coerceAtLeast(1)
            work * meanMet + (strength - work) * 1.5
        }
        return ((record.optDouble("met_seconds", 0.0) +
            record.optDouble("legacy_met_seconds", 0.0) + strengthMetSeconds) /
            60.0 * 3.5 * kg / 200.0).coerceAtLeast(0.0)
    }
}
