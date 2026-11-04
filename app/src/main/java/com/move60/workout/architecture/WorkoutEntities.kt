package com.move60.workout.architecture

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Read-model of active seconds recorded on a Korea calendar date. */
@Entity(tableName = "daily_workouts")
data class DailyWorkoutEntity(
    @PrimaryKey val date: String,
    val activeSeconds: Long,
    val estimatedKcal: Double,
    val synchronizedAt: Long
)

/** New Compose set edits are persisted here AND to the legacy store during transition. */
@Entity(tableName = "workout_sets")
data class WorkoutSetEntity(
    @PrimaryKey val key: String,
    val date: String,
    val sessionId: Long,
    val exerciseId: String,
    val setNumber: Int,
    val weightKg: String,
    val repetitions: String,
    val completed: Boolean
)
