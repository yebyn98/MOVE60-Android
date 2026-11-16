package com.move60.workout.architecture

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.move60.workout.SessionStore
import com.move60.workout.WorkoutData
import com.move60.workout.domain.StageKind
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real emulator flow: saved routine -> confirm start -> 4-stage session -> set -> pause -> confirmed end -> result. */
@RunWith(AndroidJUnit4::class)
class ComposeRoutiveSessionTest {
    @get:Rule val rule = createAndroidComposeRule<ComposeActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    /** A dialog is a second window (two compose roots), so dialogs are captured from the real screen. */
    private fun captureScreen(name: String) {
        rule.waitForIdle(); Thread.sleep(500)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.getExternalFilesDir(null), name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun has(tag: String) = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String, ms: Long = 10_000) = rule.waitUntil(ms) { has(tag) }

    /** Other tests may leave an ended legacy session behind (its result screen opens first): go back like a user would. */
    private fun waitForDashboard() {
        val end = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < end && !has("dashboard_saved_routines")) {
            if (has("result_screen")) instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            Thread.sleep(500); rule.waitForIdle()
        }
        waitTag("dashboard_saved_routines", 2_000)
    }

    @Test fun savedRoutineStartsAFourStageSessionAndEndsWithARecordedResult() {
        val legacy = SessionStore(context, WorkoutData(context))
        legacy.reloadIfChanged()
        if (legacy.running || legacy.elapsed() > 0 || legacy.stage != 0) legacy.resetAll()
        RoutiveSessionStore(context).let { s -> if (s.active()?.over == false) s.endEarly() }

        val id = "routive-session-${System.currentTimeMillis()}"
        val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, "move60_workouts_room_v1.db").build()
        try {
            val saved = runBlocking {
                RoutinePlanService(db).save(id, "4단계 시작 확인", 3_600L, mapOf(
                    StageKind.WARM_UP to listOf(RoutineExerciseInput("walk", "걷기", 300L)),
                    StageKind.STRENGTH to listOf(RoutineExerciseInput("legPress", "레그 프레스", null)),
                    StageKind.CARDIO to listOf(RoutineExerciseInput("bike", "실내 자전거", 900L))))
            }
            assertTrue("fixture routine must save: $saved", saved is RoutinePlanResult.Saved)
        } finally { db.close() }

        try {
            waitForDashboard()
            rule.onNodeWithTag("dashboard_saved_routines").performClick()
            waitTag("routine_list")
            // The list loads asynchronously: wait for the first saved routine card before scrolling to ours.
            val anyItem = androidx.compose.ui.test.SemanticsMatcher("routine item") {
                it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("routine_item_") == true
            }
            rule.waitUntil(10_000) { rule.onAllNodes(anyItem).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("routine_list").performScrollToNode(hasTestTag("routine_start_$id"))
            rule.onNodeWithTag("routine_start_$id").performClick()
            waitTag("routine_start_dialog")
            captureScreen("compose-routive-start-dialog.png")
            rule.onNodeWithTag("routine_start_confirm").performClick()

            waitTag("routive_session")
            waitTag("routive_stage_card")
            rule.onNodeWithTag("routive_stage_label").assertIsDisplayed()
            rule.onNode(hasText("1 / 4 단계 · 준비운동")).assertIsDisplayed()
            rule.onNode(hasText("계획 5분")).assertIsDisplayed()
            Thread.sleep(2_500)                                       // real running time accrues
            rule.waitForIdle()
            capture("compose-routive-session-warmup.png")

            // Next -> strength: unknown planned time is "미정", never 0, and nothing advances by itself.
            rule.onNodeWithTag("routive_next").performClick()
            rule.waitUntil(5_000) { rule.onAllNodes(hasText("2 / 4 단계 · 근력운동")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("routive_planned").assertIsDisplayed()
            rule.onNode(hasText("계획 시간 미정")).assertIsDisplayed()
            rule.onNodeWithTag("routive_remaining_unknown").assertIsDisplayed()

            // One completed set (the only thing that makes a very short session valid).
            rule.onNodeWithTag("routive_kg_legPress").performScrollTo().performTextInput("40")
            rule.onNodeWithTag("routive_reps_legPress").performScrollTo().performTextInput("10")
            rule.onNodeWithTag("routive_set_done_legPress").performScrollTo().performClick()
            rule.waitUntil(5_000) { rule.onAllNodes(hasText("1세트 완료")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(hasText("1세트 · 40kg × 10회")).assertIsDisplayed()
            capture("compose-routive-session-strength-set.png")

            // Pause -> confirmed early end -> recorded result (valid because of the completed set).
            rule.onNodeWithTag("routive_pause").performClick()
            waitTag("routive_end")
            rule.onNode(hasText("● 일시정지")).assertIsDisplayed()
            capture("compose-routive-session-paused.png")
            rule.onNodeWithTag("routive_end").performClick()
            rule.onNodeWithTag("routive_end_dialog").assertIsDisplayed()
            captureScreen("compose-routive-end-dialog.png")
            rule.onNodeWithTag("routive_end_confirm").performClick()
            waitTag("result_screen", 20_000)
            rule.waitForIdle()
            capture("compose-routive-result.png")
        } finally {
            RoutiveSessionStore(context).let { s -> if (s.active()?.over == false) s.endEarly() }   // never leave a running session for other tests
        }
    }
}
