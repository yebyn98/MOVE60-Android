package com.move60.workout.architecture

import android.content.Context
import com.move60.workout.SetRecordStore
import com.move60.workout.domain.FourStagePlans
import com.move60.workout.domain.FourStageProgress
import com.move60.workout.domain.StageTimePlan
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

sealed interface RoutiveStart {
    data class Started(val session: RoutiveSession) : RoutiveStart
    /** A running or paused session exists; it is never overwritten by a new start. */
    data object AlreadyActive : RoutiveStart
    /** The routine does not have exactly the four ROUTIVE_4 stages. */
    data object InvalidPlan : RoutiveStart
}

/** Immutable view of the active ROUTIVE_4 session. [progress] holds stage plan/elapsed; [intervals] are real active stretches. */
data class RoutiveSession(
    val routineId: String, val routineName: String,
    val recordDate: LocalDate, val day: Int, val startedAtEpochMs: Long,
    val running: Boolean, val checkpointEpochMs: Long,
    val progress: FourStageProgress, val intervals: List<Pair<Long, Long>>
) {
    val over: Boolean get() = progress.isOver
    val accumulatedSeconds: Long get() = progress.totalElapsedSeconds
}

/**
 * Durable store of ONE ROUTIVE_4 session (new SharedPreferences file; the legacy 5-stage SessionStore and its data are
 * never read, converted or written). Time is real active time: while running, seconds since the last checkpoint are
 * attributed to the current stage and recorded as an interval, so a killed process loses nothing and a paused session
 * accrues nothing. Nothing advances by itself. Finished/ended sessions are committed through [TimerSnapshot]/the existing
 * idempotent sync; a new start first copies an unconfirmed over-session to the [PendingSessionOutbox].
 */
