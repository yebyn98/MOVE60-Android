package com.move60.watch

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import com.move60.watch.Move60Theme as Theme
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.wear.ambient.AmbientLifecycleObserver
import com.google.android.gms.wearable.*

/** Interactive and ambient workout UI, laid out inside the round screen's safe square. */
class WatchActivity : ComponentActivity(), DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener {
    private val handler = Handler(Looper.getMainLooper())
    private var workout = WatchWorkoutState()
    private var connected = false
    private var ready = false
    private var pending = ""
    private var sentVersion = -1L
    private var lastConnectionCheck = 0L
    private var lastResync = 0L
    private var requestStatus = ""
    private var ambient = false
    private var visible = false
    private var displayServiceStarted = false
    private var styledIdle: Boolean? = null
    private val lime = Theme.Colors.primary
    private lateinit var root: FrameLayout
    private lateinit var safeArea: FrameLayout
    private lateinit var scroll: ScrollView
    private lateinit var ambientPanel: LinearLayout
    private lateinit var stage: TextView
    private lateinit var detail: TextView
    private lateinit var timer: TextView
    private lateinit var heartRate: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressRing: WorkoutProgressRing
    private lateinit var playButton: Button
    private lateinit var nextButton: Button
    private lateinit var ambientBattery: TextView
    private lateinit var ambientHour: TextView
    private lateinit var ambientMinute: TextView
    private lateinit var ambientDate: TextView
    private lateinit var ambientStage: TextView
    private lateinit var ambientTime: TextView

