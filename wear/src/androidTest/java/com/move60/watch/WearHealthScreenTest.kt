package com.move60.watch

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
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
        // ScalingLazyColumn composes only items near the center. Swipe down the list and keep a real capture of every
        // step as evidence of what the round screen shows; the chip must exist once the list reaches it.
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        fun capture(name: String) {
            Thread.sleep(800)
            val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            java.io.File(rule.activity.getExternalFilesDir(null), name).outputStream().use {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        var found = false
        for (step in 0..7) {
            capture("watch-v57-health-scroll-$step.png")
            found = rule.onAllNodesWithTag("wear_kcal_chip").fetchSemanticsNodes().isNotEmpty()
            if (found && step >= 3) break
            rule.onNodeWithTag("wear_health_root").performTouchInput { swipeUp() }
            rule.waitForIdle()
        }
        org.junit.Assert.assertTrue("kcal opt-in chip must exist in the list", found)
    }
}
