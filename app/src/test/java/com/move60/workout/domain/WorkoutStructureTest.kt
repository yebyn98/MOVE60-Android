package com.move60.workout.domain

import org.junit.Assert.*
import org.junit.Test

class WorkoutStructureTest {
    @Test fun missingOrUnknownMarkerIsLegacyFiveStage() {
        assertEquals(StructureVersion.LEGACY_5, StructureVersion.fromStored(null))
        assertEquals(StructureVersion.LEGACY_5, StructureVersion.fromStored("garbage"))
        assertEquals(StructureVersion.ROUTIVE_4, StructureVersion.fromStored("ROUTIVE_4"))
    }
    @Test fun newStructureHasFourStagesWithSingleCardio() {
        val s = WorkoutStructures.stages(StructureVersion.ROUTIVE_4)
        assertEquals(listOf("준비운동", "근력", "유산소", "마무리"), s.map { it.name })
        assertEquals(1, s.count { it.kind == StageKind.CARDIO })
    }
    @Test fun legacyStructureIsUnchanged() {
        assertEquals(5, WorkoutStructures.stageCount(StructureVersion.LEGACY_5))
        assertEquals("2 / 5 단계", WorkoutStructures.label(StructureVersion.LEGACY_5, 1))
        assertEquals("2 / 4 단계", WorkoutStructures.label(StructureVersion.ROUTIVE_4, 1))
    }
    @Test fun labelClampsOutOfRangeIndex() {
        assertEquals("4 / 4 단계", WorkoutStructures.label(StructureVersion.ROUTIVE_4, 9))
        assertEquals("1 / 4 단계", WorkoutStructures.label(StructureVersion.ROUTIVE_4, -3))
    }
    @Test fun strengthIsSetDrivenEvenWithPlannedTime() {
        assertEquals(TransitionMode.SET_COMPLETION, WorkoutStructures.transitionMode(StageKind.STRENGTH, 1200))
        assertEquals(TransitionMode.SET_COMPLETION, WorkoutStructures.transitionMode(StageKind.STRENGTH, null))
    }
    @Test fun nullPlanTimeIsManualNotZero() {
        assertEquals(TransitionMode.MANUAL, WorkoutStructures.transitionMode(StageKind.CARDIO, null))
        assertEquals(TransitionMode.PLANNED_TIME_OR_MANUAL, WorkoutStructures.transitionMode(StageKind.CARDIO, 1800))
        assertNull(StageTimePlan(StageKind.WARM_UP, null).plannedSeconds)
    }
    @Test fun zeroOrNegativePlanIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { StageTimePlan(StageKind.CARDIO, 0) }
        assertThrows(IllegalArgumentException::class.java) { StageTimePlan(StageKind.CARDIO, -5) }
    }
    @Test fun heartRateShownOnlyWhenValidAndFresh() {
        assertEquals("120 bpm", HeartRateFreshness.display(120, 10_000))
        assertEquals("수신 대기", HeartRateFreshness.display(120, 10_001))
        assertEquals("수신 대기", HeartRateFreshness.display(0, 100))
        assertEquals("수신 대기", HeartRateFreshness.display(29, 100))
        assertEquals("수신 대기", HeartRateFreshness.display(241, 100))
        assertEquals("30 bpm", HeartRateFreshness.display(30, 0))
        assertEquals("수신 대기", HeartRateFreshness.display(120, -1))
    }
}
