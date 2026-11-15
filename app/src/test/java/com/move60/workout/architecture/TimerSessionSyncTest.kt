package com.move60.workout.architecture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.SessionStore
import com.move60.workout.WorkoutData
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimerSessionSyncTest {
    private lateinit var context: Context
    private lateinit var db: WorkoutDatabase
    private lateinit var committer: SessionCommitter
    private lateinit var sync: TimerSessionSync
    private var nowMs = System.currentTimeMillis()

    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
        committer = SessionCommitter(db) { nowMs }
        sync = TimerSessionSync(committer) { "Asia/Seoul" }
    }
    @After fun close() { db.close() }

    private fun store() = SessionStore(context, WorkoutData(context))
    private fun snap(finished: Boolean = true, started: Long = 1_000L, acc: Int = 600, intervals: List<Pair<Long, Long>> = listOf(100L to 700L)) =
        TimerSnapshot(LocalDate.parse("2026-11-15"), 3, started, finished, acc, intervals)

    // ---- SessionStore records real intervals without changing active-seconds accounting ----
    @Test fun storeIntervalsEqualAccumulatedActiveSecondsAndSurviveReload() {
        val s = store(); s.start()
        val t0 = System.currentTimeMillis()
        s.reconcile(t0 + 300_400L); s.reconcile(t0 + 600_000L)
        assertEquals(1, s.activeIntervals().size)                    // contiguous checkpoints merge
        assertEquals(s.accumulatedActiveSeconds.toLong(), s.activeIntervals().sumOf { it.second - it.first })
        val reloaded = store()
        assertEquals(s.activeIntervals(), reloaded.activeIntervals())
        assertEquals(s.accumulatedActiveSeconds, reloaded.accumulatedActiveSeconds)
    }

    @Test fun pauseCreatesAGapNotCountedAsActive() {
        val s = store(); s.start()
        val t0 = System.currentTimeMillis()
        s.reconcile(t0 + 120_000L); s.pause()
        val before = s.activeIntervals()
        s.start()                                                    // resume later: new checkpoint, separate interval
        s.reconcile(System.currentTimeMillis() + 90_000L)
        assertTrue(s.activeIntervals().size >= before.size)
        assertEquals(s.accumulatedActiveSeconds.toLong(), s.activeIntervals().sumOf { it.second - it.first })
    }

    @Test fun legacyStateWithoutIntervalsIsNeverReconstructed() {
        assertNull(sync.bundleOrNull(snap(intervals = emptyList())))
        assertNull(sync.bundleOrNull(snap(acc = 601)))               // total mismatch -> skipped
        assertNull(sync.bundleOrNull(snap(finished = false)))
        assertNull(sync.bundleOrNull(snap(started = 0L)))
        assertNotNull(sync.bundleOrNull(snap()))
    }

    // ---- sync commits only finished, verifiable sessions, idempotently ----
    @Test fun finishedSessionIsCommittedOnceAndRetriesAreSafe() = runBlocking {
        val s = snap()
        val first = sync.syncFinished(s)
        assertTrue("got $first", first is SyncOutcome.Committed)
        assertTrue(sync.syncFinished(s) is SyncOutcome.AlreadyCommitted)
        assertTrue(sync.onForeground(s) is SyncOutcome.AlreadyCommitted)
        val row = db.routiveDao().session(sync.sessionId(s))!!
        assertEquals(600L, row.totalActiveSeconds); assertEquals(SessionCompletion.COMPLETED.name, row.completion)
        assertEquals(true, row.validSession)
    }

    @Test fun unfinishedOrUnverifiableSessionsAreSkippedWithoutWriting() = runBlocking {
        assertTrue(sync.syncFinished(snap(finished = false)) is SyncOutcome.Skipped)
        assertTrue(sync.syncFinished(snap(intervals = emptyList())) is SyncOutcome.Skipped)
        assertEquals(0, db.query("SELECT COUNT(*) FROM sessions", null).use { it.moveToFirst(); it.getInt(0) })
    }

    @Test fun belowThresholdFinishedSessionIsDiscardedNotStored() = runBlocking {
        val s = snap(acc = 30, intervals = listOf(100L to 130L))
        assertTrue(sync.syncFinished(s) is SyncOutcome.Discarded)
        assertNull(db.routiveDao().session(sync.sessionId(s)))
    }

    @Test fun foregroundSettlesChallengeEvenWithoutAnySession() = runBlocking {
        nowMs = T.ms("2026-11-15", 9)
        committer.challenges.create(challengeOf(start = "2026-11-15", end = "2026-11-15", target = 1))
        nowMs = T.ms("2026-11-16", 0, 0, 1)
        sync.onForeground(null)
        assertEquals("EXPIRED", db.routiveDao().challenge("c1")!!.status)
    }

    @Test fun conflictingContentUnderTheSameIdIsReportedNotThrownAndStoredDataStays() = runBlocking {
        assertTrue(sync.syncFinished(snap()) is SyncOutcome.Committed)
        val r = sync.syncFinished(snap(acc = 660, intervals = listOf(100L to 760L)))
        assertTrue("got $r", r is SyncOutcome.Failed)
        assertEquals(600L, db.routiveDao().session(sync.sessionId(snap()))!!.totalActiveSeconds)
    }
}
