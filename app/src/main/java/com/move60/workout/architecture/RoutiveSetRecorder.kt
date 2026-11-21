package com.move60.workout.architecture

import com.move60.workout.SetRecordStore
import com.move60.workout.domain.SetInputError
import com.move60.workout.domain.SetInputPolicy
import com.move60.workout.domain.StageKind

/**
 * Records one completed strength set of the running ROUTIVE_4 session. Input is validated FIRST (the limits every other writer
 * uses, [com.move60.workout.domain.SetLimits]); an invalid value is returned as an error and nothing changes. The rest timer
 * starts only after the stored record is confirmed as completed, and only for a user-chosen rest time.
 */
internal class RoutiveSetRecorder(private val store: RoutiveSessionStore, private val sets: SetRecordStore) {
    /** Null = the set was recorded (or there was nothing to record into); otherwise the reason it was refused. */
    fun complete(exerciseId: String, kg: String, reps: String, exercise: RoutineExerciseInput?): SetInputError? {
        val s = store.active() ?: return null
        if (s.over || s.progress.kind != StageKind.STRENGTH || kg.isBlank() || reps.isBlank()) return null
        val valid = SetInputPolicy.normalize(kg, reps) ?: return SetInputPolicy.validate(kg, reps) ?: SetInputError.NOT_STORED
        val occupied = (sets.completedSetsForSession(s.startedAtEpochMs).filter { it.exerciseId == exerciseId }.map { it.setNumber } +
            sets.skippedSetsForSession(s.startedAtEpochMs).filter { it.exerciseId == exerciseId }.map { it.setNumber }).toSet()
        val next = generateSequence(1) { it + 1 }.first { it !in occupied }
        if (exercise?.targetSets != null && next > exercise.targetSets) return SetInputError.NOT_STORED
        sets.write(s.day, exerciseId, next, SetRecordStore.Record(valid.kg, valid.reps, "5", true), s.recordDate, s.startedAtEpochMs)
        // The store keeps only a valid record as completed; confirm it before the rest (or anything else) changes.
        if (!sets.read(s.day, exerciseId, next, s.recordDate, s.startedAtEpochMs).completed) return SetInputError.NOT_STORED
        // Rest starts only when the user chose a rest time for this exercise; nothing is assumed otherwise.
        // Rest only sits between sets: none after the exercise's last target set (unknown target = cannot tell, rest runs).
        val rest = exercise?.restSeconds
        if (rest != null && restFollowsSet(exercise.targetSets, next)) store.startRest(exerciseId, rest) else store.skipRest()
        return null
    }

    /** Explicitly skip only the next untouched, planned set; never alters completed records or starts a rest. */
    fun skip(exerciseId: String, setNumber: Int, exercise: RoutineExerciseInput?): Boolean {
        val s = store.active() ?: return false
        if (s.over || s.progress.kind != StageKind.STRENGTH || exercise?.id != exerciseId) return false
        val target = exercise.targetSets ?: return false
        if (setNumber !in 1..target) return false
        val occupied = (sets.completedSetsForSession(s.startedAtEpochMs).filter { it.exerciseId == exerciseId }.map { it.setNumber } +
            sets.skippedSetsForSession(s.startedAtEpochMs).filter { it.exerciseId == exerciseId }.map { it.setNumber }).toSet()
        val next = (1..target).firstOrNull { it !in occupied } ?: return false
        if (setNumber != next) return false
        return sets.markSkipped(s.day, exerciseId, setNumber, s.recordDate, s.startedAtEpochMs)
    }

    /** Restore a skipped set to UNDECIDED while the strength stage is still active. */
    fun undoSkip(exerciseId: String, setNumber: Int, exercise: RoutineExerciseInput?): Boolean {
        val s = store.active() ?: return false
        if (s.over || s.progress.kind != StageKind.STRENGTH || exercise?.id != exerciseId) return false
        if (setNumber !in 1..(exercise.targetSets ?: 0)) return false
        return sets.undoSkipped(s.day, exerciseId, setNumber, s.recordDate, s.startedAtEpochMs)
    }
}
