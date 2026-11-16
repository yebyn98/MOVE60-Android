package com.move60.workout.architecture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.SetRecordStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Policy 5 (A): v58 `SetRecordStore` sets are viewable in v2 as they were saved, without changing anything. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacySetReaderTest {
    private lateinit var context: Context
    private lateinit var db: WorkoutDatabase
    private lateinit var store: SetRecordStore
    private lateinit var reader: LegacySetReader
    private val day = LocalDate.parse("2026-11-04")
    private val prefsName = "move60_set_records_v1"

    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
        store = SetRecordStore(context)
        reader = LegacySetReader(db) { store.completedStoredSets() }
    }
    @After fun close() { db.close() }

    private fun done(kg: String, reps: String) = SetRecordStore.Record(kg, reps, "5", true)
    private fun rawPrefs() = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private fun count(table: String): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }

    /** AC-01: the saved values are shown exactly; a legacy workout without a stored session stays unlinked and gets no time. */
    @Test fun completedV58SetsAreShownWithTheirSavedValuesAndStayUnlinked() = runBlocking {
        store.write(2, "chestPress", 1, done("40", "10"), day, token = 111L)
        store.write(2, "chestPress", 2, done("42.5", "8"), day, token = 111L)
        store.write(2, "legPress", 1, done("80", "12"), day, token = 111L)

        val view = reader.forDate(day)
        assertEquals(1, view.groups.size)
        val group = view.groups.single()
        assertFalse("no stored session exists, so nothing is linked", group.linked)
        assertNull(group.sessionId)
        assertEquals(
            listOf(Triple("chestPress", 1, "40" to "10"), Triple("chestPress", 2, "42.5" to "8"), Triple("legPress", 1, "80" to "12")),
            group.sets.map { Triple(it.exerciseId, it.setNumber, it.kg to it.reps) }
        )
        assertTrue(group.sets.all { it.origin == SetOrigin.LEGACY && !it.differsFromStored })
        assertEquals("a session must never be created for it", 0, count("sessions"))
    }

    /** Policy 5.5: uncompleted sets, drafts (token 0), invalid values and unparseable keys are never turned into records. */
    @Test fun draftsUncompletedInvalidAndMalformedKeysAreNotShown() = runBlocking {
        store.write(2, "chestPress", 1, done("40", "10"), day, token = 111L)                     // the only real one
        store.write(2, "chestPress", 2, SetRecordStore.Record("40", "8", "5", false), day, token = 111L)  // not completed
        store.write(2, "latPulldown", 1, SetRecordStore.Record("", "", "5", true), day, token = 111L)     // invalid -> stored as not completed
        store.write(2, "seatedRow", 1, done("30", "10"), day, token = 0L)                         // token 0 = draft, never a workout
        val json = """{"kg":"50","reps":"5","rpe":"5","completed":true}"""
        rawPrefs().edit()
            .putString("set:$day:2:111:chest:press:1", json)          // 7 parts
            .putString("set:not-a-date:2:111:legPress:1", json)
            .putString("set:$day:2:abc:legPress:1", json)
            .putString("set:$day:2:111:legPress:x", json)
            .putString("set:$day:2:111::1", json)                     // blank exercise id
            .putString("set:$day:2:111:legPress:0", json)             // set numbers start at 1
            .putString("set:$day:2:111:legPress:3", "not json")
            .commit()

        val sets = reader.forDate(day).groups.flatMap { it.sets }
        assertEquals(listOf("chestPress" to 1), sets.map { it.exerciseId to it.setNumber })
    }

    /** Policy 5.3: when the exact stored session exists its sets appear under it; identical duplicates are shown once. */
    @Test fun linkedSessionShowsStoredSetsPlusOnlyTheLegacyValuesNotAlreadyStored() = runBlocking {
        val sync = TimerSessionSync(SessionCommitter(db) { 5_000L }) { "Asia/Seoul" }
        val snap = TimerSnapshot(
            day, 2, 111L, true, 120, listOf(100L to 220L), false,
            listOf(TimerSet("chestPress", 1, "40", "10"), TimerSet("latPulldown", 1, "30", "12"))
        )
        assertTrue(sync.syncFinished(snap) is SyncOutcome.Committed)
        val id = sync.sessionId(snap)
        store.write(2, "chestPress", 1, done("40", "10"), day, token = 111L)     // identical to stored -> not repeated
        store.write(2, "latPulldown", 1, done("35", "12"), day, token = 111L)    // different value -> kept, flagged
        store.write(2, "chestPress", 2, done("50", "8"), day, token = 111L)      // not in the stored session

        val group = reader.forDate(day).groups.single()
        assertEquals(id, group.sessionId)
        assertTrue(group.linked)
        assertEquals(
            listOf(
                listOf("chestPress", "1", "40", "10", "STORED", "false"),
                listOf("chestPress", "2", "50", "8", "LEGACY", "false"),
                listOf("latPulldown", "1", "30", "12", "STORED", "false"),
                listOf("latPulldown", "1", "35", "12", "LEGACY", "true")
            ),
            group.sets.map { listOf(it.exerciseId, "${it.setNumber}", it.kg, it.reps, it.origin.name, "${it.differsFromStored}") }
        )
    }

    /** Policy 4 / mapping accuracy: a session with another id is not a match, so the sets are not linked to it. */
    @Test fun sessionWithADifferentIdIsNeverGuessedAsTheLink() = runBlocking {
        val sync = TimerSessionSync(SessionCommitter(db) { 5_000L }) { "Asia/Seoul" }
        val other = TimerSnapshot(day, 2, 999L, true, 120, listOf(100L to 220L), false, listOf(TimerSet("chestPress", 1, "40", "10")))
        assertTrue(sync.syncFinished(other) is SyncOutcome.Committed)
        store.write(2, "chestPress", 1, done("40", "10"), day, token = 111L)   // other token
        store.write(3, "chestPress", 1, done("40", "10"), day, token = 999L)   // same token, other weekday key

        val groups = reader.forDate(day).groups
        assertEquals(2, groups.size)
        assertTrue("neither may be linked to the stored session", groups.none { it.linked })
    }

    @Test fun otherDatesAndSeparateWorkoutsOfTheSameDateAreKeptApart() = runBlocking {
        store.write(2, "chestPress", 1, done("40", "10"), day, token = 111L)
        store.write(2, "legPress", 1, done("80", "12"), day, token = 222L)
        store.write(2, "legPress", 1, done("90", "12"), day.plusDays(1), token = 333L)

        val view = reader.forDate(day)
        assertEquals(listOf(111L, 222L), view.groups.map { it.token })
        assertEquals(1, reader.forDate(day.plusDays(1)).groups.size)
        assertTrue(reader.forDate(day.minusDays(1)).isEmpty)
    }

    /**
     * AC-02 + AC-03: reading (also repeatedly) leaves the SharedPreferences originals byte-for-byte unchanged, creates no
     * session, active time or challenge credit, and gives the same answer every time (so nothing can duplicate).
     */
    @Test fun readingIsRepeatableAndChangesNothing() = runBlocking {
        store.write(2, "chestPress", 1, done("40", "10"), day, token = 111L)
        store.write(2, "chestPress", 2, done("40", "8"), day, token = 111L)
        val before = rawPrefs().all.toSortedMap()
        val tables = listOf("sessions", "session_sets", "session_active_intervals", "session_day_slices", "challenges", "challenge_day_credits")
        val rowsBefore = tables.map { count(it) }

        val first = reader.forDate(day)
        repeat(3) { assertEquals(first, reader.forDate(day)) }

        assertEquals("originals must be unchanged", before, rawPrefs().all.toSortedMap())
        assertEquals("no Room row may be created", rowsBefore, tables.map { count(it) })
        assertEquals("no active time may appear", 0L, db.routiveDao().sessionSecondsOn(day.toString()))
        assertEquals("no challenge credit may appear", 0, count("challenge_day_credits"))
        assertEquals(2, first.groups.single().sets.size)
    }
}
