package com.move60.workout.architecture

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.move60.workout.WorkoutData
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Figma 11 -> 26 -> 27 on a real emulator: stored sessions seeded on a PAST date (so no other test's "today" is
 * affected) are opened from the calendar. Only the rows this test inserts (`record-test-*`) are removed afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ComposeRecordTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val zone = ZoneId.of("Asia/Seoul")
    private val dbFile = "move60_workouts_room_v1.db"

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private suspend fun seed(db: WorkoutDatabase, id: String, date: LocalDate, hour: Int, minute: Int, seconds: Long, sets: List<SessionSetEntity>) {
        val start = date.atTime(hour, minute).atZone(zone).toEpochSecond()
        val session = SessionEntity(id, null, zone.id, start * 1000, (start + seconds) * 1000,
            SessionCompletion.COMPLETED.name, seconds, sets.size, false, null, 0L)
        SessionCommitter(db).commit(SessionBundle(session, listOf(SessionActiveIntervalEntity(id, 0, start, start + seconds)), sets))
    }

    @Test fun storedSessionsOpenFromTheCalendarIntoDayAndSessionDetail() {
        val date = LocalDate.now(zone).minusMonths(1).withDayOfMonth(9)
        val a = "record-test-a"
        val b = "record-test-b"
        val names = WorkoutData(context)
        val legPress = requireNotNull(names.exerciseName("legPress")) { "legPress must be a curated exercise" }
        val legCurl = requireNotNull(names.exerciseName("legCurl")) { "legCurl must be a curated exercise" }
        val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
        try {
            runBlocking {
                seed(db, a, date, 20, 35, 600, listOf(SessionSetEntity(a, "legPress", 1, SetState.COMPLETED.name, "40", "12", null)))
                seed(db, b, date, 21, 42, 900, listOf(
                    SessionSetEntity(b, "legPress", 1, SetState.COMPLETED.name, "40", "12", null),
                    SessionSetEntity(b, "legPress", 2, SetState.COMPLETED.name, "45", "12", null),
                    SessionSetEntity(b, "legCurl", 1, SetState.COMPLETED.name, "20", "12", null)
                ))
            }
            db.close()

            ActivityScenario.launch(ComposeActivity::class.java).use {
                // Figma 11: month with a stored-session dot, selected-day card.
                rule.onNodeWithTag("tab_calendar").performClick()
                rule.onNodeWithTag("calendar_list").performScrollToNode(hasTestTag("calendar_month_grid"))
                rule.onNodeWithTag("calendar_prev").performScrollTo().performClick()
                rule.waitUntil(10_000) {
                    rule.onAllNodesWithContentDescription("$date · 운동 기록 있음", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithContentDescription("$date · 운동 기록 있음", substring = true).performScrollTo().performClick()
                // The card is the LAST LazyColumn item: it is composed only after the day record has loaded AND the list
                // is scrolled to it, so keep trying to scroll there until it exists.
                rule.waitUntil(10_000) {
                    runCatching { rule.onNodeWithTag("calendar_list").performScrollToNode(hasTestTag("record_day_summary")) }.isSuccess
                }
                rule.onNodeWithTag("record_day_summary").assertIsDisplayed()
                rule.onNodeWithText("운동 2회").assertIsDisplayed()
                rule.onNodeWithText("운동시간 25분 00초").assertIsDisplayed()
                capture("compose-record-calendar.png")

                // Figma 26: sessions of the day, earliest first, with stored times only.
                rule.onNodeWithTag("record_day_summary").performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("record_session_$b").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("record_day_screen").assertIsDisplayed()
                rule.onNodeWithTag("record_day_total_time").assertTextEquals("25분 00초")
                // Inside a clickable card the child tag exists only in the unmerged tree.
                rule.onNodeWithTag("record_session_time_$a", useUnmergedTree = true).assertTextEquals("10분 00초")
                rule.onNodeWithTag("record_session_time_$b", useUnmergedTree = true).assertTextEquals("15분 00초")
                rule.onNodeWithText("20:35 시작").assertIsDisplayed()
                rule.onNodeWithText("21:42 시작").assertIsDisplayed()
                capture("compose-record-day.png")

                // Figma 27: stored sets exactly as saved; the stage card collapses and expands.
                rule.onNodeWithTag("record_session_$b").performScrollTo().performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("record_session_screen").fetchSemanticsNodes().isNotEmpty() }
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("record_session_total_time").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("record_session_total_time").assertTextEquals("15분 00초")
                rule.onNodeWithTag("record_stage_summary", useUnmergedTree = true).assertTextEquals("2종목 · 3세트")
                // Stage cards start collapsed: the stored sets exist only after the user opens the card.
                rule.onNodeWithTag("record_exercise_legPress").assertDoesNotExist()
                rule.onNodeWithTag("record_stage_toggle").performClick()
                rule.onNodeWithTag("record_exercise_sets_legPress").assertTextEquals("40kg × 12회 · 45kg × 12회 (2세트)")
                rule.onNodeWithTag("record_exercise_sets_legCurl").assertTextEquals("20kg × 12회 (1세트)")
                rule.onNodeWithText(legPress).assertIsDisplayed()
                rule.onNodeWithText(legCurl).assertIsDisplayed()
                capture("compose-record-session.png")
                rule.onNodeWithTag("record_stage_toggle").performClick()
                rule.onNodeWithTag("record_exercise_legPress").assertDoesNotExist()
                rule.onNodeWithTag("record_stage_toggle").performClick()
                rule.onNodeWithTag("record_exercise_legPress").assertIsDisplayed()

                // Back returns to the day record.
                rule.onNodeWithTag("record_back").performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("record_day_screen").fetchSemanticsNodes().isNotEmpty() }
            }
        } finally {
            if (db.isOpen) db.close()
            val clean = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
            try {
                val raw = clean.openHelper.writableDatabase
                for (table in listOf("session_sets", "session_active_intervals", "session_day_slices", "session_segments", "session_adjustments"))
                    raw.execSQL("DELETE FROM `$table` WHERE sessionId LIKE 'record-test-%'")
                raw.execSQL("DELETE FROM sessions WHERE sessionId LIKE 'record-test-%'")
            } finally { clean.close() }
        }
    }
}
