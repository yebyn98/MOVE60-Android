package com.move60.workout

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class SessionDateTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("move60_native_sessions_v8",0)
    private val today get() = LocalDate.now(ZoneId.of("Asia/Seoul"))
    @Before fun isolate() {
        context.stopService(Intent(context,WorkoutService::class.java))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        prefs.edit().clear().commit()
    }
    @Test fun yesterdayCompletedRoutineDoesNotBecomeTodaysCompletion() {
        var date = today.minusDays(1)
        val data = WorkoutData(context)
        val store = SessionStore(context,data) { date }
        store.setDay(5)
        store.jumpTo(3)
        store.nextStage()
        date = today
        val fresh = SessionStore(context,data) { date }
        assertFalse(fresh.finished)
        assertEquals(0,fresh.stage)
        assertEquals(today.dayOfWeek.value.coerceIn(1,5),fresh.day)
        fresh.reloadIfChanged()
        assertEquals(today.dayOfWeek.value.coerceIn(1,5),fresh.day)
        assertTrue(store.reloadIfChanged())
        assertFalse(store.finished)
        assertEquals(0,store.elapsed())
        assertTrue(prefs.getString("daily_"+today.minusDays(1)+"_5","")!!.contains("\"finished\":true"))
    }
    @Test fun sameDateCompletionAndSelectedRoutineSurviveRestart() {
        val data = WorkoutData(context)
        val first = SessionStore(context,data)
        first.setDay(4)
        first.jumpTo(3)
        first.start()
        first.reconcile(System.currentTimeMillis()+1500L)
        first.nextStage()
        val restored = SessionStore(context,data)
        assertEquals(4,restored.day)
        assertTrue(restored.finished)
        assertTrue(restored.elapsed() > 0)
        assertEquals(first.sessionStartedAt,restored.sessionStartedAt)
    }
    @Test fun legacyPreviewAndPreviousDateAreNotImportedAsCompletedToday() {
        val data = WorkoutData(context)
        val day = data.todayDay()
        val key = "move60_"+data.weekKey()+"_"+day
        val legacy = JSONObject().put("stage",3).put("finished",true)
        prefs.edit().putString(key,legacy.toString()).putInt("active_day",5).commit()
        assertFalse(SessionStore(context,data).finished)
        legacy.put("sessionStartedAt",today.minusDays(1).atTime(12,0).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli())
        prefs.edit().putString(key,legacy.toString()).commit()
        assertFalse(SessionStore(context,data).finished)
    }
    @Test fun legacyActualWorkoutTodayIsPreserved() {
        val data = WorkoutData(context)
        val legacy = JSONObject().put("stage",3).put("finished",true).put("activeSeconds",600)
            .put("sessionStartedAt",today.atTime(0,1).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli())
        prefs.edit().putString("move60_"+data.weekKey()+"_"+data.todayDay(),legacy.toString()).commit()
        val restored = SessionStore(context,data)
        assertTrue(restored.finished)
        assertEquals(600,restored.elapsed())
    }
    @Test fun activeWorkoutContinuesAcrossMidnightAndStalePacketIsReplaced() {
        val data = WorkoutData(context)
        var date = today.minusDays(1)
        val store = SessionStore(context,data) { date }
        store.start()
        date = today
        assertFalse(store.reloadIfChanged())
        val restored = SessionStore(context,data) { date }
        assertTrue(restored.running)
        assertEquals(today.minusDays(1),restored.recordDate)
        val old = restored.snapshot()
        restored.jumpTo(3)
        restored.nextStage()
        val fresh = SessionStore(context,data)
        assertFalse(fresh.finished)
        val packet = WatchStatePublisher.createRequest(context,old).dataMap
        assertFalse(packet.getBoolean("finished"))
        assertFalse(packet.getBoolean("running"))
        assertEquals(0,packet.getInt("stageIndex"))
    }
}
