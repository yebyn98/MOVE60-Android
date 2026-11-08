package com.move60.workout

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.TimeZone

/** v54: warm-up items are timed, the dock names the current item, and strength never shows a countdown. */
@RunWith(AndroidJUnit4::class)
class DockItemAndStrengthTimeTest {
    private val seoul = TimeZone.getTimeZone("Asia/Seoul")

    @Test fun everyDayWarmUpItemsAddUpToTheStage() {
        val data = WorkoutData(InstrumentationRegistry.getInstrumentation().targetContext)
        for (day in 1..5) {
            val warm = data.routine(day).stages[0]
            assertTrue("day $day warm-up items must be timed", warm.tasks.isNotEmpty() && warm.tasks.all { it.seconds > 0 })
            assertEquals("day $day warm-up items must fill the stage", warm.minutes * 60, warm.tasks.sumOf { it.seconds })
        }
    }

    @Test fun warmUpWidgetCountsDownTheCurrentItem() {
        val state = JSONObject().put("running", false).put("finished", false).put("sessionStartedAt", 1L)
            .put("stage", 0).put("remaining", 200L).put("deadlineAt", 0L)
            .put("stageSeconds", JSONArray(listOf(300, 900, 1200, 900, 300)))
            .put("phases0", JSONArray()
                .put(JSONObject().put("name", "전신 관절 가동성").put("seconds", 180).put("rpe", "").put("kind", "준비운동"))
                .put(JSONObject().put("name", "천천히 제자리 걷기").put("seconds", 120).put("rpe", "").put("kind", "준비운동")))
        val m = WorkoutWidgetModel.build(state, null, 0L, seoul)
        assertEquals(80L, m.remainingSeconds) // 100s elapsed -> 80s left in the first 180s item
        assertTrue(m.countDown)
        assertEquals("남은 시간", m.timerLabel)
    }

    @Test fun strengthWidgetCountsOverallTimeUpAndNeverShowsRemaining() {
        val now = 1_000_000L
        val state = JSONObject().put("running", true).put("finished", false).put("sessionStartedAt", 1L)
            .put("stage", 2).put("remaining", 700L).put("deadlineAt", now + 700_000L)
            .put("activeSeconds", 1300).put("activeCheckpointAt", now - 20_000L)
            .put("stageSeconds", JSONArray(listOf(300, 900, 1200, 900, 300)))
        val m = WorkoutWidgetModel.build(state, null, now, seoul)
        assertFalse(m.countDown)
        assertEquals("전체시간", m.timerLabel)
        assertEquals(1320L, m.remainingSeconds) // 1300s saved + 20s since the last checkpoint
    }
}
