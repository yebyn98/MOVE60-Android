package com.move60.workout.architecture

import androidx.room.withTransaction
import com.move60.workout.domain.ActiveInterval
import com.move60.workout.domain.ChallengeRules
import com.move60.workout.domain.WorkoutRecognitionPolicy
import java.time.ZoneId

/**
 * Everything that makes up one finalized session. Per-date slices are NOT supplied: they are derived from the
 * UTC [intervals] in the session's own zone, so a caller can never state a date split that disagrees with time.
 */
data class SessionBundle(
    val session: SessionEntity,
    val intervals: List<SessionActiveIntervalEntity>,
    val sets: List<SessionSetEntity> = emptyList(),
    val segments: List<SessionSegmentEntity> = emptyList()
)

enum class RejectReason {
    BLANK_SESSION_ID, UNKNOWN_ENUM_VALUE, INVALID_ZONE, NEGATIVE_VALUE, INVALID_INTERVAL, DUPLICATE_INTERVAL,
    INTERVAL_OVERLAP, INTERVAL_SUM_OVERFLOW, INTERVAL_SUM_MISMATCH, DUPLICATE_SET, INVALID_SET,
    COMPLETED_SET_COUNT_MISMATCH, DUPLICATE_SEGMENT, INVALID_SEGMENT, INVALID_LEGACY_TOKEN, CHILD_SESSION_ID_MISMATCH
}

sealed interface CommitResult {
    /** Newly stored. [touchedChallenges] are ACTIVE challenges whose day aggregates were refreshed. */
    data class Committed(val sessionId: String, val touchedChallenges: List<String>) : CommitResult
    /** Identical snapshot was already stored; nothing changed (idempotent retry). */
    data class AlreadyCommitted(val sessionId: String) : CommitResult
    /**
     * Below the validity threshold at finalization (<60 active seconds and no completed strength set): NOTHING is
     * stored (no raw segments, no permanent summary) and the Draft is consumed (D-01 B, P-26, P-28 A).
     * Cancelling instead of finalizing never reaches [commit], so the Draft is kept.
     */
    data class DiscardedBelowThreshold(val sessionId: String) : CommitResult
    /** Same sessionId but different content. Stored data was NOT changed. */
    data class Conflict(val sessionId: String) : CommitResult
    data class Rejected(val reason: RejectReason, val detail: String) : CommitResult
    /** Storage failed; the transaction rolled back and any draft was kept for retry. */
    data class Failed(val cause: Throwable) : CommitResult
}

sealed interface DraftWriteResult {
    data class Saved(val revision: Long) : DraftWriteResult
    data class StaleRevision(val currentRevision: Long?) : DraftWriteResult
}

