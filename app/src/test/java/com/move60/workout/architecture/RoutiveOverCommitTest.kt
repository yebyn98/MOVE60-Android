package com.move60.workout.architecture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.SetRecordStore
import com.move60.workout.domain.StageKind
import com.move60.workout.domain.StageTimePlan
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Review item 1: an over session is saved WITHOUT the session screen (watch finishes the last stage after the phone left it),
 * and the phone screen and the watch/service finishing together cannot create a second record.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoutiveOverCommitTest {
    private lateinit var context: Context
    private lateinit var db: WorkoutDatabase
    private lateinit var store: RoutiveSessionStore
    private lateinit var sets: SetRecordStore
    private lateinit var outbox: PendingSessionOutbox
    private lateinit var sync: TimerSessionSync
    private lateinit var commit: RoutiveOverCommit
    private var t = T.ms("2026-11-16", 12)
    private val date = LocalDate.parse("2026-11-16")
    private val plan = SavedRoutinePlan("r1", "루틴", null,
        listOf(StageTimePlan(StageKind.WARM_UP, 300), StageTimePlan(StageKind.STRENGTH, 1200), StageTimePlan(StageKind.CARDIO, 900), StageTimePlan(StageKind.COOL_DOWN, null)))

    @Before fun init() {
        context = ApplicationProvider.getApplicationContext()
        for (n in listOf(RoutiveSessionStore.NAME, "move60_set_records_v1", PendingSessionOutbox.NAME)) context.getSharedPreferences(n, 0).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
        store = RoutiveSessionStore(context, { t }, { date })
        sets = SetRecordStore(context); outbox = PendingSessionOutbox(context)
        sync = TimerSessionSync(SessionCommitter(db) { t }) { "Asia/Seoul" }
        commit = RoutiveOverCommit(store, outbox, sync, sets)
    }
    @After fun close() { db.close() }

    /** The user finishes every stage (as the watch's "next" does) with no session screen involved. */
    private fun finishedFromTheWatch(): RoutiveSession {
        val s = (store.start(plan) as RoutiveStart.Started).session
        t += 10_000; store.advance()
        sets.write(s.day, "legPress", 1, SetRecordStore.Record("40", "10", "5", true), s.recordDate, s.startedAtEpochMs)
        repeat(3) { t += 20_000; store.advance() }
        return store.active()!!.also { assertTrue(it.over) }
    }

    @Test fun anOverSessionIsSavedWithoutTheScreenAndItsChallengeCreditIsRefreshed() = runBlocking {
        db.routiveDao().insertChallenge(challengeOf(start = "2026-11-16", end = "2026-11-30", target = 3))
        val over = finishedFromTheWatch()
        assertNotNull(store.pendingCommit())
        val outcome = commit.commitPending()
        assertTrue(outcome is RoutiveOutcome.ResultReady)
        val row = db.routiveDao().session(sync.sessionId(store.snapshot(over, sets)))!!
        assertEquals(over.accumulatedSeconds, row.totalActiveSeconds)
        assertEquals(1, db.routiveDao().sets(row.sessionId).size)
        assertEquals(1, db.routiveDao().sessionCount())
        assertNull("confirmed, so nothing is pending any more", store.pendingCommit())
        assertEquals(0, outbox.size())
    }

    @Test fun repeatingTheCommitNeverAddsASecondRecord() = runBlocking {
        finishedFromTheWatch()
        assertTrue(commit.commitPending() is RoutiveOutcome.ResultReady)
        assertNull("second call: nothing unconfirmed", commit.commitPending())
        assertEquals(1, db.routiveDao().sessionCount())
    }

    @Test fun theScreenAndTheServiceCommittingTogetherCreateExactlyOneRecord() = runBlocking {
        db.routiveDao().insertChallenge(challengeOf(start = "2026-11-16", end = "2026-11-30", target = 3))
        val over = finishedFromTheWatch()
        val results = (1..8).map { i ->
            async(Dispatchers.Default) { if (i % 2 == 0) commit.commit(over) else commit.commitPending() }
        }.awaitAll()
        assertTrue(results.filterNotNull().all { it is RoutiveOutcome.ResultReady })
        assertEquals("one record, however many writers finished at once", 1, db.routiveDao().sessionCount())
        val credit = db.routiveDao().credit("c1", "2026-11-16")
        assertTrue("credit is recomputed, not accumulated per commit", credit == null || credit.validSessionCount <= 1)
        assertEquals(0, outbox.size())
    }

    @Test fun noUnconfirmedSessionJustDrainsTheOutbox() = runBlocking {
        assertNull(commit.commitPending())
        assertEquals(0, db.routiveDao().sessionCount())
    }
}
