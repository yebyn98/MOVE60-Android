package com.move60.workout.domain

/**
 * Which exercises can be offered as "exercises that fit the equipment of the chosen space" (PO decisions of 2026-11-17).
 *
 * A rule lists the ways an exercise can be done: [EquipmentRule.anyOf] holds alternative sets of equipment keys, and ONE satisfied set is
 * enough ("A or B" needs only one). An EMPTY set means body weight: no equipment at all. A set with several keys needs all of them.
 * Equipment keys are the stored `Equipment` names. Only the equipment of ONE space is ever passed in; spaces are never combined.
 *
 * An exercise is a candidate only when its rule is [EquipmentRule.approved] by the PO. Unapproved rows never show up as candidates, even when
 * the equipment is there. Nothing here generates sets, repetitions, weights, intensity or time.
 * Source of every row: docs/v67-exercise-equipment-mapping-draft.md (workouts.json equipment text + the PO decisions).
 */
data class EquipmentRule(val anyOf: List<Set<String>>, val approved: Boolean) {
    init { require(anyOf.isNotEmpty()) { "a rule needs at least one way to do the exercise" } }
    fun satisfiedBy(registered: Set<String>): Boolean = anyOf.any { registered.containsAll(it) }
}

object ExerciseEquipmentRules {
    private val BODY = emptySet<String>()
    private fun one(vararg keys: String) = listOf(keys.toSet())
    private fun rule(anyOf: List<Set<String>>, approved: Boolean) = EquipmentRule(anyOf, approved)

    /**
     * Approved = the PO decided this row in the conversation (mat exercises count as body weight, wall/chair as body weight, calf raise =
     * body weight or dumbbell, "A or B" = one is enough). The other rows wait for the PO's approval of the review table.
     */
    val RULES: Map<String, EquipmentRule> = linkedMapOf(
        "legPress" to rule(one("LEG_PRESS"), false),
        "gobletSquat" to rule(one("DUMBBELL"), false),
        "boxSquat" to rule(one("BENCH"), false),
        "stepUp" to rule(one("STEP_BOX"), false),
        "gluteBridge" to rule(listOf(BODY), true),
        "hipThrust" to rule(one("HIP_THRUST_MACHINE") + one("BENCH"), true),
        "cableKickback" to rule(one("CABLE_MACHINE"), false),
        "hipAbduction" to rule(one("HIP_ABDUCTION_MACHINE"), false),
        "legCurl" to rule(one("LEG_CURL_MACHINE"), false),
        "rdl" to rule(one("DUMBBELL"), false),
        "calfRaise" to rule(listOf(BODY) + one("DUMBBELL"), true),
        "reverseLunge" to rule(listOf(BODY) + one("DUMBBELL"), true),
        "latPulldown" to rule(one("LAT_PULLDOWN"), false),
        "seatedRow" to rule(one("SEATED_ROW_MACHINE"), false),
        "cableRow" to rule(one("CABLE_MACHINE"), false),
        "chestPress" to rule(one("CHEST_PRESS_MACHINE"), false),
        "dbChestPress" to rule(one("BENCH", "DUMBBELL"), false),
        "inclinePushup" to rule(one("BENCH"), false),
        "facePull" to rule(one("CABLE_MACHINE"), false),
        "lateralRaise" to rule(one("DUMBBELL"), false),
        "bicepCurl" to rule(one("DUMBBELL"), false),
        "tricepPushdown" to rule(one("CABLE_MACHINE"), false),
        "deadBug" to rule(listOf(BODY), true),
        "birdDog" to rule(listOf(BODY), true),
        "plank" to rule(listOf(BODY), true),
        "sidePlank" to rule(listOf(BODY), true),
        "pallofPress" to rule(one("CABLE_MACHINE") + one("RESISTANCE_BAND"), true),
        "wallPushup" to rule(listOf(BODY), true),
        "chairSquat" to rule(listOf(BODY), true),
        "bandRow" to rule(one("RESISTANCE_BAND"), false),
        "clamshell" to rule(listOf(BODY) + one("RESISTANCE_BAND"), true),
        "heelSlide" to rule(listOf(BODY), true),
        "bodySquat" to rule(listOf(BODY), false),
        "march" to rule(listOf(BODY), false),
        "farmerCarry" to rule(one("DUMBBELL"), false)
    )

    fun isCandidate(exerciseId: String, registered: Set<String>): Boolean {
        val r = RULES[exerciseId] ?: return false          // no rule = never a candidate (it stays available for manual composition)
        return r.approved && r.satisfiedBy(registered)
    }

    /** The ids (keeping the given order, without repeats) that are approved AND doable with the equipment of ONE space. */
    fun candidates(exerciseIds: Collection<String>, registered: Set<String>): List<String> =
        exerciseIds.distinct().filter { isCandidate(it, registered) }
}