    private val callbacks = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            setAmbientMode(true)
        }
        override fun onExitAmbient() {
            val disk = WatchWorkoutState.load(this@WatchActivity)
            if (disk.session > 0L && disk.sequence >= workout.sequence) workout = disk
            setAmbientMode(false)
        }
        override fun onUpdateAmbient() {
            val disk = WatchWorkoutState.load(this@WatchActivity)
            if (disk.session > 0L && disk.sequence >= workout.sequence) workout = disk
            renderAmbient()
        }
    }
    private val ticker = object : Runnable {
        override fun run() {
            if (ambient || !visible) return
            renderInteractive()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        // Allow ambient mode: keeping the display fully awake prevents wrist-down transitions.
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this).apply { setBackgroundColor(Theme.Colors.background) }
        safeArea = FrameLayout(this).apply { tag = "watch_safe_area" }
        root.addView(safeArea)
        root.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val width = right - left
            val height = bottom - top
            // The ring owns the outer band of the round display (full bleed). Content lives in the
            // circle inside that band: 64% wide keeps the bottom button corners and the top title
            // clear of the ring on 396px (40 mm) and 450px (44 mm) Galaxy Watch4 screens.
            val short = minOf(width, height)
            val boxWidth = (short * 0.64f).toInt().coerceAtLeast(1)
            val boxHeight = (short * 0.80f).toInt().coerceAtLeast(1)
            val params = safeArea.layoutParams as FrameLayout.LayoutParams
            if (params.width != boxWidth || params.height != boxHeight) {
                params.width = boxWidth
                params.height = boxHeight
                params.gravity = Gravity.CENTER
                safeArea.layoutParams = params
            }
        }
        progressRing = WorkoutProgressRing(this).apply { tag = "watch_progress_ring" }
        // Full-bleed ring behind the content box.
        root.addView(progressRing, 0, FrameLayout.LayoutParams(-1, -1))
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        stage = text(12, lime, "watch_stage").apply {
            setTypeface(null, Typeface.BOLD)
            contentDescription = "현재 운동 단계 · 길게 눌러 설정"
            setOnLongClickListener { showWatchSettings(); true }
        }
        detail = text(10, Theme.Colors.textMedium, "watch_detail")
        timer = text(26, Theme.Colors.textHigh, "watch_timer").apply {
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { if (workout.restSeconds() > 0 && workout.stageIndex == 2) showRestAdjustments() }
        }
        heartRate = text(10, Theme.Colors.heart, "watch_heart").apply {
            setOnLongClickListener { showHeartZoneSettings(); true }
        }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            tag = "watch_progress"
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(lime)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Theme.Colors.outline)
            visibility = View.GONE // Progress is rendered by circular WorkoutProgressRing.
        }
        body.addView(stage, LinearLayout.LayoutParams(-1, dp(16)))
        body.addView(detail, LinearLayout.LayoutParams(-1, dp(14)))
        body.addView(timer, LinearLayout.LayoutParams(-1, dp(34)))
        body.addView(heartRate, LinearLayout.LayoutParams(-1, dp(14)))
        body.addView(progressBar, LinearLayout.LayoutParams(-1, dp(1)))
        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        playButton = button("시작", "watch_play") {
            // Finished sessions end here; starting a new workout is handled on the phone.
            if (workout.finished) finish() else sendCommand(if (workout.running) "pause" else "start")
        }
        nextButton = button("다음 단계", "watch_next") {
            sendCommand(if (workout.stageIndex == 2 && workout.restSeconds() > 0) "rest_skip"
                else if (workout.stageIndex == 2 && workout.canComplete) "complete" else "next")
        }
        nextButton.setOnLongClickListener {
            AlertDialog.Builder(this).setTitle("운동 조작")
                .setItems(arrayOf("휴식 +15초", "다음 단계로 이동")) { _, which ->
                    sendCommand(if (which == 0) "rest_add" else "next")
                }.setNegativeButton("닫기",null).show()
            true
        }
        buttons.addView(playButton, LinearLayout.LayoutParams(0, dp(36), 1f))
        buttons.addView(nextButton, LinearLayout.LayoutParams(0, dp(36), 1f).apply { leftMargin = dp(4) })
        body.addView(buttons, LinearLayout.LayoutParams(-1, dp(36)).apply { topMargin = dp(8) })
        scroll = ScrollView(this).apply {
            tag = "watch_scroll"
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = true
            addView(body)
        }
        safeArea.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        ambientPanel = LinearLayout(this).apply {
            tag = "watch_ambient"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            // No background: the safe-area box crosses the full-bleed ring at its four corners, and an opaque
            // fill there cut the ring into pieces on the real watch. The black root already provides the backdrop.
            visibility = View.GONE
        }
        // Figma ambient: battery, hour, minute and date stacked in the primary colour. The stage and
        // remaining minutes stay as one small line so the workout is still readable at a glance.
        ambientBattery = text(10, lime, "ambient_battery")
        ambientHour = text(26, Theme.Colors.textHigh, "ambient_hour").apply { setTypeface(null, Typeface.BOLD) }
        ambientMinute = text(26, lime, "ambient_minute").apply { setTypeface(null, Typeface.BOLD) }
        ambientDate = text(10, lime, "ambient_date")
        ambientStage = text(12, lime, "ambient_stage")
        ambientTime = text(10, Theme.Colors.textMedium, "ambient_time")
        // In low-power ambient mode keep just the time, session status and minute-granularity
        // context. Omitting live BPM/seconds prevents stale readings and limits burn-in risk.
        ambientPanel.addView(ambientHour, LinearLayout.LayoutParams(-1, dp(38)))
        ambientPanel.addView(ambientStage, LinearLayout.LayoutParams(-1, dp(20)))
        ambientPanel.addView(ambientTime, LinearLayout.LayoutParams(-1, dp(18)))
        safeArea.addView(ambientPanel, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH))
            lifecycle.addObserver(AmbientLifecycleObserver(this, callbacks))
        applyWorkoutState(WatchWorkoutState.load(this))
        // Animate only on a fresh explicit launch, not after configuration changes or
        // ambient resume. Instrumentation runs bypass the animation for deterministic UI tests.
        if (savedInstanceState == null && !android.app.ActivityManager.isRunningInTestHarness()
            && packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
            && !workout.ongoing && animationsEnabled()) showLaunchIntro()
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), 60)
    }

    /** Removes the launch intro overlay (idempotent; also called when the user taps to skip). */
    private fun dismissLaunchIntro() {
        if (!::root.isInitialized) return
        root.findViewWithTag<View>("watch_intro")?.let { overlay ->
            (overlay.parent as? FrameLayout)?.removeView(overlay)
        }
    }

    private fun showLaunchIntro() {
        val overlay = FrameLayout(this).apply {
            tag = "watch_intro"
            setBackgroundColor(Theme.Colors.background)
            isClickable = true
        }
        // Same Compose sequence and timing as the phone's first-screen intro (~1.7 s, tap to skip).
        overlay.addView(androidx.compose.ui.platform.ComposeView(this).apply {
            setContent { WatchSplash(onFinished = { post { dismissLaunchIntro() } }) }
        }, FrameLayout.LayoutParams(-1, -1))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
    }

    private fun animationsEnabled() = android.provider.Settings.Global.getFloat(
        contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    private fun text(size: Int, color: Int, name: String) = TextView(this).apply {
        tag = name
        textSize = when {
            size >= 26 -> Theme.Type.wearHeadline
            size >= 16 -> Theme.Type.wearTitle
            size >= 12 -> Theme.Type.wearBody
            else -> Theme.Type.wearCaption
        }
        setTextColor(color)
        gravity = Gravity.CENTER
        includeFontPadding = false
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.END
        setAutoSizeTextTypeUniformWithConfiguration(maxOf(9, size - 5), size, 1, TypedValue.COMPLEX_UNIT_SP)
    }
    private fun button(title: String, name: String, action: () -> Unit) = Button(this).apply {
        tag = name
        text = title
        isAllCaps = false
        textSize = Theme.Type.wearBody
        setTypeface(null, Typeface.BOLD)
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        includeFontPadding = false
        setPadding(dp(4), 0, dp(4), 0)
        setTextColor(Theme.Colors.textHigh)
        setSingleLine(true)
        setAutoSizeTextTypeUniformWithConfiguration(9, 12, 1, TypedValue.COMPLEX_UNIT_SP)
        background = Theme.shape(this@WatchActivity,
            Theme.Colors.surfaceElevated, Theme.Radius.control, Theme.Colors.outline)
        setOnClickListener { action() }
    }
    private fun dp(value: Int) = Theme.dp(this, value)
    /** Figma: a lone primary action is filled with onPrimary text; paired actions sit on the elevated surface. */
    private fun styleButton(button: Button, primary: Boolean) {
        button.background = Theme.shape(this, if (primary) lime else Theme.Colors.surfaceElevated,
            Theme.Radius.control, if (primary) null else Theme.Colors.outline)
        button.setTextColor(if (primary) Theme.Colors.onPrimary else Theme.Colors.textHigh)
        button.setAutoSizeTextTypeUniformWithConfiguration(9, if (primary) 16 else 12, 1, TypedValue.COMPLEX_UNIT_SP)
    }

    fun applyWorkoutState(state: WatchWorkoutState) {
        workout = state
        // Never allow the launch intro to cover an already active workout.
        if (state.ongoing || state.finished) dismissLaunchIntro()
        if (ambient) renderAmbient() else renderInteractive()
        if (visible && packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            if (state.ongoing && !displayServiceStarted) {
                state.save(this)
                startForegroundService(Intent(this, WorkoutDisplayService::class.java))
                displayServiceStarted = true
            } else if (!state.ongoing) {
                stopService(Intent(this, WorkoutDisplayService::class.java))
                displayServiceStarted = false
            }
        }
    }
    private fun renderInteractive() {
        val idle = !workout.hasSession && !workout.finished
        stage.text = when {
            idle -> java.text.SimpleDateFormat("MM.dd", java.util.Locale.getDefault()).format(java.util.Date())
            workout.finished -> "운동 완료"
            else -> workout.stageLabel
        }
        stage.setTextColor(if (idle) Theme.Colors.textHigh else lime)
        stage.contentDescription = "현재 단계 ${stage.text} · 길게 눌러 설정"
        val resting = workout.stageIndex == 2 && workout.restSeconds() > 0 && !workout.finished
        detail.text = when {
            pending.isNotBlank() -> requestStatus
            workout.finished -> "오늘 기록 저장됨"
            resting -> "휴식 · 타이머 탭해 ±15초"
            workout.stageIndex == 2 && workout.hasSession ->
                workout.exerciseName.ifBlank { workout.setLabel } +
                    (if (workout.setTotal > 0) " · ${workout.setNumber}/${workout.setTotal}세트" else "")
            (workout.stageIndex == 1 || workout.stageIndex == 3) && workout.intervalTotal > 0 ->
                "${workout.intervalIndex}/${workout.intervalTotal}구간 · ${workout.detail}"
            workout.running -> workout.detail.ifBlank { "운동 중" }
            workout.hasSession -> "일시정지"
            else -> when (workout.bodyFocus) {
                "upper" -> "상체 · 60분"
                "lower" -> "하체 · 60분"
                else -> "60분 루틴"
            }
        }
        val remaining = when {
            workout.finished -> workout.activeSeconds
            resting -> workout.restSeconds()
            idle -> 60 * 60 // the routine is a fixed 60 minutes
            else -> workout.secondsRemaining()
        }.coerceAtLeast(0)
        val strengthTarget = !resting && workout.stageIndex == 2 && !workout.finished && workout.targetReps > 0
        timer.text = if (strengthTarget) {
            // Tight round displays must show the complete weight and rep target, not an ellipsis.
            val weight = workout.setWeight.takeIf { it.isNotBlank() }?.let { "${it}kg×" } ?: ""
            weight + "${workout.targetReps}회"
        } else "%02d:%02d".format(remaining / 60, remaining % 60)
        timer.textSize = if (strengthTarget) Theme.Type.wearTitle else Theme.Type.wearHeadline
        timer.contentDescription = when {
            resting -> "남은 휴식 ${timer.text} · 눌러서 15초 늘리기 또는 줄이기"
            strengthTarget -> "현재 세트 ${timer.text}, 단계 남은 시간 ${remaining / 60}분 ${remaining % 60}초"
            else -> "운동 시간 ${timer.text}"
        }
        progressBar.progress = when {
            workout.finished -> 1000
            workout.stageIndex == 2 && workout.totalSets > 0 -> workout.completedSets.coerceIn(0,workout.totalSets)*1000/workout.totalSets
            workout.running && workout.stageIndex >= 0 -> (workout.stageIndex * 250).coerceAtMost(1000)
            else -> 0
        }
        progressRing.progress = progressBar.progress
        val prefs = getSharedPreferences("live_heart_rate", MODE_PRIVATE)
        val bpm = prefs.getInt("bpm", 0)
        val age = System.currentTimeMillis() - prefs.getLong("measured_at", 0L)
        val averagePrefs = getSharedPreferences("live_heart_rate", MODE_PRIVATE)
        val sampleCount = if (averagePrefs.getLong("session",0L) == workout.session) averagePrefs.getInt("sample_count",0) else 0
        val sampleAverage = if (sampleCount > 0) averagePrefs.getLong("sample_total",0L)/sampleCount else 0L
        val maxHeart = getSharedPreferences("watch_settings", MODE_PRIVATE).getInt("max_heart_rate",0)
        val zone = if (maxHeart in 120..230 && bpm in 30..240) ((bpm * 100 / maxHeart - 40) / 10).coerceIn(1,5) else 0
        heartRate.text = when {
            // Finished: show only the real heart-rate outcome. With no samples, explain why and what to do.
            workout.finished -> if (sampleCount > 0) "평균 ${sampleAverage}bpm" else "워치 착용 시 심박이 기록돼요"
            !connected -> "폰 연결 끊김"
            bpm in 30..240 && age in 0L..10_000L -> if (zone > 0) "♥ $bpm bpm · 추정 Z$zone" else "♥ $bpm bpm"
            else -> "♥ 연결대기"
        }
        heartRate.setTextColor(if (workout.finished) Theme.Colors.textMedium else Theme.Colors.heart)
        heartRate.contentDescription = if (bpm in 30..240 && age in 0L..10_000L)
            "심박수 $bpm, ${age / 1000}초 전 측정" else "심박수 연결대기"
        playButton.text = if (workout.finished) "종료" else if (workout.running) "일시정지" else "시작"
        if (styledIdle != idle) {
            styledIdle = idle
            styleButton(playButton, idle)
            styleButton(nextButton, false)
        }
        detail.contentDescription = detail.text.toString() + (if ((workout.stageIndex == 1 || workout.stageIndex == 3) && workout.upcomingInterval.isNotBlank()) " · 다음 구간 ${workout.upcomingInterval}" else "")
        nextButton.text = when {
            workout.stageIndex == 3 -> "운동 완료"
            workout.stageIndex == 2 && workout.restSeconds() > 0 -> "건너뛰기"
            workout.stageIndex == 2 && workout.canComplete -> "세트 완료"
            else -> "다음 단계"
        }
        val enabled = connected && ready && pending.isBlank() && workout.protocol >= 26
        playButton.isEnabled = workout.finished || enabled
        // Idle shows only the primary "시작"; the second action needs a running or paused session.
        nextButton.visibility = if (workout.finished || idle) View.GONE else View.VISIBLE
        nextButton.isEnabled = !workout.finished && !idle && enabled
        nextButton.contentDescription = (if (nextButton.text.toString() == "건너뛰기") "휴식 건너뛰기" else nextButton.text.toString()) + " · 길게 눌러 추가 조작"
    }
    fun setAmbientMode(enabled: Boolean) {
        // Wrist-down/ambient transitions must not be obscured by the startup overlay.
        if (enabled) dismissLaunchIntro()
        ambient = enabled
        progressRing.ambientStyle = enabled
        scroll.visibility = if (enabled) View.GONE else View.VISIBLE
        ambientPanel.visibility = if (enabled) View.VISIBLE else View.GONE
        handler.removeCallbacks(ticker)
        if (enabled) {
            listOf(ambientBattery, ambientHour, ambientMinute, ambientDate, ambientStage, ambientTime)
                .forEach { it.paint.isAntiAlias = false }
            renderAmbient()
        } else {
            renderInteractive()
            if (visible) handler.post(ticker)
        }
    }
    private fun renderAmbient() {
        val now = java.util.Date()
        val locale = java.util.Locale.getDefault()
        val battery = (getSystemService(BATTERY_SERVICE) as? android.os.BatteryManager)
            ?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        ambientBattery.text = if (battery in 0..100) "⚡$battery%" else ""
        ambientHour.text = java.text.SimpleDateFormat("HH:mm", locale).format(now)
        // Keep the existing stage tag for accessibility and watch screen tests.
        ambientStage.text = when {
            workout.finished -> "운동 완료"
            workout.running && workout.restSeconds() > 0 -> "세트 간 휴식"
            workout.running -> workout.stageLabel
            workout.hasSession -> "일시정지"
            else -> "운동 대기"
        }
        // Low-power ambient updates are infrequent; never imply real-time seconds or BPM.
        val rest = workout.restSeconds()
        val minutes = ((if (rest > 0) rest else workout.secondsRemaining()) + 59) / 60
        ambientTime.text = when {
            workout.finished -> "총 ${(workout.activeSeconds + 30) / 60}분 수행"
            workout.running && rest > 0 -> "남은 약 ${minutes}분"
            workout.running -> "남은 약 ${minutes}분"
            workout.hasSession -> workout.stageLabel
            else -> "휴대폰에서 시작"
        }
    }
    private fun showRestAdjustments() {
        if (workout.stageIndex != 2 || workout.restSeconds() <= 0 || workout.finished) return
        AlertDialog.Builder(this).setTitle("세트 휴식 조절")
            .setItems(arrayOf("15초 줄이기", "15초 늘리기", "휴식 건너뛰기")) { _, which ->
                sendCommand(when (which) { 0 -> "rest_sub"; 1 -> "rest_add"; else -> "rest_skip" })
            }.setNegativeButton("닫기",null).show()
    }

    private fun showHeartZoneSettings() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            hint = "최대 심박수 (120~230)"
            val current = getSharedPreferences("watch_settings",MODE_PRIVATE).getInt("max_heart_rate",0)
            if (current > 0) setText(current.toString())
        }
        AlertDialog.Builder(this).setTitle("심박 구간 기준")
            .setMessage("직접 설정한 최대 심박수 대비 추정 구간을 표시합니다. 진단용 측정값이 아닙니다.")
            .setView(input)
            .setNegativeButton("취소",null)
            .setPositiveButton("저장") { _, _ ->
                val value = input.text.toString().toIntOrNull()
                if (value != null && value in 120..230) getSharedPreferences("watch_settings",MODE_PRIVATE).edit().putInt("max_heart_rate",value).apply()
                else Toast.makeText(this,"120~230 사이로 입력하세요",Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun showWatchSettings() {
        AlertDialog.Builder(this).setTitle("MOVE60 설정")
            .setItems(arrayOf("건강 대시보드", "센서 권한 설정", "심박수 측정 중지")) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, com.move60.watch.health.WearHealthActivity::class.java))
                    1 -> openSensorSettings()
                    else -> stopService(Intent(this, HeartRateService::class.java))
                }
            }.setNegativeButton("닫기", null).show()
    }

    private fun registerHeartRateIfPermitted() {
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) return
        try {
            startForegroundService(Intent(this, HeartRateService::class.java))
        } catch (e: Exception) {
            heartRate.text = "♥ 심박수 연결 실패"
        }
    }

    private fun openSensorSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")))
    }

    private fun explainBackgroundAccessOnce() {
        val prefs = getSharedPreferences("watch_settings", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BODY_SENSORS_BACKGROUND) != PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("background_access_explained", false)) {
            prefs.edit().putBoolean("background_access_explained", true).apply()
            AlertDialog.Builder(this).setTitle("화면 밖 심박수 측정")
                .setMessage("화면을 꺼도 심박수를 계속 전송하려면 앱 권한의 센서를 '항상 허용'으로 설정해 주세요.")
                .setNegativeButton("나중에", null)
                .setPositiveButton("설정 열기") { _, _ -> openSensorSettings() }.show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 60) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                registerHeartRateIfPermitted()
                explainBackgroundAccessOnce()
            } else {
                heartRate.text = "♥ 심박수 권한이 필요합니다"
            }
        }
    }


    override fun onResume() {
        super.onResume()
        visible = true
        HeartRateService.uiVisible = true
        Wearable.getDataClient(this).addListener(this)
        Wearable.getMessageClient(this).addListener(this)
        ready = false
        checkConnection()
        registerHeartRateIfPermitted()
        applyWorkoutState(WatchWorkoutState.load(this))
        if (!ambient) handler.post(ticker)
        explainBackgroundAccessOnce()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getSharedPreferences("watch_settings", MODE_PRIVATE).getBoolean("notification_asked", false)) {
            getSharedPreferences("watch_settings", MODE_PRIVATE).edit().putBoolean("notification_asked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 63)
        }
        Wearable.getDataClient(this).getDataItems().addOnSuccessListener { items ->
            try { for (item in items) readState(item) } finally { items.release() }
        }
    }
    override fun onPause() {
        visible = false
        HeartRateService.uiVisible = false
        Wearable.getDataClient(this).removeListener(this)
        Wearable.getMessageClient(this).removeListener(this)
        handler.removeCallbacks(ticker)
        super.onPause()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    private fun readState(item: DataItem) {
        if (item.uri.path != "/move60/workout") return
        val state = WatchWorkoutState.fromData(DataMapItem.fromDataItem(item).dataMap)
        if (!state.save(this)) return
        applyWorkoutState(state)
    }
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) if (event.type == DataEvent.TYPE_CHANGED) readState(event.dataItem)
    }
    private fun checkConnection() {
        lastConnectionCheck = System.currentTimeMillis()
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            val wasConnected = connected
            connected = nodes.isNotEmpty()
            if (!connected) {
                ready = false
                pending = ""
                requestStatus = "휴대폰 연결 끊김 · 기록 상태 유지"
            } else if (!wasConnected || !ready || System.currentTimeMillis()-lastResync > 30_000L) {
                lastResync = System.currentTimeMillis()
                requestStatus = "휴대폰 상태 확인 중"
                val request = org.json.JSONObject().put("op","resync").put("id","resync").toString()
                Wearable.getMessageClient(this).sendMessage(nodes.first().id,"/move60/command",request.toByteArray())
            }
        }.addOnFailureListener { connected=false; ready=false; requestStatus="휴대폰 연결 확인" }
    }
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != "/move60/ack") return
        val ack = try { org.json.JSONObject(event.data.toString(Charsets.UTF_8)) } catch (_: Exception) { return }
        runOnUiThread {
            if (ack.optString("id") == "resync") {
                ready = true
                requestStatus = if (workout.protocol < 26) "폰·워치 모두 v26 업데이트 필요" else ""
            } else if (ack.optString("id") == pending) {
                pending = ""
                requestStatus = if (ack.optBoolean("accepted")) "" else "상태 변경됨 · 다시 선택하세요"
                lastResync = 0L
            }
            renderInteractive()
        }
    }
    private fun sendCommand(command: String) {
        if (!connected || !ready || pending.isNotEmpty() || workout.protocol < 26) {
            requestStatus = "휴대폰 연결 후 다시 선택하세요"
            checkConnection()
            return
        }
        val id = java.util.UUID.randomUUID().toString()
        pending = id
        sentVersion = workout.controlVersion
        requestStatus = "휴대폰에 요청 중"
        renderInteractive()
        val request = org.json.JSONObject().put("id",id).put("op",command).put("at",System.currentTimeMillis())
            .put("day",workout.day).put("session",workout.session).put("version",workout.controlVersion).put("stage",workout.stageIndex)
            .put("exercise",workout.exerciseId).put("set",workout.setNumber).toString()
        // MessageClient sends only to a currently connected phone; commands are never queued offline.
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) { pending=""; ready=false; requestStatus="휴대폰 연결 확인"; return@addOnSuccessListener }
            Wearable.getMessageClient(this).sendMessage(nodes.first().id,"/move60/command",request.toByteArray(Charsets.UTF_8))
                .addOnFailureListener { pending=""; ready=false; requestStatus="전송 실패 · 다시 연결하세요" }
        }.addOnFailureListener { pending=""; ready=false; requestStatus="휴대폰 연결 확인" }
        handler.postDelayed({
            if (pending == id) {
                pending = ""
                ready = false
                requestStatus = "응답 확인 중 · 상태 다시 연결"
                checkConnection()
            }
        },8_000L)
    }
}
