package com.move60.workout.architecture

import com.move60.workout.SetRecordStore

/** Watch cannot render 07-N's unfinished strength confirmation. Only a fully decided plan may advance from Wear OS. */
internal object StrengthNextGate {
    fun safeToAdvanceFromWatch(
        plan: List<RoutivePlanSnapshot.Exercise>,
        completed: List<SetRecordStore.CompletedSet>,
        skipped: List<ExplicitSetSkipStore.SkippedSet>
    ): Boolean {
        if (plan.isEmpty()) return false // missing/unknown snapshot must be handled by phone
        return plan.all { exercise ->
            val count = exercise.targetSets?.takeIf { it > 0 } ?: return@all false
            val done = completed.filter { it.exerciseId == exercise.id }.map { it.setNumber }
            val omitted = skipped.filter { it.exerciseId == exercise.id }.map { it.setNumber }
            val statuses = done + omitted
            statuses.size == count && statuses.toSet() == (1..count).toSet()
        }
    }
}
