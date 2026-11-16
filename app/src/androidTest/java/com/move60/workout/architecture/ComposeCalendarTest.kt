package com.move60.workout.architecture

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import com.move60.workout.SetRecordStore
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
        for (name in listOf("move60_workout_history_v1", "move60_workout_focus_v1", "move60_energy_v1", "preferences"))
            context.getSharedPreferences(name, 0).edit().clear().commit()
    }

    @Test fun calendarMarksTrainedDatesWithDotsAndNavigatesMonths() {
        WorkoutHistory(context).addActiveSeconds(today, 90)
        ActivityScenario.launch(ComposeActivity::class.java).use {
            rule.onNodeWithTag("tab_calendar").performClick()
            // The day cell is a clickable that merges its children, so the dot only exists in the
            // unmerged tree; the merged cell exposes the same fact to accessibility services.
            // Six-week month grids can put the selected date below the initial viewport
            // on short phone screens. Scroll the real list to that day before asserting
            // its accessibility label and recorded-day marker.
            // LazyColumn scroll-to-node targets list items, not a date nested inside the
            // six-week MonthGrid. Reveal the grid item first, then scroll its day cell.
            rule.onNodeWithTag("calendar_list").performScrollToNode(hasTestTag("calendar_month_grid"))
            rule.onNodeWithContentDescription("$today · 운동 기록 있음", substring = true).performScrollTo()
            rule.onNodeWithContentDescription("$today · 운동 기록 있음", substring = true).assertIsDisplayed()
            rule.onNodeWithTag("calendar-dot-$today", useUnmergedTree = true).assertIsDisplayed()
            capture("compose-calendar.png")
            // Scrolling to the recorded day can move the month toolbar offscreen.
            rule.onNodeWithTag("calendar_prev").performScrollTo().performClick()
            rule.onNodeWithTag("calendar_next").performScrollTo().performClick()
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
            rule.onNodeWithTag("tab_calendar").performClick()
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
            rule.onNodeWithTag("tab_calendar").performClick()
            rule.onNodeWithContentDescription("$today · 운동 기록 없음").assertIsDisplayed()
            rule.onNodeWithTag("calendar-dot-$today", useUnmergedTree = true).assertDoesNotExist()
            rule.onNodeWithText("최근 운동 기록").assertDoesNotExist()
            assertEquals(0, WorkoutHistory(context).trainedDays(java.time.YearMonth.from(today)))
            capture("compose-calendar-zero-minutes.png")
        }
    }

    /**
     * Policy 5 (A): v58-shaped completed sets are listed with their saved values under the day record, unlinked and
     * without a time. Seeded under weekday key 9 (the app only uses 1..5) so no real draft or record is overwritten,
     * and only those keys are removed afterwards.
     */
    @Test fun v58CompletedSetsAreListedUnderTheDayRecordAsSaved() {
        val names = WorkoutData(context)
        val chest = requireNotNull(names.exerciseName("chestPress")) { "chestPress must be a curated exercise" }
        val leg = requireNotNull(names.exerciseName("legPress")) { "legPress must be a curated exercise" }
        val token = 4_102_444_800_000L
        val prefs = context.getSharedPreferences("move60_set_records_v1", 0)
        WorkoutHistory(context).addActiveSeconds(today, 90 * 60)
        try {
            val store = SetRecordStore(context)
            // Distinctive values: other emulator tests also leave completed sets on today's date, which the screen
            // (correctly) lists too, so this test only looks for the rows it wrote itself.
            store.write(9, "chestPress", 1, SetRecordStore.Record("41.5", "11", "5", true), today, token)
            store.write(9, "chestPress", 2, SetRecordStore.Record("43.5", "9", "5", true), today, token)
            store.write(9, "legPress", 1, SetRecordStore.Record("81", "13", "5", true), today, token)
            ActivityScenario.launch(ComposeActivity::class.java).use {
                rule.onNodeWithTag("tab_calendar").performClick()
                rule.onNodeWithTag("calendar_list").performScrollToNode(hasTestTag("calendar_day_detail"))
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("calendar_set_records").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("calendar_set_records").performScrollTo()
                rule.onNodeWithTag("calendar_set_records").assertIsDisplayed()
                rule.onAllNodesWithTag("calendar_set_group_legacy")[0].assertExists()
                for (row in listOf(
                    "$chest · 1세트 · 41.5kg × 11회", "$chest · 2세트 · 43.5kg × 9회", "$leg · 1세트 · 81kg × 13회"
                )) {
                    rule.onNodeWithText(row).performScrollTo()
                    rule.onNodeWithText(row).assertIsDisplayed()
                }
                rule.onAllNodesWithText("저장된 운동과 연결할 수 없어 시간 없이 보여줘요.")[0].assertExists()
                capture("compose-calendar-set-records.png")
            }
        } finally {
            val edit = prefs.edit()
            prefs.all.keys.filter { it.startsWith("set:$today:9:") }.forEach { edit.remove(it) }
            edit.commit()
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
