package com.move60.workout

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** All three migrated destinations must actually render and navigate on a device. */
@RunWith(AndroidJUnit4::class)
class ComposeFoundationTest {
    @get:Rule val compose = createAndroidComposeRule<ComposeHomeActivity>()

    @Test fun composeDashboardHistoryAndSettingsNavigate() {
        compose.onNodeWithTag("compose_today").assertIsDisplayed()
        compose.onNodeWithTag("compose_today_kcal").assertExists()
        compose.onNodeWithTag("compose_start_workout").assertIsDisplayed()

        compose.onNodeWithTag("compose_nav_history").performClick()
        compose.onNodeWithTag("compose_history").assertIsDisplayed()

        compose.onNodeWithTag("compose_nav_settings").performClick()
        compose.onNodeWithTag("compose_settings").assertIsDisplayed()
        compose.onNodeWithTag("compose_weight").assertExists()

        compose.onNodeWithTag("compose_nav_today").performClick()
        compose.onNodeWithTag("compose_today").assertIsDisplayed()
    }
}
