package com.move60.workout.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DailyWorkoutEntity::class],
    version = 1,
    exportSchema = false
)
abstract class Move60Database : RoomDatabase() {
    abstract fun dailyWorkoutDao(): DailyWorkoutDao
}
