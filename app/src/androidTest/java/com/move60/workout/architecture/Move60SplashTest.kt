package com.move60.workout.architecture

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.move60.workout.ui.Move60ComposeTheme
import com.move60.workout.ui.Move60Splash
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Move60SplashTest {
    @get:Rule val rule = createComposeRule()

    @Test fun introShowsWordmarkAndFinishesByItself() {
        var finished = false
        rule.setContent { Move60ComposeTheme { Move60Splash(onFinished = { finished = true }) } }
        rule.onNodeWithText("MOVE60").assertIsDisplayed()
        rule.waitUntil(timeoutMillis = 5_000) { finished }
        assertTrue(finished)
    }

    /** Frames of the intro composable (test clock, not a full app launch) for visual review. */
    @Test fun introFramesAreCaptured() {
        rule.mainClock.autoAdvance = false
        rule.setContent { Move60ComposeTheme { Move60Splash(onFinished = { }) } }
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        for ((ms, name) in listOf(300L to "intro-1-ring.png", 600L to "intro-2-ticks.png", 600L to "intro-3-dumbbell.png")) {
            rule.mainClock.advanceTimeBy(ms)
            val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
            java.io.File(dir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
