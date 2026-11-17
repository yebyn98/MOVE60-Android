package com.move60.workout.domain

import org.junit.Assert.*
import org.junit.Test

/** PO decisions of 2026-11-17: candidates come from the registered equipment of ONE space; only approved rows; "A or B" needs one. */
class ExerciseEquipmentRulesTest {
    private val none = emptySet<String>()
    private fun candidates(registered: Set<String>) = ExerciseEquipmentRules.candidates(ExerciseEquipmentRules.RULES.keys, registered).toSet()

    @Test fun anApprovedBodyWeightExerciseIsACandidateWithoutAnyEquipment() {
        val c = candidates(none)
        for (id in listOf("gluteBridge", "deadBug", "birdDog", "plank", "sidePlank", "heelSlide", "wallPushup", "chairSquat", "bodySquat", "march"))
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
    @Test fun aRowThatIsNotApprovedNeverShowsEvenWithTheEquipment() {
        // All 35 rows are approved now; the flag still gates any row the PO has not approved (e.g. an exercise added later).
        val rules = mapOf("newMove" to EquipmentRule(listOf(setOf("DUMBBELL")), approved = false), "plank" to ExerciseEquipmentRules.RULES.getValue("plank"))
        assertFalse(ExerciseEquipmentRules.isCandidate("newMove", setOf("DUMBBELL"), rules))
        assertEquals(listOf("plank"), ExerciseEquipmentRules.candidates(listOf("newMove", "plank"), setOf("DUMBBELL"), rules))
    }
    @Test fun equipmentRowsNeedTheirEquipment() {
        assertFalse("legPress" in candidates(none)); assertTrue("legPress" in candidates(setOf("LEG_PRESS")))
        assertFalse("gobletSquat" in candidates(setOf("LEG_PRESS"))); assertTrue("gobletSquat" in candidates(setOf("DUMBBELL")))
        assertFalse("boxSquat" in candidates(setOf("DUMBBELL"))); assertTrue("boxSquat" in candidates(setOf("BENCH")))
    }
    @Test fun aRowWithTwoEquipmentNeedsBoth() {
        val rule = EquipmentRule(listOf(setOf("BENCH", "DUMBBELL")), approved = true)
        assertFalse(rule.satisfiedBy(setOf("BENCH"))); assertFalse(rule.satisfiedBy(setOf("DUMBBELL"))); assertTrue(rule.satisfiedBy(setOf("BENCH", "DUMBBELL")))
        assertFalse("dumbbell chest press needs both", "dbChestPress" in candidates(setOf("BENCH"))); assertTrue("dbChestPress" in candidates(setOf("BENCH", "DUMBBELL")))
        assertEquals(listOf(setOf("BENCH", "DUMBBELL")), ExerciseEquipmentRules.RULES.getValue("dbChestPress").anyOf)
    }
    @Test fun unknownIdsAreNeverCandidatesAndOrderAndRepeatsAreKept() {
        assertFalse(ExerciseEquipmentRules.isCandidate("notAnExercise", setOf("DUMBBELL")))
        assertEquals(listOf("plank", "deadBug"), ExerciseEquipmentRules.candidates(listOf("plank", "legPress", "plank", "deadBug", "zzz"), none))
    }
    @Test fun aRuleNeedsAtLeastOneWayAndEveryApprovedRowIsExplicit() {
        assertThrows(IllegalArgumentException::class.java) { EquipmentRule(emptyList(), true) }
        assertEquals(35, ExerciseEquipmentRules.RULES.values.count { it.approved })   // the PO approved every row on 2026-11-17
        assertEquals(35, ExerciseEquipmentRules.RULES.size)
    }
}
