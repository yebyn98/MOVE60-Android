package com.move60.workout.architecture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.SessionStore
import com.move60.workout.WatchStatePublisher
import com.move60.workout.WorkoutData
import com.move60.workout.SetRecordStore
import com.move60.workout.domain.StageKind
import com.move60.workout.domain.StageTimePlan
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ROUTIVE_4 -> watch: what the phone publishes and how it answers the watch buttons. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoutiveWatchSyncTest {
    private lateinit var context: Context
    private lateinit var store: RoutiveSessionStore
    private fun plan(cool: Long? = null) = SavedRoutinePlan("r1", "루틴", null,
        listOf(StageTimePlan(StageKind.WARM_UP, 300), StageTimePlan(StageKind.STRENGTH, 1200), StageTimePlan(StageKind.CARDIO, 900), StageTimePlan(StageKind.COOL_DOWN, cool)))

    @Before fun init() {
        context = ApplicationProvider.getApplicationContext()
        store = RoutiveSessionStore(context)          // real clock: the sync classes read the same real clock
        assertTrue(store.start(plan()) is RoutiveStart.Started)
    }
    private fun map() = RoutiveWatchSync.createRequest(context, store.active()!!).dataMap
    private fun cmd(op: String, stage: Int = store.active()!!.progress.stageIndex, session: Long = store.active()!!.startedAtEpochMs) =
        JSONObject().put("op", op).put("session", session).put("stage", stage)

    @Test fun runningKnownPlanPublishesStructureStageAndAnAbsoluteDeadline() {
        val d = map()
        assertEquals("ROUTIVE_4", d.getString("structure")); assertEquals(4, d.getInt("stageCount")); assertEquals(27, d.getInt("protocol"))
        assertEquals(0, d.getInt("stageIndex")); assertEquals("준비운동", d.getString("stageName"))
        assertTrue(d.getBoolean("running")); assertFalse(d.getBoolean("finished")); assertTrue(d.getBoolean("plannedKnown"))
        val untilDeadline = (d.getLong("deadline") - System.currentTimeMillis()) / 1000
        assertTrue("deadline is about 5 minutes ahead: $untilDeadline", untilDeadline in 295..300)
    }

    @Test fun stageOrderFollowsTheFourStagesAndAnUnknownPlanIsNotPublishedAsANumber() {
        val names = mutableListOf<String>()
        repeat(4) { names += map().getString("stageName")!!; if (it < 3) store.advance() }
        assertEquals(listOf("준비운동", "근력운동", "유산소", "마무리"), names)
        val d = map()                                     // cool-down: no planned time
        assertFalse(d.getBoolean("plannedKnown")); assertEquals(0L, d.getLong("deadline")); assertEquals(0, d.getInt("remaining"))
        assertTrue("elapsed time is what the watch shows instead", d.containsKey("stageElapsed"))
    }

    @Test fun pausedSessionKeepsItsRemainingTimeAndHasNoDeadline() {
        store.pause()
        val d = map()
        assertFalse(d.getBoolean("running")); assertEquals(0L, d.getLong("deadline")); assertTrue(d.getInt("remaining") in 298..300)
    }

    @Test fun restIsPublishedAsADeadlineWhileRunningAndAsRemainingSecondsWhilePaused() {
        store.advance()
        store.startRest("legPress", 90L)
        var d = map()
        assertTrue(d.getLong("restDeadline") > System.currentTimeMillis()); assertTrue(d.getLong("restId") != 0L)
        store.pause()
        d = map()
        assertEquals(0L, d.getLong("restDeadline")); assertTrue(d.getInt("restPaused") in 88..90)
        store.resume(); store.skipRest()
        d = map(); assertEquals(0L, d.getLong("restDeadline")); assertEquals(0, d.getInt("restPaused"))
    }

    @Test fun anEndedSessionIsPublishedAsFinishedWithoutKcalOrSetTotals() {
        store.endEarly()
        val d = map()
        assertTrue(d.getBoolean("finished")); assertFalse(d.getBoolean("running")); assertEquals("운동 종료", d.getString("detail"))
        assertEquals(0.0, d.getDouble("estimatedKcal"), 0.0); assertEquals(0, d.getInt("totalSets"))
    }

    @Test fun theLegacyPublishEntryPointReturnsTheRoutiveStateWhileARoutiveSessionIsCurrent() {
        val legacySnapshot = SessionStore(context, WorkoutData(context)).snapshot()
        val d = WatchStatePublisher.createRequest(context, legacySnapshot).dataMap
        assertEquals("ROUTIVE_4", d.getString("structure"))
    }

    @Test fun watchButtonsPauseResumeAndAdvanceTheRealSession() {
        assertEquals(true, RoutiveWatchCommands.handle(context, cmd("pause"))); assertFalse(store.active()!!.running)
        assertEquals("pausing twice is rejected, not repeated", false, RoutiveWatchCommands.handle(context, cmd("pause")))
        assertEquals(true, RoutiveWatchCommands.handle(context, cmd("start"))); assertTrue(store.active()!!.running)
        assertEquals(true, RoutiveWatchCommands.handle(context, cmd("next"))); assertEquals(1, store.active()!!.progress.stageIndex)
    }

    @Test fun staleOrUnsupportedWatchCommandsAreRejectedAndChangeNothing() {
        assertEquals("stage already moved on", false, RoutiveWatchCommands.handle(context, cmd("next", stage = 3)))
        assertEquals("other session", false, RoutiveWatchCommands.handle(context, cmd("pause", session = 1L)))
        assertEquals("no +15 s rest in ROUTIVE_4", false, RoutiveWatchCommands.handle(context, cmd("rest_add")))
        assertEquals("rest_skip without a rest", false, RoutiveWatchCommands.handle(context, cmd("rest_skip")))
        assertEquals(0, store.active()!!.progress.stageIndex); assertTrue(store.active()!!.running)
    }

    @Test fun anEndedSessionRejectsEveryWatchCommand() {
        store.endEarly()
        assertEquals(false, RoutiveWatchCommands.handle(context, cmd("start")))      // no "new workout" from the watch
        assertEquals(false, RoutiveWatchCommands.handle(context, cmd("next")))
    }
    @Test fun watchMustDeferToPhoneWhenAPlannedStrengthSetIsUndecided() {
        store.endEarly()
        val withTarget = plan().copy(exercises = mapOf(
            StageKind.STRENGTH to listOf(RoutineExerciseInput("legPress", "레그프레스", null, 3, 10))
        ))
        assertTrue(store.start(withTarget) is RoutiveStart.Started)
        store.advance() // strength
        val s = store.active()!!
        val sets = SetRecordStore(context)
        assertEquals(false, RoutiveWatchCommands.handle(context, cmd("next")))
        assertEquals(1, store.active()!!.progress.stageIndex)
        sets.write(s.day, "legPress", 1, SetRecordStore.Record("40", "10", "5", true), s.recordDate, s.startedAtEpochMs)
        sets.write(s.day, "legPress", 2, SetRecordStore.Record("40", "10", "5", true), s.recordDate, s.startedAtEpochMs)
        assertEquals(false, RoutiveWatchCommands.handle(context, cmd("next")))
        assertTrue(sets.markSkipped(s.day, "legPress", 3, s.recordDate, s.startedAtEpochMs))
        assertEquals(true, RoutiveWatchCommands.handle(context, cmd("next")))
        assertEquals(2, store.active()!!.progress.stageIndex)
    }

}
