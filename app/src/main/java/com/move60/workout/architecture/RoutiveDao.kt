package com.move60.workout.architecture

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

/** Row used to feed `WorkoutRecognitionPolicy.evaluateChallengeDay` from committed data only. */
data class StoredDayContributionRow(
    val sessionId: String,
    val date: String,
    val activeSecondsOnDate: Long,
    val totalActiveSeconds: Long,
    val completedStrengthSets: Int
)

/**
 * Low-level access for the v2 tables. Multi-row atomicity is provided by
 * [SessionCommitter] via a single Room transaction, not by this interface.
 * Correction history deliberately has NO update or delete method (append-only).
 */
@Dao
interface RoutiveDao {
    // --- sessions (committed only) ---
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSession(session: SessionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSlices(slices: List<SessionDaySliceEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSegments(segments: List<SessionSegmentEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSets(sets: List<SessionSetEntity>)

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId") suspend fun session(sessionId: String): SessionEntity?
    @Query("SELECT * FROM session_day_slices WHERE sessionId = :sessionId ORDER BY date")
    suspend fun slices(sessionId: String): List<SessionDaySliceEntity>
    @Query("SELECT * FROM session_segments WHERE sessionId = :sessionId ORDER BY orderIndex")
    suspend fun segments(sessionId: String): List<SessionSegmentEntity>
    @Query("SELECT * FROM session_sets WHERE sessionId = :sessionId ORDER BY exerciseId, setNumber")
    suspend fun sets(sessionId: String): List<SessionSetEntity>

    // --- append-only correction history ---
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun appendAdjustment(adjustment: SessionAdjustmentEntity): Long
    @Query("SELECT * FROM session_adjustments WHERE sessionId = :sessionId ORDER BY adjustmentId")
    suspend fun adjustments(sessionId: String): List<SessionAdjustmentEntity>

    // --- challenges ---
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertChallenge(challenge: ChallengeEntity)
    @Query("SELECT * FROM challenges WHERE challengeId = :challengeId") suspend fun challenge(challengeId: String): ChallengeEntity?
    @Query("SELECT * FROM challenges WHERE status = 'ACTIVE' AND startDate <= :date AND endDate >= :date")
    suspend fun activeChallengesCovering(date: String): List<ChallengeEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCreditIfAbsent(credit: ChallengeDayCreditEntity): Long
    @Query("SELECT * FROM challenge_day_credits WHERE challengeId = :challengeId ORDER BY date")
    suspend fun credits(challengeId: String): List<ChallengeDayCreditEntity>

    /** Valid, committed sessions' per-date contribution. Invalid sessions never feed challenge credit. */
    @Query(
        "SELECT d.sessionId AS sessionId, d.date AS date, d.activeSecondsOnDate AS activeSecondsOnDate, " +
            "s.totalActiveSeconds AS totalActiveSeconds, s.completedStrengthSets AS completedStrengthSets " +
            "FROM session_day_slices d JOIN sessions s ON s.sessionId = d.sessionId " +
            "WHERE d.date = :date AND s.validSession = 1 ORDER BY d.sessionId"
    )
    suspend fun validContributionsOn(date: String): List<StoredDayContributionRow>

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

    // --- legacy import ledger ---
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun recordImport(entry: LegacyImportEntity): Long
}
