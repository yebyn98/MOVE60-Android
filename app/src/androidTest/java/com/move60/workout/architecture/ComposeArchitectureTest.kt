package com.move60.workout.architecture

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeArchitectureTest {
    @get:Rule val rule = createAndroidComposeRule<ComposeActivity>()

    @Test fun composeNavigationShowsDashboardCalendarAndSettings() {
        rule.onNodeWithText("MOVE60").assertIsDisplayed()
        rule.onNodeWithContentDescription("운동 달력").performClick()
        rule.onNodeWithText("운동 기록").assertIsDisplayed()
        rule.onNodeWithContentDescription("뒤로 가기").performClick()
        rule.onNodeWithContentDescription("앱 설정").performClick()
        rule.onNodeWithText("앱 정보").assertIsDisplayed()
        rule.onNodeWithText("칼로리 계산 기준").assertIsDisplayed()
    }

    @Test fun dashboardIsOverviewNotDuplicatedWorkoutEditor() {
        rule.onNodeWithText("이번 주 운동 계획").assertIsDisplayed()
        rule.onNodeWithText("운동 상세 및 시작").assertIsDisplayed()
        rule.onNodeWithText("운동 현황").assertIsDisplayed()
        rule.onNodeWithText("상세 운동 화면").assertDoesNotExist()
        rule.onNodeWithText("중량 kg").assertDoesNotExist()
        // Actual emulator screenshot: the PR artifact includes this captured screen.
        rule.waitForIdle()
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val file = File(rule.activity.getExternalFilesDir(null), "move60-home-overview.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun roomDaoCanUpsertAndObserveAWorkoutDay() = runBlocking {
        val context = rule.activity.applicationContext
        val db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        try {
            val dao = db.workoutDao()
            dao.upsertDay(DailyWorkoutEntity("2026-11-04",120,15.0,1L))
            val rows = dao.observeRecentDays().first()
            assertEquals(1,rows.size)
            assertEquals("2026-11-04",rows.first().date)
            assertEquals(120L,rows.first().activeSeconds)
            dao.upsertDay(DailyWorkoutEntity("2026-11-04",180,22.0,2L))
            assertEquals(1,dao.observeRecentDays().first().size)
            assertEquals(180L,dao.day("2026-11-04")?.activeSeconds)
        } finally {
            db.close()
        }
    }
}
