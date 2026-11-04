package com.move60.workout.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyWorkoutDao {
    @Query("SELECT * FROM daily_workout_cache ORDER BY date DESC LIMIT 45")
    fun observeRecent(): Flow<List<DailyWorkoutEntity>>

    @Query("SELECT * FROM daily_workout_cache WHERE date = :date LIMIT 1")
    fun observeDate(date: String): Flow<DailyWorkoutEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DailyWorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<DailyWorkoutEntity>)
}
