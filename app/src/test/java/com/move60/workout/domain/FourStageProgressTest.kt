package com.move60.workout.domain

import org.junit.Assert.*
import org.junit.Test

class FourStageProgressTest {
    private fun plan(warm: Long? = 300, strength: Long? = 1200, cardio: Long? = 900, cool: Long? = 300) = FourStagePlans.from(
        listOf(
            StageTimePlan(StageKind.WARM_UP, warm), StageTimePlan(StageKind.STRENGTH, strength),
            StageTimePlan(StageKind.CARDIO, cardio), StageTimePlan(StageKind.COOL_DOWN, cool)
        )
    )!!

    @Test fun planMustBeExactlyTheFourRoutiveStagesInOrder() {
        assertNull(FourStagePlans.from(emptyList()))
        assertNull("legacy 5-stage shape is not a ROUTIVE_4 plan", FourStagePlans.from(WorkoutStructures.stages(StructureVersion.LEGACY_5).map { StageTimePlan(it.kind, 60) }))
        assertNull("wrong order", FourStagePlans.from(listOf(
            StageTimePlan(StageKind.WARM_UP, 60), StageTimePlan(StageKind.CARDIO, 60),
            StageTimePlan(StageKind.STRENGTH, 60), StageTimePlan(StageKind.COOL_DOWN, 60))))
        assertEquals(4, plan().plan.size)
    }

    @Test fun unknownPlanTimeIsNeverZeroAndHasNoRemainingTime() {
        val p = plan(cardio = null).copy(stageIndex = 2).tick(95)
        assertNull(p.plannedSeconds()); assertNull(p.remainingSeconds())
        assertEquals(0L, p.overtimeSeconds())
        assertEquals(StageReadiness.MANUAL, p.readiness(allStrengthSetsDone = false))
        assertEquals(95L, p.elapsedSeconds[2])
    }

    @Test fun remainingCountsDownAndOvertimeStartsAfterThePlan() {
        var p = plan(warm = 300).tick(100)
        assertEquals(200L, p.remainingSeconds()); assertEquals(0L, p.overtimeSeconds())
        assertEquals(StageReadiness.IN_PROGRESS, p.readiness(false))
        p = p.tick(200)
        assertEquals(StageReadiness.PLAN_REACHED, p.readiness(false))
        p = p.tick(45)
        assertEquals(0L, p.remainingSeconds()); assertEquals(45L, p.overtimeSeconds())
        assertEquals("reaching the plan must not advance by itself", 0, p.stageIndex)
    }

    @Test fun strengthIsSetDrivenAndItsPlanTimeNeverForceEnds() {
        var p = plan(strength = 600).advance()
        assertEquals(StageKind.STRENGTH, p.kind)
        p = p.tick(5_000)                                   // far beyond the plan
        assertEquals(StageReadiness.IN_PROGRESS, p.readiness(allStrengthSetsDone = false))
        assertEquals(1, p.stageIndex)
        assertEquals(StageReadiness.SETS_DONE, p.readiness(allStrengthSetsDone = true))
        val noPlan = plan(strength = null).advance().tick(30)
        assertEquals(StageReadiness.IN_PROGRESS, noPlan.readiness(false))
        assertEquals(StageReadiness.SETS_DONE, noPlan.readiness(true))
    }

    @Test fun advanceWalksFourStagesAndTheLastAdvanceFinishes() {
        var p = plan()
        val kinds = mutableListOf(p.kind)
        repeat(3) { p = p.advance(); kinds += p.kind }
        assertEquals(listOf(StageKind.WARM_UP, StageKind.STRENGTH, StageKind.CARDIO, StageKind.COOL_DOWN), kinds)
        assertTrue(p.isLast); assertFalse(p.finished)
        p = p.advance()
        assertTrue(p.finished); assertFalse(p.endedEarly)
        assertEquals("a finished session is frozen", p, p.advance().skip().back().endEarly().tick(10))
    }

    @Test fun backKeepsElapsedTimeAndAllowsResuming() {
        var p = plan().tick(120).advance().tick(60)
        p = p.back()
        assertEquals(0, p.stageIndex)
        assertEquals(listOf(120L, 60L, 0L, 0L), p.elapsedSeconds)
        p = p.tick(30)
        assertEquals(150L, p.elapsedSeconds[0])
        assertEquals("back at the first stage is a no-op", p, p.back())
    }

    @Test fun skipMarksTheStageButKeepsRealTimeAndNeverCountsAsCompleted() {
        var p = plan().tick(40).skip()
        assertEquals(1, p.stageIndex); assertEquals(setOf(0), p.skipped); assertEquals(40L, p.elapsedSeconds[0])
        p = p.back()
        assertEquals("going back resumes the skipped stage", emptySet<Int>(), p.skipped)
        val last = plan().advance().advance().advance().skip()
        assertTrue("skipping the final stage is an early end, never a complete finish", last.endedEarly && !last.finished)
    }

    @Test fun earlyEndFreezesAndKeepsElapsedForThePartialRecord() {
        val p = plan().tick(200).advance().tick(70).endEarly()
        assertTrue(p.endedEarly && !p.finished && p.isOver)
        assertEquals(270L, p.totalElapsedSeconds)
        assertEquals(p, p.tick(500))
        assertEquals(StageReadiness.OVER, p.readiness(true))
    }

    @Test fun nonPositiveTicksAreIgnoredAndInvalidStateIsRejected() {
        val p = plan()
        assertEquals(p, p.tick(0)); assertEquals(p, p.tick(-5))
        assertThrows(IllegalArgumentException::class.java) { p.copy(stageIndex = 4) }
        assertThrows(IllegalArgumentException::class.java) { p.copy(elapsedSeconds = listOf(1L, 2L, 3L)) }
        assertThrows(IllegalArgumentException::class.java) { p.copy(elapsedSeconds = listOf(-1L, 0L, 0L, 0L)) }
    }
}
