package com.move60.workout.domain

import org.junit.Assert.*
import org.junit.Test

/** PO decisions of 2026-11-17: candidates come from the registered equipment of ONE space; only approved rows; "A or B" needs one. */
class ExerciseEquipmentRulesTest {
    private val none = emptySet<String>()
    private fun candidates(registered: Set<String>) = ExerciseEquipmentRules.candidates(ExerciseEquipmentRules.RULES.keys, registered).toSet()

    @Test fun anApprovedBodyWeightExerciseIsACandidateWithoutAnyEquipment() {
        val c = candidates(none)
        for (id in listOf("gluteBridge", "deadBug", "birdDog", "plank", "sidePlank", "heelSlide", "wallPushup", "chairSquat"))
            assertTrue("$id is body weight", id in c)
    }
    @Test fun orRowsNeedOnlyOneOfTheirWays() {
        assertTrue("calf raise: body weight", "calfRaise" in candidates(none)); assertTrue("calf raise: dumbbell", "calfRaise" in candidates(setOf("DUMBBELL")))
        assertTrue("reverse lunge: body weight", "reverseLunge" in candidates(none))
        assertFalse("hip thrust needs the machine or a bench", "hipThrust" in candidates(none))
        assertTrue("hipThrust" in candidates(setOf("BENCH"))); assertTrue("hipThrust" in candidates(setOf("HIP_THRUST_MACHINE")))
        assertFalse("pallofPress" in candidates(none))
        assertTrue("pallofPress" in candidates(setOf("CABLE_MACHINE"))); assertTrue("pallofPress" in candidates(setOf("RESISTANCE_BAND")))
        assertTrue("clamshell: mat = body weight" , "clamshell" in candidates(none))
    }
    @Test fun anUnapprovedRowNeverShowsEvenWithTheEquipment() {
        val all = setOf("LEG_PRESS", "DUMBBELL", "BENCH", "STEP_BOX", "CABLE_MACHINE", "LAT_PULLDOWN", "RESISTANCE_BAND", "LEG_CURL_MACHINE",
            "HIP_ABDUCTION_MACHINE", "SEATED_ROW_MACHINE", "CHEST_PRESS_MACHINE", "HIP_THRUST_MACHINE")
        val shown = candidates(all)
        for ((id, rule) in ExerciseEquipmentRules.RULES) if (!rule.approved) assertFalse("$id is not approved", id in shown)
        assertFalse("legPress" in shown); assertFalse("bodySquat" in shown)
    }
    @Test fun aRowWithTwoEquipmentNeedsBoth() {
        val rule = EquipmentRule(listOf(setOf("BENCH", "DUMBBELL")), approved = true)
        assertFalse(rule.satisfiedBy(setOf("BENCH"))); assertFalse(rule.satisfiedBy(setOf("DUMBBELL"))); assertTrue(rule.satisfiedBy(setOf("BENCH", "DUMBBELL")))
        assertEquals("the PO has not approved this row", false, ExerciseEquipmentRules.RULES.getValue("dbChestPress").approved)
        assertEquals(listOf(setOf("BENCH", "DUMBBELL")), ExerciseEquipmentRules.RULES.getValue("dbChestPress").anyOf)
    }
    @Test fun unknownIdsAreNeverCandidatesAndOrderAndRepeatsAreKept() {
        assertFalse(ExerciseEquipmentRules.isCandidate("notAnExercise", setOf("DUMBBELL")))
        assertEquals(listOf("plank", "deadBug"), ExerciseEquipmentRules.candidates(listOf("plank", "legPress", "plank", "deadBug", "zzz"), none))
    }
    @Test fun aRuleNeedsAtLeastOneWayAndEveryApprovedRowIsExplicit() {
        assertThrows(IllegalArgumentException::class.java) { EquipmentRule(emptyList(), true) }
        assertEquals(13, ExerciseEquipmentRules.RULES.values.count { it.approved })   // only the rows the PO decided; the rest wait for approval
        assertEquals(35, ExerciseEquipmentRules.RULES.size)
    }
}
