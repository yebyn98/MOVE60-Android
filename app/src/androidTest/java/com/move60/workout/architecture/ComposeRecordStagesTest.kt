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
@RunWith(AndroidJUnit4::class)
class ComposeRecordStagesTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val zone = ZoneId.of("Asia/Seoul")
    private val dbFile = "move60_workouts_room_v1.db"
    private val id = "record-stage-a"

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun routineNameAndStageCardsAreShownFromStoredData() {
        val date = LocalDate.now(zone).minusMonths(1).withDayOfMonth(11)
        val routine = SavedRoutinePlan(
            "r1", "하체 루틴", null,
            listOf(StageTimePlan(StageKind.WARM_UP, 300), StageTimePlan(StageKind.STRENGTH, null), StageTimePlan(StageKind.CARDIO, 900), StageTimePlan(StageKind.COOL_DOWN, null)),
            mapOf(
                StageKind.WARM_UP to listOf(RoutineExerciseInput("walk", "제자리 걷기", 120)),
                StageKind.CARDIO to listOf(RoutineExerciseInput("cycle", "실내 사이클", 900))
            )
        )
        val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
        try {
            runBlocking {
                val total = 300L + 600L + 900L + 120L
                val start = date.atTime(20, 35).atZone(zone).toEpochSecond()
                val session = SessionEntity(id, null, zone.id, start * 1000, (start + total) * 1000,
                    SessionCompletion.COMPLETED.name, total, 1, false, RoutivePlanSnapshot.toJson(routine), 0L)
                val segments = listOf(
                    SessionSegmentEntity(id, 0, "WARMUP", 300, 300, null, null),
                    SessionSegmentEntity(id, 1, "STRENGTH", null, 600, null, null),
                    SessionSegmentEntity(id, 2, "CARDIO_STEADY", 900, 900, null, null),
                    SessionSegmentEntity(id, 3, "COOLDOWN", null, 120, null, null)
                )
                SessionCommitter(db).commit(
                    SessionBundle(session, listOf(SessionActiveIntervalEntity(id, 0, start, start + total)),
                        listOf(SessionSetEntity(id, "legPress", 1, SetState.COMPLETED.name, "40", "12", null)), segments)
                )
            }
            db.close()

            ActivityScenario.launch(ComposeActivity::class.java).use {
                // Calendar -> day record (same path as ComposeRecordTest).
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

                // Figma 26: routine name as the title and the real stage times (the 0-second stage would be absent).
                rule.onNodeWithText("하체 루틴").assertIsDisplayed()
                rule.onNodeWithText("준비운동 5분 · 근력운동 10분 · 유산소 15분 · 마무리 2분").assertIsDisplayed()
                capture("compose-record-day-stages.png")

                // Figma 27: stage cards. Real time first, plan next to it, plan items labelled as plan.
                rule.onNodeWithTag("record_session_$id").performScrollTo().performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("record_stage_WARMUP").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("하체 루틴").assertIsDisplayed()
                rule.onNodeWithTag("record_stage_WARMUP").performScrollTo().assertIsDisplayed()
                rule.onNodeWithText("실제 5분 · 계획 5분 · 계획 항목 1개").assertIsDisplayed()
                rule.onNodeWithText("제자리 걷기").assertIsDisplayed()
                rule.onNodeWithText("계획 2분").assertIsDisplayed()
                capture("compose-record-session-stages.png")

                rule.onNodeWithText("실제 10분 · 계획 시간 미정 · 1종목 · 1세트").performScrollTo().assertIsDisplayed()
                rule.onNodeWithTag("record_stage_CARDIO_STEADY").performScrollTo().assertIsDisplayed()
                rule.onNodeWithText("실제 15분 · 계획 15분 · 계획 항목 1개").assertIsDisplayed()
                rule.onNodeWithText("실내 사이클").assertIsDisplayed()
                // No cardio console values are stored, so the cardio stage says so instead of showing numbers.
                rule.onNodeWithTag("record_cardio_no_values").performScrollTo().assertIsDisplayed()
                rule.onNodeWithText("수치 미기록").assertIsDisplayed()
                rule.onNodeWithTag("record_stage_COOLDOWN").performScrollTo().assertIsDisplayed()
                rule.onNodeWithText("실제 2분 · 계획 시간 미정").assertIsDisplayed()
            }
        } finally {
            if (db.isOpen) db.close()
            val clean = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
            try {
                val raw = clean.openHelper.writableDatabase
                for (table in listOf("session_adjustments", "session_sets", "session_active_intervals", "session_day_slices", "session_segments"))
                    raw.execSQL("DELETE FROM `$table` WHERE sessionId LIKE 'record-stage-%'")
                raw.execSQL("DELETE FROM sessions WHERE sessionId LIKE 'record-stage-%'")
            } finally { clean.close() }
        }
    }
}
