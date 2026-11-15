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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
    /** Items of a LazyColumn are not composed until scrolled to, so scroll the list (not the node) to the tag first. */
    private fun scrolled(tag: String) = rule.onNodeWithTag(tag).also { _ ->
        rule.onNodeWithTag("routine_compose").performScrollToNode(hasTestTag(tag))
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

        scrolled("routine_stage_STRENGTH").performClick()
        waitTag("routine_picker")
        rule.onAllNodes(tagStartsWith("routine_option_")).onFirst().performClick()
        captureScreen("compose-routine-picker.png")
        rule.onNodeWithTag("routine_picker_ok").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("routine_picker")).fetchSemanticsNodes().isEmpty() }

        // One exercise, all times unknown: confirmable (E-02 B).
        rule.onNodeWithTag("routine_confirm").assertIsEnabled()
        // A known time that exceeds the target is rejected without saving.
        scrolled("routine_target").performTextInput("10")
        scrolled(rule.onAllNodes(tagStartsWith("routine_minutes_STRENGTH_")).fetchSemanticsNodes().first().config
            .getOrNull(SemanticsProperties.TestTag)!!).performTextInput("30")      // per-exercise time (stage time is derived)
        rule.onNodeWithTag("routine_confirm").performClick()
        waitTag("routine_error")
        rule.onNodeWithTag("routine_error").assertIsDisplayed()
        capture("compose-routine-error.png")
        // Fix the target: set it to 100 minutes, above the known 30 (other stages unknown), so it saves.
        scrolled("routine_target").performTextReplacement("100")
        rule.onNodeWithTag("routine_confirm").performClick()
        // Wait for either outcome and always keep the real screen, so a failure shows what the user would see.
        rule.waitUntil(8_000) {
            rule.onAllNodes(hasTestTag("routine_saved")).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(hasTestTag("routine_error")).fetchSemanticsNodes().isNotEmpty()
        }
        capture("compose-routine-after-confirm.png")
        val errors = rule.onAllNodes(hasTestTag("routine_error")).fetchSemanticsNodes()
        check(errors.isEmpty()) { "save did not succeed; screen shows: " + errors.joinToString { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty() } }
        rule.onNodeWithTag("routine_saved").assertIsDisplayed()
        capture("compose-routine-saved.png")
    }
}
