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
import androidx.test.espresso.matcher.RootMatchers.isDialog
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
        for (name in listOf("move60_native_sessions_v8", "timer", "move60_set_records_v1", "move60_energy_v1", "move60_session_energy_v1", "move60_workout_history_v1", "preferences", "move60_duration_v1"))
            context.getSharedPreferences(name, 0).edit().clear().commit()
    }
    private fun screenshot(name: String) {
        Thread.sleep(1_000)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.getExternalFilesDir(null), name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun unifiedDashboardUsesTokensAndSimplifiedSettings() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                // New Figma: all four stages remain accessible in one accordion; the active
                // step embeds its controls, with time and progress in the fixed dock.
                val timerCard = root.findViewWithTag<LinearLayout>("phone_timer_card")
                assertNull("All stages should be collapsed before workout starts", timerCard)
                assertNotNull(root.findViewWithTag<View>("phone_dock_progress"))
                assertNull(root.findViewWithTag<View>("phone_dashboard_summary"))
                assertNotNull(root.findViewWithTag<View>("phone_stage_stack"))
                for (index in 0..4) {
                    assertNotNull("Missing workout stage $index", root.findViewWithTag<View>("phone_step_$index"))
                }
                assertNull(root.findViewWithTag<View>("phone_training_heading"))
                assertNotNull("Single workout stack must be visible", root.findViewWithTag<View>("phone_stage_stack"))
                assertEquals(View.GONE, root.findViewWithTag<View>("phone_prev").visibility)
                assertEquals(View.GONE, root.findViewWithTag<View>("phone_next").visibility)
                assertEquals(20, Move60Theme.Radius.card)
                assertNull(root.findViewWithTag<View>("set-absent-1-rpe"))
            }
            onView(withText("지난 기록 불러오기")).check(doesNotExist())
            onView(withText("RPE")).check(doesNotExist())
            // Settings moved to the Compose screen; the detail screen no longer has its own entry.
            onView(withTagValue(`is`("phone_settings"))).check(doesNotExist())
            onView(withText("오류 기록 확인 · 복사 · 삭제")).check(doesNotExist())
            screenshot("phone-v34-dashboard.png")
        }
    }

    @Test fun settingsAreAbsentFromDetailScreen() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("구간 전환 알림음")).check(doesNotExist())
            onView(withText("오늘 컨디션에 맞춘 루틴")).check(doesNotExist())
            onView(withText("오류 기록 확인 · 복사 · 삭제")).check(doesNotExist())
            screenshot("phone-v27-home.png")
            onView(withTagValue(`is`("phone_settings"))).check(doesNotExist())
        }
    }
    @Test fun detailHeaderHasBackButtonTitleAndCardioBookends() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val back = root.findViewWithTag<View>("detail_back")
                val title = root.findViewWithTag<TextView>("detail_title")
                assertNotNull(back); assertNotNull(title)
                assertEquals("오늘의 운동", title.text.toString())
                assertTrue("back button must be at least 48dp", back.height >= (48 * activity.resources.displayMetrics.density).toInt())
                listOf("phone_prev", "phone_play", "phone_next").forEach { tag ->
                    val b = root.findViewWithTag<TextView>(tag) ?: return@forEach
                    val bounds = (b as? com.google.android.material.button.MaterialButton)?.background?.bounds
                    if (b.visibility == View.VISIBLE)
                        assertTrue("$tag must be at least 48dp tall", b.height >= (48 * activity.resources.displayMetrics.density).toInt())
                    if (bounds != null) assertTrue("$tag drawable ${bounds.height()} clipped by view ${b.height}", bounds.height() <= b.height)
                    val parent = b.parent as View
                    assertTrue("$tag outside its row: top=${b.top} bottom=${b.bottom} row=${parent.height}", b.top >= 0 && b.bottom <= parent.height)
                    assertTrue("$tag text must not be truncated", b.layout == null || b.layout.lineCount <= 1)
                }
                assertNull("old MOVE60 app bar must be gone", root.findViewWithTag<View>("phone_app_bar"))
                val routine = SessionStore(context, WorkoutData(context)).routine
                assertEquals(listOf("준비운동", "근력운동"), listOf(routine.stages[0].name, routine.stages[2].name))
                assertTrue(routine.stages[1].tasks.all { it.kind != "마무리 스트레칭" })
                assertTrue(routine.stages[3].tasks.all { it.kind != "마무리 스트레칭" })
                assertTrue(routine.stages[4].tasks.isNotEmpty() && routine.stages[4].tasks.all { it.kind == "마무리 스트레칭" })
                assertEquals(5, routine.stages.size)
            }
            screenshot("phone-v40-detail-header.png")
            repeat(3) { scenario.onActivity { it.window.decorView.findViewWithTag<TextView>("phone_next").performClick() } }
            instrumentation.waitForIdleSync()
            screenshot("phone-v40-cardio-final.png")
        }
    }
    @Test fun cardioStageShowsPhaseRowsWithCurrentPhaseRpeAndNextPhase() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.window.decorView.findViewWithTag<TextView>("phone_play").performClick() }
            scenario.onActivity { it.window.decorView.findViewWithTag<TextView>("phone_next").performClick() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val row = root.findViewWithTag<View>("phone_phase_row_0")
                assertNotNull("cardio phase rows must be listed", row)
                assertTrue("phase rows keep a 48dp touch height", row.height >= (48 * activity.resources.displayMetrics.density).toInt() - 1)
                assertNotNull(root.findViewWithTag<View>("phone_phase_bar"))
                assertTrue(root.findViewWithTag<TextView>("phone_phase_time").text.toString().matches(Regex("\\d{2}:\\d{2}")))
                assertTrue(root.findViewWithTag<TextView>("phone_step_chip_1").text.toString().startsWith("유산소 ①"))
            }
            screenshot("phone-v53-cardio-phases.png")
        }
    }
    @Test fun accordionHeadersUseChevronIconsWithFullTouchHeight() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                (0..4).forEach { index ->
                    val chevron = root.findViewWithTag<View>("phone_step_chevron_$index")
                    assertTrue("step $index chevron must be an icon, not a text glyph", chevron is ImageView)
                    assertTrue("chevron touch area must stay at least 48dp", chevron.layoutParams.height >= (48 * activity.resources.displayMetrics.density).toInt())
                    assertEquals("펼치기", chevron.contentDescription.toString())
                }
            }
            scenario.onActivity { it.window.decorView.findViewWithTag<View>("phone_step_header_0").performClick() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals("접기", activity.window.decorView.findViewWithTag<View>("phone_step_chevron_0").contentDescription.toString())
            }
        }
    }
    private fun allText(root: View): String {
        val out = StringBuilder()
        fun walk(v: View) { if (v is TextView) out.append(v.text).append('\n'); if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
        walk(root); return out.toString()
    }
    @Test fun upcomingAndCompletedStagesShowStructuredReadOnlyRowsNotPlainText() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Before the workout every stage is upcoming: open a timed stage and the strength stage.
            scenario.onActivity { it.window.decorView.findViewWithTag<View>("phone_step_header_3").performClick() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val row = root.findViewWithTag<View>("phone_step_row_3_0")
                assertNotNull("upcoming timed stage must list structured rows", row)
                assertTrue("rows keep a 48dp touch height", row.height >= (48 * activity.resources.displayMetrics.density).toInt() - 1)
                assertTrue(root.findViewWithTag<TextView>("phone_step_row_meta_3_0").text.toString().matches(Regex("\\d{2}:\\d{2}")))
                assertFalse(allText(root).contains("내용을 확인할 수 있어요"))
            }
            scenario.onActivity { it.window.decorView.findViewWithTag<View>("phone_step_header_2").performClick() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(root.findViewWithTag<View>("phone_step_row_2_0"))
                assertTrue(root.findViewWithTag<TextView>("phone_step_row_meta_2_0").text.toString().contains("세트"))
            }
            screenshot("phone-v55-upcoming-strength-rows.png")
            // Completed stage: start, move to stage 1, then open the finished warm-up.
            scenario.onActivity { it.window.decorView.findViewWithTag<TextView>("phone_play").performClick() }
            scenario.onActivity { it.window.decorView.findViewWithTag<TextView>("phone_next").performClick() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { it.window.decorView.findViewWithTag<View>("phone_step_header_0").performClick() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val row = root.findViewWithTag<View>("phone_step_row_0_0")
                assertNotNull("completed stage must list structured rows", row)
                assertTrue(row.contentDescription.toString().endsWith("완료"))
                assertFalse(allText(root).contains("완료한 운동 ·"))
            }
            screenshot("phone-v55-completed-warmup-rows.png")
        }
    }
    @Test fun watchKcalMessageIsValidatedAndOnlyShownWhileFreshForThisSession() {
        val now = System.currentTimeMillis()
        assertNotNull(WatchKcal.parse("kcal:123:45.5:$now", now))
        assertNull("malformed", WatchKcal.parse("kcal:abc:45:$now", now))
        assertNull("negative", WatchKcal.parse("kcal:123:-1:$now", now))
        assertNull("implausible", WatchKcal.parse("kcal:123:99999:$now", now))
        assertNull("stale replay", WatchKcal.parse("kcal:123:45:${now - 60_000L}", now))
        assertNull("future", WatchKcal.parse("kcal:123:45:${now + 60_000L}", now))
        WatchKcal.save(context, WatchKcal.Sample(123L, 45.5, now))
        assertNotNull(WatchKcal.fresh(context, 123L, now + 1_000L))
        assertNull("other workout session", WatchKcal.fresh(context, 124L, now + 1_000L))
        assertNull("no session", WatchKcal.fresh(context, 0L, now + 1_000L))
        assertNull("expired sample must never look current", WatchKcal.fresh(context, 123L, now + WatchKcal.MAX_AGE_MS + 1_000L))
        WatchKcal.save(context, WatchKcal.Sample(123L, 10.0, now - 5_000L))
        assertEquals("older replay cannot overwrite", 45.5, WatchKcal.fresh(context, 123L, now + 1_000L)!!.totalKcal, 0.0001)
    }
    @Test fun dockHidesWatchKcalUntilAFreshSampleExistsForTheRunningSession() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.window.decorView.findViewWithTag<TextView>("phone_play").performClick() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals("no watch data: line stays hidden", View.GONE,
                    activity.window.decorView.findViewWithTag<View>("phone_watch_kcal").visibility)
            }
            val token = JSONObject(SessionStore(context, WorkoutData(context)).snapshot()).optLong("sessionStartedAt")
            assertTrue("running workout has a session token", token > 0L)
            // Test fixture (not a real watch measurement): a fresh sample for the running session.
            WatchKcal.save(context, WatchKcal.Sample(token, 123.0, System.currentTimeMillis()))
            Thread.sleep(2_500)
            scenario.onActivity { activity ->
                val line = activity.window.decorView.findViewWithTag<TextView>("phone_watch_kcal")
                assertEquals(View.VISIBLE, line.visibility)
                assertTrue(line.text.toString().contains("기초대사 포함"))
            }
            screenshot("phone-v57-watch-kcal-line-FIXTURE.png")
        }
    }
    @Test fun guideImagesExistForEveryLicensedExercise() {
        val files = context.assets.list("exercise")!!.toSet()
        val ids = files.filter { it.endsWith("_0.jpg") }.map { it.removeSuffix("_0.jpg") }
        assertEquals(29, ids.size)
        ids.forEach { assertTrue("missing second frame for $it", "${it}_1.jpg" in files) }
        assertTrue(ExerciseGuide.create(context, "legPress").tag == "exercise_guide_loop")
        assertTrue(ExerciseGuide.create(context, "birdDog") is ExerciseDiagram)
    }
    @Test fun lastStageHasEnabledFinishAction() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // The only pre-start action is Start; the final action now needs confirmation.
            scenario.onActivity { it.window.decorView.findViewWithTag<TextView>("phone_play").performClick() }
            repeat(4) { scenario.onActivity { activity -> activity.window.decorView.findViewWithTag<TextView>("phone_next").performClick() } }
            scenario.onActivity { activity ->
                val next = activity.window.decorView.findViewWithTag<TextView>("phone_next")
                assertEquals("운동 종료", next.text.toString())
                assertTrue(next.isEnabled)
                next.performClick()
            }
            try {
                // The confirmation lives in its own dialog window. CI evidence showed the dialog on screen while the
                // activity window still reported has-window-focus=false, so Espresso kept the activity as root and could
                // not find the button. Waiting for the dialog root removes that timing race.
                onView(withText("기록하고 종료")).inRoot(isDialog()).perform(click())
            } catch (e: Throwable) {
                // Diagnostic evidence for CI: what was actually on screen when the confirmation was not found.
                val shot = instrumentation.uiAutomation.takeScreenshot()
                if (shot != null) File(context.getExternalFilesDir(null), "failure-last-stage-confirm.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                File(context.getExternalFilesDir(null), "failure-last-stage-confirm.txt").writeText(
                    "windows=" + instrumentation.uiAutomation.windows.joinToString { it.toString() } + "\n" + e.toString())
                throw e
            }
            onView(withText("운동 결과")).check(matches(isDisplayed()))
            scenario.onActivity { activity -> assertFalse(activity.window.decorView.findViewWithTag<TextView>("phone_next").isEnabled) }
        }
    }
    @Test fun setStepperAdjustsKgAndRepsAndCompleteButtonIsLarge() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.decorView.findViewWithTag<TextView>("phone_play").performClick()
                repeat(2) { activity.window.decorView.findViewWithTag<TextView>("phone_next").performClick() }
            }
            val id = WorkoutData(context).routine(WorkoutData(context).todayDay()).stages[2].tasks.first().id
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val kg = root.findViewWithTag<EditText>("set-$id-1-kg")
                val reps = root.findViewWithTag<EditText>("set-$id-1-reps")
                kg.setText("20"); reps.setText("10")
                root.findViewWithTag<TextView>("set-$id-1-kg-plus").performClick()
                assertEquals("22.5", kg.text.toString())
                root.findViewWithTag<TextView>("set-$id-1-kg-minus").performClick()
                assertEquals("20", kg.text.toString())
                root.findViewWithTag<TextView>("set-$id-1-reps-plus").performClick()
                assertEquals("11", reps.text.toString())
                root.findViewWithTag<TextView>("set-$id-1-reps-minus").performClick()
                root.findViewWithTag<TextView>("set-$id-1-reps-minus").performClick()
                assertEquals("9", reps.text.toString())
                // One large tappable row (>= 48dp) completes the set.
                val check = root.findViewWithTag<CheckBox>("set-$id-1-check")
                assertTrue(check.height >= (48 * activity.resources.displayMetrics.density).toInt())
                assertTrue(root.findViewWithTag<TextView>("set-$id-1-kg-plus").height >= (48 * activity.resources.displayMetrics.density).toInt())
                check.isChecked = true
                assertTrue(check.isChecked)
            }
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
            assertEquals("5", saved.rpe)
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
    @Test fun calendarIsNotDuplicatedInDetailScreen() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withTagValue(`is`("phone_calendar"))).check(doesNotExist())
        }
    }
    @Test fun cardioUsesBikeThenEllipticalAndEveryCombinationRemainsSixtyMinutes() {
        val data = WorkoutData(context)
        for ((firstMin, secondMin) in listOf(15 to 15, 15 to 20, 5 to 5, 35 to 5, 5 to 35, 20 to 10, 10 to 30, 25 to 15)) {
            data.setCardioSplit(firstMin, secondMin)
            val minutes = firstMin + secondMin
            for (week in 0L..10L) for (day in 1..5) {
                val routine = data.routine(day, week)
                assertEquals(60, routine.stages.sumOf { it.minutes })
                val first = routine.stages[1].tasks
                val second = routine.stages[3].tasks
                assertEquals(minutes * 60, (first + second).sumOf { it.seconds })
                assertEquals(routine.stages[1].minutes * 60, first.sumOf { it.seconds })
                assertEquals(routine.stages[3].minutes * 60, routine.stages[3].tasks.sumOf { it.seconds })
                assertEquals("유산소 ① is set individually", firstMin, routine.stages[1].minutes)
                assertEquals("유산소 ② is set individually", secondMin, routine.stages[3].minutes)
                assertEquals("근력 takes the rest of the 50 minutes", 50 - minutes, routine.stages[2].minutes)
                assertEquals(routine.stages[4].minutes * 60, routine.stages[4].tasks.sumOf { it.seconds })
                assertTrue("stretch stage only holds stretches", routine.stages[4].tasks.all { it.kind == "마무리 스트레칭" })
                assertTrue("first block is the bike", first.isNotEmpty() && first.all { it.kind == "실내 사이클" })
                assertTrue("second block is the elliptical", second.isNotEmpty() && second.all { it.kind == "일립티컬" })
                assertTrue("no stair climber or rowing", (first + second).none { Regex("스텝밀|로잉").containsMatchIn(it.name + it.detail) })
                assertTrue("elliptical text has no pedals", second.none { "페달" in it.name + it.detail + it.description })
                // v58: phase names never carry an "n/N회차" prefix (Figma shows plain interval names).
                assertTrue("no 회차 labels", (first + second).none { "회차" in it.name })
            }
        }
    }
    @Test fun movingToAnotherItemKeepsActiveTime() {
        val store = SessionStore(context, WorkoutData(context))
        store.resetAll()
        store.start()
        store.jumpTo(1)
        val before = store.elapsed()
        val tasks = store.routine.stages[1].tasks
        assertTrue("cardio has several items", tasks.size >= 3)
        store.jumpToPhase(2)
        assertEquals("phase moved to the picked item", 2, store.phase()?.first)
        assertTrue("active workout time is not reset or shortened", store.elapsed() >= before)
        store.jumpToPhase(0)
        assertEquals(0, store.phase()?.first)
        assertEquals("no overtime inside the target", 0, store.overtimeSeconds())
        store.resetAll()
    }
    @Test fun dailyEnergyIsIdempotentAndRecalculatesCompletedSetInputs() {
        val ledger = EnergyLedger(context)
        val start = today.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
        val snapshot = JSONObject().put("stage", 2).put("running", true)
        ledger.add(today, start, start+600_000L, snapshot)
        val initial = ledger.estimate(today)
        // Strength time alone (no checked set) must not produce energy: the timer running is not exercise.
        assertTrue(initial.isFinite())
        assertTrue("fallback legacy value must not be used once a ledger record exists", initial < 1.0)
        ledger.add(today, start, start+600_000L, snapshot)
        assertEquals(initial, ledger.estimate(today), 0.000001)
        val sets = SetRecordStore(context)
        sets.write(1, "test", 1, SetRecordStore.Record("20", "10", "5", true), token=123L)
        val low = ledger.estimate(today)
        assertTrue("a checked set adds strength energy", low > initial)
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

    @Test fun perSessionCaloriesStartAtZeroAndNeverCarryEarlierWorkoutEnergy() {
        val ledger = EnergyLedger(context)
        val firstStart = today.atTime(10, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
        val first = JSONObject().put("stage", 1).put("running", true)
            .put("sessionStartedAt", firstStart)
        assertEquals(0.0, ledger.estimateSession(JSONObject()), 0.000001)
        ledger.add(today, firstStart, firstStart + 120_000L, first)
        val firstKcal = ledger.estimateSession(first)
        assertTrue("First workout should accumulate energy", firstKcal > 0.0)
        ledger.add(today, firstStart, firstStart + 120_000L, first)
        assertEquals("Duplicate checkpoint must not double-count",firstKcal,ledger.estimateSession(first),0.000001)

        val secondStart = firstStart + 180_000L
        val second = JSONObject().put("stage", 1).put("running", false)
            .put("sessionStartedAt", secondStart)
        assertEquals("New workout must start from zero",0.0,ledger.estimateSession(second),0.000001)
        assertTrue(ledger.estimate(today) > 0.0)
        second.put("running", true)
        ledger.add(today, secondStart, secondStart + 60_000L, second)
        val secondKcal = ledger.estimateSession(second)
        assertTrue(secondKcal > 0.0)
        assertTrue("Second workout cannot include earlier energy",secondKcal < ledger.estimate(today))
        second.put("running", false)
        assertEquals("Paused time must not be counted",secondKcal,ledger.estimateSession(second),0.000001)
        first.put("running", false)
        assertEquals("First session remains intact",firstKcal,ledger.estimateSession(first),0.000001)
    }

    @Test fun dockShowsTodayOnly() {
        val ledger = EnergyLedger(context)
        val start = today.atTime(9, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
        ledger.add(today,start,start+120_000L,JSONObject().put("stage",1).put("running",true))
        assertTrue(ledger.estimate(today) > 0.0)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val kcal = root.findViewWithTag<TextView>("phone_session_calories")
                assertTrue("Dock must include today's earlier workouts",
                    kcal.text.toString().contains("오늘 누적"))
                val expectedCalories = "%.1f".format(ledger.estimate(today))
                assertTrue("Dock must show the exact current-date total",
                    kcal.text.toString().contains("$expectedCalories kcal"))
            }
            screenshot("phone-v32-session-energy-spacing.png")
        }
    }

    @Test fun dailyEnergyAccumulatesTwoSessionsAndResetsOnNextDate() {
        val ledger = EnergyLedger(context)
        val start = today.atTime(8, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
        val first = JSONObject().put("stage", 1).put("running", true).put("sessionStartedAt", start)
        assertEquals("No workout today must mean zero", 0.0, ledger.estimate(today), 0.000001)
        ledger.add(today, start, start + 120_000L, first)
        val firstTotal = ledger.estimate(today)
        assertTrue("First session should add to today's total", firstTotal > 0.0)
        ledger.add(today, start, start + 120_000L, first)
        assertEquals("Duplicate interval must not increase today's total",
            firstTotal, ledger.estimate(today), 0.000001)
        first.put("running", false)
        assertEquals("Pause or end must preserve the same total",
            firstTotal, ledger.estimate(today), 0.000001)
        val secondStart = start + 600_000L
        val second = JSONObject().put("stage", 1).put("running", true)
            .put("sessionStartedAt", secondStart)
        ledger.add(today, secondStart, secondStart + 90_000L, second)
        val combined = ledger.estimate(today)
        assertTrue("Second same-day session must extend the daily total", combined > firstTotal)
        assertEquals("Tomorrow's display must start at zero",
            0.0, ledger.estimate(today.plusDays(1)), 0.000001)
        assertEquals("Yesterday's or tomorrow's activity must not erase today's total",
            combined, ledger.estimate(today), 0.000001)
    }
}

