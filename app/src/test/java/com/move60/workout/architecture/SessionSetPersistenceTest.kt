package com.move60.workout.architecture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.SessionStore
import com.move60.workout.SetRecordStore
import com.move60.workout.WorkoutData
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** P0-1: completed strength sets must reach the stored session (P-26 B), idempotently, also for PARTIAL sessions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionSetPersistenceTest {
    private lateinit var context: Context
    private lateinit var db: WorkoutDatabase
    private lateinit var sync: TimerSessionSync
    private val dao get() = db.routiveDao()

    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
        sync = TimerSessionSync(SessionCommitter(db) { 5_000L }) { "Asia/Seoul" }
    }
    @After fun close() { db.close() }

    private val benchSet = TimerSet("chestPress", 1, "40", "10")
    private fun snap(seconds: Int, sets: List<TimerSet>, finished: Boolean = true, early: Boolean = false) = TimerSnapshot(
        LocalDate.parse("2026-11-15"), 3, 1_000L, finished, seconds, listOf(100L to 100L + seconds), early, sets
    )

    @Test fun fiftyNineSecondsWithOneCompletedSetIsStoredAsValidWithTheSet() = runBlocking {
        val s = snap(59, listOf(benchSet))
        assertTrue(sync.syncFinished(s) is SyncOutcome.Committed)
        val row = dao.session(sync.sessionId(s))!!
        assertTrue("P-26 B: one completed set makes a short session valid", row.validSession)
        assertEquals(1, row.completedStrengthSets)
        val sets = dao.sets(row.sessionId)
        assertEquals(listOf("chestPress"), sets.map { it.exerciseId })
        assertEquals("40", sets.single().weightKg); assertEquals("10", sets.single().repetitions)
        assertEquals("COMPLETED", sets.single().state)
    }

    @Test fun fiftyNineSecondsWithoutCompletedSetIsDiscarded() = runBlocking {
        val s = snap(59, emptyList())
        assertTrue(sync.syncFinished(s) is SyncOutcome.Discarded)
        assertNull(dao.session(sync.sessionId(s)))
    }

    @Test fun earlyEndedPartialSessionKeepsItsSets() = runBlocking {
        val s = snap(300, listOf(benchSet, TimerSet("legPress", 1, "80", "8")), finished = false, early = true)
        assertTrue(sync.syncFinished(s) is SyncOutcome.Committed)
        val row = dao.session(sync.sessionId(s))!!
        assertEquals("PARTIAL", row.completion)
        assertEquals(2, row.completedStrengthSets)
        assertEquals(2, dao.sets(row.sessionId).size)
    }

    @Test fun repeatedSyncNeverDuplicatesSets() = runBlocking {
        val s = snap(120, listOf(benchSet))
        assertTrue(sync.syncFinished(s) is SyncOutcome.Committed)
        assertTrue(sync.syncFinished(s) is SyncOutcome.AlreadyCommitted)
        assertTrue(sync.onForeground(s) is SyncOutcome.AlreadyCommitted)
        assertEquals(1, dao.sets(sync.sessionId(s)).size)
    }

    @Test fun setStoreReturnsOnlyThisWorkoutsCompletedValidSets() {
        val store = SetRecordStore(context)
        val day = LocalDate.parse("2026-11-15")
        store.write(3, "chestPress", 1, SetRecordStore.Record("40", "10", "5", true), day, token = 111L)
        store.write(3, "chestPress", 2, SetRecordStore.Record("40", "8", "5", false), day, token = 111L)   // not completed
        store.write(3, "latPulldown", 1, SetRecordStore.Record("", "", "5", true), day, token = 111L)      // invalid -> not completed
        store.write(3, "chestPress", 1, SetRecordStore.Record("60", "5", "5", true), day, token = 222L)    // other workout
        val sets = store.completedSetsForSession(111L)
        assertEquals(listOf(SetRecordStore.CompletedSet("chestPress", 1, "40", "10")), sets)
        assertTrue(store.completedSetsForSession(0L).isEmpty())
    }

    @Test fun timerSnapshotCarriesTheRealSetsOfTheRunningSession() {
        val session = SessionStore(context, WorkoutData(context)); session.resetAll(); session.start()
        val token = session.sessionStartedAt
        assertTrue(token > 0L)
        val store = SetRecordStore(context)
        store.write(session.day, "legPress", 1, SetRecordStore.Record("100", "8", "5", true), token = token)
        val snap = session.timerSnapshot(store)
        assertEquals(listOf(TimerSet("legPress", 1, "100", "8")), snap.completedSets)
        assertTrue("without a store the snapshot has no sets (legacy callers)", session.timerSnapshot().completedSets.isEmpty())
    }
    @Test fun explicitSkippedSetCommitsToRoomWithoutInflatingCompletedCount() = runBlocking {
        val s = snap(300, listOf(benchSet)).copy(skippedSets = listOf(TimerSkippedSet("chestPress", 2)))
        assertTrue(sync.syncFinished(s) is SyncOutcome.Committed)
        val row = dao.session(sync.sessionId(s))!!
        assertEquals(1, row.completedStrengthSets)
        val sets = dao.sets(row.sessionId).sortedBy { it.setNumber }
        assertEquals(listOf("COMPLETED", "SKIPPED"), sets.map { it.state })
        assertEquals("", sets[1].weightKg)
        assertEquals("", sets[1].repetitions)
        assertTrue(sync.syncFinished(s) is SyncOutcome.AlreadyCommitted)
        assertEquals(2, dao.sets(row.sessionId).size)
    }

    @Test fun aSkipAloneCannotMakeShortSessionValid() = runBlocking {
        val s = snap(59, emptyList()).copy(skippedSets = listOf(TimerSkippedSet("chestPress", 1)))
        assertTrue(sync.syncFinished(s) is SyncOutcome.Discarded)
        assertNull(dao.session(sync.sessionId(s)))
    }

}
