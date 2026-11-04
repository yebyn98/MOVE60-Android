package com.move60.workout.architecture

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM daily_workouts ORDER BY date DESC LIMIT :limit")
    fun observeRecentDays(limit: Int = 35): Flow<List<DailyWorkoutEntity>>

    @Query("SELECT * FROM daily_workouts WHERE date = :date LIMIT 1")
    suspend fun day(date: String): DailyWorkoutEntity?

    @Upsert
    suspend fun upsertDay(record: DailyWorkoutEntity)

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY exerciseId, setNumber")
    fun observeSets(sessionId: Long): Flow<List<WorkoutSetEntity>>

    @Upsert
    suspend fun upsertSet(record: WorkoutSetEntity)
}
