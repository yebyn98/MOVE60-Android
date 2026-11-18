package com.move60.workout.architecture

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.move60.workout.domain.StageKind
import com.move60.workout.domain.StageTimePlan
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * docs/v61 slice 2 on a real emulator: a session stored WITH the start-time plan and stage actuals shows its routine name and
 * stage times in the day list, and stage cards (real time, plan labelled as plan, "수치 미기록" for cardio) in the detail.
 * Seeds one session on a PAST date (day 11) and removes only its own `record-stage-*` rows afterwards.
 */
/**
 * docs/v71 step 4 on a real emulator: a stored cardio session shows its recorded intervals only after the user opens the (collapsed by
 * default) cardio card, with a 48dp-plus "수치 수정" row. The capture `compose-record-cardio-intervals.png` is the real screen.
 */
@RunWith(AndroidJUnit4::class)
class ComposeRecordCardioIntervalsTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val zone = ZoneId.of("Asia/Seoul")
    private val dbFile = "move60_workouts_room_v1.db"
    private val id = "record-cardio-intervals-a"

    private fun capture(name: String) {
        rule.waitForIdle(); Thread.sleep(500)
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun recordedCardioIntervalsAppearOnlyAfterOpeningTheCardioCard() {
        // A different day than ComposeRecordStagesTest so the two fixtures never share a calendar cell.
        val date = LocalDate.now(zone).minusMonths(1).withDayOfMonth(12)
        val routine = SavedRoutinePlan(
            "r2", "유산소 루틴", null,
            listOf(StageTimePlan(StageKind.CARDIO, 900)),
            mapOf(StageKind.CARDIO to listOf(RoutineExerciseInput("cycle", "실내 사이클", 900)))
        )
        val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
        try {
            runBlocking {
                val total = 900L
                val start = date.atTime(20, 35).atZone(zone).toEpochSecond()
                val session = SessionEntity(id, null, zone.id, start * 1000, (start + total) * 1000,
                    SessionCompletion.COMPLETED.name, total, 1, false, RoutivePlanSnapshot.toJson(routine), 0L)
                val segments = listOf(SessionSegmentEntity(id, 0, "CARDIO_STEADY", 900, 900, null, null))
                // Two level changes inside the 15-minute stage: 0:00 level 6, 7:00 level 8 (both real stored rows, not UI-only values).
                val inputs = listOf(
                    SessionCardioInputEntity(id, 0, 0, 0, "INDOOR_CYCLE", "level", "6", null, 0L),
                    SessionCardioInputEntity(id, 1, 0, 420, "INDOOR_CYCLE", "level", "8", null, 0L)
                )
                SessionCommitter(db).commit(
                    SessionBundle(session, listOf(SessionActiveIntervalEntity(id, 0, start, start + total)), emptyList(), segments, inputs)
                )
            }
            db.close()

            ActivityScenario.launch(ComposeActivity::class.java).use {
                rule.onNodeWithTag("tab_calendar").performClick()
                rule.onNodeWithTag("calendar_list").performScrollToNode(hasTestTag("calendar_month_grid"))
                rule.onNodeWithTag("calendar_prev").performScrollTo().performClick()
                rule.waitUntil(10_000) {
                    rule.onAllNodesWithContentDescription("$date · 운동 기록 있음", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithContentDescription("$date · 운동 기록 있음", substring = true).performScrollTo().performClick()
                rule.waitUntil(10_000) {
                    runCatching { rule.onNodeWithTag("calendar_list").performScrollToNode(hasTestTag("record_day_summary")) }.isSuccess
                }
                rule.onNodeWithTag("record_day_summary").performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("record_session_$id").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("record_session_$id").performScrollTo().performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("record_stage_CARDIO_STEADY").fetchSemanticsNodes().isNotEmpty() }

                // Collapsed by default: the intervals and the edit row do not exist until the card is opened.
                rule.onAllNodesWithTag("record_cardio_intervals").assertCountEquals(0)
                rule.onNodeWithTag("record_stage_toggle_CARDIO_STEADY").performScrollTo().performClick()
                rule.onNodeWithTag("record_cardio_intervals").performScrollTo().assertIsDisplayed()
                rule.onAllNodesWithTag("record_cardio_interval").assertCountEquals(2)
                rule.onNodeWithTag("record_cardio_edit_open").performScrollTo().assertIsDisplayed()
                capture("compose-record-cardio-intervals.png")
            }
        } finally {
            if (db.isOpen) db.close()
        }
    }
}