/** Pure validation, no Android or database dependency. */
object SessionBundleValidator {
    fun validate(bundle: SessionBundle): CommitResult.Rejected? {
        val s = bundle.session
        fun reject(reason: RejectReason, detail: String) = CommitResult.Rejected(reason, detail)

        if (s.sessionId.isBlank()) return reject(RejectReason.BLANK_SESSION_ID, "sessionId")
        if (enumOrNull<SessionCompletion>(s.completion) == null) return reject(RejectReason.UNKNOWN_ENUM_VALUE, "completion=${s.completion}")
        if (runCatching { ZoneId.of(s.zoneId) }.isFailure) return reject(RejectReason.INVALID_ZONE, s.zoneId)
        if (s.legacyToken != null && s.legacyToken <= 0L) return reject(RejectReason.INVALID_LEGACY_TOKEN, "${s.legacyToken}")
        if (s.totalActiveSeconds < 0L || s.completedStrengthSets < 0) return reject(RejectReason.NEGATIVE_VALUE, "session totals")

        if (bundle.intervals.any { it.sessionId != s.sessionId } || bundle.sets.any { it.sessionId != s.sessionId } ||
            bundle.segments.any { it.sessionId != s.sessionId }
        ) return reject(RejectReason.CHILD_SESSION_ID_MISMATCH, "child rows must reference ${s.sessionId}")

        // Active intervals: ordered, positive, non-overlapping, and summing exactly to the measured active time.
        var sum = 0L
        var previousEnd = Long.MIN_VALUE
        val intervalOrders = HashSet<Int>()
        for (iv in bundle.intervals.sortedBy { it.orderIndex }) {
            if (iv.orderIndex < 0 || !intervalOrders.add(iv.orderIndex)) return reject(RejectReason.DUPLICATE_INTERVAL, "order=${iv.orderIndex}")
            if (iv.endEpochSec <= iv.startEpochSec) return reject(RejectReason.INVALID_INTERVAL, "interval ${iv.orderIndex}")
            if (iv.startEpochSec < previousEnd) return reject(RejectReason.INTERVAL_OVERLAP, "interval ${iv.orderIndex}")
            previousEnd = iv.endEpochSec
            sum = try { Math.addExact(sum, iv.endEpochSec - iv.startEpochSec) } catch (_: ArithmeticException) {
                return reject(RejectReason.INTERVAL_SUM_OVERFLOW, "interval sum")
            }
        }
        if (sum != s.totalActiveSeconds) return reject(RejectReason.INTERVAL_SUM_MISMATCH, "intervals=$sum total=${s.totalActiveSeconds}")

        // Sets: only COMPLETED or SKIPPED rows exist; completed count must match the snapshot.
        val setKeys = HashSet<Pair<String, Int>>()
        var completed = 0
        for (set in bundle.sets) {
            val state = enumOrNull<SetState>(set.state) ?: return reject(RejectReason.UNKNOWN_ENUM_VALUE, "set state=${set.state}")
            if (set.exerciseId.isBlank() || set.setNumber < 1) return reject(RejectReason.INVALID_SET, "${set.exerciseId}#${set.setNumber}")
            if (!setKeys.add(set.exerciseId to set.setNumber)) return reject(RejectReason.DUPLICATE_SET, "${set.exerciseId}#${set.setNumber}")
            if (state == SetState.COMPLETED) {
                completed += 1
                val reps = set.repetitions.toIntOrNull()
                val kg = set.weightKg.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                if (reps == null || reps < 1) return reject(RejectReason.INVALID_SET, "completed set needs reps>=1")
                if (set.weightKg.isNotBlank() && (kg == null || kg.isNaN() || kg.isInfinite() || kg < 0.0)) {
                    return reject(RejectReason.INVALID_SET, "weight")
                }
            }
        }
        if (completed != s.completedStrengthSets) {
            return reject(RejectReason.COMPLETED_SET_COUNT_MISMATCH, "rows=$completed snapshot=${s.completedStrengthSets}")
        }

        // Segments: unique order, integer seconds > 0 (planned may be unknown = null, never 0).
        val orders = HashSet<Int>()
        for (seg in bundle.segments) {
            if (enumOrNull<SegmentKind>(seg.kind) == null) return reject(RejectReason.UNKNOWN_ENUM_VALUE, "segment kind=${seg.kind}")
            if (seg.orderIndex < 0 || !orders.add(seg.orderIndex)) return reject(RejectReason.DUPLICATE_SEGMENT, "order=${seg.orderIndex}")
            if (seg.actualSeconds <= 0L || (seg.plannedSeconds != null && seg.plannedSeconds <= 0L)) {
                return reject(RejectReason.INVALID_SEGMENT, "segment ${seg.orderIndex} seconds must be > 0")
            }
        }
        return null
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }
}

/**
 * Commits a finalized session atomically (E-03 A):
 *  - everything is written inside ONE Room transaction; any failure rolls back completely
 *  - Stored data changes only after the transaction is acknowledged; failure keeps the draft
 *  - the same sessionId is idempotent: identical retry = no-op, different content = Conflict
 *  - validity is recomputed here from `WorkoutRecognitionPolicy` (callers cannot assert it)
 *  - a below-threshold session is DISCARDED: no segments and no permanent summary are stored (D-01 B)
 *  - before storing, challenges whose end boundary has already passed are settled first, so a session saved
 *    late never changes an already ended challenge (P-24); afterwards the touched days of every still-ACTIVE
 *    challenge are re-computed from stored data (P-21), which is why a retry can never credit a day twice
 */
