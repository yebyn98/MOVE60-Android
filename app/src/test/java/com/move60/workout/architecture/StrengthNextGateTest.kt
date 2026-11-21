package com.move60.workout.architecture

import com.move60.workout.SetRecordStore
import org.junit.Assert.*
import org.junit.Test

class StrengthNextGateTest {
    private val plan = RoutivePlanSnapshot.Exercise(1, "legPress", "레그프레스", null, 3, 10, null)
    private fun done(n: Int) = SetRecordStore.CompletedSet("legPress", n, "40", "10")
    private fun skipped(n: Int) = ExplicitSetSkipStore.SkippedSet("legPress", n, 1234L)

    @Test fun watchRejectsUndecidedSetsAndUnknownPlans() {
        assertFalse(StrengthNextGate.safeToAdvanceFromWatch(listOf(plan), listOf(done(1), done(2)), emptyList()))
        assertFalse(StrengthNextGate.safeToAdvanceFromWatch(emptyList(), emptyList(), emptyList()))
        assertFalse(StrengthNextGate.safeToAdvanceFromWatch(listOf(plan.copy(targetSets = null)), listOf(done(1)), emptyList()))
    }

    @Test fun watchOnlyAdvancesWhenEveryPlannedSetIsExplicitlyDecided() {
        assertTrue(StrengthNextGate.safeToAdvanceFromWatch(listOf(plan), listOf(done(1), done(2)), listOf(skipped(3))))
        assertTrue(StrengthNextGate.safeToAdvanceFromWatch(listOf(plan), listOf(done(1), done(2), done(3)), emptyList()))
        assertFalse(StrengthNextGate.safeToAdvanceFromWatch(listOf(plan), listOf(done(1), done(2)), listOf(skipped(2))))
    }
}
