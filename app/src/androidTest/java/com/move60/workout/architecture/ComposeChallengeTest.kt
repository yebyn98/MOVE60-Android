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

    @Test fun createRenameAndAbandonWithConfirmation() {
        rule.onNodeWithTag("compose_challenge_icon").performClick()
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
        rule.onNodeWithTag("challenge_new_days").performTextInput("7")
        rule.onNodeWithTag("challenge_new_target").performTextInput("8")        // target > period: rejected in the form
        rule.onNodeWithTag("challenge_start").assertIsNotEnabled()
        capture("compose-challenge-create-invalid.png")
        rule.onNodeWithTag("challenge_new_target").performTextReplacement("4")
        rule.onNodeWithTag("challenge_start").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("challenge_active")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("challenge_progress").assertIsDisplayed()
        rule.onNodeWithText("0 / 4일 달성 (누적)").assertIsDisplayed()
        capture("compose-challenge-active.png")
        rule.onNodeWithTag("challenge_abandon").performClick()
        rule.onNodeWithTag("challenge_abandon_dialog").assertIsDisplayed()
        capture("compose-challenge-abandon-dialog.png")
        rule.onNodeWithTag("challenge_abandon_confirm").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("challenge_ended_ABANDONED")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("challenge_list").performScrollToNode(hasTestTag("challenge_ended_ABANDONED"))
        rule.onNodeWithTag("challenge_ended_ABANDONED").assertIsDisplayed()
        capture("compose-challenge-ended.png")
    }
}
