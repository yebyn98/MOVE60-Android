package com.move60.workout.architecture

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

/** One stored active interval of a VALID session, joined with the session facts the policy needs. */
data class StoredIntervalRow(
    val sessionId: String,
    val startEpochSec: Long,
    val endEpochSec: Long,
    val totalActiveSeconds: Long,
    val completedStrengthSets: Int
)

/**
 * Low-level access for the v2 tables. Multi-row atomicity is provided by [SessionCommitter] /
 * [ChallengeService] via a single Room transaction, not by this interface.
 * Correction history deliberately has NO update or delete method (append-only).
 */
@Dao
interface RoutiveDao {
    // --- sessions (committed, valid only) ---
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSession(session: SessionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertIntervals(intervals: List<SessionActiveIntervalEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSlices(slices: List<SessionDaySliceEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSegments(segments: List<SessionSegmentEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSets(sets: List<SessionSetEntity>)

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId") suspend fun session(sessionId: String): SessionEntity?
    @Query("SELECT * FROM session_active_intervals WHERE sessionId = :sessionId ORDER BY orderIndex")
    suspend fun intervals(sessionId: String): List<SessionActiveIntervalEntity>
    @Query("SELECT * FROM session_day_slices WHERE sessionId = :sessionId ORDER BY date")
    suspend fun slices(sessionId: String): List<SessionDaySliceEntity>
    @Query("SELECT * FROM session_segments WHERE sessionId = :sessionId ORDER BY orderIndex")
    suspend fun segments(sessionId: String): List<SessionSegmentEntity>
    @Query("SELECT * FROM session_sets WHERE sessionId = :sessionId ORDER BY exerciseId, setNumber")
    suspend fun sets(sessionId: String): List<SessionSetEntity>
    /** New-engine active seconds on a session-zone date. Kept separate from legacy `daily_workouts` (never summed). */
    @Query("SELECT COALESCE(SUM(activeSecondsOnDate), 0) FROM session_day_slices WHERE date = :date")
    suspend fun sessionSecondsOn(date: String): Long

    /** Session-zone ISO dates in [from, to] that have stored session time (record calendar dots). Read-only. */
    @Query("SELECT DISTINCT date FROM session_day_slices WHERE date >= :from AND date <= :to ORDER BY date")
    suspend fun sessionDatesBetween(from: String, to: String): List<String>

    /** Every session-zone ISO date that has stored session time (MY tab lifetime count). Read-only. */
    @Query("SELECT DISTINCT date FROM session_day_slices ORDER BY date")
    suspend fun allSessionDates(): List<String>

    /** Stored sessions with time on [date], earliest start first (record day list). Read-only. */
    @Query(
        "SELECT s.* FROM sessions s INNER JOIN session_day_slices d ON d.sessionId = s.sessionId " +
            "WHERE d.date = :date ORDER BY s.startedAtEpochMs, s.sessionId"
    )
    suspend fun sessionsOn(date: String): List<SessionEntity>

    /** Stored session seconds per session-zone date in [from, to] (month summary). Read-only. */
    @Query("SELECT date AS date, SUM(activeSecondsOnDate) AS seconds FROM session_day_slices WHERE date >= :from AND date <= :to GROUP BY date")
    suspend fun sessionSecondsByDate(from: String, to: String): List<DaySeconds>

    @Query("SELECT activeSecondsOnDate FROM session_day_slices WHERE sessionId = :sessionId AND date = :date")
    suspend fun sliceSeconds(sessionId: String, date: String): Long?

    /** Valid sessions' intervals overlapping [windowStart, windowEnd) UTC epoch seconds. */
    @Query(
        "SELECT i.sessionId AS sessionId, i.startEpochSec AS startEpochSec, i.endEpochSec AS endEpochSec, " +
            "s.totalActiveSeconds AS totalActiveSeconds, s.completedStrengthSets AS completedStrengthSets " +
            "FROM session_active_intervals i JOIN sessions s ON s.sessionId = i.sessionId " +
            "WHERE s.validSession = 1 AND i.startEpochSec < :windowEnd AND i.endEpochSec > :windowStart " +
            "ORDER BY i.sessionId, i.orderIndex"
    )
    suspend fun validIntervalsOverlapping(windowStart: Long, windowEnd: Long): List<StoredIntervalRow>

    // --- append-only correction history ---
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun appendAdjustment(adjustment: SessionAdjustmentEntity): Long
    @Query("SELECT * FROM session_adjustments WHERE sessionId = :sessionId ORDER BY adjustmentId")
    suspend fun adjustments(sessionId: String): List<SessionAdjustmentEntity>

    // --- challenges ---
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertChallenge(challenge: ChallengeEntity)
    @Query("SELECT * FROM challenges WHERE challengeId = :challengeId") suspend fun challenge(challengeId: String): ChallengeEntity?
    @Query("SELECT * FROM challenges WHERE status = 'ACTIVE' ORDER BY createdAtEpochMs")
    suspend fun activeChallenges(): List<ChallengeEntity>
    /** Only an ACTIVE challenge can end; COMPLETE / EXPIRED / ABANDONED are final (returns 0 rows otherwise). */
    @Query(
        "UPDATE challenges SET status = :status, endedAtEpochMs = :endedAt, endSnapshotJson = :snapshot " +
            "WHERE challengeId = :challengeId AND status = 'ACTIVE'"
    )
    suspend fun finishChallenge(challengeId: String, status: String, endedAt: Long, snapshot: String): Int
    @Query("SELECT * FROM challenges WHERE status != 'ACTIVE' ORDER BY endedAtEpochMs DESC")
    suspend fun endedChallenges(): List<ChallengeEntity>
    /** Rename is allowed only while ACTIVE; ended challenges keep their frozen snapshot (returns 0 rows otherwise). */
    @Query("UPDATE challenges SET name = :name WHERE challengeId = :challengeId AND status = 'ACTIVE'")
    suspend fun renameActive(challengeId: String, name: String): Int
    @Upsert suspend fun upsertCredit(credit: ChallengeDayCreditEntity)
    @Query("SELECT * FROM challenge_day_credits WHERE challengeId = :challengeId ORDER BY date")
    suspend fun credits(challengeId: String): List<ChallengeDayCreditEntity>
    @Query("SELECT * FROM challenge_day_credits WHERE challengeId = :challengeId AND date = :date")
    suspend fun credit(challengeId: String, date: String): ChallengeDayCreditEntity?

    // --- drafts ---
    @Upsert suspend fun putDraft(draft: SessionDraftEntity)
    @Query("SELECT * FROM session_drafts WHERE draftId = :draftId") suspend fun draft(draftId: String): SessionDraftEntity?
    @Query("SELECT * FROM session_drafts ORDER BY updatedAtEpochMs") suspend fun drafts(): List<SessionDraftEntity>
    @Query("DELETE FROM session_drafts WHERE draftId = :draftId") suspend fun deleteDraft(draftId: String): Int
    @Query(
        "UPDATE session_drafts SET payloadJson = :payload, revision = :newRevision, state = 'EDITING', " +
            "lastErrorCode = NULL, updatedAtEpochMs = :now WHERE draftId = :draftId AND revision = :expectedRevision"
    )
    suspend fun updateDraftIfRevision(draftId: String, expectedRevision: Long, newRevision: Long, payload: String, now: Long): Int
    @Query("UPDATE session_drafts SET state = :state, lastErrorCode = :code, updatedAtEpochMs = :now WHERE draftId = :draftId")
    suspend fun setDraftState(draftId: String, state: String, code: String?, now: Long): Int
    @Query("UPDATE session_drafts SET state = 'FAILED', lastErrorCode = 'UNKNOWN_OUTCOME', updatedAtEpochMs = :now WHERE state = 'SAVING'")
    suspend fun failInterruptedSaves(now: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRoutinePlan(plan: RoutinePlanEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRouteStages(stages: List<RoutineStagePlanEntity>)
    @Query("SELECT * FROM routine_plans WHERE routineId = :id") suspend fun routinePlan(id: String): RoutinePlanEntity?
    @Query("SELECT * FROM routine_stage_plans WHERE routineId = :id ORDER BY orderIndex") suspend fun routineStages(id: String): List<RoutineStagePlanEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRoutineExercises(items: List<RoutineExerciseEntity>)
    @Query("SELECT * FROM routine_exercises WHERE routineId = :id ORDER BY stageIndex, orderIndex") suspend fun routineExercises(id: String): List<RoutineExerciseEntity>
    @Query("SELECT * FROM routine_plans ORDER BY updatedAtEpochMs DESC") suspend fun routinePlans(): List<RoutinePlanEntity>
}

/** Query result: stored session seconds on one date. */
data class DaySeconds(val date: String, val seconds: Long)
