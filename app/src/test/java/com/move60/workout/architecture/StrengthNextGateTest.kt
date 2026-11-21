package com.move60.workout.architecture

import com.move60.workout.SetRecordStore
import org.junit.Assert.*
import org.junit.Test

class StrengthNextGateTest {
    private fun exercise(target: Int?) =
        RoutivePlanSnapshot.Exercise(1, "legPress", "레그프레스", null, target, 10, null)
    private fun done(n: Int) = SetRecordStore.CompletedSet("legPress", n, "40", "10")
    private fun skip(n: Int) = SetRecordStore.SkippedSet("legPress", n)

    @Test fun anUndecidedSetAlwaysRequiresPhoneConfirmation() {
        assertFalse(StrengthNextGate.safeToAdvanceFromWatch(listOf(exercise(3)), listOf(done(1), done(2)), emptyList()))
        assertFalse(StrengthNextGate.safeToAdvanceFromWatch(listOf(exercise(3)), emptyList(), emptyList()))
    }

    @Test fun fullyDecidedCompletedAndExplicitSkipsCanAdvance() {
        assertTrue(StrengthNextGate.safeToAdvanceFromWatch(
            listOf(exercise(3)), listOf(done(1), done(2)), listOf(skip(3))
        ))
        assertTrue(StrengthNextGate.safeToAdvanceFromWatch(
            listOf(exercise(3)), listOf(done(1), done(2), done(3)), emptyList()
        ))
    }

    @Test fun unknownTargetOrUntrustedSetStatusAlwaysRequiresPhone() {
        assertFalse(StrengthNextGate.safeToAdvanceFromWatch(emptyList(), emptyList(), emptyList()))
        assertFalse(StrengthNextGate.safeToAdvanceFromWatch(listOf(exercise(null)), listOf(done(1)), emptyList()))
        assertFalse(StrengthNextGate.safeToAdvanceFromWatch(listOf(exercise(3)),
            listOf(done(1), done(2)), listOf(skip(2)))) // repeated decision, third still undecided
    }
}
