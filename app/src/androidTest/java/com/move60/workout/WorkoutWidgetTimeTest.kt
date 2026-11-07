package com.move60.workout

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Regression coverage: a paused workout has deadlineAt=0 but keeps remaining seconds. */
@RunWith(AndroidJUnit4::class)
class WorkoutWidgetTimeTest {
    @Test fun pausedWorkoutShowsSavedRemainingTimeWithoutContinuingCountdown() {
        val paused = JSONObject()
            .put("sessionStartedAt", 50_000L)
            .put("running", false)
            .put("finished", false)
            .put("remaining", 213)
            .put("deadlineAt", 0L)
        assertEquals(213L, WorkoutWidget.remainingSecondsForWidget(paused, 100_000L))
        assertEquals(213L, WorkoutWidget.remainingSecondsForWidget(paused, 600_000L))
    }

    @Test fun activeWorkoutCountsDownUntilTheDeadline() {
        val running = JSONObject()
            .put("sessionStartedAt", 50_000L)
            .put("running", true)
            .put("finished", false)
            .put("remaining", 213)
            .put("deadlineAt", 130_000L)
        assertEquals(30L, WorkoutWidget.remainingSecondsForWidget(running, 100_000L))
        assertEquals(0L, WorkoutWidget.remainingSecondsForWidget(running, 135_000L))
    }

    @Test fun completedOrNotStartedWorkoutDoesNotShowAStaleTimer() {
        val finished = JSONObject()
            .put("sessionStartedAt", 50_000L)
            .put("running", false)
            .put("finished", true)
            .put("remaining", 213)
        assertEquals(0L, WorkoutWidget.remainingSecondsForWidget(finished, 100_000L))
        assertEquals(0L, WorkoutWidget.remainingSecondsForWidget(JSONObject(), 100_000L))
    }
}
