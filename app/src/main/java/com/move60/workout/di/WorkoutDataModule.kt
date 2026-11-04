package com.move60.workout.di

import android.content.Context
import androidx.room.Room
import com.move60.workout.EnergyLedger
import com.move60.workout.SessionStore
import com.move60.workout.WorkoutData
import com.move60.workout.WorkoutHistory
import com.move60.workout.data.DailyWorkoutDao
import com.move60.workout.data.Move60Database
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Presentation and workers receive persistence dependencies exclusively from Hilt. */
@Module
@InstallIn(SingletonComponent::class)
object WorkoutDataModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): Move60Database =
        Room.databaseBuilder(context, Move60Database::class.java, "move60_dashboard_cache.db")
            .build()

    @Provides
    fun dailyWorkoutDao(database: Move60Database): DailyWorkoutDao =
        database.dailyWorkoutDao()

    @Provides @Singleton
    fun workoutHistory(@ApplicationContext context: Context): WorkoutHistory = WorkoutHistory(context)

    @Provides @Singleton
    fun energyLedger(@ApplicationContext context: Context): EnergyLedger = EnergyLedger(context)

    @Provides @Singleton
    fun workoutData(@ApplicationContext context: Context): WorkoutData = WorkoutData(context)

    @Provides @Singleton
    fun workoutSession(@ApplicationContext context: Context, data: WorkoutData): SessionStore =
        SessionStore(context, data)
}
