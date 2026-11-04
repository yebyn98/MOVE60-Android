package com.move60.workout.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Read model only. The existing workout service and preference ledger remain authoritative;
 * Room mirrors confirmed daily totals without deleting or resetting workout history.
 */
@Entity(tableName = "daily_workout_cache")
data class DailyWorkoutEntity(
    @PrimaryKey val date: String,
    val activeSeconds: Long,
    val estimatedKcal: Double,
    val updatedAtMillis: Long
)
