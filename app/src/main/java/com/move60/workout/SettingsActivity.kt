package com.move60.workout

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.app.NotificationManager
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import com.move60.workout.Move60Theme as Theme
import com.move60.workout.DashboardUi
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.max

class SettingsActivity : Activity() {
    private val background = Theme.Colors.background
    private val panel = Theme.Colors.surface
    private val panelLight = Theme.Colors.surfaceElevated
    private val mint = Theme.Colors.primary
    private val subtle = Theme.Colors.outline
    private val textPrimary = Theme.Colors.textHigh
    private val textSecondary = Theme.Colors.textMedium
    private lateinit var updateManager: UpdateManager
    private lateinit var data: WorkoutData
    private lateinit var session: SessionStore
    private var soundEnabled = true
    private var calorieWeightKg = 63.0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        data = WorkoutData(this)
        session = SessionStore(this, data)
        // Settings follow the current phone-owned session, even after a watch command.
        updateManager = UpdateManager(this)
        soundEnabled = getSharedPreferences("preferences", MODE_PRIVATE).getBoolean("sound", true)
        calorieWeightKg = getSharedPreferences("preferences", MODE_PRIVATE).getFloat("calorie_weight_kg", 63f).toDouble()
        val body = column().apply {
            setPadding(dp(Theme.Space.two), dp(Theme.Space.three),
                dp(Theme.Space.two), dp(Theme.Space.four))
        }
        val navigation = row()
        navigation.addView(actionButton("←", false) { finish() }.apply {
            tag = "settings_back"
            contentDescription = "운동 화면으로 돌아가기"
            textSize = Theme.Type.heading
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        body.addView(navigation)
        addGap(body, 16)
        body.addView(label("앱 설정", 24, true))
        addGap(body, 8)
        body.addView(label("운동에 필요한 설정을 한곳에서 관리하세요.", 14, false, textSecondary))
        addGap(body, Theme.Space.three)
        val about = card().apply { tag = "settings_about_card" }
        val aboutHeader = row().apply { gravity = Gravity.CENTER_VERTICAL }
        aboutHeader.addView(label("앱 정보", 20, true), LinearLayout.LayoutParams(0, -2, 1f))
        aboutHeader.addView(label("v" + packageManager.getPackageInfo(packageName, 0).versionName,
            12, true, mint).apply { tag = "settings_version" })
        about.addView(aboutHeader)
        addGap(about, Theme.Space.one)
        about.addView(label("MOVE60 · 60분 운동을 매일 더 간결하게", 14, false, textSecondary))
        body.addView(about)
        addGap(body, Theme.Space.two)
        val settings = card()
        val alertHeader = row().apply { gravity = Gravity.CENTER_VERTICAL }
        alertHeader.addView(label("알림 · 업데이트", 20, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        val soundSwitch = Switch(this).apply {
            tag = "settings_sound_toggle"
            showText = false
            text = ""
            contentDescription = "구간 전환 알림음 켜기 또는 끄기"
            isChecked = soundEnabled
            setOnCheckedChangeListener { _, enabled ->
                soundEnabled = enabled
                getSharedPreferences("preferences", MODE_PRIVATE).edit().putBoolean("sound", enabled).apply()
            }
        }
        alertHeader.addView(soundSwitch, LinearLayout.LayoutParams(dp(56), dp(48)))
        settings.addView(alertHeader)
        addGap(settings, Theme.Space.two)
        settings.addView(actionButton("알림 설정 · 진단", false) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 25)
            else showNotificationDiagnostics()
        })
        addGap(settings, 12)
        settings.addView(actionButton("업데이트 확인", false) { updateManager.check(true) })
        addGap(settings, 12)
        body.addView(settings)
        addGap(body, 24)
        val workoutSettings = card()
        workoutSettings.addView(label("운동 설정", 18, true))
        addGap(workoutSettings, 16)
        workoutSettings.addView(actionButton("유산소 · 근력 시간 비율 변경", false) {
            session.reloadIfChanged()
            if (session.running || session.elapsed() > 0) {
                Toast.makeText(this, "운동 시작 전 초기 상태에서 변경해 주세요.", Toast.LENGTH_LONG).show()
            } else {
                val minutes = listOf(20, 25, 30, 35, 40)
                MaterialAlertDialogBuilder(this).setTitle("60분 운동 시간 배분")
                    .setSingleChoiceItems(minutes.map { "유산소 ${it}분 · 근력 ${50 - it}분" }.toTypedArray(),
                        minutes.indexOf(data.cardioMinutes())) { dialog, which ->
                        data.setCardioMinutes(minutes[which])
                        session.refreshRoutine()
                        session.resetAll()
                        dialog.dismiss()
                    }.setNegativeButton("취소", null).show()
            }
        })
        addGap(workoutSettings, 16)
        workoutSettings.addView(actionButton("삼성 헬스 · 심박수 연동 ↗", false) {
            startActivity(Intent(this, com.move60.workout.health.HealthConsentActivity::class.java))
        })
        addGap(workoutSettings, Theme.Space.two)
        workoutSettings.addView(actionButton("칼로리 계산 체중 설정 ↗", false) {
            val input = EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(calorieWeightKg.toString())
            }
            MaterialAlertDialogBuilder(this).setTitle("칼로리 추정용 체중 (kg)")
                .setMessage("운동 칼로리는 추정치이며 실제 소모량과 다를 수 있어요.")
                .setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장") { _, _ ->
                    val value = input.text.toString().toDoubleOrNull()
                    if (value != null && value in 25.0..300.0) {
                        calorieWeightKg = value
                        getSharedPreferences("preferences", MODE_PRIVATE).edit().putFloat("calorie_weight_kg", value.toFloat()).apply()
                        Toast.makeText(this, "저장했어요", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(this, "25~300kg 범위로 입력해 주세요.", Toast.LENGTH_SHORT).show()
                }.show()
        })
        addGap(workoutSettings, 16)
        workoutSettings.addView(label("오늘 컨디션에 맞춘 루틴", 16, true))
        addGap(workoutSettings, 12)
        val conditionRow = row()
        listOf("피곤" to "스텝밀 최대 10분 · 편안한 강도", "보통" to "스텝밀 최대 15분 · 계획대로 진행", "좋음" to "스텝밀 최대 15분 · 자세 유지").forEachIndexed { index, option ->
            conditionRow.addView(actionButton(option.first, false) {
                getSharedPreferences("preferences", MODE_PRIVATE).edit().putString("condition", option.first).apply()
                Toast.makeText(this, option.second, Toast.LENGTH_LONG).show()

            }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) leftMargin = dp(8) })
        }
        workoutSettings.addView(conditionRow)
        addGap(workoutSettings, 12)
        workoutSettings.addView(label("운동 중 변경한 컨디션은 다음 운동부터 적용됩니다.", 14, false, textSecondary))
        addGap(workoutSettings, 16)
        body.addView(workoutSettings)
        addGap(body, 24)
        val help = card().apply { tag = "settings_help_card" }
        help.addView(label("도움말", 20, true))
        addGap(help, Theme.Space.two)
        help.addView(label("칼로리 계산 기준", 16, true, mint))
        addGap(help, Theme.Space.one)
        help.addView(label("오늘 누적 칼로리는 오늘 실제 운동을 시작한 뒤 진행한 활동 시간과 설정 체중을 기준으로 계산됩니다. 중단 또는 종료 중에는 누적하지 않고, 같은 날짜의 여러 운동은 합산합니다. 다음 날에는 다시 0부터 시작합니다.\n\n예상 소모량은 MET 기반 추정치입니다. 완료한 근력 세트의 횟수를 참고하며, 입력 중량을 kcal로 직접 환산하지 않습니다.\n\n심박수는 실시간 참고 정보이며 별도의 개인 보정 없이 kcal에 더하지 않습니다.", 14, false, textSecondary))
        body.addView(help)
        val scroll = ScrollView(this).apply { setBackgroundColor(this@SettingsActivity.background); addView(body) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            }
            insets
        }
        setContentView(scroll)
        scroll.requestApplyInsets()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 25) showNotificationDiagnostics()
    }
    override fun onResume() { super.onResume(); if (::updateManager.isInitialized) updateManager.resumePendingInstall() }
    override fun onDestroy() { if (::updateManager.isInitialized) updateManager.onDestroy(); super.onDestroy() }
    private fun showNotificationDiagnostics() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channel = manager.getNotificationChannel(WorkoutService.CHANNEL_PROGRESS)
        val allowed = manager.areNotificationsEnabled() &&
            (channel == null || channel.importance != android.app.NotificationManager.IMPORTANCE_NONE) &&
            (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        val lastError = getSharedPreferences("move60_service_diagnostics", MODE_PRIVATE).getString("latest_error", null)
        val actualActive = try {
            manager.activeNotifications.any { it.id == 601 && it.packageName == packageName }
        } catch (_: Exception) { false }
        val cardStatus = getSharedPreferences("timer", MODE_PRIVATE).getString("card_status", "아직 운동 알림 게시 기록 없음")
        val diagnostic = (if (allowed) "알림 권한: 허용" else "알림 권한: 확인 필요") +
            "\n운동 알림 채널: " + (if (channel == null) "운동 시작 시 생성" else if (channel.importance == android.app.NotificationManager.IMPORTANCE_NONE) "차단됨" else "허용됨") +
            "\n운동 상태: " + (if (session.running) "진행 중" else "대기 또는 정지") +
            "\n실제 알림 게시 여부: " + (if (actualActive) "게시됨" else "게시되지 않음") +
            "\n카드형 알림 상태: " + cardStatus +
            "\n잠금화면 표시: 기기 설정에서도 '알림 내용 표시'를 허용해야 해요" +
            (if (lastError == null) "" else "\n최근 알림 오류: " + lastError) +
            "\n\n운동 시작 버튼을 누른 상태에서만 진행 알림이 표시돼요."
        MaterialAlertDialogBuilder(this).setTitle("운동 알림 진단").setMessage(diagnostic)
            .setNegativeButton("닫기", null)
            .setPositiveButton("알림 설정 열기") { _, _ -> openLockscreenSettings() }.show()
    }

    private fun openLockscreenSettings() {
        try {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, WorkoutService.CHANNEL_PROGRESS)
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        }
    }

    private fun label(text: String, size: Int, bold: Boolean, color: Int = textPrimary) =
        DashboardUi.label(this, text, size, bold, color)
    private fun column() = DashboardUi.column(this)
    private fun row() = DashboardUi.row(this)
    private fun card() = DashboardUi.card(this)
    private fun actionButton(title: String, main: Boolean, onClick: () -> Unit) =
        DashboardUi.actionButton(this, title, main, compact = false, onClick = onClick)
    private fun rounded(color: Int, radiusDp: Int, stroke: Int? = null) =
        DashboardUi.rounded(this, color, radiusDp, stroke)
    private fun dp(number: Int) = Theme.dp(this, number)
    private fun addGap(container: LinearLayout, height: Int) =
        DashboardUi.gap(this, container, height)
    private fun formatTime(seconds: Int): String {
        val n = max(0, seconds)
        return "%02d:%02d".format(n / 60, n % 60)
    }
}

