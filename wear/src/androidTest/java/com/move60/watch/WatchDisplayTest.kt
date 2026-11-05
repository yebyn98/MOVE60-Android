package com.move60.watch

import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WatchDisplayTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @Before fun prepare() {
        context.getSharedPreferences("watch_workout",0).edit().clear().commit()
        context.getSharedPreferences("watch_rest_alerts_v26",0).edit().clear().commit()
        context.getSharedPreferences("watch_settings", 0).edit()
            .putBoolean("background_access_explained", true)
            .putBoolean("notification_asked", true).commit()
    }
    private fun capture(name: String) {
        Thread.sleep(2_000)
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.getExternalFilesDir(null), name).outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun assertButtonInsideCircle(activity: WatchActivity, button: Button) {
        assertTrue("Button must be visible", button.isShown)
        val xy = IntArray(2)
        button.getLocationOnScreen(xy)
        val display = activity.window.decorView
        val center = IntArray(2)
        display.getLocationOnScreen(center)
        val cx = center[0] + display.width / 2.0
        val cy = center[1] + display.height / 2.0
        val radius = minOf(display.width, display.height) / 2.0 - 2
        for (x in listOf(xy[0], xy[0] + button.width))
            for (y in listOf(xy[1], xy[1] + button.height))
                assertTrue("Button corner clipped by round screen: $x,$y",
                    (x-cx)*(x-cx) + (y-cy)*(y-cy) <= radius*radius)
    }
    @Test fun allFourStagesAndBothButtonsFitSmallRoundScreen() {
        ActivityScenario.launch(WatchActivity::class.java).use { scenario ->
            val names = listOf("준비운동", "유산소", "근력운동", "마무리 스트레칭")
            names.forEachIndexed { index, name ->
                scenario.onActivity { activity ->
                    activity.applyWorkoutState(WatchWorkoutState(
                        running = true, hasSession = true, stageIndex = index, stageName = name,
                        detail = "현재 운동 구간", remaining = 600, deadline = System.currentTimeMillis()+600_000L))
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val stage = activity.window.decorView.findViewWithTag<TextView>("watch_stage")
                    assertEquals("${index+1}/4 · $name", stage.text.toString())
                    assertEquals("Stage text clipped", 0, stage.layout.getEllipsisCount(0))
                    val ring = activity.window.decorView.findViewWithTag<WorkoutProgressRing>("watch_progress_ring")
                    assertTrue("Circular exercise progress must remain visible", ring.isShown)
                    assertTrue(ring.progress in 0..1000)
                    assertButtonInsideCircle(activity, activity.window.decorView.findViewWithTag("watch_play"))
                    assertButtonInsideCircle(activity, activity.window.decorView.findViewWithTag("watch_next"))
                    assertEquals(if (index == 3) "운동 완료" else "다음 단계",
                        activity.window.decorView.findViewWithTag<Button>("watch_next").text.toString())
                }
            }
            capture("watch-small-round.png")
        }
    }
    @Test fun ambientShowsStageWithoutStaleSecondsOrHeartRateAndRestoresControls() {
        ActivityScenario.launch(WatchActivity::class.java).use { scenario ->
            // This test simulates an ambient transition, so check owned view
            // visibility flags rather than isShown(). The latter also depends
            // on whether Wear OS currently displays this Activity's window.
            // Actual on-screen visibility and circular clipping are independently
            // validated by allFourStagesAndBothButtonsFitSmallRoundScreen().
            scenario.onActivity { activity ->
                activity.applyWorkoutState(WatchWorkoutState(
                    running = true, hasSession = true, stageIndex = 1, stageName = "유산소",
                    detail = "중강도", deadline = System.currentTimeMillis()+600_000L))
                activity.setAmbientMode(true)
                val root = activity.window.decorView
                assertEquals(View.VISIBLE, root.findViewWithTag<View>("watch_ambient").visibility)
                assertEquals(View.GONE, root.findViewWithTag<View>("watch_scroll").visibility)
                assertEquals("2/4 · 유산소",
                    root.findViewWithTag<TextView>("ambient_stage").text.toString())
                val ambientTime = root.findViewWithTag<TextView>("ambient_time").text
                assertTrue("Ambient must show minute-level timing", ambientTime.contains("분"))
                assertFalse("Ambient must not show seconds", ambientTime.contains(":"))
            }
            capture("watch-ambient.png")
            scenario.onActivity { activity ->
                activity.setAmbientMode(false)
                val root = activity.window.decorView
                assertEquals(View.GONE, root.findViewWithTag<View>("watch_ambient").visibility)
                assertEquals(View.VISIBLE, root.findViewWithTag<View>("watch_scroll").visibility)
                assertEquals("일시정지",
                    root.findViewWithTag<Button>("watch_play").text.toString())
                assertEquals("다음 단계",
                    root.findViewWithTag<Button>("watch_next").text.toString())
            }
            capture("watch-restored.png")
        }
    }
    @Test fun countdownUsesAbsoluteDeadlineAndPausePreservesRemaining() {
        val running = WatchWorkoutState(running=true, hasSession=true, deadline=100_000L)
        assertEquals(10, running.secondsRemaining(90_000L))
        assertEquals(4, running.secondsRemaining(96_000L))
        assertEquals(0, running.secondsRemaining(120_000L))
        val paused = running.copy(running=false, remaining=123, deadline=0)
        assertEquals(123, paused.secondsRemaining(900_000L))
    }
    @Test fun ongoingNotificationReturnsToWorkout() {
        ActivityScenario.launch(WatchActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.applyWorkoutState(WatchWorkoutState(
                    running=true, hasSession=true, stageIndex=1, stageName="유산소",
                    detail="운동 진행", deadline=System.currentTimeMillis()+600_000L))
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            var notification: Notification? = null
            val until = System.currentTimeMillis() + 5_000L
            while (notification == null && System.currentTimeMillis() < until) {
                notification = manager.activeNotifications.firstOrNull { it.id == 604 }?.notification
                if (notification == null) Thread.sleep(100)
            }
            assertNotNull("Ongoing workout notification missing", notification)
            assertTrue(notification!!.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertNotNull(notification.contentIntent)
            notification.contentIntent.send()
            instrumentation.waitForIdleSync()
            capture("watch-ongoing-return.png")
        }
    }
    @Test fun newerSnapshotRejectsOutOfOrderStateAndPreservesPausedRest() {
        val data = com.google.android.gms.wearable.DataMap().apply {
            putInt("protocol",26); putLong("sequence",200L); putLong("session",123L)
            putLong("controlVersion",7L); putInt("stageIndex",2); putString("stageName","근력운동")
            putString("setLabel","레그프레스 · 2/4종목 · 2/3세트"); putString("exerciseId","legPress")
            putInt("setNumber",2); putInt("restPaused",45); putLong("restId",99L)
            putBoolean("hasSession",true); putBoolean("running",false)
        }
        val state = WatchWorkoutState.fromData(data)
        assertTrue(state.save(context))
        assertFalse(state.copy(sequence=100L,finished=true).save(context))
        val restored = WatchWorkoutState.load(context)
        assertEquals(200L,restored.sequence)
        assertEquals(45,restored.restSeconds(999_999L))
        assertEquals("legPress",restored.exerciseId)
        assertEquals(2,restored.setNumber)
        assertFalse(restored.finished)
    }
    @Test fun restAlertIsDeduplicatedAndLateReconnectDoesNotVibrate() {
        val state = WatchWorkoutState(running=true,restId=100L,restDeadline=1_000L)
        assertFalse(WatchRestAlert.deliver(context,state,500L))
        assertTrue(WatchRestAlert.deliver(context,state,1_001L))
        assertFalse(WatchRestAlert.deliver(context,state,1_002L))
        assertFalse(WatchRestAlert.deliver(context,state.copy(restId=200L),30_000L))
        assertFalse(WatchRestAlert.deliver(context,state.copy(restId=300L,running=false),1_001L))
    }
    @Test fun strengthSetAndRestActionsFitRoundScreen() {
        ActivityScenario.launch(WatchActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.applyWorkoutState(WatchWorkoutState(protocol=26,running=true,hasSession=true,stageIndex=2,
                    stageName="근력운동",setLabel="레그프레스 · 2/4종목 · 2/3세트",canComplete=true,
                    exerciseName="레그프레스",setNumber=2,setTotal=3,setWeight="80",targetReps=12,totalSets=12,completedSets=4))
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals("세트 완료",activity.window.decorView.findViewWithTag<Button>("watch_next").text.toString())
                val target = activity.window.decorView.findViewWithTag<TextView>("watch_timer")
                assertEquals("80kg×12회",target.text.toString())
                assertEquals("Strength target clipped on round display",0,target.layout.getEllipsisCount(0))
                assertEquals(333,activity.window.decorView.findViewWithTag<android.widget.ProgressBar>("watch_progress").progress)
                assertEquals(333,activity.window.decorView.findViewWithTag<WorkoutProgressRing>("watch_progress_ring").progress)
                assertButtonInsideCircle(activity,activity.window.decorView.findViewWithTag("watch_next"))
            }
            capture("watch-v26-set.png")
            scenario.onActivity { activity ->
                activity.applyWorkoutState(WatchWorkoutState(protocol=26,running=true,hasSession=true,stageIndex=2,
                    stageName="근력운동",restId=100L,restDeadline=System.currentTimeMillis()+60_000L))
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals("휴식 건너뛰기",activity.window.decorView.findViewWithTag<Button>("watch_next").text.toString())
                assertTrue(activity.window.decorView.findViewWithTag<TextView>("watch_timer").contentDescription.contains("15초"))
                assertButtonInsideCircle(activity,activity.window.decorView.findViewWithTag("watch_next"))
            }
            capture("watch-v26-rest.png")
            scenario.onActivity { activity ->
                activity.applyWorkoutState(WatchWorkoutState(protocol=26,finished=true,hasSession=true,
                    stageIndex=3,stageName="마무리 스트레칭",activeSeconds=3618,
                    completedSets=12,totalSets=12,estimatedKcal=307.5))
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals("60:18",activity.window.decorView.findViewWithTag<TextView>("watch_timer").text.toString())
                assertEquals(1000,activity.window.decorView.findViewWithTag<android.widget.ProgressBar>("watch_progress").progress)
                assertEquals(1000,activity.window.decorView.findViewWithTag<WorkoutProgressRing>("watch_progress_ring").progress)
                assertTrue(activity.window.decorView.findViewWithTag<TextView>("watch_heart").text.contains("307 kcal"))
            }
            capture("watch-v30-finish.png")
        }
    }

}

