package com.move60.workout

import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class PhoneFeaturesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val today get() = LocalDate.now(ZoneId.of("Asia/Seoul"))
    @Before fun isolateEmulatorData() {
        context.stopService(Intent(context, WorkoutService::class.java))
        instrumentation.waitForIdleSync()
        for (name in listOf("move60_native_sessions_v8", "timer", "move60_set_records_v1", "move60_energy_v1", "move60_workout_history_v1", "preferences", "move60_duration_v1"))
            context.getSharedPreferences(name, 0).edit().clear().commit()
    }
    private fun screenshot(name: String) {
        Thread.sleep(1_000)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.getExternalFilesDir(null), name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun settingsAreAbsentFromHomeAndOpenInInternalScreen() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("구간 전환 알림음")).check(doesNotExist())
            onView(withText("오늘 컨디션에 맞춘 루틴")).check(doesNotExist())
            onView(withText("오류 기록 확인 · 복사 · 삭제")).check(doesNotExist())
            screenshot("phone-v27-home.png")
            onView(withTagValue(`is`("phone_settings"))).perform(click())
            onView(withText("구간 전환 알림음")).check(matches(isDisplayed()))
            onView(withText("업데이트 확인")).check(matches(isDisplayed()))
            screenshot("phone-settings.png")
            pressBack()
            onView(withTagValue(`is`("phone_settings"))).check(matches(isDisplayed()))
        }
    }
    @Test fun lastStageHasEnabledFinishAction() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            repeat(3) { scenario.onActivity { activity -> activity.window.decorView.findViewWithTag<TextView>("phone_next").performClick() } }
            scenario.onActivity { activity ->
                val next = activity.window.decorView.findViewWithTag<TextView>("phone_next")
                assertEquals("운동 종료", next.text.toString())
                assertTrue(next.isEnabled)
                next.performClick()
            }
            scenario.onActivity { activity -> assertFalse(activity.window.decorView.findViewWithTag<TextView>("phone_next").isEnabled) }
        }
    }
    @Test fun inlinePerSetFieldsSurviveTimerTicksAndPersist() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                repeat(2) { activity.window.decorView.findViewWithTag<TextView>("phone_next").performClick() }
                activity.window.decorView.findViewWithTag<TextView>("phone_play").performClick()
            }
            Thread.sleep(700)
            lateinit var input: EditText
            val id = WorkoutData(context).routine(WorkoutData(context).todayDay()).stages[2].tasks.first().id
            scenario.onActivity { activity ->
                input = activity.window.decorView.findViewWithTag("set-$id-1-kg")
                input.requestFocus()
                input.setText("25")
                activity.window.decorView.findViewWithTag<EditText>("set-$id-1-reps").setText("12")
                activity.window.decorView.findViewWithTag<EditText>("set-$id-1-rpe").setText("6")
                activity.window.decorView.findViewWithTag<CheckBox>("set-$id-1-check").isChecked = true
            }
            Thread.sleep(2_100)
            scenario.onActivity { activity ->
                assertSame("Timer rebuilt the editable field", input, activity.window.decorView.findViewWithTag("set-$id-1-kg"))
                assertEquals("25", input.text.toString())
            }
            val saved = SetRecordStore(context).read(WorkoutData(context).todayDay(), id, 1, token = SessionStore(context, WorkoutData(context)).sessionStartedAt)
            assertEquals("25", saved.kg)
            assertEquals("12", saved.reps)
            assertTrue(saved.completed)
            scenario.recreate()
            scenario.onActivity { activity ->
                val restored = activity.window.decorView.findViewWithTag<EditText>("set-$id-1-kg")
                assertEquals("25", restored.text.toString())
                restored.requestRectangleOnScreen(android.graphics.Rect(0, 0, restored.width, restored.height), true)
                (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(restored.windowToken, 0)
            }
            screenshot("phone-inline-sets.png")
        }
    }
    @Test fun calendarMarksTrainedDatesWithDots() {
        WorkoutHistory(context).addActiveSeconds(today, 90)
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withTagValue(`is`("phone_calendar"))).perform(click())
            onView(withTagValue(`is`("calendar-dot-$today"))).check(matches(isDisplayed()))
            screenshot("phone-calendar.png")
            pressBack()
        }
    }
    @Test fun stairClimbingIsCappedAndEveryCombinationRemainsSixtyMinutes() {
        var found = false
        for (condition in listOf("보통", "피곤")) {
            context.getSharedPreferences("preferences", 0).edit().putString("condition", condition).commit()
            val data = WorkoutData(context)
            for (minutes in listOf(20,25,30,35,40)) {
                data.setCardioMinutes(minutes)
                for (week in 0L..10L) for (day in 1..5) {
                    val routine = data.routine(day, week)
                    assertEquals(60, routine.stages.sumOf { it.minutes })
                    assertEquals(minutes*60, routine.stages[1].tasks.sumOf { it.seconds })
                    val stairs = routine.stages[1].tasks.filter { it.kind == "스텝밀" }.sumOf { it.seconds }
                    if (stairs > 0) {
                        found = true
                        assertTrue(stairs <= (if (condition == "피곤") 10 else 15)*60)
                        assertTrue(routine.stages[1].tasks.any { it.kind == "실내 사이클" })
                    }
                }
            }
        }
        assertTrue(found)
    }
    @Test fun dailyEnergyIsIdempotentAndRecalculatesCompletedSetInputs() {
        val ledger = EnergyLedger(context)
        val start = today.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
        val snapshot = JSONObject().put("stage", 2).put("running", true)
        ledger.add(today, start, start+600_000L, snapshot)
        val initial = ledger.estimate(today)
        assertTrue(initial.isFinite() && initial > 0)
        ledger.add(today, start, start+600_000L, snapshot)
        assertEquals(initial, ledger.estimate(today), 0.000001)
        val sets = SetRecordStore(context)
        sets.write(1, "test", 1, SetRecordStore.Record("20", "10", "5", true), token=123L)
        val low = ledger.estimate(today)
        sets.write(1, "test", 1, SetRecordStore.Record("20", "20", "8", true), token=123L)
        assertTrue(ledger.estimate(today) > low)
        val beforeLoadEdit = ledger.estimate(today)
        sets.write(1, "test", 1, SetRecordStore.Record("30", "20", "8", true), token=123L)
        assertEquals(beforeLoadEdit, ledger.estimate(today), 0.000001)
        assertEquals(600.0, sets.completed(today).sumOf { it.volume }, 0.000001)
    }
    @Test fun setRecordsKeepDifferentWorkoutSessionsAndDailyTotals() {
        val store = SetRecordStore(context)
        store.write(1, "test", 1, SetRecordStore.Record("20", "10", "5", true), token=100L)
        store.beginSession(1, 200L)
        assertFalse(store.read(1, "test", 1, token=200L).completed)
        store.write(1, "test", 1, SetRecordStore.Record("25", "10", "5", true), token=200L)
        assertEquals(2, store.completed(today).size)
        assertEquals(450.0, store.completed(today).sumOf { it.volume }, 0.000001)
        assertTrue(SetRecordStore(context).read(1, "test", 1, token=100L).completed)
    }
}

