package com.move60.workout.architecture

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.move60.workout.SessionStore
import com.move60.workout.WorkoutData
import com.move60.workout.WorkoutHistory
import com.move60.workout.WorkoutService
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeCalendarTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val today get() = LocalDate.now(ZoneId.of("Asia/Seoul"))

    /** Saves a real screen capture next to the other smoke evidence (pulled into the CI artifact). */
    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Before fun isolate() {
        context.stopService(Intent(context, WorkoutService::class.java))
        instrumentation.waitForIdleSync()
        for (name in listOf("move60_workout_history_v1", "move60_energy_v1", "preferences"))
            context.getSharedPreferences(name, 0).edit().clear().commit()
    }

    @Test fun calendarMarksTrainedDatesWithDotsAndNavigatesMonths() {
        WorkoutHistory(context).addActiveSeconds(today, 90)
        ActivityScenario.launch(ComposeActivity::class.java).use {
            rule.onNodeWithContentDescription("운동 달력").performClick()
            // The day cell is a clickable that merges its children, so the dot only exists in the
            // unmerged tree; the merged cell exposes the same fact to accessibility services.
            rule.onNodeWithContentDescription("$today · 운동 기록 있음").assertIsDisplayed()
            rule.onNodeWithTag("calendar-dot-$today", useUnmergedTree = true).assertIsDisplayed()
            capture("compose-calendar.png")
            rule.onNodeWithTag("calendar_prev").performClick()
            rule.onNodeWithTag("calendar_next").performClick()
            // Summary card is the second LazyColumn item and may start below the fold on small screens.
            rule.onNodeWithTag("calendar_list").performScrollToNode(hasTestTag("calendar_summary"))
            rule.onNodeWithTag("calendar_summary").assertIsDisplayed()
            rule.onNodeWithTag("calendar_legend").assertIsDisplayed()
            rule.onNodeWithText("상체").assertIsDisplayed()
            rule.onNodeWithText("하체").assertIsDisplayed()
        }
    }

    @Test fun calendarShowsWeekStreakAndMonthGoalProgress() {
        WorkoutHistory(context).addActiveSeconds(today, 90 * 60)
        ActivityScenario.launch(ComposeActivity::class.java).use {
            rule.onNodeWithContentDescription("운동 달력").performClick()
            rule.onNodeWithTag("calendar_list").performScrollToNode(hasTestTag("calendar_habit"))
            rule.onNodeWithTag("calendar_habit").assertIsDisplayed()
            rule.onNodeWithTag("calendar_week_progress", useUnmergedTree = true).assertExists()
            rule.onNodeWithTag("calendar_month_progress", useUnmergedTree = true).assertExists()
            capture("compose-calendar-habit.png")
        }
    }

    @Test fun zeroMinuteDaysAreNotTrainedAndAreHiddenFromRecentRecords() {
        // 30 s is 0 whole minutes: not a workout day, no dot, no entry in the recent list.
        WorkoutHistory(context).addActiveSeconds(today, 30)
        ActivityScenario.launch(ComposeActivity::class.java).use {
            rule.onNodeWithContentDescription("운동 달력").performClick()
            rule.onNodeWithContentDescription("$today · 운동 기록 없음").assertIsDisplayed()
            rule.onNodeWithTag("calendar-dot-$today", useUnmergedTree = true).assertDoesNotExist()
            rule.onNodeWithText("최근 운동 기록").assertDoesNotExist()
            assertEquals(0, WorkoutHistory(context).trainedDays(java.time.YearMonth.from(today)))
            capture("compose-calendar-zero-minutes.png")
        }
    }

    @Test fun dashboardBodySelectionIsStoredAndDrivesTheDetailScreen() {
        for (name in listOf("move60_native_sessions_v8", "timer"))
            context.getSharedPreferences(name, 0).edit().clear().commit()
        ActivityScenario.launch(ComposeActivity::class.java).use {
            rule.onNodeWithText("하체").performClick()
            rule.waitForIdle()
            assertEquals("lower", SessionStore(context, WorkoutData(context)).bodyFocus)
            rule.onNodeWithText("상체").performClick()
            rule.waitForIdle()
            assertEquals("upper", SessionStore(context, WorkoutData(context)).bodyFocus)
        }
    }
}
