package com.move60.workout.architecture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.SetRecordStore
import com.move60.workout.domain.StageKind
import com.move60.workout.domain.StageTimePlan
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoutiveSessionStoreTest {
    private lateinit var context: Context
    private lateinit var db: WorkoutDatabase
    private var t = 1_700_000_000_000L
    private val date = LocalDate.parse("2026-11-16")
    private fun store() = RoutiveSessionStore(context, { t }, { date })
    private fun plan(id: String = "r1", cardio: Long? = 900L) = SavedRoutinePlan(
        id, "테스트 루틴", null,
        listOf(StageTimePlan(StageKind.WARM_UP, 300), StageTimePlan(StageKind.STRENGTH, 1200), StageTimePlan(StageKind.CARDIO, cardio), StageTimePlan(StageKind.COOL_DOWN, null))
    )

    @Before fun init() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }
    private fun started(s: RoutiveSessionStore = store()) = (s.start(plan()) as RoutiveStart.Started).session

    @Test fun startCreatesARunningFourStageSessionThatSurvivesARestart() {
        val s = started()
        assertTrue(s.running); assertEquals(0, s.progress.stageIndex); assertEquals(4, s.progress.plan.size)
        val again = store().active()!!
        assertEquals(s, again)
        assertNull("unknown cool-down time stays null, never 0", again.progress.plannedSeconds(3))
    }

    @Test fun timeAccruesOnlyWhileRunningAndIntervalsMatchTheElapsedTotal() {
        val st = store(); started(st)
        t += 100_000; st.pause()
        t += 500_000                                   // paused: nothing counts
        st.resume()
        t += 50_000; val s = st.reconcile()!!
        assertEquals(150L, s.accumulatedSeconds)
        assertEquals(2, s.intervals.size)
        assertEquals(150L, s.intervals.sumOf { it.second - it.first })
        assertEquals(150L, s.progress.elapsedSeconds[0])
    }

    @Test fun aKilledProcessLosesNoRunningTime() {
        started()
        t += 90_000
        val restored = store().reconcile()!!          // a new store instance after process death
        assertEquals(90L, restored.progress.elapsedSeconds[0])
        assertTrue(restored.running)
    }

    @Test fun timeBeforeAnActionBelongsToTheStageItHappenedIn() {
        val st = store(); started(st)
        t += 40_000; st.advance()
        t += 70_000; st.skip()
        t += 30_000; val s = st.back()!!
        assertEquals(listOf(40L, 70L, 30L, 0L), s.progress.elapsedSeconds.let { listOf(it[0], it[1], it[2], it[3]) })
        assertEquals(1, s.progress.stageIndex)
        assertEquals(140L, s.accumulatedSeconds)
        assertEquals(s, store().active())
    }

    @Test fun startNeverOverwritesARunningOrPausedSession() {
        val st = store(); val first = started(st)
        t += 10_000
        assertEquals(RoutiveStart.AlreadyActive, st.start(plan("other")))
        st.pause()
        assertEquals(RoutiveStart.AlreadyActive, st.start(plan("other")))
        assertEquals(first.startedAtEpochMs, store().active()!!.startedAtEpochMs)
        assertEquals("r1", store().active()!!.routineId)
    }

    @Test fun nonFourStagePlanIsRejectedWithoutTouchingAnything() {
        val legacyShaped = SavedRoutinePlan("x", "x", null, com.move60.workout.domain.WorkoutStructures.stages(com.move60.workout.domain.StructureVersion.LEGACY_5).map { StageTimePlan(it.kind, 60) })
        assertEquals(RoutiveStart.InvalidPlan, store().start(legacyShaped))
        assertNull(store().active())
    }

    @Test fun newStartArchivesAnUnconfirmedOverSessionBeforeReplacingIt() = runBlocking {
        val st = store(); val first = started(st)
        SetRecordStore(context).write(first.day, "chestPress", 1, SetRecordStore.Record("40", "10", "5", true), date, first.startedAtEpochMs)
        t += 120_000; st.endEarly()
        val outbox = PendingSessionOutbox(context)
        assertEquals(0, outbox.size())
        t += 5_000; val second = (st.start(plan("r2")) as RoutiveStart.Started).session
        assertNotEquals(first.startedAtEpochMs, second.startedAtEpochMs)
        assertEquals(1, outbox.size())
        val queued = outbox.pending().single()
        assertEquals(120, queued.accumulatedSeconds); assertEquals(1, queued.completedSets.size); assertTrue(queued.endedEarly)
    }

    @Test fun finishedAndEarlyEndedSessionsCommitThroughTheExistingSyncWithRealSets() = runBlocking {
        val sync = TimerSessionSync(SessionCommitter(db) { 5_000L }) { "Asia/Seoul" }
        val st = store(); val s0 = started(st)
        SetRecordStore(context).write(s0.day, "chestPress", 1, SetRecordStore.Record("40", "10", "5", true), date, s0.startedAtEpochMs)
        t += 30_000                                    // 30 s only: valid because of the completed set (P-26 B)
        val ended = st.endEarly()!!
        assertTrue(ended.over && !ended.running && ended.progress.endedEarly)
        val snap = st.snapshot(ended)
        assertTrue(sync.syncFinished(snap) is SyncOutcome.Committed)
        val row = db.routiveDao().session(sync.sessionId(snap))!!
        assertEquals("PARTIAL", row.completion); assertEquals(30L, row.totalActiveSeconds); assertEquals(1, row.completedStrengthSets)
        assertTrue("second sync is idempotent", sync.syncFinished(snap) is SyncOutcome.AlreadyCommitted)

        t += 1_000
        val done = (st.start(plan("r3")) as RoutiveStart.Started).session
        t += 61_000
        repeat(4) { st.advance() }
        val fin = st.active()!!
        assertTrue(fin.progress.finished && !fin.progress.endedEarly)
        val finRow = sync.syncFinished(st.snapshot(fin))
        assertTrue(finRow is SyncOutcome.Committed)
        assertEquals("COMPLETED", db.routiveDao().session(sync.sessionId(st.snapshot(fin)))!!.completion)
        assertEquals(done.startedAtEpochMs, fin.startedAtEpochMs)
    }

    @Test fun overSessionsAreFrozenAndRunningSessionsAreNotRestarted() {
        val st = store(); started(st)
        t += 10_000; val ended = st.endEarly()!!
        t += 99_000
        assertEquals(ended, st.reconcile()); assertEquals(ended, st.advance()); assertEquals(ended, st.resume()); assertEquals(ended, st.back())
        val st2 = store(); st2.start(plan("n")); val running = st2.resume()!!
        assertTrue(running.running)
    }

    @Test fun unreadableStoredDataIsBackedUpAndNeverDeleted() {
        val prefs = context.getSharedPreferences(RoutiveSessionStore.NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("active", "{not json").commit()
        assertNull(store().active())
        assertTrue(store().start(plan()) is RoutiveStart.Started)
        assertTrue("raw data kept under a backup key", prefs.all.any { it.key.startsWith("unreadable_") && it.value == "{not json" })
    }

    @Test fun legacySessionStoreDataIsNeverReadOrWritten() {
        val legacy = context.getSharedPreferences("move60_native_sessions_v8", Context.MODE_PRIVATE)
        legacy.edit().putString("daily_2026-11-16_1", "{\"stage\":2,\"sessionStartedAt\":5}").commit()
        val before = legacy.all.toMap()
        val st = store(); started(st); t += 20_000; st.advance(); st.endEarly()
        assertEquals(before, legacy.all.toMap())
    }

    @Test fun aConfirmedCommitIsRememberedSoTheSessionIsNeverReBuffered() = runBlocking {
        val st = store(); started(st)
        t += 61_000; val ended = st.endEarly()!!
        assertEquals(ended, st.pendingCommit())
        st.markCommitted(ended.startedAtEpochMs)
        assertNull("committed session is not pending", st.pendingCommit())
        assertTrue(st.isCommitted(ended))
        t += 1_000
        assertTrue(st.start(plan("r2")) is RoutiveStart.Started)
        assertEquals("a committed session is not archived again on the next start", 0, PendingSessionOutbox(context).size())
        assertNull("a new running session is never pending", st.pendingCommit())
    }

    @Test fun anUntrackedGapIsNeverCountedAndPausesTheSession() {
        val st = store(); started(st)
        t += 100_000; assertEquals(100L, st.reconcile()!!.accumulatedSeconds)
        t += (RoutiveAwayPolicy.MAX_UNTRACKED_GAP_SECONDS + 1) * 1_000L
        val s = st.reconcile()!!
        assertFalse(s.running); assertTrue(s.autoPaused)
        assertEquals("the gap is not exercise time", 100L, s.accumulatedSeconds)
        assertEquals(s, store().active())                       // survives a restart
        t += 5_000
        val resumed = st.resume()!!
        assertTrue(resumed.running); assertFalse(resumed.autoPaused)
        t += 20_000
        assertEquals(120L, st.reconcile()!!.accumulatedSeconds)
    }

    @Test fun aGapWithinTheLimitIsStillCounted() {
        val st = store(); started(st)
        t += RoutiveAwayPolicy.MAX_UNTRACKED_GAP_SECONDS * 1_000L
        val s = st.reconcile()!!
        assertTrue(s.running); assertFalse(s.autoPaused)
        assertEquals(RoutiveAwayPolicy.MAX_UNTRACKED_GAP_SECONDS, s.accumulatedSeconds)
    }
}
