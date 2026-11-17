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
            // Keep the shipped file name so existing history is upgraded in place.
            .addMigrations(RoutiveMigrations.MIGRATION_1_2, RoutiveMigrations.MIGRATION_2_3, RoutiveMigrations.MIGRATION_3_4, RoutiveMigrations.MIGRATION_4_5, RoutiveMigrations.MIGRATION_5_6, RoutiveMigrations.MIGRATION_6_7)
            .build()

    @Provides fun dao(database: WorkoutDatabase): WorkoutDao = database.workoutDao()
    @Provides fun spaceDao(database: WorkoutDatabase): SpaceDao = database.spaceDao()
    @Provides @Singleton fun spaceService(database: WorkoutDatabase): SpaceService = SpaceService(database)
    @Provides fun routiveDao(database: WorkoutDatabase): RoutiveDao = database.routiveDao()
    @Provides @Singleton fun sessionCommitter(database: WorkoutDatabase): SessionCommitter = SessionCommitter(database)
    @Provides @Singleton fun routinePlanService(database: WorkoutDatabase): RoutinePlanService = RoutinePlanService(database)
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
