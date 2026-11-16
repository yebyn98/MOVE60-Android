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

/**
 * Rest between two strength sets. While it runs (session running and [done] false) time goes to [elapsedSeconds] only:
 * it is not stage time, not an active interval and not challenge time. When it reaches [totalSeconds] it is [done]
 * (kept as a visible cue until the next set, skip or stage change); later time counts as active again.
 */
data class RestState(val exerciseId: String, val totalSeconds: Long, val elapsedSeconds: Long = 0L, val cued: Boolean = false) {
    val done: Boolean get() = elapsedSeconds >= totalSeconds
    val remainingSeconds: Long get() = (totalSeconds - elapsedSeconds).coerceAtLeast(0L)
}

/** Immutable view of the active ROUTIVE_4 session. [progress] holds stage plan/elapsed; [intervals] are real active stretches. */
data class RoutiveSession(
    val routineId: String, val routineName: String,
    val recordDate: LocalDate, val day: Int, val startedAtEpochMs: Long,
    val running: Boolean, val checkpointEpochMs: Long,
    val progress: FourStageProgress, val intervals: List<Pair<Long, Long>>,
    /** The session was paused automatically because time that cannot be verified (service stopped, process died, reboot) was excluded. */
    val autoPaused: Boolean = false,
    /** Process that is tracking this running session; empty = nobody (tracking released). */
    val trackerToken: String = "",
    /** Rest after a completed set, null when none. Never started without a user-chosen rest time. */
    val rest: RestState? = null,
    /** The plan as it was when this session started (docs/v61 D1). Null for sessions started before it existed. */
    val planSnapshotJson: String? = null
) {
    val over: Boolean get() = progress.isOver
    val accumulatedSeconds: Long get() = progress.totalElapsedSeconds
}

