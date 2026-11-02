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
        context.getSharedPreferences("watch_settings", 0).edit()
            .putBoolean("background_access_explained", true)
            .putBoolean("notification_asked", true).commit()
    }
    private fun capture(name: String) {
        Thread.sleep(350)
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
            scenario.onActivity { activity ->
                activity.applyWorkoutState(WatchWorkoutState(
                    running = true, hasSession = true, stageIndex = 1, stageName = "유산소",
                    detail = "중강도", deadline = System.currentTimeMillis()+600_000L))
                activity.setAmbientMode(true)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(activity.window.decorView.findViewWithTag<View>("watch_ambient").isShown)
                assertFalse(activity.window.decorView.findViewWithTag<Button>("watch_next").isShown)
                assertEquals("2/4 · 유산소", activity.window.decorView.findViewWithTag<TextView>("ambient_stage").text.toString())
                assertTrue(activity.window.decorView.findViewWithTag<TextView>("ambient_time").text.contains("분"))
                assertFalse(activity.window.decorView.findViewWithTag<TextView>("ambient_time").text.contains(":"))
            }
            capture("watch-ambient.png")
            scenario.onActivity { it.setAmbientMode(false) }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertButtonInsideCircle(activity, activity.window.decorView.findViewWithTag("watch_next"))
                assertEquals("일시정지", activity.window.decorView.findViewWithTag<Button>("watch_play").text.toString())
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
            Thread.sleep(1_000)
            val manager = context.getSystemService(NotificationManager::class.java)
            val notification = manager.activeNotifications.firstOrNull { it.id == 604 }?.notification
            assertNotNull("Ongoing workout notification missing", notification)
            assertTrue(notification!!.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertNotNull(notification.contentIntent)
            notification.contentIntent.send()
            instrumentation.waitForIdleSync()
            capture("watch-ongoing-return.png")
        }
    }
}
