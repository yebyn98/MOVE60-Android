package com.move60.workout.health

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verify Compose semantics with Compose testing APIs, not native Espresso view matchers. */
@RunWith(AndroidJUnit4::class)
class HealthConsentScreenTest {
    @get:Rule val rule = createAndroidComposeRule<HealthConsentActivity>()

    @Test fun consentScreenHasActionsAndCapturesRealScreenshot() {
        rule.onNodeWithText("건강 데이터 연동").assertIsDisplayed()
        rule.onNodeWithText("동의하고 건강 기록 불러오기").assertIsDisplayed()
        rule.onNodeWithText("MOVE60 연동 중지 및 워치 데이터 초기화").assertIsDisplayed()
        rule.waitForIdle()
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val target = File(rule.activity.getExternalFilesDir(null), "move60-health-connect-screen.png")
        target.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