/** One id per process. A running session whose tracker token differs was last tracked by a process that no longer exists. */
object RoutiveProcess { val token: String = java.util.UUID.randomUUID().toString() }

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
    private val dateNow: () -> LocalDate = { java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")) },
    private val processToken: String = RoutiveProcess.token
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
        val day = date.dayOfWeek.value   // new records support Mon..Sun (1..7); stored legacy records are never rewritten
        val session = RoutiveSession(routine.routineId, routine.name, date, day, now, true, now, plan, emptyList(), trackerToken = processToken,
            planSnapshotJson = RoutivePlanSnapshot.toJson(routine))
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

    /** The tracking service went away while the session still runs: count up to now, then mark it untracked. */
    fun releaseTracking(now: Long = clock()) {
        val s = read() ?: return
        if (s.over || !s.running) return
        write(settle(s, now).let { if (it.running) it.copy(trackerToken = "") else it }, durable = true)
    }

    fun pause(): RoutiveSession? = mutate { s, now -> settle(s, now).let { it.copy(running = false, checkpointEpochMs = 0L) } }

    fun resume(): RoutiveSession? = mutate { s, now ->
        if (s.over || s.running) s else s.copy(running = true, checkpointEpochMs = now, autoPaused = false, trackerToken = processToken)
    }

    /** Starts the rest timer after a completed set. [seconds] must be a user-chosen value; no default exists. */
    fun startRest(exerciseId: String, seconds: Long): RoutiveSession? = mutate { s, now ->
        if (s.progress.kind != com.move60.workout.domain.StageKind.STRENGTH || seconds !in RoutinePlanService.REST_RANGE) s
        else settle(s, now).let { it.copy(rest = RestState(exerciseId, seconds)) }
    }

    /**
     * True exactly once when a rest has just finished: whoever gets true (the screen or the service, never both) gives the
     * single end-of-rest vibration. The flag is stored, so a restart or a second observer cannot repeat it.
     */
    fun consumeRestCue(now: Long = clock()): Boolean {
        val s = reconcile(now) ?: return false
        val r = s.rest ?: return false
        if (!r.done || r.cued) return false
        write(s.copy(rest = r.copy(cued = true)), durable = true)
        return true
    }

    /** Ends the rest early (or clears the "rest finished" cue). The time already rested stays excluded. */
    fun skipRest(): RoutiveSession? = mutate { s, now -> settle(s, now).let { if (it.rest == null) it else it.copy(rest = null) } }

    fun advance(): RoutiveSession? = progressed { it.advance() }
    fun skip(): RoutiveSession? = progressed { it.skip() }
    fun back(): RoutiveSession? = progressed { it.back() }
    fun endEarly(): RoutiveSession? = progressed { it.endEarly() }

    /** Commit input for the existing idempotent sync: real intervals, real elapsed seconds and the sets actually completed. */
    fun snapshot(session: RoutiveSession = requireNotNull(read()) { "no active session" }, sets: SetRecordStore? = SetRecordStore(context)): TimerSnapshot =
        TimerSnapshot(
            session.recordDate, session.day, session.startedAtEpochMs, session.progress.finished, session.accumulatedSeconds.toInt(),
            session.intervals, session.progress.endedEarly,
            sets?.completedSetsForSession(session.startedAtEpochMs).orEmpty().map { TimerSet(it.exerciseId, it.setNumber, it.kg, it.reps) },
            stages = session.progress.plan.mapIndexed { i, p -> TimerStage(p.kind, p.plannedSeconds, session.progress.elapsedSeconds[i]) },
            planSnapshotJson = session.planSnapshotJson
        )

    // ---- internals ----------------------------------------------------------------------------------------------

    private fun settle(s: RoutiveSession, now: Long): RoutiveSession {
        if (!s.running || s.over || s.checkpointEpochMs <= 0L) return s
        // Time that nobody verifiably tracked (the tracking process is gone, or tracking was released) is never counted,
        // with no grace period: the session pauses at its last checkpoint and the user resumes.
        if (s.trackerToken != processToken) return s.copy(running = false, checkpointEpochMs = 0L, autoPaused = true)
        val delta = ((now - s.checkpointEpochMs) / 1000L).coerceAtLeast(0L)
        if (delta <= 0L) return s
        // Rest time is split off first: it advances only the rest timer, never stage time or an active interval.
        val rest = s.rest
        val restUsed = if (rest != null && !rest.done) minOf(delta, rest.remainingSeconds) else 0L
        val active = delta - restUsed
        val start = Math.floorDiv(s.checkpointEpochMs, 1000L) + restUsed
        var intervals = s.intervals
        if (active > 0L) {
            val last = s.intervals.lastOrNull()
            intervals = if (last != null && last.second == start) s.intervals.dropLast(1) + (last.first to start + active)
            else s.intervals + (start to start + active)
        }
        return s.copy(
            progress = if (active > 0L) s.progress.tick(active) else s.progress, intervals = intervals,
            checkpointEpochMs = s.checkpointEpochMs + delta * 1000L,
            rest = if (restUsed > 0L) rest!!.copy(elapsedSeconds = rest.elapsedSeconds + restUsed) else rest
        )
    }

    /** A session that just became over is no longer running. */
    private fun over(s: RoutiveSession) = if (s.over) s.copy(running = false, checkpointEpochMs = 0L) else s

    /** Settles running time into the current stage first, then applies a user action to the progress. */
    private fun progressed(action: (FourStageProgress) -> FourStageProgress): RoutiveSession? =
        mutate { s, now -> settle(s, now).let { over(it.copy(progress = action(it.progress), rest = null)) } }

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
            .put("running", s.running).put("checkpointAt", s.checkpointEpochMs).put("autoPaused", s.autoPaused).put("trackerToken", s.trackerToken)
            .put("planSnapshot", s.planSnapshotJson ?: JSONObject.NULL)
            .put("rest", s.rest?.let { JSONObject().put("exerciseId", it.exerciseId).put("total", it.totalSeconds).put("elapsed", it.elapsedSeconds).put("cued", it.cued) } ?: JSONObject.NULL)
            .put("stageIndex", p.stageIndex).put("finished", p.finished).put("endedEarly", p.endedEarly)
            .put("plan", JSONArray().also { a -> p.plan.forEach { a.put(it.plannedSeconds ?: JSONObject.NULL) } })
            .put("elapsed", JSONArray().also { a -> p.elapsedSeconds.forEach { a.put(it) } })
            .put("skipped", JSONArray().also { a -> p.skipped.sorted().forEach { a.put(it) } })
            .put("performed", JSONArray().also { a -> p.performed.sorted().forEach { a.put(it) } })
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
        val skipped = (0 until skippedArr.length()).map { skippedArr.getInt(it) }.toSet()
        val stageIndex = o.getInt("stageIndex")
        val finished = o.optBoolean("finished")
        // Sessions stored before "performed" existed: every stage already passed and not skipped was performed.
        val performed = o.optJSONArray("performed")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() }
            ?: ((0 until (if (finished) plan.size else stageIndex)).toSet() - skipped)
        val progress = FourStageProgress(
            plan, stageIndex, (0 until elapsedArr.length()).map { elapsedArr.getLong(it) }, skipped, performed, finished, o.optBoolean("endedEarly")
        )
        RoutiveSession(
            o.getString("routineId"), o.optString("routineName"), LocalDate.parse(o.getString("recordDate")), o.getInt("day"),
            o.getLong("startedAt"), o.optBoolean("running"), o.optLong("checkpointAt"), progress,
            (0 until ivArr.length()).map { ivArr.getJSONArray(it).let { p -> p.getLong(0) to p.getLong(1) } },
            o.optBoolean("autoPaused"), o.optString("trackerToken"),
            o.optJSONObject("rest")?.let { RestState(it.getString("exerciseId"), it.getLong("total"), it.getLong("elapsed"), it.optBoolean("cued")) },
            // Missing key (a session started before this existed) and JSON null both mean "no plan snapshot".
            if (o.isNull("planSnapshot")) null else o.getString("planSnapshot").takeIf { it.isNotEmpty() }
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
