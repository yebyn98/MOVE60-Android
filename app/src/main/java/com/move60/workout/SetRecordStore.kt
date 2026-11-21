package com.move60.workout

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** Dated per-set drafts and performed values; legacy aggregate logs remain untouched. */
class SetRecordStore(context: Context) {
    private val prefs = context.getSharedPreferences("move60_set_records_v1", Context.MODE_PRIVATE)
    data class Record(val kg: String = "", val reps: String = "", val rpe: String = "5", val completed: Boolean = false, val skipped: Boolean = false) {
        val valid: Boolean get() = kg.toDoubleOrNull()?.let { it in 0.0..com.move60.workout.domain.SetLimits.MAX_KG } == true &&
            reps.toIntOrNull()?.let { it in com.move60.workout.domain.SetLimits.MIN_REPS..com.move60.workout.domain.SetLimits.MAX_REPS } == true &&
            rpe.toIntOrNull()?.let { it in 1..10 } == true
        val volume: Double get() = if (completed && valid) kg.toDouble() * reps.toInt() else 0.0
    }
    fun today() = LocalDate.now(ZoneId.of("Asia/Seoul"))
    private fun key(date: LocalDate, day: Int, id: String, set: Int, token: Long) = "set:$date:$day:$token:$id:$set"
    fun read(day: Int, id: String, set: Int, date: LocalDate = today(), token: Long = 0L): Record =
        decode(prefs.getString(key(date, day, id, set, token), null) ?: prefs.getString(key(date, day, id, set, 0L), null)).let { if (token > 0L && !prefs.contains(key(date, day, id, set, token))) it.copy(completed = false) else it }
    fun write(day: Int, id: String, set: Int, record: Record, date: LocalDate = today(), token: Long = 0L) {
        val json = JSONObject().put("kg", record.kg).put("reps", record.reps).put("rpe", record.rpe)
            .put("completed", record.completed && record.valid && !record.skipped)
            .put("skipped", record.skipped && !record.completed)
        val edit = prefs.edit().putString(key(date, day, id, set, token), json.toString())
        if (token > 0L) edit.putString(key(date, day, id, set, 0L), JSONObject(json.toString()).put("completed", false).toString())
        edit.apply()
    }
    /**
     * Explicit skip is a separate durable state: a pending set is never silently converted to SKIPPED by moving stages.
     * A synchronous write prevents an app/process exit from losing the user's confirmed decision.
     */
    fun markSkipped(day: Int, id: String, set: Int, date: LocalDate, token: Long): Boolean {
        if (token <= 0L || id.isBlank() || set < 1) return false
        val before = read(day, id, set, date, token)
        if (before.completed || before.skipped) return false
        val json = JSONObject().put("kg", before.kg).put("reps", before.reps).put("rpe", before.rpe)
            .put("completed", false).put("skipped", true)
        return prefs.edit().putString(key(date, day, id, set, token), json.toString()).commit()
    }

    /** Reverses only an explicit skip, without touching completed sets or other session tokens. */
    fun undoSkipped(day: Int, id: String, set: Int, date: LocalDate, token: Long): Boolean {
        if (token <= 0L || set < 1) return false
        val before = read(day, id, set, date, token)
        if (!before.skipped || before.completed) return false
        val json = JSONObject().put("kg", before.kg).put("reps", before.reps).put("rpe", before.rpe)
            .put("completed", false).put("skipped", false)
        return prefs.edit().putString(key(date, day, id, set, token), json.toString()).commit()
    }

    fun previous(id: String, set: Int, currentToken: Long): Record? {
        val date = today().toString()
        return prefs.all.entries.mapNotNull { (key, value) ->
            val parts = key.split(':')
            if (parts.size != 6 || parts[0] != "set" || parts[1] > date || parts[4] != id || parts[5] != set.toString()) return@mapNotNull null
            val token = parts[3].toLongOrNull() ?: return@mapNotNull null
            val record = decode(value as? String)
            if (token <= 0L || token == currentToken || !record.completed || !record.valid) null
            else Triple(parts[1], token, record)
        }.sortedWith(compareByDescending<Triple<String, Long, Record>> { it.first }.thenByDescending { it.second }).firstOrNull()?.third
    }
    fun completed(date: LocalDate): List<Record> = prefs.all.filterKeys { it.startsWith("set:$date:") && it.split(':').getOrNull(3) != "0" }
        .values.map { decode(it as? String) }.filter { it.completed && it.valid }
    /** Completed records belonging to a single workout, not all workouts on the date. */
    fun completedForSession(token: Long): List<Record> {
        if (token <= 0L) return emptyList()
        return prefs.all.filterKeys { key ->
            val parts = key.split(':')
            parts.size == 6 && parts[0] == "set" && parts[3] == token.toString()
        }.values.map { decode(it as? String) }.filter { it.completed && it.valid }
    }

