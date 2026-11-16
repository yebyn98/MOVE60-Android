package com.move60.workout.architecture

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room v1 -> v2. Purely ADDITIVE: only CREATE statements for the new ROUTIVE tables/indices.
 * `daily_workouts` and `workout_sets` (and all SharedPreferences stores) are not touched, so a
 * failed or interrupted upgrade can never lose existing workout history. There is deliberately
 * no fallbackToDestructiveMigration anywhere in this app.
 *
 * The statements are copied verbatim from the Room-exported schema (schemas/.../2.json);
 * RoutiveMigrationTest validates the migrated database against that exported schema.
 */
object RoutiveMigrations {
    val V1_TO_V2_STATEMENTS: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `sessions` (`sessionId` TEXT NOT NULL, `legacyToken` INTEGER, `zoneId` TEXT NOT NULL, `startedAtEpochMs` INTEGER, `endedAtEpochMs` INTEGER, `completion` TEXT NOT NULL, `totalActiveSeconds` INTEGER NOT NULL, `completedStrengthSets` INTEGER NOT NULL, `validSession` INTEGER NOT NULL, `planSnapshotJson` TEXT, `committedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`sessionId`))",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_sessions_legacyToken` ON `sessions` (`legacyToken`)",
        "CREATE TABLE IF NOT EXISTS `session_active_intervals` (`sessionId` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `startEpochSec` INTEGER NOT NULL, `endEpochSec` INTEGER NOT NULL, PRIMARY KEY(`sessionId`, `orderIndex`), FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`sessionId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_session_active_intervals_startEpochSec` ON `session_active_intervals` (`startEpochSec`)",
        "CREATE INDEX IF NOT EXISTS `index_session_active_intervals_endEpochSec` ON `session_active_intervals` (`endEpochSec`)",
        "CREATE TABLE IF NOT EXISTS `session_day_slices` (`sessionId` TEXT NOT NULL, `date` TEXT NOT NULL, `activeSecondsOnDate` INTEGER NOT NULL, PRIMARY KEY(`sessionId`, `date`), FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`sessionId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_session_day_slices_date` ON `session_day_slices` (`date`)",
        "CREATE TABLE IF NOT EXISTS `session_segments` (`sessionId` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `kind` TEXT NOT NULL, `plannedSeconds` INTEGER, `actualSeconds` INTEGER NOT NULL, `equipment` TEXT, `metricsJson` TEXT, PRIMARY KEY(`sessionId`, `orderIndex`), FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`sessionId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE TABLE IF NOT EXISTS `session_sets` (`sessionId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `setNumber` INTEGER NOT NULL, `state` TEXT NOT NULL, `weightKg` TEXT NOT NULL, `repetitions` TEXT NOT NULL, `completedAtEpochMs` INTEGER, PRIMARY KEY(`sessionId`, `exerciseId`, `setNumber`), FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`sessionId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE TABLE IF NOT EXISTS `session_adjustments` (`adjustmentId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `field` TEXT NOT NULL, `oldValue` TEXT, `newValue` TEXT, `reason` TEXT, FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`sessionId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_session_adjustments_sessionId` ON `session_adjustments` (`sessionId`)",
        "CREATE TABLE IF NOT EXISTS `challenges` (`challengeId` TEXT NOT NULL, `name` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT NOT NULL, `timeZoneId` TEXT NOT NULL, `targetDays` INTEGER NOT NULL, `status` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `endedAtEpochMs` INTEGER, `endSnapshotJson` TEXT, PRIMARY KEY(`challengeId`))",
        "CREATE INDEX IF NOT EXISTS `index_challenges_status` ON `challenges` (`status`)",
        "CREATE TABLE IF NOT EXISTS `challenge_day_credits` (`challengeId` TEXT NOT NULL, `date` TEXT NOT NULL, `activeSeconds` INTEGER NOT NULL, `validSessionCount` INTEGER NOT NULL, `credited` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`challengeId`, `date`), FOREIGN KEY(`challengeId`) REFERENCES `challenges`(`challengeId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE TABLE IF NOT EXISTS `session_drafts` (`draftId` TEXT NOT NULL, `kind` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `revision` INTEGER NOT NULL, `state` TEXT NOT NULL, `lastErrorCode` TEXT, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`draftId`))"
    )

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            V1_TO_V2_STATEMENTS.forEach(db::execSQL)
        }
    }

    /** Room v2 -> v3: additive routine plan tables (nullable planned seconds). Copied from schemas/.../3.json. */
    val V2_TO_V3_STATEMENTS: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `routine_plans` (`routineId` TEXT NOT NULL, `name` TEXT NOT NULL, `structure` TEXT NOT NULL, `targetSeconds` INTEGER, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`routineId`))",
        "CREATE TABLE IF NOT EXISTS `routine_stage_plans` (`routineId` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `kind` TEXT NOT NULL, `plannedSeconds` INTEGER, PRIMARY KEY(`routineId`, `orderIndex`), FOREIGN KEY(`routineId`) REFERENCES `routine_plans`(`routineId`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    )

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            V2_TO_V3_STATEMENTS.forEach(db::execSQL)
        }
    }

    /** Room v3 -> v4: additive routine_exercises table. Copied from schemas/.../4.json. */
    val V3_TO_V4_STATEMENTS: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `routine_exercises` (`routineId` TEXT NOT NULL, `stageIndex` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, `exerciseId` TEXT NOT NULL, `name` TEXT NOT NULL, `plannedSeconds` INTEGER, PRIMARY KEY(`routineId`, `stageIndex`, `orderIndex`), FOREIGN KEY(`routineId`) REFERENCES `routine_plans`(`routineId`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    )

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            V3_TO_V4_STATEMENTS.forEach(db::execSQL)
        }
    }

    /** Room v4 -> v5: three nullable columns on routine_exercises (existing rows keep NULL = unset). Copied from schemas/.../5.json. */
    val V4_TO_V5_STATEMENTS: List<String> = listOf(
        "ALTER TABLE `routine_exercises` ADD COLUMN `targetSets` INTEGER",
        "ALTER TABLE `routine_exercises` ADD COLUMN `targetReps` INTEGER",
        "ALTER TABLE `routine_exercises` ADD COLUMN `restSeconds` INTEGER"
    )

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            V4_TO_V5_STATEMENTS.forEach(db::execSQL)
        }
    }
}
