package com.move60.workout.architecture

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.move60.workout.WorkoutHistory
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PO decision 1 on a real emulator: a user who already has records sees the "kcal is no longer shown" banner ONCE, on the
 * first visit to the record screen, and it never blocks. The runner marks the notice as seen for all other tests, so this
 * test resets that flag; it adds one old v58 day and removes it again, and restores the flag afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ComposeKcalNoticeTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val notices get() = context.getSharedPreferences("move60_notices_v1", Context.MODE_PRIVATE)

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun anExistingUserSeesTheNoticeOnceOnTheRecordScreen() {
        // An old v58 day that the test owns (a date no other test uses); the existing-user check only needs "some history".
        val day = LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(1).withDayOfMonth(3)
        val historyPrefs = context.getSharedPreferences("move60_workout_history_v1", Context.MODE_PRIVATE)
        val hadDay = historyPrefs.contains(day.toString())
        if (!hadDay) WorkoutHistory(context).addActiveSeconds(day, 600)
        notices.edit().putBoolean("kcal_hidden_seen", false).commit()
        try {
            ActivityScenario.launch(ComposeActivity::class.java).use {
                rule.onNodeWithTag("tab_calendar").performClick()
                rule.waitUntil(15_000) { rule.onAllNodesWithTag("kcal_notice").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("kcal_notice").assertIsDisplayed()
                // Shown once means: remembered as seen as soon as it is displayed.
                assertEquals(true, notices.getBoolean("kcal_hidden_seen", false))
                capture("compose-kcal-notice.png")
                // It does not block: the calendar stays usable behind it, and it can be dismissed.
                rule.onNodeWithTag("calendar_list").assertIsDisplayed()
                rule.onNodeWithTag("kcal_notice_dismiss").performClick()
                rule.waitUntil(5_000) { rule.onAllNodesWithTag("kcal_notice").fetchSemanticsNodes().isEmpty() }
            }
            ActivityScenario.launch(ComposeActivity::class.java).use {
                rule.onNodeWithTag("tab_calendar").performClick()
                rule.waitUntil(15_000) { rule.onAllNodesWithTag("calendar_list").fetchSemanticsNodes().isNotEmpty() }
                rule.waitForIdle(); Thread.sleep(1_500)
                assertEquals("never shown a second time", 0, rule.onAllNodesWithTag("kcal_notice").fetchSemanticsNodes().size)
            }
        } finally {
            if (!hadDay) historyPrefs.edit().remove(day.toString()).commit()
            notices.edit().putBoolean("kcal_hidden_seen", true).commit()
        }
    }
}
