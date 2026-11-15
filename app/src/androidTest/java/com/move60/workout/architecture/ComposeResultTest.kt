package com.move60.workout.architecture

import android.content.Intent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real-screen evidence for Figma 08: a stored PARTIAL session and a session that was never stored (save failed). */
@RunWith(AndroidJUnit4::class)
class ComposeResultTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun launch(id: String) = ActivityScenario.launch<ComposeActivity>(
        Intent(context, ComposeActivity::class.java).putExtra("move60_open_result", id)
    )

    @Test fun storedPartialSessionShowsMeasuredResult() {
        val zone = ZoneId.systemDefault()
        val start = Instant.now().minusSeconds(1_200).epochSecond
        val date = Instant.ofEpochSecond(start).atZone(zone).toLocalDate()
        val id = "timer-$date-d5-${start * 1000}"
        val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, "move60_workouts_room_v1.db")
            .addMigrations(RoutiveMigrations.MIGRATION_1_2).build()
        runBlocking {
            val session = SessionEntity(id, null, zone.id, start * 1000, (start + 700) * 1000,
                SessionCompletion.PARTIAL.name, 700, 0, false, null, 0L)
            SessionCommitter(db).commit(SessionBundle(session, listOf(SessionActiveIntervalEntity(id, 0, start, start + 700))))
        }
        db.close()
        launch(id).use {
            rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("result_active_time")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("운동을 일부 마쳤어요").assertIsDisplayed()
            rule.onNodeWithTag("result_save_state").assertIsDisplayed()
            rule.onNodeWithText("11분 40초").assertIsDisplayed()
            capture("compose-result-partial.png")
        }
    }

    @Test fun unsavedSessionShowsSaveFailureWithRetryAndNoInventedValues() {
        launch("timer-2026-01-01-d1-1700000000000").use {
            rule.waitUntil(15_000) { rule.onAllNodes(hasTestTag("result_retry")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("결과를 저장하지 못했어요").assertIsDisplayed()
            rule.onNodeWithTag("result_retry").assertIsDisplayed()
            capture("compose-result-save-failed.png")
        }
    }
}
