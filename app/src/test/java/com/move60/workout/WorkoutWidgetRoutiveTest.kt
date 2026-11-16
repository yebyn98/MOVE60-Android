package com.move60.workout

import com.move60.workout.architecture.RoutiveSession
import com.move60.workout.architecture.RestState
import com.move60.workout.domain.FourStageProgress
import com.move60.workout.domain.StageKind
import com.move60.workout.domain.StageTimePlan
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** The widget reads the same ROUTIVE_4 session as screen, notification and watch. */
class WorkoutWidgetRoutiveTest {
    private val plan = listOf(StageTimePlan(StageKind.WARM_UP, 300), StageTimePlan(StageKind.STRENGTH, null),
        StageTimePlan(StageKind.CARDIO, 900), StageTimePlan(StageKind.COOL_DOWN, null))
    private fun session(stage: Int, elapsed: List<Long>, running: Boolean = true, rest: RestState? = null, finished: Boolean = false, early: Boolean = false) =
        RoutiveSession("r", "루틴", LocalDate.of(2026, 11, 16), 1, 1L, running, 1L,
            FourStageProgress(plan, stage, elapsed, finished = finished, endedEarly = early), emptyList(), rest = rest)
    private fun model(s: RoutiveSession) = WorkoutWidgetModel.fromRoutive(s, null, 0L)

    @Test fun plannedStageCountsDownAndOverrunCountsUp() {
        val m = model(session(0, listOf(100, 0, 0, 0)))
        assertTrue(m.countDown); assertEquals(200L, m.remainingSeconds); assertEquals(WidgetStatus.RUNNING, m.status)
        val over = model(session(0, listOf(340, 0, 0, 0)))
        assertTrue(over.overtime); assertEquals(40L, over.remainingSeconds)
    }

    @Test fun unplannedStageShowsElapsedNeverAMadeUpCountdown() {
        val m = model(session(3, listOf(0, 0, 0, 75)))
        assertFalse(m.countDown); assertEquals(75L, m.remainingSeconds); assertEquals("경과 시간", m.timerLabel)
    }

    @Test fun restShowsItsOwnCountdownAndStrengthShowsTotalTime() {
        val strength = model(session(1, listOf(300, 120, 0, 0)))
        assertFalse(strength.countDown); assertEquals(420L, strength.remainingSeconds); assertEquals("전체시간", strength.timerLabel)
        val rest = model(session(1, listOf(300, 120, 0, 0), rest = RestState("e", 60, 20)))
        assertTrue(rest.countDown); assertEquals(40L, rest.remainingSeconds); assertEquals("휴식", rest.timerLabel)
    }

    @Test fun pausedAndEndedStatesAreDistinct() {
        assertEquals(WidgetStatus.PAUSED, model(session(0, listOf(10, 0, 0, 0), running = false)).status)
        assertEquals("운동 종료", model(session(0, listOf(10, 0, 0, 0), running = false, early = true)).title)
        assertEquals("오늘 운동 완료", model(session(3, listOf(300, 600, 900, 60), running = false, finished = true)).title)
    }

    @Test fun heartShownOnlyWhenValid() {
        val s = session(0, listOf(0, 0, 0, 0))
        assertNull(WorkoutWidgetModel.fromRoutive(s, WidgetHeart(250, 1L), 0L).heartBpm)
        assertEquals(120, WorkoutWidgetModel.fromRoutive(s, WidgetHeart(120, 1L), 0L).heartBpm)
    }
}
