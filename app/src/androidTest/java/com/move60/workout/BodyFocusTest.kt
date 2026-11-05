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
    @Test fun homeSelectionChangesExercisesAndOnlyStrengthHasDetails() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val upper = root.findViewWithTag<TextView>("body_focus_upper")
                val lower = root.findViewWithTag<TextView>("body_focus_lower")
                val minTouchHeight = (48 * activity.resources.displayMetrics.density).toInt()
                assertTrue("Upper selector must have an accessible touch target", upper.layoutParams.height >= minTouchHeight)
                assertTrue("Lower selector must have an accessible touch target", lower.layoutParams.height >= minTouchHeight)
                assertEquals("상체", upper.text.toString())
                assertEquals("하체", lower.text.toString())
                root.findViewWithTag<TextView>("body_focus_upper").performClick()
                assertTrue(root.findViewWithTag<TextView>("body_focus_upper").isSelected)
                root.findViewWithTag<TextView>("body_focus_lower").performClick()
                assertTrue(root.findViewWithTag<TextView>("body_focus_lower").isSelected)
                val store = SessionStore(context,WorkoutData(context))
                assertEquals("lower",store.bodyFocus)
                val details = descendants(root).filterIsInstance<TextView>().filter { it.text.toString() == "상세 ›" }
                assertEquals(store.routine.stages[2].tasks.size,details.size)
                assertTrue(details.all { it.tag.toString().startsWith("strength_detail_") })
                repeat(2) { root.findViewWithTag<TextView>("phone_next").performClick() }
                assertTrue(root.findViewWithTag<TextView>("strength_detail_"+store.routine.stages[2].tasks.first().id).isShown)
            }
            Thread.sleep(800)
            val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(context.getExternalFilesDir(null),"phone-v28-body-focus.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
            scenario.recreate()
            scenario.onActivity { activity -> assertTrue(activity.window.decorView.findViewWithTag<TextView>("body_focus_lower").isSelected) }
        }
    }
}
