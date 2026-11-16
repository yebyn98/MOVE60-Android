package com.move60.workout.architecture

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

                rule.onNodeWithTag("my_spaces_open").performScrollTo().performClick()
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

    private fun cleanE2e(includeHome: Boolean) {
        val clean = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
        try {
            val raw = clean.openHelper.writableDatabase
            val where = if (includeHome) "name LIKE 'e2e %' OR spaceId = 'home'" else "name LIKE 'e2e %'"
            raw.execSQL("DELETE FROM space_equipment WHERE spaceId IN (SELECT spaceId FROM workout_spaces WHERE $where)")
            raw.execSQL("DELETE FROM workout_spaces WHERE $where")
        } finally { clean.close() }
    }

    /** Home registration + the cardio model dialog (Figma 73). Skipped when a real "home" space already exists, so user data is never touched. */
    @Test fun homeSpaceWithCardioModelIsSavedAndReadBack() {
        val probe = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
        val homeExists = try { runBlocking { probe.spaceDao().space("home") != null } } finally { probe.close() }
        org.junit.Assume.assumeFalse("a real home space exists on this device", homeExists)
        try {
            ActivityScenario.launch(ComposeActivity::class.java).use {
                rule.onNodeWithTag("tab_settings").performClick()
                rule.onNodeWithTag("my_spaces_open").performScrollTo().performClick()
                rule.onNodeWithTag("my_add_home").performScrollTo().performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("my_default_space").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("my_default_space").performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("my_eq_DUMBBELL").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("my_eq_INDOOR_CYCLE").performClick()
                rule.onNodeWithTag("my_eq_DUMBBELL").performClick()
                capture("compose-my-home-equipment.png")
                rule.onNodeWithTag("my_model_open").performClick()
                rule.onNodeWithTag("my_model_dialog").assertIsDisplayed()
                rule.onNodeWithTag("my_brand_INDOOR_CYCLE").performTextInput("테스트브랜드")
                rule.onNodeWithTag("my_model_INDOOR_CYCLE").performTextInput("M1")
                captureScreen("compose-my-model-dialog.png")
                rule.onNodeWithTag("my_model_done").performClick()
                rule.onNodeWithTag("my_equipment_save").performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("my_spaces").fetchSemanticsNodes().isNotEmpty() }
                rule.waitUntil(10_000) { textExists("등록된 기구 2종") }
                capture("compose-my-home-saved.png")
            }
            val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
            try {
                val rows = runBlocking { db.spaceDao().equipment("home") }
                assertEquals(listOf("DUMBBELL", "INDOOR_CYCLE"), rows.map { it.equipmentKey })
                val cycle = rows.first { it.equipmentKey == "INDOOR_CYCLE" }
                assertEquals("테스트브랜드" to "M1", cycle.brand to cycle.model)
                assertEquals(null to null, rows.first { it.equipmentKey == "DUMBBELL" }.let { it.brand to it.model })
            } finally { db.close() }
        } finally { cleanE2e(includeHome = true) }
    }

    /** Changing the default space and the duplicate-name message (Figma 61). */
    @Test fun defaultSpaceCanBeChangedAndADuplicateNameIsRejectedWithAMessage() {
        val seed = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
        try {
            val onlyOurs = runBlocking { seed.spaceDao().spaces().isEmpty() }
            org.junit.Assume.assumeTrue("real spaces exist on this device", onlyOurs)
            runBlocking {
                SpaceService(seed).create("e2e-a", SpaceKind.GYM, "e2e 가")
                SpaceService(seed).create("e2e-b", SpaceKind.GYM, "e2e 나")
            }
        } finally { seed.close() }
        try {
            ActivityScenario.launch(ComposeActivity::class.java).use {
                rule.onNodeWithTag("tab_settings").performClick()
                rule.onNodeWithTag("my_spaces_open").performScrollTo().performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("my_set_default_e2e-b").fetchSemanticsNodes().isNotEmpty() }
                capture("compose-my-spaces-two.png")
                rule.onNodeWithTag("my_set_default_e2e-b").performScrollTo().performClick()
                // The old default now offers "set as default"; the new default no longer does.
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("my_set_default_e2e-a").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("my_set_default_e2e-b").assertDoesNotExist()
                capture("compose-my-default-changed.png")

                rule.onNodeWithTag("my_add_gym").performScrollTo().performClick()
                rule.onNodeWithTag("my_add_gym_name").performTextInput("e2e 가")
                rule.onNodeWithTag("my_add_gym_confirm").performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("my_spaces_message").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("같은 이름의 운동 공간이 이미 있어요.").assertIsDisplayed()
                capture("compose-my-duplicate-name.png")
            }
            val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
            try {
                val spaces = runBlocking { db.spaceDao().spaces() }
                assertEquals(listOf("e2e-b" to true, "e2e-a" to false), spaces.map { it.spaceId to it.isDefault })
                assertEquals("the rejected duplicate wrote nothing", 2, spaces.size)
            } finally { db.close() }
        } finally { cleanE2e(includeHome = false) }
    }

    /** Figma 10 trend/stats/milestones and Figma 68 report from a REAL stored session (yesterday 20:35, 720 s, one completed set). */
    @Test fun hubTrendAndReportShowTheStoredSession() {
        val zone = java.time.ZoneId.of("Asia/Seoul")
        val id = "record-my-a"
        val date = java.time.LocalDate.now(zone).minusDays(1)
        val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
        try {
            runBlocking {
                val start = date.atTime(20, 35).atZone(zone).toEpochSecond()
                val session = SessionEntity(id, null, zone.id, start * 1000, (start + 720) * 1000,
                    SessionCompletion.COMPLETED.name, 720, 1, false, null, 0L)
                SessionCommitter(db).commit(SessionBundle(session, listOf(SessionActiveIntervalEntity(id, 0, start, start + 720)),
                    listOf(SessionSetEntity(id, "legPress", 1, SetState.COMPLETED.name, "40", "12", null))))
            }
            db.close()
            ActivityScenario.launch(ComposeActivity::class.java).use {
                rule.onNodeWithTag("tab_settings").performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("my_trend").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("my_trend").performScrollTo().assertIsDisplayed()
                rule.onNodeWithTag("my_trend_minutes").performClick()
                rule.onNodeWithTag("my_stat_sessions").performScrollTo().assertIsDisplayed()
                rule.onNodeWithTag("my_stat_challenges").assertIsDisplayed()
                rule.onNodeWithTag("my_milestone_first").performScrollTo().assertIsDisplayed()
                capture("compose-my-home-full.png")

                rule.onNodeWithTag("my_report_open").performScrollTo().performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("my_report_latest").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("my_report_chart").assertIsDisplayed()
                rule.onNodeWithTag("my_report_latest").assertIsDisplayed()
                rule.onNodeWithText("12분 00초 · 운동 기록 상세 보기").assertIsDisplayed()
                rule.onNodeWithTag("my_report_composition").performScrollTo().assertIsDisplayed()
                rule.onNodeWithTag("my_report_ex_legPress").performScrollTo().assertIsDisplayed()
                rule.onNodeWithText("40kg × 12회 (1세트)").assertIsDisplayed()
                capture("compose-my-report.png")
                // The latest-session card opens the real session detail.
                rule.onNodeWithTag("my_report_latest").performScrollTo().performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("record_exercise_sets_legPress").fetchSemanticsNodes().isNotEmpty() }
            }
        } finally {
            if (db.isOpen) db.close()
            val clean = Room.databaseBuilder(context, WorkoutDatabase::class.java, dbFile).build()
            try {
                val raw = clean.openHelper.writableDatabase
                for (table in listOf("session_adjustments", "session_sets", "session_active_intervals", "session_day_slices", "session_segments"))
                    raw.execSQL("DELETE FROM `$table` WHERE sessionId LIKE 'record-my-%'")
                raw.execSQL("DELETE FROM sessions WHERE sessionId LIKE 'record-my-%'")
            } finally { clean.close() }
        }
    }
}
