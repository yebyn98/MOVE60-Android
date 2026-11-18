package com.move60.workout.architecture

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real-screen evidence for the challenge tab: create (start fixed to today) -> active card -> abandon confirm dialog. */
@RunWith(AndroidJUnit4::class)
class ComposeChallengeTest {
    @get:Rule val rule = createAndroidComposeRule<ComposeActivity>()

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** A Dialog is a separate window, so the Compose root capture fails; take the real device screenshot instead. */
    private fun captureScreen(name: String) {
        rule.waitForIdle(); Thread.sleep(500)
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), name).outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun createRenameAndAbandonWithConfirmation() {
        rule.onNodeWithTag("tab_challenge").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasTestTag("challenge_create")).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(hasTestTag("challenge_active")).fetchSemanticsNodes().isNotEmpty()
        }
        // A previous run on the same emulator may have left an active challenge: abandon it through the real dialog.
        if (rule.onAllNodes(hasTestTag("challenge_active")).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithTag("challenge_abandon").performClick()
            rule.onNodeWithTag("challenge_abandon_confirm").performClick()
            rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("challenge_create")).fetchSemanticsNodes().isNotEmpty() }
        }
        rule.onNodeWithTag("challenge_start").assertIsNotEnabled()
        rule.onNodeWithTag("challenge_new_name").performTextInput("11월 꾸준히")
        // Figma FINAL/30: 4/6/8-week picker calculates an inclusive end date.
        rule.onNodeWithTag("challenge_period_select").performClick()
        rule.onNodeWithTag("challenge_period_4w").performClick()
        val start = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
        rule.onNodeWithTag("challenge_start_date_select").assertIsDisplayed().performClick()
        rule.onNodeWithTag("challenge_date_picker").assertIsDisplayed()
        rule.onNodeWithTag("challenge_start_date_cancel").performClick()
        rule.onNodeWithText(start.format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"))).assertIsDisplayed()
        rule.onNodeWithText("종료 예정일 ${start.plusDays(27)}").assertIsDisplayed()
        rule.onNodeWithTag("challenge_goal_select").performClick()
        rule.onNodeWithTag("challenge_goal_12").performClick()
        rule.onNodeWithTag("challenge_start").assertIsEnabled()
        // Existing non-preset input must remain usable: 7-day goal rejects 8 credited days.
        rule.onNodeWithTag("challenge_period_select").performClick()
        rule.onNodeWithTag("challenge_period_custom").performClick()
        rule.onNodeWithTag("challenge_new_days").performTextInput("7")
        rule.onNodeWithText("종료 예정일 ${start.plusDays(6)}").assertIsDisplayed()
        rule.onNodeWithTag("challenge_goal_select").performClick()
        rule.onNodeWithTag("challenge_goal_custom").performClick()
        rule.onNodeWithTag("challenge_new_target").performTextInput("8")        // target > period: rejected
        rule.onNodeWithTag("challenge_start").assertIsNotEnabled()
        capture("compose-challenge-create-invalid.png")
        rule.onNodeWithTag("challenge_new_target").performTextReplacement("4")
        rule.onNodeWithTag("challenge_start").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("challenge_active")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("challenge_progress").assertIsDisplayed()
        rule.onNodeWithText("0 / 4일 달성 (누적)").assertIsDisplayed()
        capture("compose-challenge-active.png")
        // P-02 C: only the name may be edited. Invalid save keeps the current draft field visible.
        rule.onNodeWithTag("challenge_rename").performClick()
        rule.onNodeWithTag("challenge_name_field").performTextReplacement("  ")
        rule.onNodeWithTag("challenge_name_save").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasTestTag("challenge_name_field")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("challenge_name_field").assertIsDisplayed()
        // After a successful write, refresh and only then close the editor.
        rule.onNodeWithTag("challenge_name_field").performTextReplacement("11월 실천 도전")
        rule.onNodeWithTag("challenge_name_save").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasTestTag("challenge_rename")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("11월 실천 도전").assertIsDisplayed()
        // Figma 03: the active challenge is summarised on the today home and opens the challenge tab.
        rule.onNodeWithTag("tab_today").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("dashboard_challenge_card")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("dashboard_challenge_card").assertIsDisplayed()
        capture("compose-home-challenge-card.png")
        rule.onNodeWithTag("dashboard_challenge_card").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("challenge_active")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("challenge_abandon").performClick()
        rule.onNodeWithTag("challenge_abandon_dialog").assertIsDisplayed()
        captureScreen("compose-challenge-abandon-dialog.png")
        rule.onNodeWithTag("challenge_abandon_confirm").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("challenge_ended_ABANDONED")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("challenge_list").performScrollToNode(hasTestTag("challenge_ended_ABANDONED"))
        rule.onNodeWithTag("challenge_ended_ABANDONED").assertIsDisplayed()
        capture("compose-challenge-ended.png")
    }
}
