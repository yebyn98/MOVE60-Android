package com.move60.workout.architecture

import android.content.Intent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.move60.workout.WorkoutService
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real-screen evidence for Figma 05-07/58/59: start -> progress -> pause -> confirmed early end -> result. */
@RunWith(AndroidJUnit4::class)
class ComposeWorkoutProgressTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun isolate() {
        context.stopService(Intent(context, WorkoutService::class.java))
        instrumentation.waitForIdleSync()
        for (name in listOf("timer", "move60_set_records_v1", "move60_energy_v1", "move60_session_energy_v1",
            "move60_workout_history_v1", "move60_result_seen_v1"))
            context.getSharedPreferences(name, 0).edit().clear().commit()
    }

    /**
     * Process-wide singletons keep the previous test's timer in memory, so clearing prefs is not enough: reset the
     * stored session through the app's own reset (P-28 A) and wait until the overview shows the idle start button.
     */
    private fun ensureIdleOverview() {
        val store = com.move60.workout.SessionStore(context, com.move60.workout.WorkoutData(context))
        store.reloadIfChanged()
        if (store.running || store.elapsed() > 0 || store.stage != 0) store.resetAll()
        // The process-wide repository may still hold the previous test's ended session; opening the app then shows its
        // result screen first. Go back to the overview like a user would.
        val end = System.currentTimeMillis() + 15_000
        fun idle() = rule.onAllNodes(hasTestTag("dock_play") and androidx.compose.ui.test.hasText("운동 시작")).fetchSemanticsNodes().isNotEmpty()
        while (System.currentTimeMillis() < end && !idle()) {
            if (rule.onAllNodes(hasTestTag("result_screen")).fetchSemanticsNodes().isNotEmpty())
                instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            Thread.sleep(500)
            rule.waitForIdle()
        }
        if (!idle()) {
            captureScreen("ensure-idle-overview-failed.png")      // evidence for the next investigation
            throw AssertionError("idle overview (dock_play '운동 시작') did not appear within 15 s")
        }
    }

    private fun tagStartsWith(prefix: String) = androidx.compose.ui.test.SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun captureScreen(name: String) {
        rule.waitForIdle(); Thread.sleep(500)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.getExternalFilesDir(null), name).outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun startPauseConfirmedEarlyEndAndResult() {
        ActivityScenario.launch(ComposeActivity::class.java).use {
            ensureIdleOverview()
            rule.onNodeWithTag("dock_play").performClick()
            rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("workout_progress")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("workout_status").assertIsDisplayed()
            rule.onNodeWithText("● 진행 중").assertIsDisplayed()
            rule.onNodeWithTag("workout_remaining").assertIsDisplayed()
            capture("compose-workout-progress.png")
            Thread.sleep(3_000)                       // let real active time accumulate
            rule.onNodeWithTag("workout_pause").performClick()
            rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("workout_end")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("● 일시정지").assertIsDisplayed()
            capture("compose-workout-paused.png")
            rule.onNodeWithTag("workout_end").performClick()
            rule.onNodeWithTag("workout_end_dialog").assertIsDisplayed()
            captureScreen("compose-workout-end-dialog.png")
            rule.onNodeWithTag("workout_end_confirm").performClick()
            // Under one minute and no sets is not stored (D-01 B): the result says so instead of showing an error.
            rule.waitUntil(20_000) { rule.onAllNodes(hasTestTag("result_screen")).fetchSemanticsNodes().isNotEmpty() }
            rule.waitUntil(20_000) {
                rule.onAllNodes(hasTestTag("result_save_state")).fetchSemanticsNodes().isNotEmpty() &&
                    rule.onAllNodes(androidx.compose.ui.test.hasText("저장 안 함")).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("기록할 운동이 없어요").assertIsDisplayed()
            capture("compose-workout-result-not-stored.png")
        }
    }

    /** Strength stage: enter kg/reps for a set, complete it, and see the per-exercise counter move. */
    @Test fun strengthStageAcceptsSetEntryAndCountsCompletedSets() {
        ActivityScenario.launch(ComposeActivity::class.java).use {
            ensureIdleOverview()
            rule.onNodeWithTag("dock_play").performClick()
            rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("workout_progress")).fetchSemanticsNodes().isNotEmpty() }
            repeat(2) {                                   // warm-up -> cardio -> strength
                rule.onNodeWithTag("workout_next").performClick()
                Thread.sleep(800)
            }
            rule.waitUntil(8_000) { rule.onAllNodes(tagStartsWith("workout_task_")).fetchSemanticsNodes().isNotEmpty() }
            capture("compose-workout-strength.png")
            val kg = rule.onAllNodes(tagStartsWith("set_kg_"))[0]
            kg.performTextInput("40")
            rule.onAllNodes(tagStartsWith("set_reps_"))[0].performTextInput("12")
            rule.onAllNodes(tagStartsWith("set_done_"))[0].performClick()
            rule.waitUntil(8_000) { rule.onAllNodesWithText("1 / ", substring = true).fetchSemanticsNodes().isNotEmpty() }
            capture("compose-workout-strength-set-done.png")
        }
    }

    /**
     * P0-1 end to end: a set completed on screen must be stored with the early-ended session (valid by P-26 B even
     * under one minute) and the result screen must show exactly that stored set.
     */
    @Test fun completedSetThenEarlyEndIsStoredAndShownOnTheResult() {
        ActivityScenario.launch(ComposeActivity::class.java).use {
            ensureIdleOverview()
            rule.onNodeWithTag("dock_play").performClick()
            rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("workout_progress")).fetchSemanticsNodes().isNotEmpty() }
            repeat(2) { rule.onNodeWithTag("workout_next").performClick(); Thread.sleep(800) }
            rule.waitUntil(8_000) { rule.onAllNodes(tagStartsWith("workout_task_")).fetchSemanticsNodes().isNotEmpty() }
            rule.onAllNodes(tagStartsWith("set_kg_"))[0].performTextInput("40")
            rule.onAllNodes(tagStartsWith("set_reps_"))[0].performTextInput("12")
            rule.onAllNodes(tagStartsWith("set_done_"))[0].performClick()
            rule.waitUntil(8_000) { rule.onAllNodesWithText("1 / ", substring = true).fetchSemanticsNodes().isNotEmpty() }
            Thread.sleep(2_000)                                   // real active time, still < 60 s
            rule.onNodeWithTag("workout_pause").performClick()
            rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("workout_end")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("workout_end").performClick()
            rule.onNodeWithTag("workout_end_confirm").performClick()
            rule.waitUntil(20_000) { rule.onAllNodes(hasTestTag("result_screen")).fetchSemanticsNodes().isNotEmpty() }
            rule.waitUntil(20_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("저장됨")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("운동을 일부 마쳤어요").assertIsDisplayed()
            rule.onNodeWithTag("result_sets").assertIsDisplayed()
            rule.onNodeWithText("1세트").assertIsDisplayed()
            capture("compose-workout-result-with-set.png")
        }
    }

    /**
     * P1-1 on a real device/emulator (real SharedPreferences and Room, no UI): an early-ended session whose commit was
     * never confirmed must not be lost when a new workout starts. No activity is launched on purpose: opening the app
     * would commit the ended session on foreground, which hides exactly the case being tested.
     */
    @Test fun newWorkoutStartedBeforeCommitKeepsTheEarlierSessionAndStoresIt() {
        val outbox = PendingSessionOutbox(context)
        val store = com.move60.workout.SessionStore(context, com.move60.workout.WorkoutData(context))
        try {
            // A previous test may have left an ended session in the slot; resetting it correctly buffers it. Start from an empty outbox afterwards.
            store.reloadIfChanged(); store.resetAll()
            outbox.pending().forEach { outbox.remove(outbox.sessionId(it)) }
            store.start()
            com.move60.workout.SetRecordStore(context).write(store.day, "chestPress", 1,
                com.move60.workout.SetRecordStore.Record("40", "10", "5", true), store.recordDate, store.sessionStartedAt)
            store.reconcile(System.currentTimeMillis() + 120_000L)
            org.junit.Assert.assertTrue(store.endEarly())          // ended, nothing committed
            val expected = store.timerSnapshot(com.move60.workout.SetRecordStore(context))
            val expectedId = outbox.sessionId(expected)
            org.junit.Assert.assertTrue("fixture: real intervals must exist", expected.intervals.isNotEmpty())
            org.junit.Assert.assertEquals("nothing is buffered while the slot still holds the session", 0, outbox.size())

            store.start()                                          // the new workout overwrites the single slot
            org.junit.Assert.assertNotEquals(expected.startedAtEpochMs, store.sessionStartedAt)
            org.junit.Assert.assertEquals("the earlier session was copied before being replaced", 1, outbox.size())

            (context.applicationContext as com.move60.workout.Move60Application).syncTimerSessionAsync()
            val db = androidx.room.Room.databaseBuilder(context, WorkoutDatabase::class.java, "move60_workouts_room_v1.db").build()
            try {
                val deadline = System.currentTimeMillis() + 20_000
                var stored: SessionEntity? = null
                while (System.currentTimeMillis() < deadline && stored == null) {
                    stored = kotlinx.coroutines.runBlocking { db.routiveDao().session(expectedId) }
                    if (stored == null) Thread.sleep(500)
                }
                org.junit.Assert.assertNotNull("earlier session must be stored after the new workout started", stored)
                org.junit.Assert.assertEquals("PARTIAL", stored!!.completion)
                org.junit.Assert.assertEquals(expected.accumulatedSeconds.toLong(), stored.totalActiveSeconds)
                org.junit.Assert.assertEquals(1, kotlinx.coroutines.runBlocking { db.routiveDao().sets(expectedId) }.size)
            } finally { db.close() }
            val drainDeadline = System.currentTimeMillis() + 10_000
            while (outbox.size() > 0 && System.currentTimeMillis() < drainDeadline) Thread.sleep(250)
            org.junit.Assert.assertEquals("confirmed commit empties the outbox", 0, outbox.size())
        } finally {
            store.resetAll()                                       // never leave a running session for the next test
            context.stopService(Intent(context, WorkoutService::class.java))
        }
    }

    /** The notification still opens the legacy detail activity; it must see the same running session. */
    @Test fun legacyDetailActivityStillSeesTheSessionStartedInCompose() {
        ActivityScenario.launch(ComposeActivity::class.java).use {
            ensureIdleOverview()
            rule.onNodeWithTag("dock_play").performClick()
            rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("workout_progress")).fetchSemanticsNodes().isNotEmpty() }
            Thread.sleep(1_500)
        }
        ActivityScenario.launch(com.move60.workout.MainActivity::class.java).use { legacy ->
            legacy.onActivity { activity ->
                org.junit.Assert.assertNotNull(activity.window.decorView.findViewWithTag<android.view.View>("phone_dock_progress"))
            }
        }
        val store = com.move60.workout.SessionStore(context, com.move60.workout.WorkoutData(context))
        store.reloadIfChanged()
        org.junit.Assert.assertTrue("session started in Compose must keep running for the legacy screen", store.running)
        org.junit.Assert.assertTrue(store.elapsed() >= 1)
    }
}
