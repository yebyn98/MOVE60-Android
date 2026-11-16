package com.move60.workout.architecture

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.move60.workout.domain.StageKind
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real-screen evidence for the saved routine list: stored values are shown exactly, unknown times as "미정". */
@RunWith(AndroidJUnit4::class)
class ComposeRoutineListTest {
    @get:Rule val rule = createAndroidComposeRule<ComposeActivity>()

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        File(dir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun waitTag(tag: String) = rule.waitUntil(8_000) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    @Test fun savedRoutineAppearsInTheListWithExactAndUnknownTimes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "list-test-${System.currentTimeMillis()}"
        val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, "move60_workouts_room_v1.db").build()
        try {
            val result = runBlocking {
                RoutinePlanService(db).save(id, "목록 확인 루틴", 3_600L, mapOf(
                    StageKind.WARM_UP to listOf(RoutineExerciseInput("walk", "걷기", 150L)),
                    StageKind.STRENGTH to listOf(RoutineExerciseInput("legPress", "레그 프레스", null)),
                    StageKind.CARDIO to listOf(RoutineExerciseInput("bike", "실내 자전거", 1_800L))))
            }
            org.junit.Assert.assertTrue("fixture routine must save: $result", result is RoutinePlanResult.Saved)
        } finally { db.close() }

        waitTag("dashboard_saved_routines")
        rule.onNodeWithTag("dashboard_saved_routines").performClick()
        waitTag("routine_list")
        waitTag("routine_item_$id")
        rule.onNodeWithTag("routine_item_$id").assertIsDisplayed()
        // Other tests may have saved routines too: only look inside this routine's card.
        fun inItem(text: String) = rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag("routine_item_$id")))
        inItem("목록 확인 루틴").assertIsDisplayed()
        inItem("목표 60분 · 운동 3개").assertIsDisplayed()
        inItem("2분 30초").assertIsDisplayed()                         // custom 2.5 minutes kept, not rounded
        inItem("30분").assertIsDisplayed()
        inItem("미정").assertIsDisplayed()                             // strength time unknown: never 0 or a default
        capture("compose-routine-list.png")
        rule.onNodeWithTag("routine_list_back").performClick()
        waitTag("dashboard_compose_routine")
    }
}
