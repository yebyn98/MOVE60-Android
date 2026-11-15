package com.move60.workout.architecture

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * ROUTIVE v2 persistence contract (Room schema v2). ADDITIVE ONLY: the legacy
 * `daily_workouts` / `workout_sets` tables and the SharedPreferences stores are not
 * modified, so existing history, settings and upgrade paths stay intact.
 *
 * Only COMMITTED (acknowledged) data lives in the tables below. In-progress edits live in
 * [SessionDraftEntity] and never change stored data until a save is acknowledged (E-03 A).
 * Dates are ISO local dates in the session's own [SessionEntity.zoneId] (P-25); a session
 * that crosses midnight has one [SessionDaySliceEntity] per date (P-20).
 */

/** A finalized exercise session snapshot (P-24). Plan values are kept apart from measured ones (P-21). */
@Entity(
    tableName = "sessions",
    indices = [Index(value = ["legacyToken"], unique = true), Index(value = ["source"])]
)
data class SessionEntity(
    @PrimaryKey val sessionId: String,
    /** [SessionSource] name. */
    val source: String,
    /** Legacy `SessionStore.sessionStartedAt` token when this session came from the 60-minute engine. */
    val legacyToken: Long?,
    val zoneId: String,
    val startedAtEpochMs: Long?,
    val endedAtEpochMs: Long?,
    /** [SessionCompletion] name. An unfinished workout is never stored as COMPLETED. */
    val completion: String,
    val totalActiveSeconds: Long,
    val completedStrengthSets: Int,
    /** Snapshot of `WorkoutRecognitionPolicy.validSession` at commit time. NOT challenge credit. */
    val validSession: Boolean,
    /** Planned values at start, kept separately from measured values. Null when no plan existed. */
    val planSnapshotJson: String?,
    val committedAtEpochMs: Long
)

/** Active seconds of a session attributed to one local date (idempotency key: sessionId + date). */
@Entity(
    tableName = "session_day_slices",
    primaryKeys = ["sessionId", "date"],
    foreignKeys = [ForeignKey(SessionEntity::class, ["sessionId"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["date"])]
)
data class SessionDaySliceEntity(
    val sessionId: String,
    val date: String,
    val activeSecondsOnDate: Long
)

/** Actual intensity segment / interval round. Raw segments are stored only for valid sessions (D-01 B). */
@Entity(
    tableName = "session_segments",
    primaryKeys = ["sessionId", "orderIndex"],
    foreignKeys = [ForeignKey(SessionEntity::class, ["sessionId"], ["sessionId"], onDelete = ForeignKey.CASCADE)]
)
data class SessionSegmentEntity(
    val sessionId: String,
    val orderIndex: Int,
    /** [SegmentKind] name. */
    val kind: String,
    val plannedSeconds: Long?,
    val actualSeconds: Long,
    val equipment: String?,
    /** User-entered console values with their units, preserved exactly as entered (never overwritten). */
    val metricsJson: String?
)

/** A strength set that was explicitly completed or skipped. Unrecorded sets are never stored. */
@Entity(
    tableName = "session_sets",
    primaryKeys = ["sessionId", "exerciseId", "setNumber"],
    foreignKeys = [ForeignKey(SessionEntity::class, ["sessionId"], ["sessionId"], onDelete = ForeignKey.CASCADE)]
)
data class SessionSetEntity(
    val sessionId: String,
    val exerciseId: String,
    val setNumber: Int,
    /** [SetState] name. */
    val state: String,
    val weightKg: String,
    val repetitions: String,
    val completedAtEpochMs: Long?
)

/** Append-only correction history (P-22/23/09). The DAO exposes no update or delete. */
@Entity(
    tableName = "session_adjustments",
    foreignKeys = [ForeignKey(SessionEntity::class, ["sessionId"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["sessionId"])]
)
data class SessionAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val adjustmentId: Long = 0,
    val sessionId: String,
    val createdAtEpochMs: Long,
    val field: String,
    val oldValue: String?,
    val newValue: String?,
    val reason: String?
)

/** Period-limited challenge. Journey (open-ended) is a separate domain and is not modelled here. */
@Entity(tableName = "challenges", indices = [Index(value = ["status"])])
data class ChallengeEntity(
    @PrimaryKey val challengeId: String,
    val name: String,
    val startDate: String,
    val endDate: String,
    val targetDays: Int,
    /** [ChallengeStatus] name. */
    val status: String,
    val createdAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val endSnapshotJson: String?
)

/** One credited challenge day (max one per challenge per date). Derived only from committed sessions. */
@Entity(
    tableName = "challenge_day_credits",
    primaryKeys = ["challengeId", "date"],
    foreignKeys = [ForeignKey(ChallengeEntity::class, ["challengeId"], ["challengeId"], onDelete = ForeignKey.CASCADE)]
)
data class ChallengeDayCreditEntity(
    val challengeId: String,
    val date: String,
    val qualifyingActiveSeconds: Long,
    val validSessionCount: Int,
    val creditedAtEpochMs: Long
)

/** Uncommitted edit buffer. A failed save keeps the draft; Stored data is untouched until ACK. */
@Entity(tableName = "session_drafts")
data class SessionDraftEntity(
    @PrimaryKey val draftId: String,
    val kind: String,
    val payloadJson: String,
    /** Monotonic edit revision; used to reject a stale writer. */
    val revision: Long,
    /** [DraftState] name. */
    val state: String,
    val lastErrorCode: String?,
    val updatedAtEpochMs: Long
)

/** Idempotency ledger for importing legacy data exactly once per source record. */
@Entity(tableName = "legacy_import_ledger", primaryKeys = ["source", "sourceKey"])
data class LegacyImportEntity(
    val source: String,
    val sourceKey: String,
    val targetSessionId: String,
    val importedAtEpochMs: Long
)

enum class SessionSource { LIVE, LEGACY_SESSION, LEGACY_DAY_AGGREGATE }
enum class SessionCompletion { COMPLETED, PARTIAL, STOPPED, UNKNOWN }
enum class SegmentKind { WARMUP, STRENGTH, CARDIO_WORK, CARDIO_RECOVERY, CARDIO_STEADY, COOLDOWN }
enum class SetState { COMPLETED, SKIPPED }
enum class ChallengeStatus { ACTIVE, COMPLETE, EXPIRED, ABANDONED }
enum class DraftState { EDITING, SAVING, FAILED }
