package com.move60.watch

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.move60.watch.health.WearHealthActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearHealthScreenTest {
    @get:Rule val rule = createAndroidComposeRule<WearHealthActivity>()

    @Test fun watch4HealthShowsRealSensorPlaceholderWithoutInventingNumbers() {
        rule.onNodeWithText("MOVE60 · 건강").assertIsDisplayed()
        rule.onNodeWithText("현재 심박수").assertIsDisplayed()
        // A freshly installed app cannot present fake Samsung Health samples.
        val persisted = rule.activity.getSharedPreferences("move60_phone_health_v1", 0)
        if (!persisted.getBoolean("consented", false)) {
            org.junit.Assert.assertFalse("No consent: health metrics must not be imported",
                persisted.contains("sleepMinutes"))
        }
    }

    @Test fun vascularLoadHasNoSyntheticReadPath() {
        org.junit.Assert.assertEquals("/move60/health", WearHealthActivity.HEALTH_PATH)
        val state = WearHealthActivity.HealthState()
        org.junit.Assert.assertNull(state.sleepMinutes)
        org.junit.Assert.assertNull(state.liveBpm)
        org.junit.Assert.assertFalse(state.healthConsent)
    }
}
