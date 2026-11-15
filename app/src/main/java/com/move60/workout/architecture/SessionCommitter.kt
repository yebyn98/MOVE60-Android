package com.move60.workout.architecture

import androidx.room.withTransaction
import com.move60.workout.domain.StoredWorkoutDayContribution
import com.move60.workout.domain.WorkoutRecognitionPolicy
import java.time.LocalDate
import java.time.ZoneId

/** Everything that makes up one finalized session. Built only from measured/confirmed values. */
data class SessionBundle(
    val session: SessionEntity,
    val slices: List<SessionDaySliceEntity>,
    val sets: List<SessionSetEntity> = emptyList(),
    val segments: List<SessionSegmentEntity> = emptyList()
)

enum class RejectReason {
    BLANK_SESSION_ID, UNKNOWN_ENUM_VALUE, INVALID_ZONE, NEGATIVE_VALUE, INVALID_DATE, DUPLICATE_DAY_SLICE,
    SLICE_SUM_MISMATCH, SLICE_SUM_OVERFLOW, DUPLICATE_SET, INVALID_SET, COMPLETED_SET_COUNT_MISMATCH,
    DUPLICATE_SEGMENT, INVALID_SEGMENT, INVALID_LEGACY_TOKEN, LEGACY_AGGREGATE_MUST_BE_UNKNOWN,
    CHILD_SESSION_ID_MISMATCH
}

sealed interface CommitResult {
    /** Newly stored. [creditedChallenges] lists challenge IDs that gained a credited day. */
    data class Committed(val sessionId: String, val creditedChallenges: List<String>) : CommitResult
    /** Identical snapshot was already stored; nothing changed (idempotent retry). */
    data class AlreadyCommitted(val sessionId: String) : CommitResult
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
        if (enumOrNull<SessionSource>(s.source) == null) return reject(RejectReason.UNKNOWN_ENUM_VALUE, "source=${s.source}")
        val completion = enumOrNull<SessionCompletion>(s.completion)
            ?: return reject(RejectReason.UNKNOWN_ENUM_VALUE, "completion=${s.completion}")
        if (runCatching { ZoneId.of(s.zoneId) }.isFailure) return reject(RejectReason.INVALID_ZONE, s.zoneId)
        if (s.legacyToken != null && s.legacyToken <= 0L) return reject(RejectReason.INVALID_LEGACY_TOKEN, "${s.legacyToken}")
        if (s.source == SessionSource.LEGACY_DAY_AGGREGATE.name && completion != SessionCompletion.UNKNOWN) {
            return reject(RejectReason.LEGACY_AGGREGATE_MUST_BE_UNKNOWN, "a day aggregate cannot claim completion")
        }
        if (s.totalActiveSeconds < 0L || s.completedStrengthSets < 0) return reject(RejectReason.NEGATIVE_VALUE, "session totals")

        if (bundle.slices.any { it.sessionId != s.sessionId } || bundle.sets.any { it.sessionId != s.sessionId } ||
            bundle.segments.any { it.sessionId != s.sessionId }
        ) return reject(RejectReason.CHILD_SESSION_ID_MISMATCH, "child rows must reference ${s.sessionId}")

        // Day slices: unique valid dates, non-negative, exact sum (never wall-clock based).
        val dates = HashSet<String>()
        var sum = 0L
        for (slice in bundle.slices) {
            if (runCatching { LocalDate.parse(slice.date) }.isFailure) return reject(RejectReason.INVALID_DATE, slice.date)
            if (!dates.add(slice.date)) return reject(RejectReason.DUPLICATE_DAY_SLICE, slice.date)
            if (slice.activeSecondsOnDate < 0L) return reject(RejectReason.NEGATIVE_VALUE, "slice ${slice.date}")
            sum = try { Math.addExact(sum, slice.activeSecondsOnDate) } catch (_: ArithmeticException) {
                return reject(RejectReason.SLICE_SUM_OVERFLOW, "slice sum")
            }
        }
        if (sum != s.totalActiveSeconds) return reject(RejectReason.SLICE_SUM_MISMATCH, "slices=$sum total=${s.totalActiveSeconds}")

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
 *  - challenge credit (>=600s summed over VALID stored sessions, max one per date) is derived
 *    from committed rows, separately from ordinary-session validity
 *  - raw segments of a below-threshold session are not stored (D-01 B); its summary row is kept
 *    with validSession=false and never counts toward anything.
 */
class SessionCommitter(private val db: WorkoutDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao get() = db.routiveDao()

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
                val existing = dao.session(normalized.session.sessionId)
                if (existing != null) {
                    val same = sameContent(existing, normalized)
                    if (draftId != null && same) dao.deleteDraft(draftId)
                    return@withTransaction if (same) CommitResult.AlreadyCommitted(existing.sessionId)
                    else CommitResult.Conflict(existing.sessionId)
                }
                dao.insertSession(normalized.session)
                dao.insertSlices(normalized.slices)
                if (normalized.sets.isNotEmpty()) dao.insertSets(normalized.sets)
                if (normalized.segments.isNotEmpty()) dao.insertSegments(normalized.segments)
                val credited = if (normalized.session.validSession) creditChallenges(normalized) else emptyList()
                if (draftId != null) dao.deleteDraft(draftId)
                CommitResult.Committed(normalized.session.sessionId, credited)
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

    private fun normalize(bundle: SessionBundle): SessionBundle {
        val s = bundle.session
        val valid = WorkoutRecognitionPolicy.validSession(s.totalActiveSeconds, s.completedStrengthSets)
        return bundle.copy(
            session = s.copy(validSession = valid, committedAtEpochMs = clock()),
            slices = bundle.slices.sortedBy { it.date },
            sets = bundle.sets.sortedWith(compareBy({ it.exerciseId }, { it.setNumber })),
            segments = if (valid) bundle.segments.sortedBy { it.orderIndex } else emptyList()
        )
    }

    private suspend fun sameContent(existing: SessionEntity, incoming: SessionBundle): Boolean =
        existing.copy(committedAtEpochMs = 0L) == incoming.session.copy(committedAtEpochMs = 0L) &&
            dao.slices(existing.sessionId) == incoming.slices &&
            dao.sets(existing.sessionId) == incoming.sets &&
            dao.segments(existing.sessionId) == incoming.segments

    private suspend fun creditChallenges(bundle: SessionBundle): List<String> {
        val credited = ArrayList<String>()
        for (slice in bundle.slices) {
            if (slice.activeSecondsOnDate <= 0L) continue
            val active = dao.activeChallengesCovering(slice.date)
            if (active.isEmpty()) continue
            val date = LocalDate.parse(slice.date)
            val contributions = dao.validContributionsOn(slice.date).map {
                StoredWorkoutDayContribution(it.sessionId, LocalDate.parse(it.date), it.activeSecondsOnDate, it.totalActiveSeconds, it.completedStrengthSets)
            }
            val decision = WorkoutRecognitionPolicy.evaluateChallengeDay(date, contributions)
            if (!decision.credited) continue
            for (challenge in active) {
                // INSERT OR IGNORE on (challengeId, date): at most one credited day per challenge per date.
                val inserted = dao.insertCreditIfAbsent(
                    ChallengeDayCreditEntity(challenge.challengeId, slice.date, decision.qualifyingActiveSeconds, decision.validSessionCount, clock())
                )
                if (inserted != -1L) credited += challenge.challengeId
            }
        }
        return credited.distinct()
    }
}
