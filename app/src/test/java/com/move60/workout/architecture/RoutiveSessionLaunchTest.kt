package com.move60.workout.architecture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.SessionStore
import com.move60.workout.SetRecordStore
import com.move60.workout.WorkoutData
import com.move60.workout.domain.FourStagePlans
import com.move60.workout.domain.StageKind
import com.move60.workout.domain.StageReadiness
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
class RoutiveSessionLaunchTest {
    private lateinit var context: Context
    private lateinit var db: WorkoutDatabase
    private val date = LocalDate.parse("2026-11-16")

    @Before fun init() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }

    private suspend fun save(id: String) = RoutinePlanService(db).save(id, "시작 테스트", 3_600L, mapOf(
        StageKind.WARM_UP to listOf(RoutineExerciseInput("walk", "걷기", 300L)),
        StageKind.STRENGTH to listOf(RoutineExerciseInput("legPress", "레그 프레스", null)),
        StageKind.CARDIO to listOf(RoutineExerciseInput("bike", "실내 자전거", 900L))))

    private fun launcher() = RoutiveSessionLauncher(context, RoutinePlanService(db))

    @Test fun startsFromASavedRoutineAndRefusesASecondStart() = runBlocking {
        assertTrue(save("r1") is RoutinePlanResult.Saved); assertTrue(save("r2") is RoutinePlanResult.Saved)
        assertNull(launcher().activeSession())
        assertEquals(LaunchResult.Started("r1"), launcher().start("r1"))
        val active = launcher().activeSession()!!
        assertEquals("r1", active.routineId); assertEquals("시작 테스트", active.routineName)
        assertEquals(LaunchResult.AlreadyActive("시작 테스트"), launcher().start("r2"))
        assertEquals("the running session is untouched", "r1", RoutiveSessionStore(context).active()!!.routineId)
    }

    @Test fun unknownRoutineIsNotFoundAndStartsNothing() = runBlocking {
        assertEquals(LaunchResult.NotFound, launcher().start("missing"))
        assertNull(RoutiveSessionStore(context).active())
    }

    @Test fun aLegacySessionInProgressBlocksTheStartAndIsLeftAlone() = runBlocking {
        save("r1")
        val legacy = SessionStore(context, WorkoutData(context))
        legacy.start()
        assertTrue(legacy.hasActiveSession)
        assertEquals(LaunchResult.LegacyActive, launcher().start("r1"))
        assertNull(RoutiveSessionStore(context).active())
        assertTrue("legacy session still running", SessionStore(context, WorkoutData(context)).hasActiveSession)
    }

    @Test fun anEndedSessionDoesNotBlockTheNextStart() = runBlocking {
        save("r1")
        assertEquals(LaunchResult.Started("r1"), launcher().start("r1"))
        RoutiveSessionStore(context).endEarly()
        assertNull("ended session is not active", launcher().activeSession())
        assertEquals(LaunchResult.Started("r1"), launcher().start("r1"))
    }

    // ---- UI model ----

    private fun session(vararg plan: Long?, elapsedInStage: Long = 0, stage: Int = 0): RoutiveSession {
        var p = FourStagePlans.from(listOf(StageKind.WARM_UP, StageKind.STRENGTH, StageKind.CARDIO, StageKind.COOL_DOWN)
            .mapIndexed { i, k -> StageTimePlan(k, plan[i]) }.toList())!!
        repeat(stage) { p = p.advance() }
        p = p.tick(elapsedInStage)
        return RoutiveSession("r", "루틴", date, 1, 1_000L, true, 1_000L, p, emptyList())
    }

    @Test fun unknownPlannedTimeIsMissingNeverZero() {
        val ui = buildRoutiveUi(session(300L, null, 900L, null, stage = 1, elapsedInStage = 30), emptyList(), emptyList(), null)
        assertEquals("근력운동", ui.stageTitle); assertEquals(1, ui.stageIndex); assertEquals(4, ui.stageCount)
        assertEquals("계획 시간 미정", ui.plannedLabel); assertNull(ui.remainingSeconds); assertEquals(30L, ui.elapsedSeconds)
        assertEquals("유산소", ui.nextStageTitle)
    }

    @Test fun planReachedIsOnlyAHintAndOvertimeIsReported() {
        val reached = buildRoutiveUi(session(300L, null, 900L, null, stage = 0, elapsedInStage = 300), emptyList(), emptyList(), null)
        assertEquals(StageReadiness.PLAN_REACHED, reached.readiness); assertFalse(reached.over)
        val over = buildRoutiveUi(session(300L, null, 900L, null, stage = 0, elapsedInStage = 330), emptyList(), emptyList(), null)
        assertEquals(30L, over.overtimeSeconds); assertEquals("계획 5분", over.plannedLabel)
    }

    @Test fun completedSetsAreGroupedPerExerciseAndNextSetNumberFollows() {
        val ex = listOf(RoutineExerciseInput("legPress", "레그 프레스", null), RoutineExerciseInput("row", "로우", null))
        val sets = listOf(SetRecordStore.CompletedSet("legPress", 2, "50", "8"), SetRecordStore.CompletedSet("legPress", 1, "40", "10"))
        val ui = buildRoutiveUi(session(300L, null, 900L, null, stage = 1), ex, sets, null)
        val leg = ui.exercises.first { it.id == "legPress" }
        assertEquals(listOf(1, 2), leg.done.map { it.number }); assertEquals(3, leg.nextSetNumber)
        assertEquals(1, ui.exercises.first { it.id == "row" }.nextSetNumber)
        assertEquals("sets are not all done (row has none)", StageReadiness.IN_PROGRESS, ui.readiness)
        val all = buildRoutiveUi(session(300L, null, 900L, null, stage = 1), ex.take(1), sets, null)
        assertEquals(StageReadiness.SETS_DONE, all.readiness)
    }
}
