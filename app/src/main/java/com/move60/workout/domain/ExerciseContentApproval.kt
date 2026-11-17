package com.move60.workout.domain

/**
 * Exercise guidance (the start/end images and the "동작 순서" text) is shown only for exercises whose use rights AND movement accuracy /
 * cautions were verified, with the evidence recorded (docs/v67, PO decision B). The set is EMPTY on purpose: there is no such record for
 * any exercise yet. Add an id here only together with its evidence (source, rights, reviewer, review date) in docs/v67-exercise-guidance-evidence.md.
 * Files and text of unapproved exercises are kept untouched; they are just not shown.
 */
object ExerciseContentApproval {
    val APPROVED: Set<String> = emptySet()
    fun isApproved(exerciseId: String): Boolean = exerciseId in APPROVED
}
