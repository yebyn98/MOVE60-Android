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

    @Test fun timeNobodyTrackedIsNeverCountedAndThereIsNoGracePeriod() {
        val first = store(); started(first)
        t += 100_000; assertEquals(100L, first.reconcile()!!.accumulatedSeconds)
        // The process that tracked the session died; a new process (different token) finds it still "running".
        t += 3_000
        val next = RoutiveSessionStore(context, { t }, { date }, "new-process")
        val s = next.reconcile()!!
        assertFalse(s.running); assertTrue(s.autoPaused)
        assertEquals("not even 3 s of the untracked gap is counted", 100L, s.accumulatedSeconds)
        assertEquals(s, RoutiveSessionStore(context, { t }, { date }, "new-process").active())
        t += 5_000
        val resumed = next.resume()!!
        assertTrue(resumed.running); assertFalse(resumed.autoPaused)
        t += 20_000
        assertEquals(120L, next.reconcile()!!.accumulatedSeconds)
    }

    @Test fun aLongGapInTheSameLiveProcessIsStillCountedBecauseTheTrackerSurvived() {
        val st = store(); started(st)
        t += 3_600_000L                                  // screen off for an hour, service alive in the same process
        val s = st.reconcile()!!
        assertTrue(s.running); assertFalse(s.autoPaused); assertEquals(3_600L, s.accumulatedSeconds)
    }

    @Test fun releasedTrackingPausesAtTheNextLookWithoutCountingLaterTime() {
        val st = store(); started(st)
        t += 50_000; st.releaseTracking()
        t += 400_000
        val s = st.reconcile()!!
        assertFalse(s.running); assertTrue(s.autoPaused); assertEquals(50L, s.accumulatedSeconds)
    }

    @Test fun sessionsStoredBeforeTrackingTokensExistAreSafelyPausedNotCounted() {
        val st = store(); started(st)
        val prefs = context.getSharedPreferences(RoutiveSessionStore.NAME, Context.MODE_PRIVATE)
        val json = org.json.JSONObject(prefs.getString("active", null)!!).also { it.remove("trackerToken"); it.remove("performed") }
        prefs.edit().putString("active", json.toString()).commit()
        t += 10_000
        val s = store().reconcile()!!
        assertTrue(s.autoPaused); assertEquals(0L, s.accumulatedSeconds)
    }

    @Test fun newRecordsSupportSundayAndCommitThroughTheExistingSync() = runBlocking {
        val sunday = LocalDate.parse("2026-11-22")
        val st = RoutiveSessionStore(context, { t }, { sunday })
        val s0 = (st.start(plan()) as RoutiveStart.Started).session
        assertEquals(7, s0.day)
        SetRecordStore(context).write(s0.day, "chestPress", 1, SetRecordStore.Record("40", "10", "5", true), sunday, s0.startedAtEpochMs)
        t += 61_000; val ended = st.endEarly()!!
        val sync = TimerSessionSync(SessionCommitter(db) { 5_000L }) { "Asia/Seoul" }
        val snap = st.snapshot(ended)
        assertTrue(snap.day == 7 && sync.sessionId(snap).contains("-d7-"))
        assertTrue(sync.syncFinished(snap) is SyncOutcome.Committed)
        assertEquals(1, db.routiveDao().session(sync.sessionId(snap))!!.completedStrengthSets)
    }

    @Test fun explicitFinalSkipIsSavedAsCompletedAndEarlyEndWithUnfinishedStagesAsPartial() = runBlocking {
        val sync = TimerSessionSync(SessionCommitter(db) { 5_000L }) { "Asia/Seoul" }
        val st = store(); started(st)
        t += 70_000; st.advance(); t += 70_000; st.advance(); t += 70_000; st.advance()
        t += 10_000; val done = st.skip()!!
        assertTrue(done.progress.finished && !done.progress.endedEarly)
        val snap = st.snapshot(done)
        assertTrue(snap.finished && !snap.endedEarly)
        assertTrue(sync.syncFinished(snap) is SyncOutcome.Committed)
        assertEquals("COMPLETED", db.routiveDao().session(sync.sessionId(snap))!!.completion)

        t += 1_000
        val second = (st.start(plan("r2")) as RoutiveStart.Started).session
        t += 90_000; st.advance(); t += 30_000
        val early = st.endEarly()!!
        assertEquals(3, StageStatusCheck.unfinished(early))
        val snap2 = st.snapshot(early)
        assertTrue(sync.syncFinished(snap2) is SyncOutcome.Committed)
        assertEquals("PARTIAL", db.routiveDao().session(sync.sessionId(snap2))!!.completion)
        assertNotEquals(second.startedAtEpochMs, done.startedAtEpochMs)
    }

    private object StageStatusCheck {
        fun unfinished(s: RoutiveSession) = (0 until 4).count { s.progress.statusOf(it) == com.move60.workout.domain.StageStatus.UNFINISHED }
    }

    // ---- rest timer (step B2) -----------------------------------------------------------------------------------

    private fun inStrength(st: RoutiveSessionStore) { started(st); t += 10_000; st.advance() }   // 10 s warm-up, now strength

    @Test fun restTimeIsExcludedFromStageTimeIntervalsAndTotals() {
        val st = store(); inStrength(st)
        t += 30_000; st.startRest("legPress", 60L)           // 30 s of strength work, then a 60 s rest
        t += 45_000; var s = st.reconcile()!!
        assertEquals(15L, s.rest!!.remainingSeconds); assertFalse(s.rest!!.done)
        assertEquals("only the 30 s of work counts in the strength stage", 30L, s.progress.elapsedSeconds[1])
        assertEquals(40L, s.accumulatedSeconds)              // 10 warm-up + 30 strength
        assertEquals(40L, s.intervals.sumOf { it.second - it.first })
        t += 50_000; s = st.reconcile()!!                    // 15 s finish the rest, 35 s active again
        assertTrue(s.rest!!.done)
        assertEquals(30L + 35L, s.progress.elapsedSeconds[1]); assertEquals(75L, s.accumulatedSeconds)
        assertEquals(75L, s.intervals.sumOf { it.second - it.first })
    }

    @Test fun pausingFreezesTheRestAndResumeContinuesIt() {
        val st = store(); inStrength(st)
        st.startRest("legPress", 60L)
        t += 20_000; st.pause()
        t += 600_000                                          // paused: neither rest nor stage time moves
        assertEquals(40L, st.reconcile()!!.rest!!.remainingSeconds)
        st.resume(); t += 10_000
        val s = st.reconcile()!!
        assertEquals(30L, s.rest!!.remainingSeconds); assertEquals(10L, s.accumulatedSeconds)   // only the warm-up counted
    }

    @Test fun restSurvivesARestartAndSkippingKeepsTheRestedTimeExcluded() {
        val st = store(); inStrength(st)
        st.startRest("legPress", 90L); t += 30_000
        val reopened = store().active()!!                     // stored value, not yet settled
        assertEquals("legPress", reopened.rest!!.exerciseId)
        val skipped = store().skipRest()!!
        assertNull(skipped.rest)
        assertEquals("30 s rested are not stage time", 0L, skipped.progress.elapsedSeconds[1])
    }

    @Test fun invalidOrNonStrengthRestIsIgnoredAndStageChangeClearsRest() {
        val st = store(); started(st)
        assertNull("warm-up has no rest timer", st.startRest("walk", 60L)!!.rest)
        t += 5_000; st.advance()
        assertNull("no rest below the allowed range", st.startRest("legPress", 1L)!!.rest)
        assertNotNull(st.startRest("legPress", 60L)!!.rest)
        assertNull("changing stage ends the rest", st.advance()!!.rest)
    }

    @Test fun legacyStoredSessionsWithoutRestStillRead() {
        started()
        context.getSharedPreferences(RoutiveSessionStore.NAME, Context.MODE_PRIVATE).let { p ->
            val o = org.json.JSONObject(p.getString("active", null)!!); o.remove("rest"); p.edit().putString("active", o.toString()).commit()
        }
        assertNull(store().active()!!.rest)
    }

    @Test fun theEndOfRestCueFiresExactlyOnceAndSurvivesARestart() {
        val st = store(); inStrength(st)
        st.startRest("legPress", 30L)
        t += 10_000; assertFalse("still resting", st.consumeRestCue())
        t += 25_000; assertTrue("rest just ended", st.consumeRestCue())
        assertFalse("never twice (screen and service share it)", st.consumeRestCue())
        assertFalse("a restarted process does not repeat it", store().consumeRestCue())
        assertTrue(store().active()!!.rest!!.cued)
        st.skipRest(); st.startRest("legPress", 30L)
        t += 31_000; assertTrue("the next rest gets its own cue", st.consumeRestCue())
    }

    @Test fun noCueWhilePausedAndNoCueWithoutRest() {
        val st = store(); inStrength(st)
        assertFalse(st.consumeRestCue())
        st.startRest("legPress", 20L); t += 5_000; st.pause(); t += 600_000
        assertFalse("a paused rest does not finish", st.consumeRestCue())
    }

    // ---- review item 3: the previous record is kept when it cannot be copied to the outbox ----------------------------------

    /** A copy that fails the way a full or broken disk does: either "false" or an exception. */
    private class FailingOutbox(context: Context, private val throwing: Boolean) : PendingSessionOutbox(context) {
        override fun enqueue(s: TimerSnapshot): Boolean = if (throwing) throw IllegalStateException("disk full") else false
    }
    private fun failingStore(throwing: Boolean) = RoutiveSessionStore(context, { t }, { date }, outbox = FailingOutbox(context, throwing))

    private fun endedWithASet(): RoutiveSession {
        val st = store(); val first = started(st)
        SetRecordStore(context).write(first.day, "chestPress", 1, SetRecordStore.Record("40", "10", "5", true), date, first.startedAtEpochMs)
        t += 120_000; st.endEarly()
        return st.active()!!
    }

    @Test fun aFailedOutboxCopyKeepsTheOldSessionIntactAndTheNewStartCanBeRetried() {
        val old = endedWithASet()
        t += 5_000
        for (throwing in listOf(false, true)) {
            assertEquals("throwing=$throwing", RoutiveStart.PreserveFailed, failingStore(throwing).start(plan("r2")))
            val kept = store().active()!!
            assertEquals(old.startedAtEpochMs, kept.startedAtEpochMs); assertEquals("r1", kept.routineId)
            assertTrue(kept.over); assertEquals(120L, kept.accumulatedSeconds); assertEquals(old.intervals, kept.intervals)
            assertEquals("its sets are intact", 1, SetRecordStore(context).completedSetsForSession(old.startedAtEpochMs).size)
            assertEquals("nothing was half-written to the outbox", 0, PendingSessionOutbox(context).size())
        }
        // The user tries again once the copy can be written: the old record moves to the outbox and the new session starts.
        val second = (store().start(plan("r2")) as RoutiveStart.Started).session
        assertNotEquals(old.startedAtEpochMs, second.startedAtEpochMs)
        val queued = PendingSessionOutbox(context).pending().single()
        assertEquals(120, queued.accumulatedSeconds); assertEquals(1, queued.completedSets.size)
    }

    @Test fun anOverSessionThatNeverHadTimeCannotBeCommittedSoItDoesNotBlockANewStart() {
        val st = store(); started(st); st.endEarly()      // ended at the same instant: no interval, nothing a commit could use
        assertTrue(st.active()!!.intervals.isEmpty())
        t += 1_000
        assertTrue(failingStore(false).start(plan("r2")) is RoutiveStart.Started)
    }

    @Test fun anAlreadyConfirmedSessionNeedsNoCopyEither() {
        val old = endedWithASet()
        store().markCommitted(old.startedAtEpochMs)
        t += 1_000
        assertTrue(failingStore(true).start(plan("r2")) is RoutiveStart.Started)
    }

    @Test fun aRunningOrPausedSessionIsStillNeverReplaced() {
        started()
        assertEquals(RoutiveStart.AlreadyActive, failingStore(false).start(plan("r2")))
    }
}
