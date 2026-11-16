package com.move60.workout.architecture

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.room.migration.Migration
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrades a REAL v1 database file (created from the shipped v1 schema export) to v2 and checks
 * that legacy history survives byte-for-byte and the result matches the Room-exported v2 schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoutiveMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WorkoutDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )
    private val dbName = "migration-test"

    private fun seedV1(db: SupportSQLiteDatabase) {
        db.insert("daily_workouts", SQLiteDatabase.CONFLICT_FAIL, ContentValues().apply {
            put("date", "2026-11-04"); put("activeSeconds", 3540L); put("estimatedKcal", 212.5); put("synchronizedAt", 1730000000000L)
        })
        db.insert("daily_workouts", SQLiteDatabase.CONFLICT_FAIL, ContentValues().apply {
            put("date", "2026-11-05"); put("activeSeconds", 59L); put("estimatedKcal", 3.0); put("synchronizedAt", 1730000100000L)
        })
        db.insert("workout_sets", SQLiteDatabase.CONFLICT_FAIL, ContentValues().apply {
            put("key", "2026-11-04:1730000000000:bench:1"); put("date", "2026-11-04"); put("sessionId", 1730000000000L)
            put("exerciseId", "bench"); put("setNumber", 1); put("weightKg", "40"); put("repetitions", "10"); put("completed", 1)
        })
    }

    private fun legacyRows(db: SupportSQLiteDatabase): List<String> {
        val out = ArrayList<String>()
        db.query("SELECT date, activeSeconds, estimatedKcal, synchronizedAt FROM daily_workouts ORDER BY date").use {
            while (it.moveToNext()) out += "d|${it.getString(0)}|${it.getLong(1)}|${it.getDouble(2)}|${it.getLong(3)}"
        }
        db.query("SELECT key, date, sessionId, exerciseId, setNumber, weightKg, repetitions, completed FROM workout_sets ORDER BY key").use {
            while (it.moveToNext()) out += "s|" + (0..7).joinToString("|") { i -> it.getString(i) }
        }
        return out
    }

    @Test fun upgradePreservesLegacyHistoryAndMatchesExportedSchema() {
        val before: List<String>
        helper.createDatabase(dbName, 1).use { db -> seedV1(db); before = legacyRows(db) }
        assertEquals(3, before.size)

        // validateDroppedTables=true also proves nothing in v1 was dropped.
        helper.runMigrationsAndValidate(dbName, 2, true, RoutiveMigrations.MIGRATION_1_2).use { db ->
            assertEquals("legacy rows must be unchanged", before, legacyRows(db))
            for (table in listOf("sessions", "session_day_slices", "session_segments", "session_sets",
                "session_adjustments", "challenges", "challenge_day_credits", "session_drafts", "session_active_intervals")) {
                db.query("SELECT COUNT(*) FROM `$table`").use { c -> assertTrue(c.moveToFirst()); assertEquals("$table starts empty", 0, c.getInt(0)) }
            }
        }
    }

    @Test fun upgradedFileOpensWithRealRoomAndOldDataStillReadable() = runBlocking {
        helper.createDatabase(dbName, 1).use { seedV1(it) }
        helper.runMigrationsAndValidate(dbName, 2, true, RoutiveMigrations.MIGRATION_1_2).close()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbName)
            .addMigrations(RoutiveMigrations.MIGRATION_1_2, RoutiveMigrations.MIGRATION_2_3, RoutiveMigrations.MIGRATION_3_4, RoutiveMigrations.MIGRATION_4_5, RoutiveMigrations.MIGRATION_5_6).allowMainThreadQueries().build()
        try {
            val legacy = db.workoutDao()
            assertEquals(3540L, legacy.day("2026-11-04")?.activeSeconds)
            assertEquals(59L, legacy.day("2026-11-05")?.activeSeconds)
            assertEquals(1, legacy.observeSets(1730000000000L).first().size)
            assertEquals(null, db.routiveDao().session("anything"))
        } finally { db.close() }
    }

    @Test fun failedMigrationRollsBackAndLeavesLegacyDataIntact() {
        val before: List<String>
        helper.createDatabase(dbName, 1).use { db -> seedV1(db); before = legacyRows(db) }
        val broken = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                RoutiveMigrations.V1_TO_V2_STATEMENTS.forEach(db::execSQL)
                db.execSQL("THIS IS NOT VALID SQL")
            }
        }
        try {
            helper.runMigrationsAndValidate(dbName, 2, true, broken)
            fail("a broken migration must not report success")
        } catch (expected: Exception) { /* expected */ }

        // File is still v1: history intact and no half-created v2 tables remain.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val raw = android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val rows = ArrayList<String>()
            raw.rawQuery("SELECT date, activeSeconds FROM daily_workouts ORDER BY date", null).use { while (it.moveToNext()) rows += "${it.getString(0)}|${it.getLong(1)}" }
            assertEquals(listOf("2026-11-04|3540", "2026-11-05|59"), rows)
            raw.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='sessions'", null).use { assertFalse("rolled back", it.moveToFirst()) }
            assertEquals(1, raw.version)
        } finally { raw.close() }
    }

    @Test fun v2ToV3AddsRoutinePlanTablesWithoutTouchingExistingData() {
        val before: List<String>
        helper.createDatabase(dbName, 1).use { db -> seedV1(db); before = legacyRows(db) }
        helper.runMigrationsAndValidate(dbName, 3, true, RoutiveMigrations.MIGRATION_1_2, RoutiveMigrations.MIGRATION_2_3).use { db ->
            assertEquals(before, legacyRows(db))
            for (t in listOf("routine_plans", "routine_stage_plans")) {
                db.query("SELECT COUNT(*) FROM `$t`").use { c -> assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)) }
            }
        }
    }

    @Test fun v3ToV4AddsExerciseTableAndKeepsRoutinePlans() {
        helper.createDatabase(dbName, 1).use { seedV1(it) }
        helper.runMigrationsAndValidate(dbName, 3, true, RoutiveMigrations.MIGRATION_1_2, RoutiveMigrations.MIGRATION_2_3).use { db ->
            db.execSQL("INSERT INTO routine_plans(routineId,name,structure,targetSeconds,createdAtEpochMs,updatedAtEpochMs) VALUES('r1','하체','ROUTIVE_4',NULL,1,1)")
            db.execSQL("INSERT INTO routine_stage_plans(routineId,orderIndex,kind,plannedSeconds) VALUES('r1',0,'WARM_UP',NULL)")
        }
        helper.runMigrationsAndValidate(dbName, 4, true, RoutiveMigrations.MIGRATION_3_4).use { db ->
            db.query("SELECT name, structure FROM routine_plans WHERE routineId='r1'").use { assertTrue(it.moveToFirst()); assertEquals("하체", it.getString(0)) }
            db.query("SELECT plannedSeconds FROM routine_stage_plans WHERE routineId='r1'").use { assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)) }
            db.query("SELECT COUNT(*) FROM routine_exercises").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            assertEquals(2, db.query("SELECT COUNT(*) FROM daily_workouts").use { it.moveToFirst(); it.getInt(0) })
        }
    }

    @Test fun v4ToV5AddsNullableStrengthTargetsAndKeepsEveryExistingRow() {
        helper.createDatabase(dbName, 1).use { seedV1(it) }
        helper.runMigrationsAndValidate(dbName, 4, true, RoutiveMigrations.MIGRATION_1_2, RoutiveMigrations.MIGRATION_2_3, RoutiveMigrations.MIGRATION_3_4).use { db ->
            db.execSQL("INSERT INTO routine_plans(routineId,name,structure,targetSeconds,createdAtEpochMs,updatedAtEpochMs) VALUES('r1','하체','ROUTIVE_4',NULL,1,1)")
            db.execSQL("INSERT INTO routine_exercises(routineId,stageIndex,orderIndex,exerciseId,name,plannedSeconds) VALUES('r1',1,0,'legPress','레그 프레스',NULL)")
            db.execSQL("INSERT INTO routine_exercises(routineId,stageIndex,orderIndex,exerciseId,name,plannedSeconds) VALUES('r1',2,0,'bike','실내 자전거',900)")
        }
        helper.runMigrationsAndValidate(dbName, 5, true, RoutiveMigrations.MIGRATION_4_5).use { db ->
            db.query("SELECT exerciseId, plannedSeconds, targetSets, targetReps, restSeconds FROM routine_exercises ORDER BY stageIndex").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("legPress", c.getString(0))
                assertTrue("old rows are unset, not defaulted", c.isNull(1) && c.isNull(2) && c.isNull(3) && c.isNull(4))
                assertTrue(c.moveToNext()); assertEquals(900L, c.getLong(1)); assertTrue(c.isNull(4))
            }
            db.execSQL("UPDATE routine_exercises SET targetSets=3, targetReps=10, restSeconds=90 WHERE exerciseId='legPress'")
            db.query("SELECT restSeconds FROM routine_exercises WHERE exerciseId='legPress'").use { assertTrue(it.moveToFirst()); assertEquals(90L, it.getLong(0)) }
            assertEquals(2, db.query("SELECT COUNT(*) FROM daily_workouts").use { it.moveToFirst(); it.getInt(0) })
        }
    }

    /** v5 -> v6 adds the MY tab tables; every existing row (history, routines) survives and the new tables start empty. */
    @Test fun v5ToV6AddsSpaceTablesWithoutTouchingExistingRows() {
        helper.createDatabase(dbName, 5).use { db ->
            seedV1(db)
            db.execSQL("INSERT INTO routine_plans(routineId,name,structure,targetSeconds,createdAtEpochMs,updatedAtEpochMs) VALUES('r1','하체','ROUTIVE_4',NULL,1,2)")
        }
        helper.runMigrationsAndValidate(dbName, 6, true, RoutiveMigrations.MIGRATION_5_6).use { db ->
            assertEquals(2, db.query("SELECT COUNT(*) FROM daily_workouts").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(1, db.query("SELECT COUNT(*) FROM routine_plans").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(0, db.query("SELECT COUNT(*) FROM workout_spaces").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(0, db.query("SELECT COUNT(*) FROM space_equipment").use { it.moveToFirst(); it.getInt(0) })
        }
    }
}
