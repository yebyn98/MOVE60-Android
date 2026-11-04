package com.move60.workout.architecture

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DailyWorkoutEntity::class, WorkoutSetEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
}
