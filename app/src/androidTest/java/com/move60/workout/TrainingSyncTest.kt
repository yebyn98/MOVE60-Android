package com.move60.workout

import android.content.Intent
import android.graphics.Bitmap
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.io.File

@RunWith(AndroidJUnit4::class)
class TrainingSyncTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val today get() = LocalDate.now(ZoneId.of("Asia/Seoul"))
    @Before fun isolate() {
        context.stopService(Intent(context,WorkoutService::class.java))
        instrumentation.waitForIdleSync()
        listOf("move60_native_sessions_v8","timer","move60_set_records_v1","move60_energy_v1",
            "move60_workout_history_v1","preferences","move60_duration_v1","watch_commands_v26",
            "watch_publish_v26","rest_alerts_v26").forEach { context.getSharedPreferences(it,0).edit().clear().commit() }
    }
    private fun request(store: SessionStore, op: String) = JSONObject().put("id",UUID.randomUUID().toString())
        .put("op",op).put("at",System.currentTimeMillis()).put("session",store.sessionStartedAt)
        .put("day",store.day).put("version",store.controlVersion).put("stage",store.stage)
    private fun training(): SessionStore {
        val store = SessionStore(context,WorkoutData(context))
        store.setDay(4)
        store.jumpTo(2)
        store.start()
        SetRecordStore(context).beginSession(store.day,store.sessionStartedAt)
        return store
    }
    private fun capture(name: String) {
        Thread.sleep(700)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.getExternalFilesDir(null),name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun restSurvivesPauseRestartAndIsClearedByStageAndFinish() {
        var store = training()
        store.startRest(60)
        assertTrue(store.restSeconds() in 59..60)
        store.pause()
        val paused = store.restSeconds()
        Thread.sleep(1100)
        store = SessionStore(context,WorkoutData(context))
        assertEquals(paused,store.restSeconds())
        store.adjustRest(15)
        assertEquals(paused+15,store.restSeconds())
        store.start()
        assertTrue(store.restDeadline > System.currentTimeMillis())
        store.jumpTo(3)
        assertEquals(0,store.restSeconds())
        store.nextStage()
        assertTrue(SessionStore(context,WorkoutData(context)).finished)
    }
    @Test fun previousRecordsExcludeDraftsCurrentSessionAndFutureDates() {
        val records = SetRecordStore(context)
        records.write(1,"test",1,SetRecordStore.Record("10","10","5",true),today.minusDays(1),100L)
        records.write(4,"test",1,SetRecordStore.Record("20","12","6",true),today,200L)
        records.write(4,"test",1,SetRecordStore.Record("30","15","7",true),today,300L)
        records.write(4,"test",1,SetRecordStore.Record("99","99","9",true),today.plusDays(1),400L)
        records.write(4,"test",1,SetRecordStore.Record("88","88","8",true),today,0L)
        assertEquals("20",records.previous("test",1,300L)?.kg)
        assertNull(records.previous("missing",1,300L))
    }
    @Test fun duplicateExpiredAndOldSessionCommandsCannotChangeCurrentWorkout() {
        val store = training()
        val next = request(store,"next")
        assertTrue(WatchCommandProcessor.apply(context,next))
        assertFalse(WatchCommandProcessor.apply(context,next))
        assertEquals(3,SessionStore(context,WorkoutData(context)).stage)
        assertFalse(WatchCommandProcessor.apply(context,request(store,"next")))
        val current = SessionStore(context,WorkoutData(context))
        val expired = request(current,"next").put("at",System.currentTimeMillis()-20_000L)
        assertFalse(WatchCommandProcessor.apply(context,expired))
        val previous = request(current,"pause").put("session",current.sessionStartedAt-1)
        assertFalse(WatchCommandProcessor.apply(context,previous))
        assertTrue(SessionStore(context,WorkoutData(context)).running)
    }
    @Test fun watchCompletionUpdatesPhoneAndOutboundPacketIncludesRestAndNextSet() {
        val phone = training()
        val next = requireNotNull(TrainingProgress.next(phone))
        SetRecordStore(context).write(phone.day,next.task.id,next.set,SetRecordStore.Record("25","12","6"),token=phone.sessionStartedAt)
        val complete = request(phone,"complete").put("exercise",next.task.id).put("set",next.set)
        assertTrue(WatchCommandProcessor.apply(context,complete))
        assertFalse(WatchCommandProcessor.apply(context,complete))
        assertTrue(phone.reloadIfChanged())
        assertTrue(phone.checked.contains("strength-"+phone.day+"-"+next.task.id+"-"+next.set))
        assertTrue(phone.restSeconds() > 0)
        val packet = WatchStatePublisher.createRequest(context,phone.snapshot()).dataMap
        assertEquals(26,packet.getInt("protocol"))
        assertEquals(phone.sessionStartedAt,packet.getLong("session"))
        assertEquals(phone.restDeadline,packet.getLong("restDeadline"))
        assertEquals(2,packet.getInt("setNumber"))
        assertEquals(TrainingProgress.next(phone)?.task?.name,packet.getString("exerciseName"))
        assertTrue(packet.getInt("totalSets") > 0)
        assertEquals(1,packet.getInt("completedSets"))
        assertEquals(phone.elapsed(),packet.getInt("activeSeconds"))
        assertTrue(packet.getDouble("estimatedKcal") >= 0.0)
        assertFalse(packet.getBoolean("canComplete"))
        val skip = request(phone,"rest_skip")
        assertTrue(WatchCommandProcessor.apply(context,skip))
        phone.reloadIfChanged()
        val stale = JSONObject(phone.snapshot()).put("controlVersion",0).put("stage",0)
        val recovered = WatchStatePublisher.createRequest(context,stale.toString()).dataMap
        assertEquals(2,recovered.getInt("stageIndex"))
        assertEquals(0L,recovered.getLong("restDeadline"))
        phone.startRest(90)
        val before = phone.restSeconds()
        val less = request(phone,"rest_sub")
        assertTrue(WatchCommandProcessor.apply(context,less))
        phone.reloadIfChanged()
        assertTrue(phone.restSeconds() <= before - 14)
        assertFalse(WatchCommandProcessor.apply(context,less))
    }
    @Test fun phoneCheckboxStartsRestAndImportsPreviousWithoutCompletingSets() {
        val data = WorkoutData(context)
        val task = data.routine(data.todayDay()).stages[2].tasks.first()
        SetRecordStore(context).write(data.todayDay(),task.id,1,SetRecordStore.Record("22","11","5",true),today.minusDays(1),123L)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<TextView>("phone_next").performClick()
                activity.window.decorView.findViewWithTag<TextView>("phone_next").performClick()
                activity.window.decorView.findViewWithTag<TextView>("phone_play").performClick()
                activity.window.decorView.findViewWithTag<TextView>("import-"+task.id).performClick()
                assertEquals("22",activity.window.decorView.findViewWithTag<EditText>("set-"+task.id+"-1-kg").text.toString())
                val check = activity.window.decorView.findViewWithTag<CheckBox>("set-"+task.id+"-1-check")
                assertFalse(check.isChecked)
                check.isChecked = true
                assertTrue(activity.window.decorView.findViewWithTag<LinearLayout>("phone_rest").isShown)
            }
            Thread.sleep(1200)
            scenario.onActivity { activity ->
                val store = SessionStore(context,data)
                assertTrue(store.restSeconds() > 0)
                activity.window.decorView.findViewWithTag<LinearLayout>("phone_rest").requestRectangleOnScreen(android.graphics.Rect(0,0,300,150),true)
            }
            capture("phone-v26-rest.png")
            scenario.onActivity { activity -> activity.window.decorView.findViewWithTag<TextView>("phone_play").performClick() }
            instrumentation.waitForIdleSync()
            Thread.sleep(300)
            assertEquals("Paused service checkpoint must be cleared",0L,context.getSharedPreferences("timer",0).getLong("history_checkpoint_ms",0L))
            val pausedSeconds = WorkoutHistory(context).record(today).activeSeconds
            Thread.sleep(1800)
            assertEquals("Paused time leaked into workout history",pausedSeconds,WorkoutHistory(context).record(today).activeSeconds)
            scenario.onActivity { activity -> activity.window.decorView.findViewWithTag<TextView>("phone_play").performClick() }
            scenario.onActivity { activity -> activity.window.decorView.findViewWithTag<TextView>("phone_rest_skip").performClick() }
            assertEquals(0,SessionStore(context,data).restSeconds())
        }
    }
}
