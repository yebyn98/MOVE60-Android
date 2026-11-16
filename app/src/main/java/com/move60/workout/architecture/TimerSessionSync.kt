package com.move60.workout.architecture

import com.move60.workout.SessionStore
import java.time.LocalDate

/** Read-only view of the legacy timer state needed to commit a finished session. */
data class TimerSnapshot(
    val recordDate: LocalDate,
    val day: Int,
    val startedAtEpochMs: Long,
    val finished: Boolean,
    val accumulatedSeconds: Int,
    val intervals: List<Pair<Long, Long>>,
    val endedEarly: Boolean = false,
    /** Sets the user actually completed in this workout (real exercise id, weight and reps from SetRecordStore). */
    val completedSets: List<TimerSet> = emptyList(),
    /** Per-stage planned and ACTUAL seconds of a ROUTIVE_4 session (docs/v61 D2); empty for the legacy 5-stage timer. */
    val stages: List<TimerStage> = emptyList(),
    /** Plan at session start as JSON (docs/v61 D1); null when there was none. Never read from the live routine. */
    val planSnapshotJson: String? = null
)

data class TimerSet(val exerciseId: String, val setNumber: Int, val weightKg: String, val repetitions: String)

/** One stage of a ROUTIVE_4 session: the plan (null = unknown, never 0) and the real active seconds spent in it. */
data class TimerStage(val kind: com.move60.workout.domain.StageKind, val plannedSeconds: Long?, val actualSeconds: Long)

/**
 * Stage actuals as `session_segments` rows (docs/v61 D2): `orderIndex` = stage index, one row per stage that has real time.
 * A stage with 0 seconds gets no row (the validator rejects 0). If the stages do not add up to the measured active time the
 * whole list is dropped, so an inconsistent record is never stored; the session itself is still saved.
 */
internal fun stageSegments(sessionId: String, s: TimerSnapshot): List<SessionSegmentEntity> {
    if (s.stages.isEmpty()) return emptyList()
    if (s.stages.any { it.actualSeconds < 0L } || s.stages.sumOf { it.actualSeconds } != s.accumulatedSeconds.toLong()) return emptyList()
    return s.stages.mapIndexedNotNull { index, st ->
        if (st.actualSeconds <= 0L) null
        else SessionSegmentEntity(sessionId, index, segmentKindOf(st.kind).name, st.plannedSeconds, st.actualSeconds, null, null)
    }
}

private fun segmentKindOf(kind: com.move60.workout.domain.StageKind): SegmentKind = when (kind) {
    com.move60.workout.domain.StageKind.WARM_UP -> SegmentKind.WARMUP
    com.move60.workout.domain.StageKind.STRENGTH -> SegmentKind.STRENGTH
    com.move60.workout.domain.StageKind.CARDIO -> SegmentKind.CARDIO_STEADY
    com.move60.workout.domain.StageKind.COOL_DOWN -> SegmentKind.COOLDOWN
}

fun SessionStore.timerSnapshot(setStore: com.move60.workout.SetRecordStore? = null) = TimerSnapshot(
    recordDate, day, sessionStartedAt, finished, accumulatedActiveSeconds, activeIntervals(), endedEarly,
    setStore?.completedSetsForSession(sessionStartedAt).orEmpty().map { TimerSet(it.exerciseId, it.setNumber, it.kg, it.reps) }
)

sealed interface SyncOutcome {
    data class Skipped(val reason: String) : SyncOutcome
    data class Committed(val sessionId: String) : SyncOutcome
    data class AlreadyCommitted(val sessionId: String) : SyncOutcome
    data class Discarded(val sessionId: String) : SyncOutcome
    data class Failed(val detail: String) : SyncOutcome
}

/**
 * Bridges the legacy native timer to the Room v2 session store. Only a session that the user finished is committed,
 * and only from real recorded active intervals: older states without intervals, or whose interval total does not
 * match the timer's own active seconds, are skipped instead of being reconstructed. Never throws into the timer flow.
 */
class TimerSessionSync(private val committer: SessionCommitter, private val zoneId: () -> String) {
    fun sessionId(s: TimerSnapshot) = "timer-${s.recordDate}-d${s.day}-${s.startedAtEpochMs}"

    fun bundleOrNull(s: TimerSnapshot): SessionBundle? {
        if (!(s.finished || s.endedEarly) || s.startedAtEpochMs <= 0L || s.intervals.isEmpty()) return null
        if (s.intervals.any { it.second <= it.first }) return null
        if (s.intervals.sumOf { it.second - it.first } != s.accumulatedSeconds.toLong()) return null
        val id = sessionId(s)
        val rows = s.intervals.mapIndexed { i, (a, b) -> SessionActiveIntervalEntity(id, i, a, b) }
        val session = SessionEntity(
            id, null, zoneId(), s.intervals.first().first * 1000L, s.intervals.last().second * 1000L,
            (if (s.finished) SessionCompletion.COMPLETED else SessionCompletion.PARTIAL).name, s.accumulatedSeconds.toLong(),
            s.completedSets.size, false, s.planSnapshotJson?.takeIf { it.isNotBlank() }, 0L
        )
        // Only COMPLETED sets are stored; the committer recomputes validity (>=60 s OR >=1 completed set, P-26 B).
        val sets = s.completedSets.map { SessionSetEntity(id, it.exerciseId, it.setNumber, SetState.COMPLETED.name, it.weightKg, it.repetitions, null) }
        return SessionBundle(session, rows, sets, stageSegments(id, s))
    }

    suspend fun syncFinished(s: TimerSnapshot): SyncOutcome {
        val bundle = bundleOrNull(s) ?: return SyncOutcome.Skipped(
            if (!(s.finished || s.endedEarly)) "not finished" else "no verifiable interval record"
        )
        return try {
            when (val r = committer.commit(bundle)) {
                is CommitResult.Committed -> SyncOutcome.Committed(r.sessionId)
                is CommitResult.AlreadyCommitted -> SyncOutcome.AlreadyCommitted(r.sessionId)
                is CommitResult.DiscardedBelowThreshold -> SyncOutcome.Discarded(r.sessionId)
                is CommitResult.Conflict -> SyncOutcome.Failed("conflict")
                is CommitResult.Rejected -> SyncOutcome.Failed("rejected: ${r.reason}")
                is CommitResult.Failed -> SyncOutcome.Failed(r.cause.message ?: "write failed")
            }
        } catch (e: Exception) { SyncOutcome.Failed(e.message ?: "error") }
    }

    /** App foreground: commit a finished timer session first, then settle challenges whose boundary has passed. */
    suspend fun onForeground(s: TimerSnapshot?): SyncOutcome {
        val outcome = if (s == null) SyncOutcome.Skipped("no snapshot") else syncFinished(s)
        try { committer.challenges.settleDue() } catch (_: Exception) { /* retried on next foreground */ }
        return outcome
    }
}
