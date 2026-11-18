package com.move60.workout.architecture

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Policy 6 on a real emulator: correct a set in the session detail, see the new value with the stored original beside it,
 * and see that invalid input cannot be saved (the user-facing history screen was removed on 2026-11-18). Seeds one session on a PAST date (day 10, so it never
 * collides with other tests' "today") and removes only its own `record-corr-*` rows afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ComposeRecordCorrectionTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val zone = ZoneId.of("Asia/Seoul")
    private val dbFile = "move60_workouts_room_v1.db"
    private val id = "record-corr-a"

    /** Dialogs are separate windows, so the Compose root capture cannot see them: take the real device screenshot. */
    private fun captureScreen(name: String) {
        rule.waitForIdle(); Thread.sleep(500)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.getExternalFilesDir(null), name).outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun correctionKeepsTheOriginalShowsTheHistoryAndRejectsInvalidInput() {
        val date = LocalDate.now(zone).minusMonths(1).withDayOfMonth(10)
        val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
        try {
            runBlocking {
                val start = date.atTime(20, 35).atZone(zone).toEpochSecond()
                val session = SessionEntity(id, null, zone.id, start * 1000, (start + 600) * 1000,
                    SessionCompletion.COMPLETED.name, 600, 2, false, null, 0L)
                val sets = listOf(
                    SessionSetEntity(id, "legPress", 1, SetState.COMPLETED.name, "40", "12", null),
                    SessionSetEntity(id, "legPress", 2, SetState.COMPLETED.name, "45", "12", null)
                )
                SessionCommitter(db).commit(SessionBundle(session, listOf(SessionActiveIntervalEntity(id, 0, start, start + 600)), sets))
            }
            db.close()

            ActivityScenario.launch(ComposeActivity::class.java).use {
                // Calendar -> day record -> session detail (same path as ComposeRecordTest).
                rule.onNodeWithTag("tab_calendar").performClick()
                rule.onNodeWithTag("calendar_list").performScrollToNode(hasTestTag("calendar_month_grid"))
                rule.onNodeWithTag("calendar_prev").performScrollTo().performClick()
                rule.waitUntil(10_000) {
                    rule.onAllNodesWithContentDescription("$date · 운동 기록 있음", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithContentDescription("$date · 운동 기록 있음", substring = true).performScrollTo().performClick()
                // The selected-day card is the LAST lazy item: keep scrolling until it exists.
                rule.waitUntil(10_000) {
                    runCatching { rule.onNodeWithTag("calendar_list").performScrollToNode(hasTestTag("record_day_summary")) }.isSuccess
                }
                rule.onNodeWithTag("record_day_summary").performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("record_session_$id").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("record_session_$id").performScrollTo().performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("record_edit_open").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("record_exercise_sets_legPress").assertTextEquals("40kg × 12회 · 45kg × 12회 (2세트)")

                // Open the edit dialog: nothing is changed yet, so saving is not possible.
                rule.onNodeWithTag("record_edit_open").performScrollTo().performClick()
                rule.onNodeWithTag("record_edit_dialog").assertIsDisplayed()
                rule.onNodeWithTag("record_edit_save").assertIsNotEnabled()

                // An invalid value cannot be saved.
                rule.onNodeWithTag("record_edit_reps_legPress#1").performTextReplacement("0")
                rule.onNodeWithTag("record_edit_save").assertIsNotEnabled()
                rule.onNodeWithTag("record_edit_reps_legPress#1").performTextReplacement("12")
                rule.onNodeWithTag("record_edit_save").assertIsNotEnabled()

                // A valid change can be saved. Dialogs are separate windows, so use the real screenshot.
                rule.onNodeWithTag("record_edit_kg_legPress#1").performTextReplacement("42.5")
                rule.onNodeWithTag("record_edit_save").assertIsEnabled()
                captureScreen("compose-record-correction-dialog.png")
                rule.onNodeWithTag("record_edit_save").performClick()

                // The record is reloaded only after the save succeeded: new value on top, stored original beside it.
                rule.waitUntil(15_000) {
                    runCatching {
                        rule.onNodeWithTag("record_exercise_sets_legPress").assertTextEquals("42.5kg × 12회 · 45kg × 12회 (2세트)")
                    }.isSuccess
                }
                rule.onNodeWithText("1세트 수정됨 · 원본 40kg × 12회").assertIsDisplayed()

                // There is no history screen for the user; the history rows stay stored (see SessionCorrectionServiceTest).
                rule.onNodeWithTag("record_history_open").assertDoesNotExist()
            }
        } finally {
            if (db.isOpen) db.close()
            val clean = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
            try {
                val raw = clean.openHelper.writableDatabase
                for (table in listOf("session_adjustments", "session_sets", "session_active_intervals", "session_day_slices", "session_segments"))
                    raw.execSQL("DELETE FROM `$table` WHERE sessionId LIKE 'record-corr-%'")
                raw.execSQL("DELETE FROM sessions WHERE sessionId LIKE 'record-corr-%'")
            } finally { clean.close() }
        }
    }
}
