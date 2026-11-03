package com.move60.workout

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.max

class SettingsActivity : Activity() {
    private val background = Color.rgb(11,17,32)
    private val panel = Color.rgb(21,31,50)
    private val panelLight = Color.rgb(27,42,64)
    private val mint = Color.rgb(180,245,110)
    private val subtle = Color.rgb(37,54,77)
    private val textPrimary = Color.rgb(237,245,255)
    private val textSecondary = Color.rgb(166,185,205)
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
        val body = column().apply { setPadding(dp(18), dp(24), dp(18), dp(24)) }
        body.addView(actionButton("← 운동 화면", false) { finish() })
        addGap(body, 16)
        body.addView(label("앱 설정", 24, true))
        addGap(body, 16)
        val settings = column().apply { setPadding(dp(5), dp(4), dp(5), dp(4)) }
        settings.addView(actionButton("알림 설정 · 진단", false) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 25)
            else showNotificationDiagnostics()
        })
        addGap(settings, 8)
        settings.addView(actionButton("업데이트 확인", false) { updateManager.check(true) })
        addGap(settings, 8)
        val preferenceRow = row().apply { gravity = Gravity.CENTER_VERTICAL }
        val soundSwitch = Switch(this).apply {
            text = "구간 전환 알림음"
            textSize = 12f
            setTextColor(textSecondary)
            isChecked = soundEnabled
            setOnCheckedChangeListener { _, enabled ->
                soundEnabled = enabled
                getSharedPreferences("preferences", MODE_PRIVATE).edit().putBoolean("sound", enabled).apply()
            }
        }
        preferenceRow.addView(soundSwitch, LinearLayout.LayoutParams(0, -2, 1f))
        settings.addView(actionButton("유산소 · 근력 시간 비율 변경", false) {
            session.reloadIfChanged()
            if (session.running || session.elapsed() > 0) {
                Toast.makeText(this, "운동 시작 전 초기 상태에서 변경해 주세요.", Toast.LENGTH_LONG).show()
            } else {
                val minutes = listOf(20, 25, 30, 35, 40)
                AlertDialog.Builder(this).setTitle("60분 운동 시간 배분")
                    .setSingleChoiceItems(minutes.map { "유산소 ${it}분 · 근력 ${50 - it}분" }.toTypedArray(),
                        minutes.indexOf(data.cardioMinutes())) { dialog, which ->
                        data.setCardioMinutes(minutes[which])
                        session.refreshRoutine()
                        session.resetAll()
                        dialog.dismiss()
                    }.setNegativeButton("취소", null).show()
            }
        })
        addGap(settings, 8)
        settings.addView(preferenceRow)
        addGap(settings, 8)
        settings.addView(actionButton("칼로리 계산 체중 설정 ↗", false) {
            val input = EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(calorieWeightKg.toString())
            }
            AlertDialog.Builder(this).setTitle("칼로리 추정용 체중 (kg)")
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
        addGap(settings, 8)
        settings.addView(label("오늘 컨디션에 맞춘 루틴", 12, true, textSecondary))
        val conditionRow = row()
        listOf("피곤" to "스텝밀 최대 10분 · 편안한 강도", "보통" to "스텝밀 최대 15분 · 계획대로 진행", "좋음" to "스텝밀 최대 15분 · 자세 유지").forEachIndexed { index, option ->
            conditionRow.addView(actionButton(option.first, false) {
                getSharedPreferences("preferences", MODE_PRIVATE).edit().putString("condition", option.first).apply()
                Toast.makeText(this, option.second, Toast.LENGTH_LONG).show()

            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { if (index > 0) leftMargin = dp(5) })
        }
        settings.addView(conditionRow)
        settings.addView(label("운동 중 변경한 컨디션은 다음 운동부터 적용됩니다.", 11, false, textSecondary))
        addGap(settings, 8)
        settings.addView(label("MOVE60 ${packageManager.getPackageInfo(packageName, 0).versionName}", 11, false, textSecondary))
        addGap(settings, 8)
        settings.addView(actionButton("오류 기록 확인 · 복사 · 삭제", false) { CrashReporter.showDiagnostics(this) })
        body.addView(settings)

        addGap(body, 12)
        body.addView(label("칼로리 계산 기준", 15, true, mint))
        body.addView(label("실제 운동시간·체중·MET 기반의 추정치입니다. 완료한 근력 세트는 횟수(1회 3초 가정)와 RPE를 반영합니다. 입력 중량은 총 운동량으로 기록하며 kg을 kcal로 직접 환산하지 않습니다. 개인 보정이 없는 심박수는 열량 계산에 합산하지 않습니다.", 13, false, textSecondary))
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
        AlertDialog.Builder(this).setTitle("운동 알림 진단").setMessage(diagnostic)
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

    private fun label(text: String, size: Int, bold: Boolean, color: Int = textPrimary) = TextView(this).apply {
        this.text = text
        setTextColor(color)
        textSize = size.toFloat()
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = true
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun card() = column().apply {
        background = rounded(panel, 20, subtle)
        setPadding(dp(17), dp(18), dp(17), dp(18))
    }
    private fun actionButton(title: String, main: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = title
        textSize = if (main) 15f else 12f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (main) Color.rgb(22, 37, 26) else textPrimary)
        background = rounded(if (main) mint else panelLight, 12, if (main) mint else subtle)
        gravity = Gravity.CENTER
        minHeight = dp(if (main) 53 else 43)
        setPadding(dp(8), dp(9), dp(8), dp(9))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }
    private fun rounded(color: Int, radiusDp: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun dp(number: Int) = (resources.displayMetrics.density * number + 0.5f).toInt()
    private fun addGap(container: LinearLayout, height: Int) {
        container.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }
    private fun formatTime(seconds: Int): String {
        val n = max(0, seconds)
        return "%02d:%02d".format(n / 60, n % 60)
    }
}

