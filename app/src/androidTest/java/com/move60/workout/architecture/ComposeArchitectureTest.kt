package com.move60.workout.architecture

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
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
        // Settings is a long LazyColumn: scroll to each section before asserting it is visible.
        rule.onNodeWithTag("settings_list").performScrollToNode(hasText("칼로리 계산 기준"))
        rule.onNodeWithText("칼로리 계산 기준").assertIsDisplayed()
        rule.onNodeWithTag("settings_list").performScrollToNode(hasText("앱 정보"))
        rule.onNodeWithText("앱 정보").assertIsDisplayed()
    }

    @Test fun composeSettingsHasEveryLegacySettingsFeature() {
        rule.onNodeWithContentDescription("앱 설정").performClick()
        rule.onNodeWithTag("settings_back").assertIsDisplayed()
        rule.onNodeWithTag("settings_sound_toggle").assertIsDisplayed()
        rule.onNodeWithText("알림 설정 · 진단").assertIsDisplayed()
        rule.onNodeWithText("업데이트 확인").assertIsDisplayed()
        val list = rule.onNodeWithTag("settings_list")
        list.performScrollToNode(hasText("오늘 컨디션에 맞춘 루틴"))
        rule.onNodeWithText("피곤").assertIsDisplayed()
        rule.onNodeWithText("보통").assertIsDisplayed()
        rule.onNodeWithText("좋음").assertIsDisplayed()
        list.performScrollToNode(hasText("칼로리 계산 체중"))
        rule.onNodeWithText("칼로리 계산 체중").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("settings_version"))
        rule.onNodeWithTag("settings_version").assertIsDisplayed()
        list.performScrollToNode(hasText("유산소 · 근력 시간 비율 변경", substring = true))
        rule.onNodeWithText("유산소 · 근력 시간 비율 변경", substring = true).performClick()
        rule.onNodeWithText("60분 운동 시간 배분").assertIsDisplayed()
        rule.onNodeWithText("취소").performClick()
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
