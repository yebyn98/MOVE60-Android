package com.move60.workout.architecture

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * ROUTIVE v2 persistence contract (Room schema v2, not yet released). ADDITIVE ONLY: the legacy
 * `daily_workouts` / `workout_sets` tables and the SharedPreferences stores are not modified, so
 * existing history, settings and upgrade paths stay intact. Legacy day totals are NOT converted into
 * sessions (C-20261115-04); they stay the old calendar record and never feed challenge credit.
 *
 * Only COMMITTED (acknowledged) data lives in the tables below. In-progress edits live in
 * [SessionDraftEntity] and never change stored data until a save is acknowledged (E-03 A).
 * A below-threshold session is never stored at all (D-01 B / P-26 / P-28).
 * The source of truth for time is [SessionActiveIntervalEntity] (UTC). Per-date slices in the
 * session's zone are derived from it for the calendar (P-20); challenge days are derived from the
 * intervals in the CHALLENGE's own fixed zone (P-25), never from another zone's slices.
 */

/** A finalized exercise session snapshot (P-24). Plan values are kept apart from measured ones (P-21). */
@Entity(
    tableName = "sessions",
    indices = [Index(value = ["legacyToken"], unique = true)]
)
data class SessionEntity(
    @PrimaryKey val sessionId: String,
    /** The running engine's `SessionStore.sessionStartedAt` token, to correlate with the old engine while both exist. */
    val legacyToken: Long?,
    val zoneId: String,
    val startedAtEpochMs: Long?,
    val endedAtEpochMs: Long?,
    /** [SessionCompletion] name. An unfinished workout is never stored as COMPLETED. */
    val completion: String,
    val totalActiveSeconds: Long,
    val completedStrengthSets: Int,
    /** `WorkoutRecognitionPolicy.validSession` at commit time; always true for a stored session. NOT challenge credit. */
    val validSession: Boolean,
    /** Planned values at start, kept separately from measured values. Null when no plan existed. */
    val planSnapshotJson: String?,
    val committedAtEpochMs: Long
)

