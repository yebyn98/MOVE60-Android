package com.move60.watch

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
class WatchActivity : ComponentActivity(), DataClient.OnDataChangedListener {
    private val handler = Handler(Looper.getMainLooper())
    private var workout = WatchWorkoutState()
    private var ambient = false
    private var visible = false
    private var displayServiceStarted = false
    private val lime = Color.rgb(180, 245, 110)
    private lateinit var root: FrameLayout
    private lateinit var safeArea: FrameLayout
    private lateinit var scroll: ScrollView
    private lateinit var ambientPanel: LinearLayout
    private lateinit var stage: TextView
    private lateinit var detail: TextView
    private lateinit var timer: TextView
    private lateinit var heartRate: TextView
    private lateinit var playButton: Button
    private lateinit var nextButton: Button
    private lateinit var ambientClock: TextView
    private lateinit var ambientStage: TextView
    private lateinit var ambientTime: TextView

    private val callbacks = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            setAmbientMode(true)
        }
        override fun onExitAmbient() {
            workout = WatchWorkoutState.load(this@WatchActivity)
            setAmbientMode(false)
        }
        override fun onUpdateAmbient() {
            workout = WatchWorkoutState.load(this@WatchActivity)
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
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // Allow ambient mode: keeping the display fully awake prevents wrist-down transitions.
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        safeArea = FrameLayout(this).apply { tag = "watch_safe_area" }
        root.addView(safeArea)
        root.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val width = right - left
            val height = bottom - top
            val side = ((minOf(width, height) * 0.7071f).toInt() - dp(4)).coerceAtLeast(1)
            val params = safeArea.layoutParams as FrameLayout.LayoutParams
            if (params.width != side || params.height != side) {
                params.width = side
                params.height = side
                params.gravity = Gravity.CENTER
                safeArea.layoutParams = params
            }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        stage = text(13, lime, "watch_stage").apply {
            setTypeface(null, Typeface.BOLD)
            contentDescription = "현재 운동 단계 · 길게 눌러 설정"
            setOnLongClickListener { showWatchSettings(); true }
        }
        detail = text(10, Color.LTGRAY, "watch_detail")
        timer = text(30, Color.WHITE, "watch_timer")
        heartRate = text(12, Color.rgb(255, 145, 145), "watch_heart")
        body.addView(stage, LinearLayout.LayoutParams(-1, dp(18)))
        body.addView(detail, LinearLayout.LayoutParams(-1, dp(14)))
        body.addView(timer, LinearLayout.LayoutParams(-1, dp(32)))
        body.addView(heartRate, LinearLayout.LayoutParams(-1, dp(18)))
        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        playButton = button("시작", "watch_play") {
            sendCommand(if (workout.running) "pause" else "start")
        }
        nextButton = button("다음 단계", "watch_next") { sendCommand("next") }
        buttons.addView(playButton, LinearLayout.LayoutParams(0, dp(44), 1f))
        buttons.addView(nextButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(4) })
        body.addView(buttons, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(4) })
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
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        ambientClock = text(12, Color.LTGRAY, "ambient_clock")
        ambientStage = text(13, Color.LTGRAY, "ambient_stage")
        ambientTime = text(20, Color.LTGRAY, "ambient_time")
        ambientPanel.addView(ambientClock, LinearLayout.LayoutParams(-1, dp(20)))
        ambientPanel.addView(ambientStage, LinearLayout.LayoutParams(-1, dp(22)))
        ambientPanel.addView(ambientTime, LinearLayout.LayoutParams(-1, dp(28)))
        ambientPanel.addView(text(10, Color.GRAY, "ambient_hint").apply { text = "손목을 들어 조작" },
            LinearLayout.LayoutParams(-1, dp(18)))
        safeArea.addView(ambientPanel, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH))
            lifecycle.addObserver(AmbientLifecycleObserver(this, callbacks))
        applyWorkoutState(WatchWorkoutState.load(this))
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), 60)
    }

    private fun text(size: Int, color: Int, name: String) = TextView(this).apply {
        tag = name
        textSize = size.toFloat()
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
        textSize = 11f
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        includeFontPadding = false
        setPadding(dp(2), 0, dp(2), 0)
        setTextColor(Color.WHITE)
        setSingleLine(true)
        setAutoSizeTextTypeUniformWithConfiguration(9, 11, 1, TypedValue.COMPLEX_UNIT_SP)
        background = GradientDrawable().apply {
            setColor(Color.rgb(34, 51, 65))
            cornerRadius = dp(12).toFloat()
        }
        setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    fun applyWorkoutState(state: WatchWorkoutState) {
        workout = state
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
        stage.text = if (workout.finished) "운동 완료" else workout.stageLabel
        stage.contentDescription = "현재 단계 ${stage.text} · 길게 눌러 설정"
        detail.text = when {
            workout.finished -> "오늘 운동을 마쳤어요"
            workout.running -> workout.detail.ifBlank { "운동 중" }
            workout.hasSession -> "일시정지"
            else -> "휴대폰 운동과 연결 대기"
        }
        val remaining = workout.secondsRemaining()
        timer.text = "%02d:%02d".format(remaining / 60, remaining % 60)
        val prefs = getSharedPreferences("live_heart_rate", MODE_PRIVATE)
        val bpm = prefs.getInt("bpm", 0)
        val age = System.currentTimeMillis() - prefs.getLong("measured_at", 0L)
        heartRate.text = if (bpm in 30..240 && age in 0L..10_000L) "♥ $bpm bpm" else "♥ 연결대기"
        heartRate.contentDescription = if (bpm in 30..240 && age in 0L..10_000L)
            "심박수 $bpm, ${age / 1000}초 전 측정" else "심박수 연결대기"
        playButton.text = if (workout.running) "일시정지" else if (workout.finished) "새 운동" else "시작"
        nextButton.text = if (workout.stageIndex == 3) "운동 완료" else "다음 단계"
        nextButton.isEnabled = !workout.finished
    }
    fun setAmbientMode(enabled: Boolean) {
        ambient = enabled
        scroll.visibility = if (enabled) View.GONE else View.VISIBLE
        ambientPanel.visibility = if (enabled) View.VISIBLE else View.GONE
        handler.removeCallbacks(ticker)
        if (enabled) {
            listOf(ambientClock, ambientStage, ambientTime).forEach { it.paint.isAntiAlias = false }
            renderAmbient()
        } else {
            renderInteractive()
            if (visible) handler.post(ticker)
        }
    }
    private fun renderAmbient() {
        ambientClock.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        ambientStage.text = if (workout.finished) "운동 완료" else workout.stageLabel
        // Ambient refresh is infrequent. Do not show stale seconds or live BPM.
        val minutes = (workout.secondsRemaining() + 59) / 60
        ambientTime.text = if (workout.running) "남은 약 ${minutes}분" else if (workout.hasSession) "일시정지" else "운동 대기"
    }
    private fun showWatchSettings() {
        AlertDialog.Builder(this).setTitle("MOVE60 설정")
            .setItems(arrayOf("센서 권한 설정", "심박수 측정 중지")) { _, which ->
                if (which == 0) openSensorSettings()
                else stopService(Intent(this, HeartRateService::class.java))
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
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
        state.save(this)
        applyWorkoutState(state)
    }
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) if (event.type == DataEvent.TYPE_CHANGED) readState(event.dataItem)
    }
    private fun sendCommand(command: String) {
        detail.text = "휴대폰에 요청 중"
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) { detail.text = "휴대폰 연결 확인"; return@addOnSuccessListener }
            for (node in nodes) Wearable.getMessageClient(this)
                .sendMessage(node.id, "/move60/command", command.toByteArray(Charsets.UTF_8))
                .addOnFailureListener { detail.text = "요청 전송 실패" }
        }.addOnFailureListener { detail.text = "휴대폰 연결 확인" }
    }
}
