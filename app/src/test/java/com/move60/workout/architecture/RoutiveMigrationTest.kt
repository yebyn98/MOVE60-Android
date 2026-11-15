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
            .addMigrations(RoutiveMigrations.MIGRATION_1_2).allowMainThreadQueries().build()
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
}
