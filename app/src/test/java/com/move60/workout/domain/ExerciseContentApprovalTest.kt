package com.move60.workout.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PO decision B: nothing is shown without a recorded verification; the approved set starts empty. */
class ExerciseContentApprovalTest {
    @Test fun noExerciseIsApprovedUntilItsEvidenceIsRecorded() {
        assertTrue(ExerciseContentApproval.APPROVED.isEmpty())
        for (id in listOf("legPress", "birdDog", "bodySquat", "")) assertFalse(id, ExerciseContentApproval.isApproved(id))
    }
}
