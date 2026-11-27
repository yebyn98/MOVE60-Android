package com.move60.workout.architecture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.move60.workout.ui.LocalMove60Toast
import com.move60.workout.ui.Move60ComposeTheme
import com.move60.workout.ui.Move60ToastHost
import com.move60.workout.ui.Move60ToastState
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * docs/v71 step 5 on a real emulator: the cardio correction sheet is its own window, so a failed save must show the app toast ABOVE
 * the sheet's scrim (not dimmed underneath), keep the sheet open and keep the typed value. The save is forced to fail here so the
 * visual can be captured: the real device screenshot is saved as `compose-cardio-sheet-toast.png`.
 */
@RunWith(AndroidJUnit4::class)
class ComposeCardioSheetToastTest {
    @get:Rule val rule = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** Sheets and popups are separate windows, so the Compose root capture cannot see them: take the real device screenshot. */
    private fun captureScreen(name: String) {
        rule.waitForIdle(); Thread.sleep(500)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun failedSaveShowsTheToastAboveTheSheetAndKeepsTheSheetAndTheTypedValue() {
        val record = CardioStageRecords.build(
            0, 720, listOf(SessionCardioInputEntity("s", 0, 0, 0, "INDOOR_CYCLE", "level", "6", null, 0L)), emptyList()
        )!!
        rule.setContent {
            Move60ComposeTheme {
                val toast = remember { Move60ToastState() }
                CompositionLocalProvider(LocalMove60Toast provides toast) {
                    Box(Modifier.fillMaxSize()) {
                        Move60ToastHost(toast, Modifier.align(Alignment.TopCenter))
                        CardioCorrectionSheet(
                            record, onDismiss = {},
                            onSave = { _, done -> done(CardioCorrectionResult.Failed(RuntimeException("forced"))) },
                            onFailure = { toast.show("저장하지 못했어요. 다시 시도해 주세요.") }
                        )
                    }
                }
            }
        }
        // P-29: a model-unverified console has no trustworthy +/- step. Its correction uses raw direct input.
        val input = rule.onNodeWithTag("cardio_stepper_level_input")
        input.assertTextContains("6", substring = true)
        rule.onNodeWithTag("cardio_save").assertIsNotEnabled()
        input.performTextReplacement("7")
        input.assertTextContains("7", substring = true)
        rule.onNodeWithTag("cardio_save").assertIsEnabled().performClick()

        // Forced persistence failure must preserve the actual input and leave the sheet open.
        // The window-local toast must remain above the sheet scrim rather than being dimmed.
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("move60_toast_window").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("move60_toast_window").assertIsDisplayed()
        rule.onNodeWithTag("move60_toast_window").assertTextEquals("저장하지 못했어요. 다시 시도해 주세요.")
        rule.onNodeWithTag("cardio_correction_sheet").assertIsDisplayed()
        rule.onNodeWithTag("cardio_stepper_level_input").assertTextContains("7", substring = true)
        rule.onNodeWithTag("cardio_save").assertIsEnabled()
        captureScreen("compose-cardio-sheet-toast.png")
    }
}
