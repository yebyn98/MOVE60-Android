package com.move60.workout.architecture

import android.content.Intent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.move60.workout.WorkoutService
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real-screen evidence for Figma 05-07/58/59: start -> progress -> pause -> confirmed early end -> result. */
@RunWith(AndroidJUnit4::class)
class ComposeWorkoutProgressTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun isolate() {
        context.stopService(Intent(context, WorkoutService::class.java))
        instrumentation.waitForIdleSync()
        for (name in listOf("timer", "move60_set_records_v1", "move60_energy_v1", "move60_session_energy_v1",
            "move60_workout_history_v1", "move60_result_seen_v1"))
            context.getSharedPreferences(name, 0).edit().clear().commit()
    }

    private fun tagStartsWith(prefix: String) = androidx.compose.ui.test.SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun captureScreen(name: String) {
        rule.waitForIdle(); Thread.sleep(500)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.getExternalFilesDir(null), name).outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun startPauseConfirmedEarlyEndAndResult() {
        ActivityScenario.launch(ComposeActivity::class.java).use {
            rule.onNodeWithTag("dock_play").performClick()
            rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("workout_progress")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("workout_status").assertIsDisplayed()
            rule.onNodeWithText("● 진행 중").assertIsDisplayed()
            rule.onNodeWithTag("workout_remaining").assertIsDisplayed()
            capture("compose-workout-progress.png")
            Thread.sleep(3_000)                       // let real active time accumulate
            rule.onNodeWithTag("workout_pause").performClick()
            rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("workout_end")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("● 일시정지").assertIsDisplayed()
            capture("compose-workout-paused.png")
            rule.onNodeWithTag("workout_end").performClick()
            rule.onNodeWithTag("workout_end_dialog").assertIsDisplayed()
            captureScreen("compose-workout-end-dialog.png")
            rule.onNodeWithTag("workout_end_confirm").performClick()
            // Under one minute and no sets is not stored (D-01 B): the result says so instead of showing an error.
            rule.waitUntil(20_000) { rule.onAllNodes(hasTestTag("result_screen")).fetchSemanticsNodes().isNotEmpty() }
            rule.waitUntil(20_000) {
                rule.onAllNodes(hasTestTag("result_save_state")).fetchSemanticsNodes().isNotEmpty() &&
                    rule.onAllNodes(androidx.compose.ui.test.hasText("저장 안 함")).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("기록할 운동이 없어요").assertIsDisplayed()
            capture("compose-workout-result-not-stored.png")
        }
    }

    /** Strength stage: enter kg/reps for a set, complete it, and see the per-exercise counter move. */
    @Test fun strengthStageAcceptsSetEntryAndCountsCompletedSets() {
        ActivityScenario.launch(ComposeActivity::class.java).use {
            rule.onNodeWithTag("dock_play").performClick()
            rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("workout_progress")).fetchSemanticsNodes().isNotEmpty() }
            repeat(2) {                                   // warm-up -> cardio -> strength
                rule.onNodeWithTag("workout_next").performClick()
                Thread.sleep(800)
            }
            rule.waitUntil(8_000) { rule.onAllNodes(tagStartsWith("workout_task_")).fetchSemanticsNodes().isNotEmpty() }
            capture("compose-workout-strength.png")
            val kg = rule.onAllNodes(tagStartsWith("set_kg_"))[0]
            kg.performTextInput("40")
            rule.onAllNodes(tagStartsWith("set_reps_"))[0].performTextInput("12")
            rule.onAllNodes(tagStartsWith("set_done_"))[0].performClick()
            rule.waitUntil(8_000) { rule.onAllNodesWithText("1 / ", substring = true).fetchSemanticsNodes().isNotEmpty() }
            capture("compose-workout-strength-set-done.png")
        }
    }

    /** The notification still opens the legacy detail activity; it must see the same running session. */
    @Test fun legacyDetailActivityStillSeesTheSessionStartedInCompose() {
        ActivityScenario.launch(ComposeActivity::class.java).use {
            rule.onNodeWithTag("dock_play").performClick()
            rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("workout_progress")).fetchSemanticsNodes().isNotEmpty() }
            Thread.sleep(1_500)
        }
        ActivityScenario.launch(com.move60.workout.MainActivity::class.java).use { legacy ->
            legacy.onActivity { activity ->
                org.junit.Assert.assertNotNull(activity.window.decorView.findViewWithTag<android.view.View>("phone_dock_progress"))
            }
        }
        val store = com.move60.workout.SessionStore(context, com.move60.workout.WorkoutData(context))
        store.reloadIfChanged()
        org.junit.Assert.assertTrue("session started in Compose must keep running for the legacy screen", store.running)
        org.junit.Assert.assertTrue(store.elapsed() >= 1)
    }
}
