package com.move60.workout.architecture

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 최초 운동 설정 on a real emulator (Figma FINAL/01, FINAL/02): launched with `move60_force_first_run` because the test runner
 * marks the setup done for all other tests. Creates one gym named `e2e 첫 설정` and removes it, and restores the first-run
 * flags, afterwards. Skipped when the device already has spaces, so a real user's default space is never changed.
 */
@RunWith(AndroidJUnit4::class)
class ComposeFirstRunTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val dbFile = "move60_workouts_room_v1.db"
    private val gymName = "e2e 첫 설정"

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun prefs() = context.getSharedPreferences("move60_first_run_v1", Context.MODE_PRIVATE)
    private fun textExists(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun purposeThenSpaceThenEquipmentThenChangeThePurposeFromMy() {
        val probe = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
        val noSpaces = try { runBlocking { probe.spaceDao().spaces().isEmpty() } } finally { probe.close() }
        assumeTrue("real spaces exist on this device", noSpaces)
        prefs().edit().clear().commit()
        try {
            val launch = Intent(context, ComposeActivity::class.java).putExtra("move60_force_first_run", true)
            ActivityScenario.launch<ComposeActivity>(launch).use {
                // Figma 01: nothing is preselected, so the button is disabled until one purpose is chosen.
                rule.waitUntil(15_000) { rule.onAllNodesWithTag("setup_purpose").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("tab_bar").assertDoesNotExist()
                rule.onNodeWithTag("setup_purpose_continue").assertIsNotEnabled()
                capture("compose-firstrun-purpose-empty.png")
                rule.onNodeWithTag("purpose_STRENGTH").performClick()
                rule.onNodeWithTag("setup_purpose_continue").assertIsEnabled()
                capture("compose-firstrun-purpose.png")
                rule.onNodeWithTag("setup_purpose_continue").performClick()

                // Figma 02: a space is required; the gym path asks for its name.
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("setup_space").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("tab_bar").assertDoesNotExist()
                capture("compose-firstrun-space.png")
                rule.onNodeWithTag("setup_gym").performClick()
                rule.onNodeWithTag("my_add_gym_name").performTextInput(gymName)
                rule.onNodeWithTag("my_add_gym_confirm").performClick()

                // The new default space's equipment list opens on top of the normal start.
                rule.waitUntil(15_000) { rule.onAllNodesWithTag("my_equipment").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("my_eq_LEG_PRESS").assertIsDisplayed()
                capture("compose-firstrun-equipment.png")
                rule.onNodeWithTag("my_back").performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("tab_bar").fetchSemanticsNodes().isNotEmpty() }

                // 마이: the saved purpose is shown and can be changed.
                rule.onNodeWithTag("tab_settings").performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("my_purpose_open").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("my_purpose_open").performScrollTo().assertIsDisplayed()
                rule.waitUntil(10_000) { textExists("근력 향상") }
                rule.onNodeWithTag("my_purpose_open").performScrollTo().performClick()
                rule.onNodeWithTag("my_purpose").assertIsDisplayed()
                rule.onNodeWithTag("purpose_HABIT").performClick()
                capture("compose-my-purpose.png")
                rule.onNodeWithTag("my_purpose_save").performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("my_home").fetchSemanticsNodes().isNotEmpty() }
                rule.waitUntil(10_000) { textExists("운동 습관") }
            }
            val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
            try {
                val spaces = runBlocking { db.spaceDao().spaces() }
                assertEquals(listOf(gymName to true), spaces.map { it.name to it.isDefault })
            } finally { db.close() }
            assertEquals("HABIT", prefs().getString("purpose", null))
            assertEquals(true, prefs().getBoolean("done", false))
        } finally {
            val clean = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
            try {
                val raw = clean.openHelper.writableDatabase
                raw.execSQL("DELETE FROM space_equipment WHERE spaceId IN (SELECT spaceId FROM workout_spaces WHERE name = '$gymName')")
                raw.execSQL("DELETE FROM workout_spaces WHERE name = '$gymName'")
            } finally { clean.close() }
            // Back to the state the runner set up for the other tests.
            prefs().edit().clear().putBoolean("done", true).commit()
        }
    }
}
