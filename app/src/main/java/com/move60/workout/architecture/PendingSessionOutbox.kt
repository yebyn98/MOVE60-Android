package com.move60.workout.architecture

import android.content.Context
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable hand-over buffer for a finished (or early-ended) timer session whose Room commit is not confirmed yet.
 *
 * The legacy timer state keeps exactly one session per day slot, and starting a new one (or resetting, switching the
 * day, a date rollover) replaces the previous intervals. Before that happens the finished session is copied here
 * with its real intervals and completed sets. An entry is removed only after the idempotent commit reports
 * Committed, AlreadyCommitted or DiscardedBelowThreshold; any other outcome keeps it for the next retry.
 * Nothing is invented: the entry is a verbatim copy of the timer snapshot.
 */
class PendingSessionOutbox(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun sessionId(s: TimerSnapshot) = "timer-${s.recordDate}-d${s.day}-${s.startedAtEpochMs}"

    /** Stores the snapshot once; an existing entry (and its retry bookkeeping) is kept. Returns true when stored or present. */
    fun enqueue(s: TimerSnapshot): Boolean {
        if (!(s.finished || s.endedEarly) || s.startedAtEpochMs <= 0L || s.intervals.isEmpty()) return false
        val id = sessionId(s)
        if (prefs.contains(id)) return true
        val json = JSONObject()
            .put("recordDate", s.recordDate.toString()).put("day", s.day).put("startedAt", s.startedAtEpochMs)
            .put("finished", s.finished).put("endedEarly", s.endedEarly).put("seconds", s.accumulatedSeconds)
            .put("intervals", JSONArray().also { a -> s.intervals.forEach { a.put(JSONArray().put(it.first).put(it.second)) } })
            .put("sets", JSONArray().also { a ->
                s.completedSets.forEach { a.put(JSONObject().put("id", it.exerciseId).put("n", it.setNumber).put("kg", it.weightKg).put("reps", it.repetitions)) }
            })
            .put("attempts", 0)
        // commit() (not apply()) so the copy is on disk before the caller overwrites the timer state.
        return prefs.edit().putString(id, json.toString()).commit()
    }

    fun pending(): List<TimerSnapshot> = prefs.all.keys.mapNotNull { read(it) }.sortedBy { it.startedAtEpochMs }

    fun size(): Int = prefs.all.size

    fun remove(sessionId: String) { prefs.edit().remove(sessionId).apply() }

    fun markFailed(sessionId: String, detail: String) {
        val raw = prefs.getString(sessionId, null) ?: return
        val json = try { JSONObject(raw) } catch (_: Exception) { return }
        json.put("attempts", json.optInt("attempts", 0) + 1).put("lastError", detail.take(200))
        prefs.edit().putString(sessionId, json.toString()).apply()
    }

    /** Sessions whose commit hit a conflict (same id, different content). They stay here untouched, are never merged automatically. */
    fun conflicts(): List<TimerSnapshot> = pending().filter { lastError(sessionId(it)) == CONFLICT }

    fun lastError(sessionId: String): String? =
        try { JSONObject(prefs.getString(sessionId, "{}") ?: "{}").optString("lastError").takeIf { it.isNotEmpty() } } catch (_: Exception) { null }

    fun attempts(sessionId: String): Int =
        try { JSONObject(prefs.getString(sessionId, "{}") ?: "{}").optInt("attempts", 0) } catch (_: Exception) { 0 }

    private fun read(id: String): TimerSnapshot? = try {
        val o = JSONObject(prefs.getString(id, null) ?: return null)
        val iv = o.getJSONArray("intervals")
        val sets = o.optJSONArray("sets") ?: JSONArray()
        TimerSnapshot(
            LocalDate.parse(o.getString("recordDate")), o.getInt("day"), o.getLong("startedAt"),
            o.optBoolean("finished"), o.getInt("seconds"),
            (0 until iv.length()).map { iv.getJSONArray(it).let { p -> p.getLong(0) to p.getLong(1) } },
            o.optBoolean("endedEarly"),
            (0 until sets.length()).map { sets.getJSONObject(it).let { s -> TimerSet(s.getString("id"), s.getInt("n"), s.getString("kg"), s.getString("reps")) } }
        )
    } catch (_: Exception) { null }

    companion object { const val NAME = "move60_pending_sessions_v1"; const val CONFLICT = "conflict" }
}

/** Retries every buffered session; entries leave the outbox only on a confirmed outcome. */
suspend fun TimerSessionSync.drain(outbox: PendingSessionOutbox): List<SyncOutcome> =
    outbox.pending().map { snapshot ->
        val id = sessionId(snapshot)
        val outcome = syncFinished(snapshot)
        when (outcome) {
            is SyncOutcome.Committed, is SyncOutcome.AlreadyCommitted, is SyncOutcome.Discarded -> outbox.remove(id)
            is SyncOutcome.Failed -> outbox.markFailed(id, outcome.detail)
            is SyncOutcome.Skipped -> outbox.markFailed(id, outcome.reason)
        }
        outcome
    }
