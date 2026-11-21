package com.move60.workout.architecture

import com.move60.workout.domain.StageKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure 07-N guard: only planned, unperformed, undecided sets need the two-button confirmation. */
class RoutiveExplicitSkipUiStateTest {
    private fun exercise(completed: Int, skipped: List<Int> = emptyList(), target: Int? = 3) =
        RoutiveExerciseUi(
            id = "legPress", name = "레그프레스", plannedLabel = "미정",
            done = (1..completed).map { RoutiveSetUi(it, "40", "12") },
            targetSets = target, skipped = skipped
        )

    @Test fun nextStageRequiresConfirmationWhileAPlannedSetIsUndecided() {
        val state = RoutiveSessionUiState(stageKind = StageKind.STRENGTH, exercises = listOf(exercise(2)))
        assertEquals(1, state.undecidedStrengthSets)
        assertEquals(3, state.exercises.single().nextSetNumber)
    }

    @Test fun explicitSkippedSetDoesNotCountAsCompletedAndRemovesUndecidedPrompt() {
        val state = RoutiveSessionUiState(
            stageKind = StageKind.STRENGTH, exercises = listOf(exercise(2, skipped = listOf(3)))
        )
        assertEquals(0, state.undecidedStrengthSets)
        assertEquals(2, state.exercises.single().done.size)
        assertEquals(4, state.exercises.single().nextSetNumber)
        assertEquals(1, state.exercises.single().skipped.size)
    }

    @Test fun unknownTargetNeverInventsASetToSkipOrAConfirmationCount() {
        val state = RoutiveSessionUiState(
            stageKind = StageKind.STRENGTH, exercises = listOf(exercise(1, target = null))
        )
        assertEquals(0, state.undecidedStrengthSets)
        assertEquals(2, state.exercises.single().nextSetNumber)
    }

    @Test fun nonStrengthStageHasNoStrengthPendingConfirmation() {
        val state = RoutiveSessionUiState(stageKind = StageKind.CARDIO, exercises = listOf(exercise(1)))
        assertEquals(0, state.undecidedStrengthSets)
    }
}
