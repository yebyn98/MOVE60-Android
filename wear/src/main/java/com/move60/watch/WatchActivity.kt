package com.move60.watch

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.widget.ScrollView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.Chronometer
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import android.Manifest
import android.content.pm.PackageManager

/** Wear OS companion displays the current phone workout stage and countdown. */
class WatchActivity : Activity(), DataClient.OnDataChangedListener {
    private lateinit var title: TextView
    private lateinit var timer: Chronometer
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var playButton: Button
    private lateinit var heartRate: TextView
    private val heartTicker = object : Runnable {
        override fun run() {
            val prefs = getSharedPreferences("live_heart_rate", MODE_PRIVATE)
            val bpm = prefs.getInt("bpm", 0)
            val age = System.currentTimeMillis() - prefs.getLong("measured_at", 0L)
            heartRate.text = if (bpm in 30..240 && age in 0L..10_000L)
                "♥ $bpm bpm · ${age / 1000}초 전" else "♥ 연결대기"
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(18, 18, 18, 18)
            setBackgroundColor(Color.rgb(20, 29, 39))
        }
        title = TextView(this).apply { text = "MOVE60"; textSize = 20f; setTextColor(Color.rgb(180, 245, 110)); gravity = Gravity.CENTER }
        timer = Chronometer(this).apply { textSize = 32f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setCountDown(true) }
        status = TextView(this).apply { text = "휴대폰 운동 시작을 기다리는 중"; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        container.addView(title)
        container.addView(timer)
        container.addView(status)
        heartRate = TextView(this).apply { text = "♥ 연결대기"; textSize = 16f; setTextColor(Color.rgb(255, 120, 130)); gravity = Gravity.CENTER }
        container.addView(heartRate)
        playButton = Button(this).apply {
            text = "▶ 시작"
            setOnClickListener { sendCommand(if (text == "Ⅱ 일시정지") "pause" else "start") }
        }
        val nextButton = Button(this).apply {
            text = "다음 단계 →"
            setOnClickListener { sendCommand("next") }
        }
        container.addView(playButton)
        container.addView(nextButton)
        val backgroundAccess = TextView(this).apply {
            text = "화면 밖 심박 측정 권한 설정"
            textSize = 11f
            setTextColor(Color.rgb(180, 245, 110))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
            setOnClickListener { openSensorSettings() }
        }
        if (Build.VERSION.SDK_INT >= 33) container.addView(backgroundAccess)
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(container) })
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), 60)
        }
    }

    private fun registerHeartRateIfPermitted() {
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) return
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
        HeartRateService.uiVisible = true
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getSharedPreferences("watch_settings", MODE_PRIVATE).getBoolean("notification_asked", false)) {
            getSharedPreferences("watch_settings", MODE_PRIVATE).edit().putBoolean("notification_asked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 63)
        }
        Wearable.getDataClient(this).addListener(this)
        registerHeartRateIfPermitted()
        handler.removeCallbacks(heartTicker)
        handler.post(heartTicker)
        explainBackgroundAccessOnce()
        Wearable.getDataClient(this).getDataItems().addOnSuccessListener { items ->
            try {
                for (item in items) if (item.uri.path == "/move60/workout") {
                    showState(DataMapItem.fromDataItem(item).dataMap.getBoolean("running"),
                        DataMapItem.fromDataItem(item).dataMap.getString("stage") ?: "",
                        DataMapItem.fromDataItem(item).dataMap.getLong("deadline"))
                }
            } finally { items.release() }
        }
    }
    override fun onPause() {
        HeartRateService.uiVisible = false
        Wearable.getDataClient(this).removeListener(this)
        handler.removeCallbacks(heartTicker)
        super.onPause()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == com.google.android.gms.wearable.DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == "/move60/workout") {
                val data = DataMapItem.fromDataItem(event.dataItem).dataMap
                runOnUiThread { showState(data.getBoolean("running"), data.getString("stage") ?: "", data.getLong("deadline")) }
            }
        }
    }
    private fun sendCommand(command: String) {
        status.text = "휴대폰에 요청 전달 중…"
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                status.text = "연결된 휴대폰이 없습니다"
                return@addOnSuccessListener
            }
            for (node in nodes) {
                Wearable.getMessageClient(this)
                    .sendMessage(node.id, "/move60/command", command.toByteArray(Charsets.UTF_8))
                    .addOnFailureListener { status.text = "요청 전송 실패 · 연결을 확인하세요" }
            }
        }.addOnFailureListener { status.text = "휴대폰 연결 확인 실패" }
    }
    private fun showState(running: Boolean, stage: String, deadline: Long) {
        if (running) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        title.text = if (running) stage else "MOVE60"
        status.text = if (running) "휴대폰 운동과 동기화됨" else "운동 대기 중"
        playButton.text = if (running) "Ⅱ 일시정지" else "▶ 시작"
        timer.stop()
        if (running) {
            timer.base = SystemClock.elapsedRealtime() + (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
            timer.start()
        } else timer.text = "00:00"
    }
}
