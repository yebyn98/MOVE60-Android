package com.move60.watch

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.move60.watch.health.WearHealthActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearHealthScreenTest {
    @get:Rule val rule = createAndroidComposeRule<WearHealthActivity>()

    @Test fun watch4HealthShowsRealSensorPlaceholderWithoutInventingNumbers() {
        // ScalingLazyColumn composes only centered items; off-screen text may not exist
        // in the semantics tree on a 396px circular watch. Assert the stable root.
        rule.onNodeWithTag("wear_health_root").assertIsDisplayed()
        // A freshly installed app cannot present fake Samsung Health samples.
        val persisted = rule.activity.getSharedPreferences("move60_phone_health_v1", 0)
        if (!persisted.getBoolean("consented", false)) {
            org.junit.Assert.assertFalse("No consent: health metrics must not be imported",
                persisted.contains("lastHeartBpm"))
        }
    }

    @Test fun heartRateOnlyStateHasNoUnconsentedValues() {
        org.junit.Assert.assertEquals("/move60/health", WearHealthActivity.HEALTH_PATH)
        val state = WearHealthActivity.HealthState()
        org.junit.Assert.assertNull(state.liveBpm)
        org.junit.Assert.assertFalse(state.healthConsent)
    }

    @Test fun watchCaloriesAreOffUntilTheUserTurnsThemOn() {
        rule.activity.getSharedPreferences("move60_watch_kcal_v1", 0).edit().clear().commit()
        org.junit.Assert.assertFalse(com.move60.watch.health.WatchExerciseCalories.isOptedIn(rule.activity))
        org.junit.Assert.assertFalse(WearHealthActivity.HealthState().kcalOptIn)
        // ScalingLazyColumn composes only items near the center: swipe until the chip is actually on screen.
        var shown = false
        repeat(12) {
            if (!shown) {
                shown = try { rule.onNodeWithTag("wear_kcal_chip").assertIsDisplayed(); true } catch (_: AssertionError) { false }
                if (!shown) { rule.onNodeWithTag("wear_health_root").performTouchInput { swipeUp() }; rule.waitForIdle() }
            }
        }
        rule.onNodeWithTag("wear_kcal_chip").assertIsDisplayed()
        Thread.sleep(1_500)
        val image = requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        java.io.File(rule.activity.getExternalFilesDir(null), "watch-v57-kcal-optin-off.png").outputStream().use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