/** Measured active stretch in UTC epoch seconds. Their lengths sum to `totalActiveSeconds`; pauses are excluded. */
@Entity(
    tableName = "session_active_intervals",
    primaryKeys = ["sessionId", "orderIndex"],
    foreignKeys = [ForeignKey(SessionEntity::class, ["sessionId"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["startEpochSec"]), Index(value = ["endEpochSec"])]
)
data class SessionActiveIntervalEntity(
    val sessionId: String,
    val orderIndex: Int,
    val startEpochSec: Long,
    val endEpochSec: Long
)

/** Active seconds attributed to one local date of the SESSION's zone (derived for the calendar; P-20). */
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

/**
 * Cardio equipment value the user SAVED during a cardio stage (docs/v71). One row per input event and metric; the original row is
 * never updated (a later correction is a new `session_adjustments` row). [atActiveSeconds] is session ACTIVE elapsed seconds, so a
 * pause never counts. [valueText] is exactly what the user typed (a number); nothing is estimated or filled in.
 */
@Entity(
    tableName = "session_cardio_inputs",
    primaryKeys = ["sessionId", "inputIndex", "metricKey"],
    foreignKeys = [ForeignKey(SessionEntity::class, ["sessionId"], ["sessionId"], onDelete = ForeignKey.CASCADE)]
)
data class SessionCardioInputEntity(
    val sessionId: String,
    val inputIndex: Int,
    val stageIndex: Int,
    val atActiveSeconds: Int,
    /** `Equipment` enum name, e.g. INDOOR_CYCLE. */
    val equipmentKey: String,
    /** [com.move60.workout.domain.CardioMetric.key]. */
    val metricKey: String,
    val valueText: String,
    /** Unit label shown with the value (null = none). */
    val unit: String?,
    val enteredAtEpochMs: Long
)

/** Period-limited challenge. Journey (open-ended) is a separate domain and is not modelled here. */
@Entity(tableName = "challenges", indices = [Index(value = ["status"])])
data class ChallengeEntity(
    @PrimaryKey val challengeId: String,
    val name: String,
    /** First day (inclusive), ISO local date in [timeZoneId]. */
    val startDate: String,
    /** Last day (INCLUSIVE). Expiry is judged at endDate + 1 day 00:00 in [timeZoneId] (C-20261115-03). */
    val endDate: String,
    /** IANA zone captured at creation. Never follows the device afterwards (P-25). */
    val timeZoneId: String,
    /** CUMULATIVE credited days, not consecutive (C-20261115-02). */
    val targetDays: Int,
    /** [ChallengeStatus] name. */
    val status: String,
    val createdAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val endSnapshotJson: String?
)

/**
 * Re-computable read model of one challenge date (P-21). While the challenge is ACTIVE it is rebuilt from the
 * committed valid sessions every time a related session is saved/corrected, so [activeSeconds] always shows the
 * latest total and a day is credited at most once ([credited] = total >= 600 s). It is frozen when the challenge
 * ends; the end snapshot in [ChallengeEntity.endSnapshotJson] is what the terminal state reports (P-24).
 */
@Entity(
    tableName = "challenge_day_credits",
    primaryKeys = ["challengeId", "date"],
    foreignKeys = [ForeignKey(ChallengeEntity::class, ["challengeId"], ["challengeId"], onDelete = ForeignKey.CASCADE)]
)
data class ChallengeDayCreditEntity(
    val challengeId: String,
    val date: String,
    val activeSeconds: Long,
    val validSessionCount: Int,
    val credited: Boolean,
    val updatedAtEpochMs: Long
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

/** How far the plan was performed. Validity (>=60 s active or >=1 completed set) is derived separately and never from this. */
enum class SessionCompletion { COMPLETED, PARTIAL, NOT_PERFORMED }
enum class SegmentKind { WARMUP, STRENGTH, CARDIO_WORK, CARDIO_RECOVERY, CARDIO_STEADY, COOLDOWN }
enum class SetState { COMPLETED, SKIPPED }
enum class ChallengeStatus { ACTIVE, COMPLETE, EXPIRED, ABANDONED }
enum class DraftState { EDITING, SAVING, FAILED }

/**
 * A user's saved routine plan (Room v3, additive). Stage times are the PLAN only; measured time lives in sessions (P-21).
 * `targetSeconds` is null when the user gave no total target (E-02 B).
 */
@Entity(tableName = "routine_plans")
data class RoutinePlanEntity(
    @PrimaryKey val routineId: String,
    val name: String,
    /** `StructureVersion` name; new plans are ROUTIVE_4. */
    val structure: String,
    val targetSeconds: Long?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

/** One top-level stage of a routine plan. `plannedSeconds` null = not confirmed; never stored as 0 or a default. */
@Entity(
    tableName = "routine_stage_plans",
    primaryKeys = ["routineId", "orderIndex"],
    foreignKeys = [ForeignKey(RoutinePlanEntity::class, ["routineId"], ["routineId"], onDelete = ForeignKey.CASCADE)]
)
data class RoutineStagePlanEntity(
    val routineId: String,
    val orderIndex: Int,
    /** `StageKind` name. */
    val kind: String,
    val plannedSeconds: Long?
)

/** An exercise the user put into one stage of a routine plan (Room v4, additive). `plannedSeconds` null = unknown. */
@Entity(
    tableName = "routine_exercises",
    primaryKeys = ["routineId", "stageIndex", "orderIndex"],
    foreignKeys = [ForeignKey(RoutinePlanEntity::class, ["routineId"], ["routineId"], onDelete = ForeignKey.CASCADE)]
)
data class RoutineExerciseEntity(
    val routineId: String,
    val stageIndex: Int,
    val orderIndex: Int,
    /** Catalog id (e.g. a strength exercise id) or a cardio machine name. */
    val exerciseId: String,
    val name: String,
    val plannedSeconds: Long?,
    /** Strength targets chosen by the user. null = not set; never a generated default (v5). */
    val targetSets: Int? = null,
    val targetReps: Int? = null,
    /** Rest between sets in seconds; null = no rest timer (no automatic value is ever invented). */
    val restSeconds: Long? = null
)
