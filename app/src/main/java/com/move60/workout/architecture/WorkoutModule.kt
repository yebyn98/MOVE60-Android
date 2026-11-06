package com.move60.workout.architecture

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.move60.workout.EnergyLedger
import com.move60.workout.SessionStore
import com.move60.workout.SetRecordStore
import com.move60.workout.StageLog
import com.move60.workout.WorkoutData
import com.move60.workout.WorkoutHistory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkoutProvides {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): WorkoutDatabase =
        Room.databaseBuilder(context, WorkoutDatabase::class.java, "move60_workouts_room_v1.db")
            .build()

    @Provides fun dao(database: WorkoutDatabase): WorkoutDao = database.workoutDao()
    @Provides @Singleton fun workManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
    @Provides @Singleton fun data(@ApplicationContext context: Context): WorkoutData = WorkoutData(context)
    @Provides @Singleton fun session(@ApplicationContext context: Context, data: WorkoutData): SessionStore =
        SessionStore(context, data)
    @Provides fun history(@ApplicationContext context: Context): WorkoutHistory = WorkoutHistory(context)
    @Provides fun energy(@ApplicationContext context: Context): EnergyLedger = EnergyLedger(context)
    @Provides fun sets(@ApplicationContext context: Context): SetRecordStore = SetRecordStore(context)
    @Provides fun stages(@ApplicationContext context: Context): StageLog = StageLog(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutBindings {
    @Binds @Singleton
    abstract fun repository(implementation: DefaultWorkoutRepository): WorkoutRepository
    @Binds @Singleton
    abstract fun preferences(implementation: LegacyPreferenceRepository): PreferenceRepository
}
