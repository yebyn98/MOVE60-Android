package com.move60.workout.architecture

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToIndex
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

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun composeNavigationShowsDashboardCalendarAndSettings() {
        rule.onNodeWithText("MOVE60").assertIsDisplayed()
        capture("compose-dashboard.png")
        rule.onNodeWithContentDescription("운동 달력").performClick()
        rule.onNodeWithText("운동 달력").assertIsDisplayed()
        rule.onNodeWithContentDescription("뒤로 가기").performClick()
        rule.onNodeWithContentDescription("앱 설정").performClick()
        capture("compose-settings.png")
        // Settings is a long LazyColumn: scroll to each section before asserting it is visible.
        rule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_version_row"))
        rule.onNodeWithText("앱 버전").assertIsDisplayed()
    }

    @Test fun composeSettingsHasEverySettingInThreeGroups() {
        rule.onNodeWithContentDescription("앱 설정").performClick()
        rule.onNodeWithTag("settings_back").assertIsDisplayed()
        val list = rule.onNodeWithTag("settings_list")
        list.performScrollToNode(hasText("오늘 컨디션 루틴"))
        rule.onNodeWithText("피곤").assertIsDisplayed()
        rule.onNodeWithText("보통").assertIsDisplayed()
        rule.onNodeWithText("좋음").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("settings_ratio"))
        for (minutes in listOf(20, 25, 30, 35, 40)) rule.onNodeWithTag("settings_ratio_$minutes").assertIsDisplayed()
        list.performScrollToNode(hasText("칼로리 계산 체중"))
        rule.onNodeWithText("칼로리 계산 체중").assertIsDisplayed()
        rule.onNodeWithTag("settings_weight_field").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("settings_sound_toggle"))
        rule.onNodeWithTag("settings_sound_toggle").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("settings_version_row"))
        rule.onNodeWithTag("settings_version_row").assertIsDisplayed()
        // The removed one-card-per-setting rows must not come back.
        rule.onNodeWithText("건강 데이터 연동").assertDoesNotExist()
        rule.onNodeWithText("알림 설정 · 진단").assertDoesNotExist()
        capture("compose-settings-list.png")
    }

    @Test fun ratioChipsChangeTheSplitInline() {
        rule.onNodeWithContentDescription("앱 설정").performClick()
        val list = rule.onNodeWithTag("settings_list")
        list.performScrollToNode(hasTestTag("settings_ratio"))
        rule.onNodeWithTag("settings_ratio_25").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("유산소 25분 · 근력 25분", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("settings_ratio_30").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("유산소 30분 · 근력 20분", substring = true).assertIsDisplayed()
    }

    @Test fun turningNotificationsOnOpensTheDiagnostics() {
        rule.onNodeWithContentDescription("앱 설정").performClick()
        rule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_sound_toggle"))
        // Off then On: only the Off -> On change leads to the diagnostics dialog.
        if (rule.onAllNodesWithText("운동 알림 진단").fetchSemanticsNodes().isEmpty()) {
            rule.onNodeWithTag("settings_sound_toggle").performClick()
            rule.waitForIdle()
            rule.onNodeWithText("운동 알림 진단").assertDoesNotExist()
        }
        rule.onNodeWithTag("settings_sound_toggle").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("운동 알림 진단").assertIsDisplayed()
        rule.onNodeWithText("닫기").performClick()
    }

    @Test fun dashboardIsOverviewNotDuplicatedWorkoutEditor() {
        // Today's routine and the start button come first; the weekly plan sits below and may need a scroll.
        rule.onNodeWithTag("dashboard_routine_card").assertIsDisplayed()
        rule.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("dashboard_plan_card"))
        rule.onNodeWithText("이번 주 운동 계획").assertIsDisplayed()
        rule.onNodeWithTag("dashboard_list").performScrollToIndex(0)
        rule.onNodeWithTag("workout_dock").assertIsDisplayed()
        rule.onNodeWithText("운동 시작").assertIsDisplayed()
        // The former "운동 현황" card now lives in the dock (progress bar) and the routine card header.
        rule.onNodeWithText("운동 현황").assertDoesNotExist()
        rule.onNodeWithTag("dock_progress").assertIsDisplayed()
        rule.onNodeWithTag("dashboard_session_caption").assertIsDisplayed()
        rule.onNodeWithText("상세 운동 화면").assertDoesNotExist()
        rule.onNodeWithText("중량 kg").assertDoesNotExist()
        // Actual emulator screenshot: the PR artifact includes this captured screen.
        rule.waitForIdle()
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val file = File(rule.activity.getExternalFilesDir(null), "move60-home-overview.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun weeklyPlanCanBeCollapsedAndReopened() {
        val hint = "요일과 운동 부위를 선택하면 상세 운동 화면에 적용돼요."
        rule.onNodeWithTag("dashboard_list").performScrollToNode(hasTestTag("dashboard_plan_header"))
        // Collapse/expand is a toggle whatever the initial state was: after two clicks we are back where we started.
        val startedOpen = rule.onAllNodesWithText(hint).fetchSemanticsNodes().isNotEmpty()
        rule.onNodeWithTag("dashboard_plan_header").performClick()
        rule.waitForIdle()
        assertEquals(!startedOpen, rule.onAllNodesWithText(hint).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("dashboard_plan_header").performClick()
        rule.waitForIdle()
        assertEquals(startedOpen, rule.onAllNodesWithText(hint).fetchSemanticsNodes().isNotEmpty())
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
