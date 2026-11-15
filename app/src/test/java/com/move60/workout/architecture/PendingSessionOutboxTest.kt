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

/**
 * P1-1: a finished / early-ended session whose Room commit has not been confirmed must survive the timer slot being
 * replaced (new start, reset) and be committed later with its real intervals and sets; never duplicated, never invented.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingSessionOutboxTest {
    private lateinit var context: Context
    private lateinit var db: WorkoutDatabase
    private lateinit var sync: TimerSessionSync
    private lateinit var outbox: PendingSessionOutbox
    private val dao get() = db.routiveDao()

    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
        sync = TimerSessionSync(SessionCommitter(db) { 5_000L }) { "Asia/Seoul" }
        outbox = PendingSessionOutbox(context)
    }
    @After fun close() { db.close() }

    private fun store() = SessionStore(context, WorkoutData(context))

    /** Runs a real session for [seconds] of active time and ends it early (PARTIAL) without any commit. */
    private fun endedEarly(seconds: Int, withSet: Boolean): Pair<SessionStore, TimerSnapshot> {
        val s = store(); s.start()
        if (withSet) SetRecordStore(context).write(s.day, "chestPress", 1, SetRecordStore.Record("40", "10", "5", true), s.recordDate, s.sessionStartedAt)
        s.reconcile(System.currentTimeMillis() + seconds * 1000L)
        assertTrue(s.endEarly())
        return s to s.timerSnapshot(SetRecordStore(context))
    }

    @Test fun startingAnotherSessionBeforeCommitKeepsTheEarlierOneAndDrainCommitsIt() = runBlocking {
        val (s, before) = endedEarly(120, withSet = true)
        assertTrue(before.endedEarly && before.intervals.isNotEmpty())
        assertEquals("nothing committed yet", 0, outbox.size())

        s.start()                                            // overwrites the single timer slot
        assertNotEquals(before.startedAtEpochMs, s.sessionStartedAt)
        assertEquals(1, outbox.size())
        val queued = outbox.pending().single()
        assertEquals(before.intervals, queued.intervals)
        assertEquals(before.accumulatedSeconds, queued.accumulatedSeconds)
        assertEquals(listOf(TimerSet("chestPress", 1, "40", "10")), queued.completedSets)

        val result = sync.drain(outbox)
        assertTrue(result.single() is SyncOutcome.Committed)
        val row = dao.session(sync.sessionId(before))!!
        assertEquals("PARTIAL", row.completion)
        assertEquals(before.accumulatedSeconds.toLong(), row.totalActiveSeconds)
        assertEquals(1, row.completedStrengthSets)
        assertEquals(1, dao.sets(row.sessionId).size)
        assertEquals("confirmed commit empties the outbox", 0, outbox.size())
    }

    @Test fun resetOfAFinishedUncommittedSessionKeepsItInTheOutbox() = runBlocking {
        val (s, before) = endedEarly(90, withSet = false)
        s.resetAll()
        assertEquals(1, outbox.size())
        assertTrue(sync.drain(outbox).single() is SyncOutcome.Committed)
        assertEquals(before.accumulatedSeconds.toLong(), dao.session(sync.sessionId(before))!!.totalActiveSeconds)
    }

    @Test fun resettingARunningSessionStaysADiscardAndIsNotArchived() {
        val s = store(); s.start()
        s.reconcile(System.currentTimeMillis() + 300_000L)
        s.resetAll()                                         // P-28 A: user discards an unfinished session
        assertEquals(0, outbox.size())
    }

    @Test fun failedCommitKeepsTheEntryAndALaterRetrySucceedsWithoutDuplicates() = runBlocking {
        val (s, before) = endedEarly(120, withSet = true)
        s.start()
        val broken = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
        val brokenSync = TimerSessionSync(SessionCommitter(broken) { 5_000L }) { "Asia/Seoul" }
        broken.close()
        assertTrue(brokenSync.drain(outbox).single() is SyncOutcome.Failed)
        assertEquals("a failed write must not drop the only copy", 1, outbox.size())
        assertEquals(1, outbox.attempts(sync.sessionId(before)))

        assertTrue(sync.drain(outbox).single() is SyncOutcome.Committed)
        assertTrue("retry after success is a no-op", sync.drain(outbox).isEmpty())
        assertEquals(1, dao.sets(sync.sessionId(before)).size)
        assertEquals(0, outbox.size())
    }

    @Test fun alreadyCommittedSessionIsClearedWithoutChangingStoredData() = runBlocking {
        val (s, before) = endedEarly(120, withSet = true)
        assertTrue(sync.syncFinished(before) is SyncOutcome.Committed)
        s.start()
        assertEquals(1, outbox.size())
        assertTrue(sync.drain(outbox).single() is SyncOutcome.AlreadyCommitted)
        assertEquals(0, outbox.size())
        assertEquals(1, dao.sets(sync.sessionId(before)).size)
    }

    @Test fun belowThresholdSessionIsDiscardedByThePolicyAndLeavesTheOutbox() = runBlocking {
        val (s, before) = endedEarly(30, withSet = false)
        s.start()
        assertEquals(1, outbox.size())
        assertTrue(sync.drain(outbox).single() is SyncOutcome.Discarded)
        assertNull(dao.session(sync.sessionId(before)))
        assertEquals(0, outbox.size())
    }

    @Test fun enqueueRefusesUnfinishedOrUnverifiableSnapshotsAndKeepsExistingBookkeeping() {
        val running = TimerSnapshot(java.time.LocalDate.parse("2026-11-15"), 3, 1_000L, false, 60, listOf(100L to 160L))
        assertFalse(outbox.enqueue(running))
        val noIntervals = running.copy(finished = true, intervals = emptyList())
        assertFalse(outbox.enqueue(noIntervals))
        val ok = running.copy(finished = true)
        assertTrue(outbox.enqueue(ok))
        outbox.markFailed(outbox.sessionId(ok), "boom")
        assertTrue(outbox.enqueue(ok))
        assertEquals("a second enqueue must not reset retry bookkeeping", 1, outbox.attempts(outbox.sessionId(ok)))
        assertEquals(1, outbox.size())
    }
}
