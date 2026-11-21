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
import java.time.YearMonth

/** Figma 11/26/27 read model: stored values only, legacy totals separate, nothing written. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordReaderTest {
    private lateinit var context: Context
    private lateinit var db: WorkoutDatabase
    private lateinit var store: SetRecordStore
    private lateinit var reader: RecordReader
    private val nov9 = LocalDate.parse("2026-11-09")

    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("move60_set_records_v1", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
        store = SetRecordStore(context)
        reader = RecordReader(db) { store.completedStoredSets() }
    }
    @After fun close() { db.close() }

    private fun completedSet(id: String, exercise: String, number: Int, kg: String, reps: String) =
        SessionSetEntity(id, exercise, number, SetState.COMPLETED.name, kg, reps, null)

    /** A stored session of [seconds] starting at [hour]:[minute] on [date] (Asia/Seoul) with the given completed sets. */
    private suspend fun commit(id: String, date: String, hour: Int, minute: Int, seconds: Long, sets: List<SessionSetEntity> = emptyList()) {
        val base = sessionBundle(id, listOf(T.run(T.at(date, hour, minute), seconds)), sets = sets.size)
        val result = SessionCommitter(db) { 5_000L }.commit(base.copy(sets = sets))
        assertTrue("$id must be stored: $result", result is CommitResult.Committed)
    }

    @Test fun monthDatesListOnlyDatesWithStoredSessionTimeInThatMonth() = runBlocking {
        commit("a", "2026-11-02", 7, 0, 600)
        commit("b", "2026-11-09", 20, 35, 600)
        commit("c", "2026-12-01", 7, 0, 600)

        assertEquals(setOf(LocalDate.parse("2026-11-02"), nov9), reader.sessionDates(YearMonth.of(2026, 11)))
        assertEquals(setOf(LocalDate.parse("2026-12-01")), reader.sessionDates(YearMonth.of(2026, 12)))
        assertTrue(reader.sessionDates(YearMonth.of(2026, 10)).isEmpty())
    }

    @Test fun dayListsSessionsEarliestFirstWithTheirOwnStoredValues() = runBlocking {
        commit("late", "2026-11-09", 21, 42, 900, listOf(completedSet("late", "legPress", 1, "80", "12")))
        commit("early", "2026-11-09", 20, 35, 2720,
            listOf(completedSet("early", "legPress", 1, "40", "12"), completedSet("early", "legCurl", 1, "20", "12")))

        val day = reader.day(nov9)
        assertEquals(listOf("early", "late"), day.sessions.map { it.sessionId })
        assertEquals(listOf("20:35", "21:42"), day.sessions.map { it.startClock })
        assertEquals(listOf(2720L, 900L), day.sessions.map { it.activeSecondsOnDate })
        assertEquals(2720L + 900L, day.sessionSeconds)
        assertEquals(listOf(listOf("legCurl", "legPress"), listOf("legPress")), day.sessions.map { it.exerciseIds })
        assertEquals(listOf(2, 1), day.sessions.map { it.completedSets })
        assertEquals(listOf(SessionCompletion.COMPLETED, SessionCompletion.COMPLETED), day.sessions.map { it.completion })
    }

    /** C-20261115-04: the legacy day total is returned on its own and never added to stored session time. */
    @Test fun legacyDayTotalStaysSeparateAndLegacyOnlyDaysAreNotEmpty() = runBlocking {
        db.workoutDao().upsertDay(DailyWorkoutEntity("2026-11-09", 3540L, 212.5, 1L))
        commit("s", "2026-11-09", 20, 35, 600)

        val both = reader.day(nov9)
        assertEquals(3540L, both.legacyActiveSeconds)
        assertEquals("stored session time must not include the legacy total", 600L, both.sessionSeconds)

        db.workoutDao().upsertDay(DailyWorkoutEntity("2026-11-04", 1800L, 100.0, 1L))
        val legacyOnly = reader.day(LocalDate.parse("2026-11-04"))
        assertTrue(legacyOnly.sessions.isEmpty())
        assertEquals(1800L, legacyOnly.legacyActiveSeconds)
        assertFalse(legacyOnly.isEmpty)

        assertTrue(reader.day(LocalDate.parse("2026-11-20")).isEmpty)
    }

    /** Policy 5 on the day record: v58 sets that match no stored session are listed separately, nothing is invented. */
    @Test fun unlinkedLegacySetsAppearOnTheDayAndLinkedOnesJoinTheirSession() = runBlocking {
        commit("timer-2026-11-09-d1-111", "2026-11-09", 20, 35, 600, listOf(completedSet("timer-2026-11-09-d1-111", "chestPress", 1, "40", "10")))
        store.write(1, "chestPress", 1, SetRecordStore.Record("40", "10", "5", true), nov9, token = 111L)   // same as stored
        store.write(1, "chestPress", 2, SetRecordStore.Record("50", "8", "5", true), nov9, token = 111L)    // legacy-only, same workout
        store.write(1, "legPress", 1, SetRecordStore.Record("80", "12", "5", true), nov9, token = 999L)     // no stored session

        val day = reader.day(nov9)
        assertEquals(1, day.sessions.size)
        assertEquals(listOf(999L), day.unlinkedLegacy.map { it.token })

        val detail = reader.session("timer-2026-11-09-d1-111")!!
        assertEquals(listOf("chestPress"), detail.exercises.map { it.exerciseId })
        assertEquals(
            listOf(Triple(1, "40", SetOrigin.STORED), Triple(2, "50", SetOrigin.LEGACY)),
            detail.exercises.single().sets.map { Triple(it.setNumber, it.kg, it.origin) }
        )
    }

    @Test fun sessionDetailGroupsStoredSetsByExerciseAndMissingSessionIsNull() = runBlocking {
        commit("d", "2026-11-09", 20, 35, 600, listOf(
            completedSet("d", "legPress", 1, "40", "12"), completedSet("d", "legPress", 2, "45", "12"), completedSet("d", "legCurl", 1, "20", "12")
        ))
        val detail = reader.session("d")!!
        assertEquals(nov9, detail.date)
        assertEquals(600L, detail.session.totalActiveSeconds)
        assertEquals(listOf("legCurl", "legPress"), detail.exercises.map { it.exerciseId })
        assertEquals(listOf(1, 2), detail.exercises.last().sets.map { it.setNumber })
        assertNull(reader.session("does-not-exist"))
    }

    @Test fun readingNeverChangesStoredRows() = runBlocking {
        commit("r", "2026-11-09", 20, 35, 600, listOf(completedSet("r", "legPress", 1, "40", "12")))
        store.write(1, "legPress", 1, SetRecordStore.Record("80", "12", "5", true), nov9, token = 5L)
        val tables = listOf("sessions", "session_sets", "session_active_intervals", "session_day_slices", "challenges", "challenge_day_credits", "daily_workouts")
        fun counts() = tables.map { t -> db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$t`").use { it.moveToFirst(); it.getInt(0) } }
        val before = counts()
        val prefsBefore = context.getSharedPreferences("move60_set_records_v1", Context.MODE_PRIVATE).all.toSortedMap()

        repeat(3) { reader.day(nov9); reader.session("r"); reader.sessionDates(YearMonth.of(2026, 11)) }

        assertEquals(before, counts())
        assertEquals(prefsBefore, context.getSharedPreferences("move60_set_records_v1", Context.MODE_PRIVATE).all.toSortedMap())
    }

    @Test fun formattingShowsStoredValuesWithoutInventingAnything() {
        assertEquals("60분 20초", RecordFormat.duration(3620))
        assertEquals("15분 00초", RecordFormat.duration(900))
        assertEquals("0분 07초", RecordFormat.duration(7))
        assertEquals("0분 00초", RecordFormat.duration(-5))
        assertEquals("20:35", RecordFormat.clock(T.ms("2026-11-09", 20, 35), "Asia/Seoul"))
        assertNull(RecordFormat.clock(null, "Asia/Seoul"))
        assertNull(RecordFormat.clock(T.ms("2026-11-09", 20, 35), "Not/AZone"))

        fun s(kg: String, reps: String) = ShownSet("x", 1, kg, reps, SetOrigin.STORED)
        assertEquals("", RecordFormat.setSummary(emptyList()))
        assertEquals("40kg × 10회 (1세트)", RecordFormat.setSummary(listOf(s("40", "10"))))
        assertEquals("20kg × 12회 × 3세트", RecordFormat.setSummary(listOf(s("20", "12"), s("20", "12"), s("20", "12"))))
        assertEquals("40kg × 12회 · 45kg × 12회 (2세트)", RecordFormat.setSummary(listOf(s("40", "12"), s("45", "12"))))
        assertEquals("완료", RecordFormat.completionLabel(SessionCompletion.COMPLETED))
        assertEquals("일부 수행", RecordFormat.completionLabel(SessionCompletion.PARTIAL))
        assertEquals("상태 확인 안 됨", RecordFormat.completionLabel(null))
    }
    @Test fun skippedSetsRemainVisibleButNotCountedAsCompletedVolume() = runBlocking {
        val id = "skipped-visible"
        val original = sessionBundle(id, listOf(T.run(T.at("2026-11-09", 20, 35), 600)), sets = 1)
        val performed = completedSet(id, "legPress", 1, "40", "12")
        val skipped = SessionSetEntity(id, "legPress", 2, SetState.SKIPPED.name, "", "", null)
        assertTrue(SessionCommitter(db) { 5_000L }.commit(original.copy(sets = listOf(performed, skipped))) is CommitResult.Committed)
        val detail = reader.session(id)!!
        assertEquals(1, detail.session.completedSets)
        assertEquals(1, detail.session.skippedSets)
        assertEquals(listOf(2), detail.skippedSets.map { it.setNumber })
        assertEquals("SKIPPED", detail.skippedSets.single().state)
        assertEquals(listOf(1), detail.exercises.single().sets.map { it.setNumber })
        assertEquals("40kg × 12회 (1세트)", RecordFormat.setSummary(detail.exercises.single().sets))
    }

}
