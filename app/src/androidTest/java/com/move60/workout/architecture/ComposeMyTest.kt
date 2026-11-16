package com.move60.workout.architecture

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MY tab on a real emulator (Figma 10, 61, 62): nothing is seeded, a gym registered by hand shows up, its equipment is saved
 * and read back. Removes only the rows it created (`gym-e2e-*` names) afterwards.
 */
@RunWith(AndroidJUnit4::class)
class ComposeMyTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val dbFile = "move60_workouts_room_v1.db"
    private val gymName = "e2e 헬스장"

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun captureScreen(name: String) {
        rule.waitForIdle(); Thread.sleep(500)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.getExternalFilesDir(null), name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun registerAGymPickEquipmentAndSaveIt() {
        try {
            ActivityScenario.launch(ComposeActivity::class.java).use {
                rule.onNodeWithTag("tab_settings").performClick()
                rule.onNodeWithTag("my_home").assertIsDisplayed()
                rule.onNodeWithTag("my_cumulative_days").assertIsDisplayed()
                // The v1 settings stay reachable from the header shortcut.
                rule.onNodeWithTag("my_settings_open").assertIsDisplayed()
                capture("compose-my-home.png")

                rule.onNodeWithTag("my_spaces_open").performClick()
                rule.onNodeWithTag("my_spaces").assertIsDisplayed()
                rule.onNodeWithTag("my_add_gym").performScrollTo().performClick()
                rule.onNodeWithTag("my_add_gym_dialog").assertIsDisplayed()
                rule.onNodeWithTag("my_add_gym_name").performTextInput(gymName)
                captureScreen("compose-my-add-gym.png")
                rule.onNodeWithTag("my_add_gym_confirm").performClick()
                rule.waitUntil(10_000) { textExists(gymName) }
                capture("compose-my-spaces.png")

                rule.onAllNodesWithText(gymName, substring = true).onFirst().performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("my_eq_LEG_PRESS").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("my_eq_LEG_PRESS").performClick()
                rule.onNodeWithTag("my_eq_INDOOR_CYCLE").performClick()
                capture("compose-my-equipment.png")
                rule.onNodeWithTag("my_equipment_save").performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("my_spaces").fetchSemanticsNodes().isNotEmpty() }
                rule.waitUntil(10_000) { textExists("등록된 기구 2종") }
            }
            val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
            try {
                val saved = runBlocking {
                    val space = db.spaceDao().spaces().first { it.name == gymName }
                    db.spaceDao().equipment(space.spaceId).map { it.equipmentKey }
                }
                assertEquals(listOf("INDOOR_CYCLE", "LEG_PRESS"), saved)
            } finally { db.close() }
        } finally {
            val clean = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
            try {
                val raw = clean.openHelper.writableDatabase
                raw.execSQL("DELETE FROM space_equipment WHERE spaceId IN (SELECT spaceId FROM workout_spaces WHERE name = '$gymName')")
                raw.execSQL("DELETE FROM workout_spaces WHERE name = '$gymName'")
            } finally { clean.close() }
        }
    }

    private fun textExists(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
}