class SessionCommitter(private val db: WorkoutDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao get() = db.routiveDao()
    val challenges: ChallengeService = ChallengeService(db, clock)

    suspend fun saveDraft(draftId: String, kind: String, payloadJson: String, expectedRevision: Long?): DraftWriteResult =
        db.withTransaction {
            val now = clock()
            val current = dao.draft(draftId)
            when {
                current == null && expectedRevision == null -> {
                    dao.putDraft(SessionDraftEntity(draftId, kind, payloadJson, 1L, DraftState.EDITING.name, null, now))
                    DraftWriteResult.Saved(1L)
                }
                current == null || expectedRevision == null || current.revision != expectedRevision ->
                    DraftWriteResult.StaleRevision(current?.revision)
                else -> {
                    val next = current.revision + 1
                    val rows = dao.updateDraftIfRevision(draftId, expectedRevision, next, payloadJson, now)
                    if (rows == 1) DraftWriteResult.Saved(next) else DraftWriteResult.StaleRevision(dao.draft(draftId)?.revision)
                }
            }
        }

    /** Treat saves interrupted by process death as unknown-outcome; a retry is safe because commit is idempotent. */
    suspend fun recoverInterruptedSaves(): Int = dao.failInterruptedSaves(clock())

    suspend fun commit(bundle: SessionBundle, draftId: String? = null): CommitResult {
        SessionBundleValidator.validate(bundle)?.let { return it }
        val normalized = normalize(bundle)
        if (draftId != null) runCatching { dao.setDraftState(draftId, DraftState.SAVING.name, null, clock()) }
        return try {
            val result = db.withTransaction {
                val id = normalized.bundle.session.sessionId
                val existing = dao.session(id)
                if (existing != null) {
                    val same = sameContent(existing, normalized)
                    if (draftId != null && same) dao.deleteDraft(draftId)
                    return@withTransaction if (same) CommitResult.AlreadyCommitted(id) else CommitResult.Conflict(id)
                }
                if (!normalized.valid) {
                    if (draftId != null) dao.deleteDraft(draftId)      // finalized and discarded: the Draft is consumed
                    return@withTransaction CommitResult.DiscardedBelowThreshold(id)
                }
                val nowMs = clock()
                challenges.settleAllActive(nowMs)                      // late saves must not alter ended challenges
                val b = normalized.bundle
                dao.insertSession(b.session)
                if (b.intervals.isNotEmpty()) dao.insertIntervals(b.intervals)
                if (normalized.slices.isNotEmpty()) dao.insertSlices(normalized.slices)
                if (b.sets.isNotEmpty()) dao.insertSets(b.sets)
                if (b.segments.isNotEmpty()) dao.insertSegments(b.segments)
                val touched = challenges.onSessionStored(b.intervals.map { ActiveInterval(it.startEpochSec, it.endEpochSec) }, nowMs)
                if (draftId != null) dao.deleteDraft(draftId)
                CommitResult.Committed(id, touched)
            }
            if (draftId != null && result is CommitResult.Conflict) {
                runCatching { dao.setDraftState(draftId, DraftState.FAILED.name, "CONFLICT", clock()) }
            }
            result
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) {
                if (draftId != null) runCatching { dao.setDraftState(draftId, DraftState.FAILED.name, "UNKNOWN_OUTCOME", clock()) }
                throw error
            }
            if (draftId != null) runCatching { dao.setDraftState(draftId, DraftState.FAILED.name, "WRITE_FAILED", clock()) }
            CommitResult.Failed(error)
        }
    }

    private class Normalized(val bundle: SessionBundle, val valid: Boolean, val slices: List<SessionDaySliceEntity>)

    private fun normalize(bundle: SessionBundle): Normalized {
        val s = bundle.session
        val valid = WorkoutRecognitionPolicy.validSession(s.totalActiveSeconds, s.completedStrengthSets)
        val intervals = bundle.intervals.sortedBy { it.orderIndex }
        val slices = ChallengeRules
            .sliceByDate(intervals.map { ActiveInterval(it.startEpochSec, it.endEpochSec) }, ZoneId.of(s.zoneId))
            .map { (date, seconds) -> SessionDaySliceEntity(s.sessionId, date.toString(), seconds) }
        return Normalized(
            bundle.copy(
                session = s.copy(validSession = valid, committedAtEpochMs = clock()),
                intervals = intervals,
                sets = bundle.sets.sortedWith(compareBy({ it.exerciseId }, { it.setNumber })),
                segments = bundle.segments.sortedBy { it.orderIndex }
            ),
            valid, slices
        )
    }

    private suspend fun sameContent(existing: SessionEntity, incoming: Normalized): Boolean {
        val b = incoming.bundle
        return incoming.valid &&
            existing.copy(committedAtEpochMs = 0L) == b.session.copy(committedAtEpochMs = 0L) &&
            dao.intervals(existing.sessionId) == b.intervals &&
            dao.slices(existing.sessionId) == incoming.slices &&
            dao.sets(existing.sessionId) == b.sets &&
            dao.segments(existing.sessionId) == b.segments
    }
}
