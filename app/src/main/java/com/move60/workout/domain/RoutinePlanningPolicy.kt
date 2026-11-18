package com.move60.workout.domain

/**
 * ROUTIVE v2 planning policies. Pure Kotlin (no Android dependencies).
 *
 * This module is intentionally not wired into the legacy 60-minute SessionStore yet.
 * Migrating a running session requires a separate, tested persistence migration.
 */
data class PlannedExercise(
    val id: String,
    /** Null means the user has not supplied a planned duration; never coerce it to zero. */
    val plannedSeconds: Long?
)

enum class PlanValidationIssue {
    NO_EXERCISES,
    DUPLICATE_EXERCISE_ID,
    INVALID_EXERCISE_DURATION,
    INVALID_TARGET_DURATION,
    SUM_OVERFLOW,
    KNOWN_TIME_EXCEEDS_TARGET,
    TOTAL_TIME_MISMATCH
}

data class RoutineDurationEvaluation(
    /** Sum of the explicitly known, positive durations, each exercise counted once. */
    val knownSeconds: Long,
    /** Null when any exercise has an unknown planned duration. */
    val totalSeconds: Long?,
    val targetSeconds: Long?,
    val issue: PlanValidationIssue? = null
) {
    val canConfirm: Boolean get() = issue == null
    val hasUnknownTime: Boolean get() = totalSeconds == null
}

object RoutineDurationPolicy {
    /**
     * E-02 B: At least one exercise is required; unknown planned time alone must
     * not block confirmation. However, a known partial sum cannot exceed an explicit
     * target, and a completely known sum must match the explicit target.
     *
     * Warm-up, cooldown and other activities must be supplied exactly once each
     * with distinct IDs. Do not double count strength rests already included in an
     * exercise's plannedSeconds.
     */
    fun evaluate(
        exercises: List<PlannedExercise>,
        targetSeconds: Long?
    ): RoutineDurationEvaluation {
        fun invalid(issue: PlanValidationIssue) =
            RoutineDurationEvaluation(0, null, targetSeconds, issue)

        if (exercises.isEmpty()) return invalid(PlanValidationIssue.NO_EXERCISES)
        if (targetSeconds != null && targetSeconds <= 0L) {
            return invalid(PlanValidationIssue.INVALID_TARGET_DURATION)
        }
        if (exercises.any { it.id.isBlank() } ||
            exercises.map { it.id }.toSet().size != exercises.size
        ) return invalid(PlanValidationIssue.DUPLICATE_EXERCISE_ID)
        if (exercises.any { it.plannedSeconds != null && it.plannedSeconds <= 0L }) {
            return invalid(PlanValidationIssue.INVALID_EXERCISE_DURATION)
        }

        var known = 0L
        for (exercise in exercises) {
            val seconds = exercise.plannedSeconds ?: continue
            known = try {
                Math.addExact(known, seconds)
            } catch (_: ArithmeticException) {
                return invalid(PlanValidationIssue.SUM_OVERFLOW)
            }
        }
        val complete = exercises.all { it.plannedSeconds != null }
        val total = if (complete) known else null
        val issue = when {
            targetSeconds != null && known > targetSeconds ->
                PlanValidationIssue.KNOWN_TIME_EXCEEDS_TARGET
            targetSeconds != null && complete && known != targetSeconds ->
                PlanValidationIssue.TOTAL_TIME_MISMATCH
            else -> null
        }
        return RoutineDurationEvaluation(known, total, targetSeconds, issue)
    }
}

data class IntervalRoundDraft(
    val workSeconds: Long?,
    /** Null is only permitted when this round has no recovery segment at all. */
    val recoverySeconds: Long?,
    val hasRecoverySegment: Boolean
)

enum class IntervalValidationIssue {
    INVALID_WORK_TIME,
    INVALID_RECOVERY_TIME,
    UNEXPECTED_RECOVERY_TIME,
    SUM_OVERFLOW
}

data class IntervalRoundEvaluation(
    val durationSeconds: Long?,
    val issue: IntervalValidationIssue? = null
) {
    val isValid: Boolean get() = issue == null
}

object IntervalRoundPolicy {
    /** User-entered positive whole seconds, not arbitrary AUTO suggestions. */
    fun evaluate(round: IntervalRoundDraft): IntervalRoundEvaluation {
        val work = round.workSeconds
        if (work == null || work <= 0L) {
            return IntervalRoundEvaluation(null, IntervalValidationIssue.INVALID_WORK_TIME)
        }
        if (round.hasRecoverySegment &&
            (round.recoverySeconds == null || round.recoverySeconds <= 0L)
        ) return IntervalRoundEvaluation(null, IntervalValidationIssue.INVALID_RECOVERY_TIME)
        if (!round.hasRecoverySegment && round.recoverySeconds != null) {
            return IntervalRoundEvaluation(null, IntervalValidationIssue.UNEXPECTED_RECOVERY_TIME)
        }
        val total = try {
            Math.addExact(work, round.recoverySeconds ?: 0L)
        } catch (_: ArithmeticException) {
            return IntervalRoundEvaluation(null, IntervalValidationIssue.SUM_OVERFLOW)
        }
        return IntervalRoundEvaluation(total)
    }
}
