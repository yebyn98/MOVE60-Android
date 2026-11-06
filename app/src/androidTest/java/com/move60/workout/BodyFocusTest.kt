package com.move60.workout

import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.io.File

@RunWith(AndroidJUnit4::class)
class BodyFocusTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @Before fun isolate() {
        context.stopService(Intent(context,WorkoutService::class.java))
        instrumentation.waitForIdleSync()
        listOf("move60_native_sessions_v8","timer","move60_set_records_v1","move60_energy_v1","move60_session_energy_v1",
            "move60_workout_history_v1","preferences","move60_duration_v1","move60_exercise_preferences_v1").forEach {
            context.getSharedPreferences(it,0).edit().clear().commit()
        }
    }
    @Test fun generatedSplitsAndAlternativesStayWithinSelectedBodyArea() {
        val data = WorkoutData(context)
        for (focus in listOf("upper","lower")) for (day in 1..5) for (week in 0L..10L) {
            val routine = data.routine(day,week,focus)
            assertEquals(60,routine.stages.sumOf { it.minutes })
            val tasks = routine.stages[2].tasks
            assertEquals(4,tasks.size)
            assertEquals(4,tasks.map { it.id }.distinct().size)
            tasks.forEachIndexed { slot,task ->
                val ids = data.alternatives(day,slot,focus).map { it.first }
                assertTrue(task.id in ids)
                ids.forEach { id ->
                    val area = requireNotNull(data.detail(id)).area
                    assertTrue(area, if (focus == "upper") area.startsWith("상체") else area.startsWith("하체") || area.startsWith("둔근"))
                }
            }
        }
        assertFalse(data.replaceExercise(1,0,"latPulldown","lower"))
        assertFalse(data.replaceExercise(1,0,"legPress","upper"))
    }
    @Test fun selectionSurvivesRestartAndIsIsolatedByActualDate() {
        val data = WorkoutData(context)
        var date = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val first = SessionStore(context,data) { date }
        assertTrue(first.selectBodyFocus("upper"))
        val ids = first.routine.stages[2].tasks.map { it.id }
        val restored = SessionStore(context,data) { date }
        assertEquals("upper",restored.bodyFocus)
        assertEquals(ids,restored.routine.stages[2].tasks.map { it.id })
        date = date.plusDays(1)
        assertTrue(restored.reloadIfChanged())
        assertNull(restored.bodyFocus)
        assertFalse(restored.finished)
        assertTrue(restored.selectBodyFocus("lower"))
        assertEquals("lower",SessionStore(context,data) { date }.bodyFocus)
    }
    @Test fun activeAndPausedSessionsRejectChangesAndWatchReceivesNewSplit() {
        val data = WorkoutData(context)
        val store = SessionStore(context,data)
        assertTrue(store.selectBodyFocus("upper"))
        store.jumpTo(2)
        store.start()
        val token = store.sessionStartedAt
        val task = store.routine.stages[2].tasks.first()
        SetRecordStore(context).write(store.day,task.id,1,SetRecordStore.Record("20","12","5",true),token=token)
        assertFalse(store.selectBodyFocus("lower"))
        store.pause()
        assertFalse(store.selectBodyFocus("lower"))
        assertEquals(token,store.sessionStartedAt)
        val oldPacket = store.snapshot()
        store.jumpTo(3)
        store.nextStage()
        assertTrue(store.selectBodyFocus("lower"))
        assertTrue(SetRecordStore(context).read(store.day,task.id,1,token=token).completed)
        store.jumpTo(2)
        val packet = WatchStatePublisher.createRequest(context,oldPacket).dataMap
        assertEquals("하체 근력",packet.getString("stageName"))
        assertTrue(store.routine.stages[2].tasks.any { it.id == packet.getString("exerciseId") })
        assertEquals(store.controlVersion,packet.getLong("controlVersion"))
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    @Test fun detailScreenFollowsOverviewSelectionAndOnlyStrengthHasSetEntry() {
        val data = WorkoutData(context)
        assertTrue(SessionStore(context,data).selectBodyFocus("lower"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                // Selection lives on the Compose overview; the detail screen must not duplicate it.
                assertNull(root.findViewWithTag<View>("body_focus_upper"))
                assertNull(root.findViewWithTag<View>("body_focus_lower"))
                assertTrue(root.findViewWithTag<TextView>("phone_day_info").text.toString().endsWith("하체"))
                val store = SessionStore(context,data)
                // The finalized Figma has four collapsed steps before workout starts.
                assertNotNull(root.findViewWithTag<View>("phone_stage_stack"))
                for (step in 0..3) assertNotNull(root.findViewWithTag<View>("phone_step_$step"))
                root.findViewWithTag<TextView>("phone_play").performClick()
                repeat(2) { root.findViewWithTag<TextView>("phone_next").performClick() }
                assertTrue(root.findViewWithTag<View>("phone_current_exercise").isShown)
                assertNotNull(root.findViewWithTag<View>("set-"+store.routine.stages[2].tasks.first().id+"-1-kg"))
            }
            Thread.sleep(800)
            val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(context.getExternalFilesDir(null),"phone-v28-body-focus.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
            // Open the per-exercise popup (set entry + guide + alternatives) and capture it.
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<View>("phone_current_guide").performClick()
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(800)
            val popup = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(context.getExternalFilesDir(null),"phone-exercise-popup.png").outputStream().use { popup.compress(Bitmap.CompressFormat.PNG,100,it) }
            // The last block of the popup must be reachable and fully visible above the dialog buttons.
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(org.hamcrest.CoreMatchers.startsWith("지난 기록")))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .perform(androidx.test.espresso.action.ViewActions.scrollTo())
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed()))
            Thread.sleep(500)
            val bottom = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(context.getExternalFilesDir(null),"phone-exercise-popup-bottom.png").outputStream().use { bottom.compress(Bitmap.CompressFormat.PNG,100,it) }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(activity.window.decorView.findViewWithTag<TextView>("phone_day_info").text.toString().endsWith("하체"))
            }
        }
    }
}
