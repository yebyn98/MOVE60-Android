package com.move60.workout.architecture

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * v1: daily_workouts, workout_sets (legacy read-model).
 * v2: ROUTIVE session/interval/segment/set/adjustment/challenge/draft tables (unreleased), added by
 * [RoutiveMigrations.MIGRATION_1_2]. Legacy tables are never altered or dropped.
 * v3: routine_plans / routine_stage_plans (nullable planned seconds), added by [RoutiveMigrations.MIGRATION_2_3].
 * v4: routine_exercises (exercises chosen per stage), added by [RoutiveMigrations.MIGRATION_3_4].
 * v5: nullable strength targets (sets, reps, rest seconds) on routine_exercises, [RoutiveMigrations.MIGRATION_4_5].
 */
@Database(
    entities = [
        DailyWorkoutEntity::class, WorkoutSetEntity::class,
        SessionEntity::class, SessionActiveIntervalEntity::class, SessionDaySliceEntity::class, SessionSegmentEntity::class,
        SessionSetEntity::class, SessionAdjustmentEntity::class,
        ChallengeEntity::class, ChallengeDayCreditEntity::class,
        SessionDraftEntity::class,
        RoutinePlanEntity::class, RoutineStagePlanEntity::class, RoutineExerciseEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun routiveDao(): RoutiveDao
}
