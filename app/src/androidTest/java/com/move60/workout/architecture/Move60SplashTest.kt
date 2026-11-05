package com.move60.workout.architecture

import androidx.compose.ui.test.junit4.createComposeRule
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
}
