package com.move60.workout.architecture

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real-screen evidence for Figma 41 (routine compose): needs >=1 exercise, optional times, saved routine persists. */
@RunWith(AndroidJUnit4::class)
class ComposeRoutineComposeTest {
    @get:Rule val rule = createAndroidComposeRule<ComposeActivity>()

    private fun out(name: String) = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), name)
    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        out(name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun captureScreen(name: String) {
        rule.waitForIdle(); Thread.sleep(500)
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        out(name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun tagStartsWith(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }
    private fun waitTag(tag: String) = rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun composeRoutineRequiresOneExerciseAndSavesWithUnknownTimes() {
        waitTag("dashboard_compose_routine")
        rule.onNodeWithTag("dashboard_compose_routine").performClick()
        waitTag("routine_compose")
        // Empty state: cannot confirm, and the target is "미정" (no fixed 60 minutes).
        rule.onNodeWithTag("routine_confirm").assertIsNotEnabled()
        rule.onNodeWithTag("routine_need_one").assertIsDisplayed()
        rule.onNodeWithTag("routine_target_label").assertIsDisplayed()
        capture("compose-routine-empty.png")

        rule.onNodeWithTag("routine_stage_STRENGTH").performScrollTo().performClick()
        waitTag("routine_picker")
        rule.onAllNodes(tagStartsWith("routine_option_")).onFirst().performClick()
        captureScreen("compose-routine-picker.png")
        rule.onNodeWithTag("routine_picker_ok").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("routine_picker")).fetchSemanticsNodes().isEmpty() }

        // One exercise, all times unknown: confirmable (E-02 B).
        rule.onNodeWithTag("routine_confirm").assertIsEnabled()
        // A known time that exceeds the target is rejected without saving.
        rule.onNodeWithTag("routine_target").performScrollTo().performTextInput("10")
        rule.onNodeWithTag("routine_minutes_CARDIO").performScrollTo().performTextInput("30")
        rule.onNodeWithTag("routine_confirm").performClick()
        waitTag("routine_error")
        rule.onNodeWithTag("routine_error").performScrollTo().assertIsDisplayed()
        capture("compose-routine-error.png")
        // Fix the target: appending "0" makes it 100 minutes, above the known 30 (other stages unknown), so it saves.
        rule.onNodeWithTag("routine_target").performScrollTo().performTextInput("0")
        rule.onNodeWithTag("routine_confirm").performClick()
        waitTag("routine_saved")
        rule.onNodeWithTag("routine_saved").assertIsDisplayed()
        capture("compose-routine-saved.png")
    }
}
