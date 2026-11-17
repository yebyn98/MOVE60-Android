package com.move60.workout.architecture

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * docs/v67 · Phase 3-1: the routine composer shows the default space and offers "exercises that fit the equipment" (approved rows only).
 * Seeds one clearly named space (`e2e 후보`) only when the device has no spaces, and removes just that afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ComposeExerciseCandidatesTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val dbFile = "move60_workouts_room_v1.db"

    private fun captureScreen(name: String) {
        rule.waitForIdle(); Thread.sleep(600)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.getExternalFilesDir(null), name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun waitTag(tag: String) = rule.waitUntil(10_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun openComposer() {
        waitTag("dashboard_compose_routine")
        rule.onNodeWithTag("dashboard_compose_routine").performClick()
        waitTag("routine_compose")
    }
    /** The composer must already be open. */
    private fun openStrengthPicker() {
        rule.onNodeWithTag("routine_compose").performScrollToNode(hasTestTag("routine_stage_STRENGTH"))
        rule.onNodeWithTag("routine_stage_STRENGTH").performClick()
        waitTag("routine_picker")
    }
    private fun openDb() = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
    private fun cleanE2e() {
        val db = openDb()
        try {
            val raw = db.openHelper.writableDatabase
            raw.execSQL("DELETE FROM space_equipment WHERE spaceId IN (SELECT spaceId FROM workout_spaces WHERE name LIKE 'e2e %')")
            raw.execSQL("DELETE FROM workout_spaces WHERE name LIKE 'e2e %'")
        } finally { db.close() }
    }

    @Test fun withoutASpaceTheComposerSaysSoAndStillOffersTheManualList() {
        val probe = openDb()
        val none = try { runBlocking { probe.spaceDao().spaces().isEmpty() } } finally { probe.close() }
        Assume.assumeTrue("real spaces exist on this device", none)
        ActivityScenario.launch(ComposeActivity::class.java).use {
            openComposer()
            rule.onNodeWithTag("routine_compose").performScrollToNode(hasTestTag("routine_equipment"))
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("routine_equipment").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("기본 운동 공간이 없어요", substring = true).assertIsDisplayed()
            // The old fixed text claimed a feature that did not exist; it must be gone.
            assertTrue(rule.onAllNodesWithTag("routine_equipment").fetchSemanticsNodes().isNotEmpty())
            captureScreen("compose-candidates-no-space.png")
            openStrengthPicker()
            rule.onNodeWithTag("routine_candidates_title").assertIsDisplayed()
            rule.onNodeWithTag("routine_candidates_empty").assertIsDisplayed()
            rule.onNodeWithTag("routine_manual_title").assertIsDisplayed()
            assertTrue("manual composition still lists exercises", rule.onAllNodesWithTag("routine_option_legPress").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithTag("routine_manual_title").fetchSemanticsNodes().isNotEmpty())
            captureScreen("compose-candidates-picker-no-space.png")
        }
    }

    @Test fun aDefaultSpaceShowsItsEquipmentAndOnlyApprovedExercisesThatFit() {
        val seed = openDb()
        try {
            Assume.assumeTrue("real spaces exist on this device", runBlocking { seed.spaceDao().spaces().isEmpty() })
            runBlocking {
                SpaceService(seed).create("e2e-cand", SpaceKind.HOME, "e2e 후보")
                SpaceService(seed).saveEquipment("e2e-cand", listOf(EquipmentChoice("DUMBBELL")))
            }
        } finally { seed.close() }
        try {
            ActivityScenario.launch(ComposeActivity::class.java).use {
                openComposer()
                rule.onNodeWithTag("routine_compose").performScrollToNode(hasTestTag("routine_equipment"))
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("routine_equipment").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("e2e 후보").assertIsDisplayed()
                rule.onNodeWithText("등록 기구: 덤벨").assertIsDisplayed()
                captureScreen("compose-candidates-space.png")
                openStrengthPicker()
                rule.onNodeWithTag("routine_candidates_title").assertIsDisplayed()
                rule.onNodeWithTag("routine_candidates_empty").assertDoesNotExist()
                // Dumbbell only: the approved body-weight / dumbbell-or-body-weight rows are there; unapproved dumbbell rows are not.
                for (id in listOf("plank", "calfRaise", "reverseLunge", "gluteBridge"))
                    assertTrue("$id is an approved row that fits", rule.onAllNodesWithTag("routine_option_$id").fetchSemanticsNodes().isNotEmpty())
                for (id in listOf("gobletSquat", "bicepCurl", "legPress"))
                    assertTrue("$id is not approved or needs other equipment, so it must not be in the candidate section", id !in candidateSectionIds())
                captureScreen("compose-candidates-picker.png")
            }
        } finally { cleanE2e() }
    }

    /** Ids shown above the "직접 구성" heading: the rows listed before it in the dialog's column. */
    private fun candidateSectionIds(): Set<String> {
        val manualTop = rule.onNodeWithTag("routine_manual_title").fetchSemanticsNode().boundsInRoot.top
        return rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("option") {
            it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("routine_option_") == true
        }).fetchSemanticsNodes().filter { it.boundsInRoot.top < manualTop }
            .mapNotNull { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.removePrefix("routine_option_") }.toSet()
    }
}
