package com.move60.watch

import android.app.Activity
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
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType

/** Wear OS companion displays the current phone workout stage and countdown. */
class WatchActivity : Activity(), DataClient.OnDataChangedListener {
    private lateinit var title: TextView
    private lateinit var timer: Chronometer
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var playButton: Button
    private lateinit var heartRate: TextView
    private val measureClient by lazy { HealthServices.getClient(this).measureClient }
    private val heartRateCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) = Unit
        override fun onDataReceived(data: DataPointContainer) {
            val bpm = data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value?.toInt() ?: return
            runOnUiThread { heartRate.text = "♥ $bpm bpm" }
            sendHeartRate(bpm)
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
        heartRate = TextView(this).apply { text = "♥ 심박수 연결 중"; textSize = 16f; setTextColor(Color.rgb(255, 120, 130)); gravity = Gravity.CENTER }
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
        setContentView(container)
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), 60)
        }
    }

    private var heartRateRegistered = false

    private fun registerHeartRateIfPermitted() {
        if (heartRateRegistered || checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) return
        heartRateRegistered = true
        try {
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, heartRateCallback)
                .addOnFailureListener {
                    heartRateRegistered = false
                    runOnUiThread { heartRate.text = "♥ 심박수 연결 실패" }
                }
        } catch (e: Exception) {
            heartRateRegistered = false
            heartRate.text = "♥ 심박수 연결 실패"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 60) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                registerHeartRateIfPermitted()
            } else {
                heartRate.text = "♥ 심박수 권한이 필요합니다"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        registerHeartRateIfPermitted()
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
        Wearable.getDataClient(this).removeListener(this)
        if (heartRateRegistered) {
            heartRateRegistered = false
            measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, heartRateCallback)
        }
        super.onPause()
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
    private fun sendHeartRate(bpm: Int) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node -> Wearable.getMessageClient(this)
                .sendMessage(node.id, "/move60/command", "heart:$bpm".toByteArray(Charsets.UTF_8)) }
        }
    }

    private fun showState(running: Boolean, stage: String, deadline: Long) {
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
