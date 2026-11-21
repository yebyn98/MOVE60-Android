package com.move60.workout.architecture

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.getOrNull
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
    private fun notificationPosted(): Boolean =
        (context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .activeNotifications.any { it.id == RoutiveSessionService.NOTIFICATION_ID }
    private fun waitNotification(expected: Boolean, what: String) {
        val end = System.currentTimeMillis() + 12_000
        while (System.currentTimeMillis() < end && notificationPosted() != expected) Thread.sleep(300)
        org.junit.Assert.assertEquals(what, expected, notificationPosted())
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

    /** Frees both session kinds, saves a fixture routine and returns its id. */
    private fun prepareRoutine(strength: RoutineExerciseInput = RoutineExerciseInput("legPress", "레그 프레스", null)): String {
        if (android.os.Build.VERSION.SDK_INT >= 33)
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, "android.permission.POST_NOTIFICATIONS")
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
                    StageKind.STRENGTH to listOf(strength),
                    StageKind.CARDIO to listOf(RoutineExerciseInput("bike", "실내 자전거", 900L))))
            }
            assertTrue("fixture routine must save: $saved", saved is RoutinePlanResult.Saved)
        } finally { db.close() }
        return id
    }

    private fun startFromList(id: String) {
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
    }

    private fun restNotificationBody(): String? =
        (context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .activeNotifications.firstOrNull { it.id == RoutiveSessionService.NOTIFICATION_ID }
            ?.notification?.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()

    @Test fun restTimerRunsAfterASetAndIsExcludedFromActiveTime() {
        val id = prepareRoutine(RoutineExerciseInput("legPress", "레그 프레스", null, 3, 10, 6L))
        try {
            startFromList(id)
            rule.onNodeWithTag("routive_next").performClick()                        // -> strength
            rule.waitUntil(5_000) { rule.onAllNodes(hasText("2 / 4 단계 · 근력운동")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("routive_sets_legPress").performScrollTo().assertIsDisplayed()
            rule.onNode(hasText("0 / 3 세트")).assertIsDisplayed()                   // target sets shown
            rule.onNode(hasText("1세트 · 목표 10회 · 휴식 00:06")).performScrollTo().assertIsDisplayed()
            capture("compose-routive-strength-targets.png")
            rule.onNodeWithTag("routive_kg_legPress").performScrollTo().performTextInput("40")
            rule.onNodeWithTag("routive_reps_legPress").performScrollTo().performTextInput("10")
            rule.onNodeWithTag("routive_set_done_legPress").performScrollTo().performClick()
            waitTag("routive_rest_card")
            val workBeforeRest = RoutiveSessionStore(context).reconcile()!!.progress.elapsedSeconds[1]
            val wallStart = System.currentTimeMillis()
            capture("compose-routive-rest-running.png")
            rule.waitUntil(5_000) { restNotificationBody()?.contains("휴식") == true }
            rule.waitUntil(15_000) { rule.onAllNodes(hasText("휴식 끝")).fetchSemanticsNodes().isNotEmpty() }
            val wallMs = System.currentTimeMillis() - wallStart
            val workAfterRest = RoutiveSessionStore(context).reconcile()!!.progress.elapsedSeconds[1]
            assertTrue("the rest ran for real (wall ${wallMs}ms)", wallMs >= 4_000)
            assertTrue("rest time is not stage time: $workBeforeRest -> $workAfterRest after ${wallMs}ms", workAfterRest - workBeforeRest <= 2)
            capture("compose-routive-rest-done.png")
            rule.onNodeWithTag("routive_rest_skip").performClick()
            rule.waitUntil(5_000) { !has("routive_rest_card") }
            rule.onNode(hasText("1 / 3 세트")).performScrollTo().assertIsDisplayed()
        } finally {
            RoutiveSessionStore(context).let { s -> if (s.active()?.over == false) s.endEarly() }
        }
    }

    @Test fun conflictingBufferedRecordShowsANoticeOnTheRecordScreenAndIsKept() {
        val outbox = PendingSessionOutbox(context)
        val started = 1_600_000_000_000L + System.currentTimeMillis() % 1000
        val snap = TimerSnapshot(java.time.LocalDate.of(2026, 11, 2), 1, started, false, 70, listOf(started / 1000 to started / 1000 + 70), true)
        val id = outbox.sessionId(snap)
        try {
            assertTrue(outbox.enqueue(snap)); outbox.markFailed(id, PendingSessionOutbox.CONFLICT)
            waitForDashboard()
            rule.onNodeWithTag("tab_calendar").performClick()
            waitTag("calendar_list")
            waitTag("calendar_conflict_notice")
            rule.onNodeWithTag("calendar_conflict_notice").assertIsDisplayed()
            rule.onNode(hasText("확인이 필요한 기록 1건", substring = true)).assertIsDisplayed()
            capture("compose-record-conflict-notice.png")
            assertTrue("the entry is kept untouched", outbox.pending().any { outbox.sessionId(it) == id })
            rule.onNodeWithTag("calendar_conflict_notice").performClick()
            waitTag("calendar_conflict_dialog")
            rule.onNodeWithTag("calendar_conflict_item_$id").assertIsDisplayed()
            captureScreen("compose-record-conflict-dialog.png")
            rule.onNodeWithTag("calendar_conflict_keep").performClick()               // 보관 유지: nothing changes
            rule.waitUntil(5_000) { !has("calendar_conflict_dialog") }
            assertTrue("keeping leaves the buffered copy", outbox.pending().any { outbox.sessionId(it) == id })
            rule.onNodeWithTag("calendar_conflict_notice").performClick()
            waitTag("calendar_conflict_dialog")
            rule.onNodeWithTag("calendar_conflict_discard_$id").performClick()
            waitTag("calendar_conflict_discard_dialog")
            captureScreen("compose-record-conflict-discard-confirm.png")
            rule.onNodeWithTag("calendar_conflict_discard_confirm").performClick()
            rule.waitUntil(5_000) { !has("calendar_conflict_notice") }
            assertTrue("only the buffered copy is gone", outbox.pending().none { outbox.sessionId(it) == id })
            rule.onNodeWithTag("tab_today").performClick()
        } finally { outbox.remove(id) }
    }

    @Test fun tabsShowOnNormalScreensAndHideDuringRunAndPause() {
        val id = prepareRoutine()
        try {
            waitForDashboard()
            rule.onNodeWithTag("tab_bar").assertIsDisplayed()
            startFromList(id)
            assertTrue("tab bar hidden in the running session", !has("tab_bar"))
            rule.onNodeWithTag("routive_pause").performClick()
            waitTag("routive_resume")
            assertTrue("tab bar hidden while paused", !has("tab_bar"))
        } finally {
            RoutiveSessionStore(context).let { s -> if (s.active()?.over == false) s.endEarly() }
        }
    }

    @Test fun explicitFinalSkipFinishesAndIsSavedAsCompleted() {
        val id = prepareRoutine()
        try {
            startFromList(id)
            Thread.sleep(1_500)
            rule.onNodeWithTag("routive_next").performClick()                        // -> strength
            rule.waitUntil(5_000) { rule.onAllNodes(hasText("2 / 4 단계 · 근력운동")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("routive_kg_legPress").performScrollTo().performTextInput("40")
            rule.onNodeWithTag("routive_reps_legPress").performScrollTo().performTextInput("10")
            rule.onNodeWithTag("routive_set_done_legPress").performScrollTo().performClick()
            rule.waitUntil(5_000) { rule.onAllNodes(hasText("1세트 완료")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("routive_next").performClick()                        // -> cardio
            rule.onNodeWithTag("routive_next").performClick()                        // -> cool-down (last)
            rule.waitUntil(5_000) { rule.onAllNodes(hasText("4 / 4 단계 · 마무리")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(hasText("운동 완료")).assertIsDisplayed()                    // primary action
            rule.onNodeWithTag("routive_skip_last").performScrollTo().assertIsDisplayed()   // secondary action
            capture("compose-routive-last-stage.png")
            rule.onNodeWithTag("routive_skip_last").performClick()
            waitTag("routive_skip_last_dialog")
            captureScreen("compose-routive-skip-last-dialog.png")
            rule.onNodeWithTag("routive_skip_last_confirm").performClick()
            waitTag("result_screen", 20_000)
            // The result screen exists while the save is still running ("결과를 저장하고 있어요"): wait for the final judgement text.
            rule.waitUntil(20_000) { rule.onAllNodes(hasText("오늘 운동을 마쳤어요")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(hasText("오늘 운동을 마쳤어요")).assertIsDisplayed()          // skip is not an automatic PARTIAL
            capture("compose-routive-result-skip-last.png")
        } finally {
            RoutiveSessionStore(context).let { s -> if (s.active()?.over == false) s.endEarly() }
        }
    }

    @Test fun savedRoutineStartsAFourStageSessionAndEndsWithARecordedResult() {
        val id = prepareRoutine()
        try {
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
            captureScreen("compose-routive-start-dialog.png")
            rule.onNodeWithTag("routine_start_confirm").performClick()

            waitTag("routive_session")
            waitTag("routive_stage_card")
            rule.onNodeWithTag("routive_stage_label").assertIsDisplayed()
            rule.onNode(hasText("1 / 4 단계 · 준비운동")).assertIsDisplayed()
            waitNotification(true, "ongoing workout notification while the session is in progress")
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
            waitNotification(false, "notification is removed when the session is over")
            rule.waitForIdle()
            capture("compose-routive-result.png")
        } finally {
            RoutiveSessionStore(context).let { s -> if (s.active()?.over == false) s.endEarly() }   // never leave a running session for other tests
        }
    }
    @Test fun unfinishedStrengthRequiresTwoButtonsAndPerSetSkipCanBeUndone() {
        val id = prepareRoutine(RoutineExerciseInput("legPress", "레그프레스", null, 3, 10))
        try {
            startFromList(id)
            rule.onNodeWithTag("routive_next").performClick() // warm-up -> strength
            rule.waitUntil(5_000) { rule.onAllNodes(hasText("2 / 4 단계 · 근력운동")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("routive_next").performClick()
            waitTag("routive_next_unfinished_dialog")
            rule.onNodeWithTag("routive_next_unfinished_continue").performClick()
            rule.onNodeWithTag("routive_set_skip_legPress_1").performScrollTo().performClick()
            waitTag("routive_set_skip_dialog")
            rule.onNodeWithTag("routive_set_skip_cancel").performClick()
            assertTrue(SetRecordStore(context).skippedSetsForSession(RoutiveSessionStore(context).active()!!.startedAtEpochMs).isEmpty())
            rule.onNodeWithTag("routive_set_skip_legPress_1").performScrollTo().performClick()
            rule.onNodeWithTag("routive_set_skip_confirm").performClick()
            waitTag("routive_set_skip_undo_legPress_1")
            val token = RoutiveSessionStore(context).active()!!.startedAtEpochMs
            assertTrue(SetRecordStore(context).skippedSetsForSession(token).any { it.exerciseId == "legPress" && it.setNumber == 1 })
            assertTrue(SetRecordStore(context).completedSetsForSession(token).isEmpty())
            rule.onNodeWithTag("routive_set_skip_undo_legPress_1").performScrollTo().performClick()
            rule.waitUntil(5_000) { SetRecordStore(context).skippedSetsForSession(token).isEmpty() }
            assertTrue("undo never marks a set completed", SetRecordStore(context).completedSetsForSession(token).isEmpty())
        } finally {
            RoutiveSessionStore(context).let { s -> if (s.active()?.over == false) s.endEarly() }
        }
    }

}
