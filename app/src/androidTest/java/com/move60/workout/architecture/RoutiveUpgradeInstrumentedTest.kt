package com.move60.workout.architecture

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P0-1: upgrade a real v1 (shipped v58) database file to the current schema on a device/emulator.
 *
 * Uses its own database file name so it can never touch the app's real `move60_workouts_room_v1.db`
 * (user workout history). The file is removed before and after each test.
 * The JVM twin is `RoutiveMigrationTest`; this class proves the same chain on real SQLite and a real
 * Android runtime. It is only evidence of what actually ran in CI, not of a physical-device upgrade.
 */
@RunWith(AndroidJUnit4::class)
class RoutiveUpgradeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val dbName = "upgrade-instrumented-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation, WorkoutDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory()
    )

    private val chain = arrayOf(
        RoutiveMigrations.MIGRATION_1_2, RoutiveMigrations.MIGRATION_2_3,
        RoutiveMigrations.MIGRATION_3_4, RoutiveMigrations.MIGRATION_4_5, RoutiveMigrations.MIGRATION_5_6
    )

    @Before fun removeStaleTestFile() { context.deleteDatabase(dbName) }
    @After fun removeTestFile() { context.deleteDatabase(dbName) }

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

    /** v1 -> v5 in one run: nothing dropped (validateDroppedTables) and every legacy row identical. */
    @Test fun v1FileUpgradesToCurrentSchemaAndKeepsLegacyHistory() {
        val before: List<String>
        helper.createDatabase(dbName, 1).use { db -> seedV1(db); before = legacyRows(db) }
        assertEquals(3, before.size)

        helper.runMigrationsAndValidate(dbName, 5, true, *chain).use { db ->
            assertEquals("legacy rows must be unchanged after v1 -> v5", before, legacyRows(db))
        }
    }

    /** The upgraded file opens with the real Room database class and old data is still readable through the DAOs. */
    @Test fun upgradedFileOpensWithRealRoomAndOldDataStillReadable() = runBlocking {
        helper.createDatabase(dbName, 1).use { seedV1(it) }
        helper.runMigrationsAndValidate(dbName, 5, true, *chain).close()

        val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbName)
            .addMigrations(*chain).build()
        try {
            val legacy = db.workoutDao()
            assertEquals(3540L, legacy.day("2026-11-04")?.activeSeconds)
            assertEquals(59L, legacy.day("2026-11-05")?.activeSeconds)
            assertEquals(1, legacy.observeSets(1730000000000L).first().size)
        } finally { db.close() }
    }
}
