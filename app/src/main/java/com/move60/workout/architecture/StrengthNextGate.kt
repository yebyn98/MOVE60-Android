package com.move60.workout.architecture

import com.move60.workout.SetRecordStore

/**
 * The watch cannot show the phone's 07-N unfinished-set confirmation.
 * Unknown plans or undecided set numbers are always handled on the phone.
 * Explicit SKIPPED is a decision, not a completed set (never increases performed volume).
 */
internal object StrengthNextGate {
    fun safeToAdvanceFromWatch(
        planned: List<RoutivePlanSnapshot.Exercise>,
        completed: List<SetRecordStore.CompletedSet>,
        skipped: List<SetRecordStore.SkippedSet>
    ): Boolean {
        if (planned.isEmpty()) return false
        return planned.all { exercise ->
            val target = exercise.targetSets?.takeIf { it > 0 } ?: return@all false
            val done = completed.filter { it.exerciseId == exercise.id }.map { it.setNumber }
            val skip = skipped.filter { it.exerciseId == exercise.id }.map { it.setNumber }
            val decided = done + skip
            decided.size == target && decided.toSet() == (1..target).toSet()
        }
    }
}
