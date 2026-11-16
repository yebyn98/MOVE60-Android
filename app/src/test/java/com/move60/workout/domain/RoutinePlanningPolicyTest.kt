package com.move60.workout.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutinePlanningPolicyTest {
    @Test fun zeroExercisesCannotConfirm() {
        val result = RoutineDurationPolicy.evaluate(emptyList(), null)
        assertFalse(result.canConfirm)
        assertEquals(PlanValidationIssue.NO_EXERCISES, result.issue)
    }

    @Test fun unknownPlannedTimeCanConfirmOneExercise() {
        val result = RoutineDurationPolicy.evaluate(
            listOf(PlannedExercise("strength", null)), null
        )
        assertTrue(result.canConfirm)
        assertNull(result.totalSeconds)
        assertEquals(0L, result.knownSeconds)
    }

    @Test fun knownTimeDoesNotBecomeTotalWhenAnotherExerciseIsUnknown() {
        val result = RoutineDurationPolicy.evaluate(
            listOf(PlannedExercise("warmup", 180), PlannedExercise("strength", null)),
            targetSeconds = 600
        )
        assertTrue(result.canConfirm)
        assertNull(result.totalSeconds)
        assertEquals(180L, result.knownSeconds)
    }

    @Test fun cannotConfirmIfKnownPartAlreadyExceedsTarget() {
        val result = RoutineDurationPolicy.evaluate(
            listOf(PlannedExercise("cardio", 601), PlannedExercise("strength", null)),
            targetSeconds = 600
        )
        assertEquals(PlanValidationIssue.KNOWN_TIME_EXCEEDS_TARGET, result.issue)
    }

    @Test fun completeDurationsMustMatchExplicitTarget() {
        val exercises = listOf(PlannedExercise("warmup", 120), PlannedExercise("cardio", 180))
        assertEquals(
            PlanValidationIssue.TOTAL_TIME_MISMATCH,
            RoutineDurationPolicy.evaluate(exercises, 600).issue
        )
        val valid = RoutineDurationPolicy.evaluate(exercises, 300)
        assertTrue(valid.canConfirm)
        assertEquals(300L, valid.totalSeconds)
    }

    @Test fun duplicateExerciseIdDoesNotDoubleCount() {
        val result = RoutineDurationPolicy.evaluate(
            listOf(PlannedExercise("bike", 60), PlannedExercise("bike", 60)), null
        )
        assertEquals(PlanValidationIssue.DUPLICATE_EXERCISE_ID, result.issue)
    }

    @Test fun zeroDurationIsNotUnknown() {
        val result = RoutineDurationPolicy.evaluate(listOf(PlannedExercise("bike", 0)), null)
        assertEquals(PlanValidationIssue.INVALID_EXERCISE_DURATION, result.issue)
    }

    @Test fun knownSumCannotOverflow() {
        val result = RoutineDurationPolicy.evaluate(
            listOf(PlannedExercise("a", Long.MAX_VALUE), PlannedExercise("b", 1)), null
        )
        assertEquals(PlanValidationIssue.SUM_OVERFLOW, result.issue)
    }

    @Test fun noFixedSixtyMinuteRequirement() {
        val result = RoutineDurationPolicy.evaluate(listOf(PlannedExercise("bike", 420)), null)
        assertTrue(result.canConfirm)
        assertEquals(420L, result.totalSeconds)
    }

    @Test fun roundWorkAndRecoveryHaveSeparatePositiveDurations() {
        val valid = IntervalRoundPolicy.evaluate(IntervalRoundDraft(60, 120, true))
        assertTrue(valid.isValid)
        assertEquals(180L, valid.durationSeconds)
        assertEquals(
            IntervalValidationIssue.INVALID_WORK_TIME,
            IntervalRoundPolicy.evaluate(IntervalRoundDraft(0, 60, true)).issue
        )
        assertEquals(
            IntervalValidationIssue.INVALID_RECOVERY_TIME,
            IntervalRoundPolicy.evaluate(IntervalRoundDraft(60, null, true)).issue
        )
    }

    @Test fun recoveryCanBeOmittedOnlyWhenSegmentIsAbsent() {
        val last = IntervalRoundPolicy.evaluate(IntervalRoundDraft(60, null, false))
        assertTrue(last.isValid)
        assertEquals(60L, last.durationSeconds)
        assertEquals(
            IntervalValidationIssue.UNEXPECTED_RECOVERY_TIME,
            IntervalRoundPolicy.evaluate(IntervalRoundDraft(60, 0, false)).issue
        )
    }
}