class RoutiveSessionStore(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val dateNow: () -> LocalDate = { java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")) }
) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun active(): RoutiveSession? = read()

    /** The commit of this (over) session was confirmed (saved, already saved, or below the validity threshold). */
    fun markCommitted(startedAtEpochMs: Long) { prefs.edit().putLong(COMMITTED_KEY, startedAtEpochMs).commit() }
    fun isCommitted(s: RoutiveSession): Boolean = prefs.getLong(COMMITTED_KEY, 0L) == s.startedAtEpochMs
    /** An over session whose commit has not been confirmed yet (the only kind that is ever re-buffered). */
    fun pendingCommit(): RoutiveSession? = read()?.takeIf { it.over && !isCommitted(it) }

    /** Starts a session from a saved routine. Refuses to overwrite a running/paused one; an over-session is archived first. */
    fun start(routine: SavedRoutinePlan): RoutiveStart {
        val plan = FourStagePlans.from(routine.stages) ?: return RoutiveStart.InvalidPlan
        val current = read()
        if (current != null && !current.over) return RoutiveStart.AlreadyActive
        if (current != null) archive(current)
        // Data that exists but cannot be read is preserved under a backup key before the slot is reused (never deleted).
        prefs.getString(KEY, null)?.takeIf { current == null }?.let { prefs.edit().putString("unreadable_${clock()}", it).commit() }
        val now = clock()
        val date = dateNow()
        val day = date.dayOfWeek.value.coerceIn(1, 5)
        val session = RoutiveSession(routine.routineId, routine.name, date, day, now, true, now, plan, emptyList())
        SetRecordStore(context).beginSession(day, now)
        write(session, durable = true)
        return RoutiveStart.Started(session)
    }

    /** Attributes elapsed running time to the current stage. Returns the up-to-date session or null when there is none. */
    fun reconcile(now: Long = clock()): RoutiveSession? {
        val s = read() ?: return null
        val next = settle(s, now)
        return if (next === s) s else write(next, durable = false)
    }

    fun pause(): RoutiveSession? = mutate { s, now -> settle(s, now).let { it.copy(running = false, checkpointEpochMs = 0L) } }

    fun resume(): RoutiveSession? = mutate { s, now ->
        if (s.over || s.running) s else s.copy(running = true, checkpointEpochMs = now)
    }

    fun advance(): RoutiveSession? = progressed { it.advance() }
    fun skip(): RoutiveSession? = progressed { it.skip() }
    fun back(): RoutiveSession? = progressed { it.back() }
    fun endEarly(): RoutiveSession? = progressed { it.endEarly() }

    /** Commit input for the existing idempotent sync: real intervals, real elapsed seconds and the sets actually completed. */
    fun snapshot(session: RoutiveSession = requireNotNull(read()) { "no active session" }, sets: SetRecordStore? = SetRecordStore(context)): TimerSnapshot =
        TimerSnapshot(
            session.recordDate, session.day, session.startedAtEpochMs, session.progress.finished, session.accumulatedSeconds.toInt(),
            session.intervals, session.progress.endedEarly,
            sets?.completedSetsForSession(session.startedAtEpochMs).orEmpty().map { TimerSet(it.exerciseId, it.setNumber, it.kg, it.reps) }
        )

    // ---- internals ----------------------------------------------------------------------------------------------

    private fun settle(s: RoutiveSession, now: Long): RoutiveSession {
        if (!s.running || s.over || s.checkpointEpochMs <= 0L) return s
        val delta = ((now - s.checkpointEpochMs) / 1000L).coerceAtLeast(0L)
        if (delta <= 0L) return s
        val start = Math.floorDiv(s.checkpointEpochMs, 1000L)
        val last = s.intervals.lastOrNull()
        val intervals = if (last != null && last.second == start) s.intervals.dropLast(1) + (last.first to start + delta)
        else s.intervals + (start to start + delta)
        return s.copy(progress = s.progress.tick(delta), intervals = intervals, checkpointEpochMs = s.checkpointEpochMs + delta * 1000L)
    }

    /** A session that just became over is no longer running. */
    private fun over(s: RoutiveSession) = if (s.over) s.copy(running = false, checkpointEpochMs = 0L) else s

    /** Settles running time into the current stage first, then applies a user action to the progress. */
    private fun progressed(action: (FourStageProgress) -> FourStageProgress): RoutiveSession? =
        mutate { s, now -> settle(s, now).let { over(it.copy(progress = action(it.progress))) } }

    private fun mutate(change: (RoutiveSession, Long) -> RoutiveSession): RoutiveSession? {
        val s = read() ?: return null
        if (s.over) return s
        val next = change(s, clock())
        return write(next, durable = next.over)
    }

    private fun archive(s: RoutiveSession) {
        if (isCommitted(s)) return
        try { PendingSessionOutbox(context).enqueue(snapshot(s)) } catch (_: Throwable) { /* the slot stays intact on failure */ }
    }

    private fun write(s: RoutiveSession, durable: Boolean): RoutiveSession {
        val p = s.progress
        val json = JSONObject()
            .put("routineId", s.routineId).put("routineName", s.routineName)
            .put("recordDate", s.recordDate.toString()).put("day", s.day).put("startedAt", s.startedAtEpochMs)
            .put("running", s.running).put("checkpointAt", s.checkpointEpochMs)
            .put("stageIndex", p.stageIndex).put("finished", p.finished).put("endedEarly", p.endedEarly)
            .put("plan", JSONArray().also { a -> p.plan.forEach { a.put(it.plannedSeconds ?: JSONObject.NULL) } })
            .put("elapsed", JSONArray().also { a -> p.elapsedSeconds.forEach { a.put(it) } })
            .put("skipped", JSONArray().also { a -> p.skipped.sorted().forEach { a.put(it) } })
            .put("intervals", JSONArray().also { a -> s.intervals.forEach { a.put(JSONArray().put(it.first).put(it.second)) } })
        val edit = prefs.edit().putString(KEY, json.toString())
        if (durable) edit.commit() else edit.apply()
        return s
    }

    private fun read(): RoutiveSession? = try {
        val o = JSONObject(prefs.getString(KEY, null) ?: return null)
        val kinds = com.move60.workout.domain.WorkoutStructures.stages(com.move60.workout.domain.StructureVersion.ROUTIVE_4).map { it.kind }
        val planArr = o.getJSONArray("plan")
        val plan = kinds.indices.map { i -> StageTimePlan(kinds[i], if (planArr.isNull(i)) null else planArr.getLong(i)) }
        val elapsedArr = o.getJSONArray("elapsed")
        val skippedArr = o.optJSONArray("skipped") ?: JSONArray()
        val ivArr = o.getJSONArray("intervals")
        val progress = FourStageProgress(
            plan, o.getInt("stageIndex"), (0 until elapsedArr.length()).map { elapsedArr.getLong(it) },
            (0 until skippedArr.length()).map { skippedArr.getInt(it) }.toSet(), o.optBoolean("finished"), o.optBoolean("endedEarly")
        )
        RoutiveSession(
            o.getString("routineId"), o.optString("routineName"), LocalDate.parse(o.getString("recordDate")), o.getInt("day"),
            o.getLong("startedAt"), o.optBoolean("running"), o.optLong("checkpointAt"), progress,
            (0 until ivArr.length()).map { ivArr.getJSONArray(it).let { p -> p.getLong(0) to p.getLong(1) } }
        )
    } catch (_: Exception) { null }   // unreadable data is left in place, never overwritten or deleted by a read

    companion object { const val NAME = "move60_routive_session_v1"; private const val KEY = "active"; private const val COMMITTED_KEY = "committed_started_at" }
}

/** True when this sync outcome confirms what happens to the session with [id] (nothing more to retry). */
internal fun SyncOutcome.isConfirmedFor(id: String): Boolean = when (this) {
    is SyncOutcome.Committed -> sessionId == id
    is SyncOutcome.AlreadyCommitted -> sessionId == id
    is SyncOutcome.Discarded -> sessionId == id
    else -> false
}
