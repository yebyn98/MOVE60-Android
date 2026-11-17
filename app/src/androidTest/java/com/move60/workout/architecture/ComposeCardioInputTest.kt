package com.move60.workout.architecture

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real emulator flow (docs/v71 step 3): on the cardio stage the user saves machine values; cancelling or an invalid value stores nothing. */
@RunWith(AndroidJUnit4::class)
class ComposeCardioInputTest {
    @get:Rule val rule = createAndroidComposeRule<ComposeActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val elliptical = WorkoutData.CARDIO_SECOND
    private val cycle = WorkoutData.CARDIO_FIRST

    /** A dialog is a second window, so it is captured from the real screen. */
    private fun captureScreen(name: String) {
        rule.waitForIdle(); Thread.sleep(500)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.getExternalFilesDir(null), name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun has(tag: String) = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String, ms: Long = 10_000) = rule.waitUntil(ms) { has(tag) }

    private fun waitForDashboard() {
        val end = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < end && !has("dashboard_saved_routines")) {
            if (has("result_screen")) instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            Thread.sleep(500); rule.waitForIdle()
        }
        waitTag("dashboard_saved_routines", 2_000)
    }

    private fun prepareRoutine(): String {
        if (android.os.Build.VERSION.SDK_INT >= 33)
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, "android.permission.POST_NOTIFICATIONS")
        val legacy = SessionStore(context, WorkoutData(context))
        legacy.reloadIfChanged()
        if (legacy.running || legacy.elapsed() > 0 || legacy.stage != 0) legacy.resetAll()
        RoutiveSessionStore(context).let { s -> if (s.active()?.over == false) s.endEarly() }
        val id = "routive-cardio-${System.currentTimeMillis()}"
        val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, "move60_workouts_room_v1.db").build()
        try {
            val saved = runBlocking {
                RoutinePlanService(db).save(id, "유산소 수치 확인", 3_600L, mapOf(
                    StageKind.WARM_UP to listOf(RoutineExerciseInput("walk", "걷기", 300L)),
                    StageKind.STRENGTH to listOf(RoutineExerciseInput("legPress", "레그 프레스", null)),
                    StageKind.CARDIO to listOf(RoutineExerciseInput(cycle, cycle, 900L), RoutineExerciseInput(elliptical, elliptical, 900L))))
            }
            assertTrue("fixture routine must save: $saved", saved is RoutinePlanResult.Saved)
        } finally { db.close() }
        return id
    }

    private fun startAndGoToCardio(id: String) {
        waitForDashboard()
        rule.onNodeWithTag("dashboard_saved_routines").performClick()
        waitTag("routine_list")
        val anyItem = androidx.compose.ui.test.SemanticsMatcher("routine item") {
            it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("routine_item_") == true
        }
        rule.waitUntil(10_000) { rule.onAllNodes(anyItem).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("routine_list").performScrollToNode(hasTestTag("routine_start_$id"))
        rule.onNodeWithTag("routine_start_$id").performClick()
        waitTag("routine_start_dialog")
        rule.onNodeWithTag("routine_start_confirm").performClick()
        waitTag("routive_session"); waitTag("routive_stage_card")
        repeat(2) { rule.onNodeWithTag("routive_next").performClick(); rule.waitForIdle(); Thread.sleep(400) }
        waitTag("routive_cardio_input_$elliptical")
    }

    private fun saved() = RoutiveSessionStore(context).active()!!.cardioInputs

    @Test fun theUserSavesMachineValuesAndCancelOrInvalidInputStoresNothing() {
        val id = prepareRoutine()
        startAndGoToCardio(id)
        rule.onNodeWithTag("routive_session").performScrollToNode(hasTestTag("routive_cardio_input_$cycle"))
        rule.onNodeWithTag("routive_cardio_hint_$elliptical").performScrollTo().assertIsDisplayed()
        captureScreen("compose-cardio-1-stage.png")

        // The elliptical asks for level AND incline; the cycle only for level.
        rule.onNodeWithTag("routive_cardio_input_$cycle").performScrollTo().performClick()
        waitTag("routive_cardio_dialog_$cycle")
        assertTrue(has("routive_cardio_field_${cycle}_level")); assertTrue("a cycle has no incline", !has("routive_cardio_field_${cycle}_incline"))
        rule.onNodeWithTag("routive_cardio_cancel_$cycle").performClick()
        rule.waitUntil(5_000) { !has("routive_cardio_dialog_$cycle") }

        rule.onNodeWithTag("routive_cardio_input_$elliptical").performScrollTo().performClick()
        waitTag("routive_cardio_dialog_$elliptical")
        assertTrue(has("routive_cardio_field_${elliptical}_level")); assertTrue(has("routive_cardio_field_${elliptical}_incline"))
        captureScreen("compose-cardio-2-dialog-empty.png")

        // Nothing typed -> refused with a message, dialog stays, nothing stored.
        rule.onNodeWithTag("routive_cardio_save_$elliptical").performClick()
        waitTag("routive_cardio_error_$elliptical")
        assertTrue(saved().isEmpty())
        captureScreen("compose-cardio-3-dialog-error.png")

        // Typing then cancelling stores nothing.
        rule.onNodeWithTag("routive_cardio_field_${elliptical}_level").performTextInput("9")
        rule.onNodeWithTag("routive_cardio_cancel_$elliptical").performClick()
        rule.waitUntil(5_000) { !has("routive_cardio_dialog_$elliptical") }
        assertTrue("cancel must not store anything", saved().isEmpty())

        // A real save: level 5, incline 2.
        rule.onNodeWithTag("routive_cardio_input_$elliptical").performScrollTo().performClick()
        waitTag("routive_cardio_dialog_$elliptical")
        rule.onNodeWithTag("routive_cardio_field_${elliptical}_level").performTextInput("5")
        rule.onNodeWithTag("routive_cardio_field_${elliptical}_incline").performTextInput("2")
        captureScreen("compose-cardio-4-dialog-filled.png")
        rule.onNodeWithTag("routive_cardio_save_$elliptical").performClick()
        rule.waitUntil(5_000) { !has("routive_cardio_dialog_$elliptical") }
        waitTag("routive_cardio_entry_${elliptical}_0")
        rule.onNodeWithTag("routive_cardio_entry_${elliptical}_0").performScrollTo().assertIsDisplayed()
        captureScreen("compose-cardio-5-saved.png")

        val rows = saved()
        assertEquals(mapOf("level" to "5", "incline" to "2"), rows.associate { it.metricKey to it.valueText })
        assertTrue(rows.all { it.equipmentKey == "ELLIPTICAL" && it.stageIndex == 2 })
        RoutiveSessionStore(context).let { s -> if (s.active()?.over == false) s.endEarly() }
    }
}