    data class CompletedSet(val exerciseId: String, val setNumber: Int, val kg: String, val reps: String)
    data class SkippedSet(val exerciseId: String, val setNumber: Int)

    /** Session-scoped explicit skips; they are never included in completed set count or exercise volume. */
    fun skippedSetsForSession(token: Long): List<SkippedSet> {
        if (token <= 0L) return emptyList()
        return prefs.all.mapNotNull { (rawKey, value) ->
            val parts = rawKey.split(':')
            if (parts.size != 6 || parts[0] != "set" || parts[3] != token.toString()) return@mapNotNull null
            val number = parts[5].toIntOrNull()?.takeIf { it >= 1 } ?: return@mapNotNull null
            val record = decode(value as? String)
            if (record.skipped && !record.completed) SkippedSet(parts[4], number) else null
        }.sortedWith(compareBy({ it.exerciseId }, { it.setNumber }))
    }
    /**
     * Completed, valid sets of ONE workout (identified by its session token), with the exercise id and set number
     * parsed from the storage key. Never mixes other workouts of the same date. Ordered for stable comparison.
     */
    fun completedSetsForSession(token: Long): List<CompletedSet> {
        if (token <= 0L) return emptyList()
        return prefs.all.mapNotNull { (key, value) ->
            val parts = key.split(':')
            if (parts.size != 6 || parts[0] != "set" || parts[3] != token.toString()) return@mapNotNull null
            val number = parts[5].toIntOrNull() ?: return@mapNotNull null
            val record = decode(value as? String)
            if (!record.completed || !record.valid) null else CompletedSet(parts[4], number, record.kg, record.reps)
        }.sortedWith(compareBy({ it.exerciseId }, { it.setNumber }))
    }
    /** A completed set parsed from its storage key, with the exact stored kg/reps strings. */
    data class StoredSet(
        val date: LocalDate, val day: Int, val token: Long,
        val exerciseId: String, val setNumber: Int, val kg: String, val reps: String
    )

    /**
     * READ-ONLY policy-5 view of every completed, valid set that belongs to a workout (token > 0). Drafts (token 0),
     * uncompleted or invalid values and keys that do not parse exactly are left out; nothing is changed or deleted.
     * Keys are unique, so one stored set can never appear twice.
     */
    fun completedStoredSets(): List<StoredSet> = prefs.all.mapNotNull { (key, value) ->
        val parts = key.split(':')
        if (parts.size != 6 || parts[0] != "set") return@mapNotNull null
        val date = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return@mapNotNull null
        val day = parts[2].toIntOrNull() ?: return@mapNotNull null
        val token = parts[3].toLongOrNull()?.takeIf { it > 0L } ?: return@mapNotNull null
        val exerciseId = parts[4].takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val number = parts[5].toIntOrNull()?.takeIf { it >= 1 } ?: return@mapNotNull null
        val record = decode(value as? String)
        if (!record.completed || !record.valid) null else StoredSet(date, day, token, exerciseId, number, record.kg, record.reps)
    }.sortedWith(compareBy({ it.date }, { it.token }, { it.exerciseId }, { it.setNumber }))

    fun beginSession(day: Int, token: Long) {
        if (token <= 0L) return
        val prefix = "set:${today()}:$day:0:"
        val edit = prefs.edit()
        prefs.all.filterKeys { it.startsWith(prefix) }.forEach { (key, value) ->
            val target = key.replace(prefix, "set:${today()}:$day:$token:")
            if (!prefs.contains(target)) edit.putString(target, value as? String)
        }
        edit.apply()
    }
    private fun decode(value: String?): Record = try {
        val o = JSONObject(value ?: "{}")
        Record(o.optString("kg"), o.optString("reps"), o.optString("rpe", "5"), o.optBoolean("completed"), o.optBoolean("skipped", false))
    } catch (_: Exception) { Record() }
}

