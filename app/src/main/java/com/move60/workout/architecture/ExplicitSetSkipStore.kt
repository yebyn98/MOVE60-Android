package com.move60.workout.architecture

import android.content.Context
import com.move60.workout.SetRecordStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Session-scoped, explicit strength-set skip decisions. Stored separately from the legacy completed-set records,
 * which must never be overwritten or implicitly converted to SKIPPED by a stage transition.
 *
 * Every change is appended to the per-set history, including an undo. No Room migration is needed: the final
 * session's current SKIPPED decisions are copied to existing session_sets rows by TimerSessionSync.
 */
internal class ExplicitSetSkipStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val completed = SetRecordStore(context)

    data class SkippedSet(val exerciseId: String, val setNumber: Int, val decidedAtEpochMs: Long)

    private fun key(token: Long, exerciseId: String, setNumber: Int) = "$token:$exerciseId:$setNumber"

    fun skippedForSession(token: Long): List<SkippedSet> {
        if (token <= 0L) return emptyList()
        return synchronized(LOCK) {
            prefs.all.asSequence().filter { it.key.startsWith("$token:") }.mapNotNull { (_, raw) ->
                val obj = runCatching { JSONObject(raw as? String ?: return@mapNotNull null) }.getOrNull()
                    ?: return@mapNotNull null
                if (obj.optString("state") != SKIPPED) return@mapNotNull null
                val id = obj.optString("exerciseId")
                val n = obj.optInt("setNumber", 0)
                if (id.isBlank() || n < 1) null else SkippedSet(id, n, obj.optLong("decidedAt", 0L))
            }.sortedWith(compareBy({ it.exerciseId }, { it.setNumber })).toList()
        }
    }

    /** True only for a durable, explicit decision. A completed set can never be skipped. Idempotent on retry. */
    fun skip(token: Long, exerciseId: String, setNumber: Int, nowMs: Long = System.currentTimeMillis()): Boolean =
        synchronized(LOCK) {
            if (token <= 0L || exerciseId.isBlank() || setNumber < 1 || nowMs <= 0L) return@synchronized false
            if (completed.completedSetsForSession(token).any { it.exerciseId == exerciseId && it.setNumber == setNumber })
                return@synchronized false
            val key = key(token, exerciseId, setNumber)
            val existing = prefs.getString(key, null)
            val row = if (existing == null) JSONObject()
                else runCatching { JSONObject(existing) }.getOrNull() ?: return@synchronized false
            when (row.optString("state")) {
                SKIPPED -> return@synchronized true
                "", UNDECIDED -> Unit
                else -> return@synchronized false
            }
            val history = row.optJSONArray("history") ?: JSONArray()
            history.put(JSONObject().put("state", SKIPPED).put("at", nowMs))
            row.put("exerciseId", exerciseId).put("setNumber", setNumber)
                .put("state", SKIPPED).put("decidedAt", nowMs).put("history", history)
            prefs.edit().putString(key, row.toString()).commit()
        }

    /** Undo changes only the current decision; the original SKIPPED history remains append-only. */
    fun undo(token: Long, exerciseId: String, setNumber: Int, nowMs: Long = System.currentTimeMillis()): Boolean =
        synchronized(LOCK) {
            if (token <= 0L || exerciseId.isBlank() || setNumber < 1 || nowMs <= 0L) return@synchronized false
            val key = key(token, exerciseId, setNumber)
            val existing = prefs.getString(key, null) ?: return@synchronized false
            val row = runCatching { JSONObject(existing) }.getOrNull() ?: return@synchronized false
            if (row.optString("state") != SKIPPED) return@synchronized false
            val history = row.optJSONArray("history") ?: JSONArray()
            history.put(JSONObject().put("state", UNDECIDED).put("at", nowMs))
            row.put("state", UNDECIDED).put("history", history)
            prefs.edit().putString(key, row.toString()).commit()
        }

    /** Read-only decision history for diagnostic tests and future correction auditing. */
    fun history(token: Long, exerciseId: String, setNumber: Int): List<String> = synchronized(LOCK) {
        if (token <= 0L) return@synchronized emptyList()
        val raw = prefs.getString(key(token, exerciseId, setNumber), null) ?: return@synchronized emptyList()
        val array = runCatching { JSONObject(raw).optJSONArray("history") }.getOrNull()
            ?: return@synchronized emptyList()
        (0 until array.length()).mapNotNull { i ->
            runCatching { array.getJSONObject(i).getString("state") }.getOrNull()
        }
    }

    companion object {
        const val NAME = "move60_explicit_set_skip_v1"
        private const val SKIPPED = "SKIPPED"
        private const val UNDECIDED = "UNDECIDED"
        private val LOCK = Any()
    }
}
