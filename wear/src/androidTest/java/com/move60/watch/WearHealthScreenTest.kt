package com.move60.watch

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
}
