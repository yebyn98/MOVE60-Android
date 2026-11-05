package com.move60.workout.architecture

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onRoot
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
            val dot = rule.onNodeWithTag("calendar-dot-$today")
            try { dot.assertIsDisplayed() } catch (e: AssertionError) {
                // Surface the geometry so a CI failure shows why a rendered dot is not visible.
                val node = dot.fetchSemanticsNode()
                throw AssertionError("dot bounds=${node.boundsInRoot} root=${rule.onRoot().fetchSemanticsNode().size} " +
                    "dots=${rule.onAllNodes(hasTestTag("calendar-dot-$today")).fetchSemanticsNodes().size}", e)
            }
            rule.onNodeWithTag("calendar_prev").performClick()
            rule.onNodeWithTag("calendar_next").performClick()
            // Summary card is the second LazyColumn item and may start below the fold on small screens.
            rule.onNodeWithTag("calendar_list").performScrollToNode(hasTestTag("calendar_summary"))
            rule.onNodeWithTag("calendar_summary").assertIsDisplayed()
            rule.onNodeWithText("● 운동한 날").assertIsDisplayed()
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
