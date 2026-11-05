package com.move60.watch.health

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.horologist.compose.layout.fillMaxRectangle
import com.move60.watch.HeartRateService
import com.move60.watch.Move60Theme
import com.move60.watch.WatchActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Safe Wear OS circular health summary. Only real sensor and consented phone-sync readings.
 * Samsung Health Data SDK is Android PHONE only and Vascular Load is unavailable on Watch4.
 */
class WearHealthActivity : ComponentActivity(), DataClient.OnDataChangedListener {
    data class HealthState(
        val liveBpm: Int? = null,
        val heartStatus: String = "측정 대기",
        val sharedBpm: Int? = null,
        val healthConsent: Boolean = false,
        val measuredAt: Long = 0L
    )

    private var displayState by mutableStateOf(HealthState())
    private val prefs by lazy { getSharedPreferences("move60_phone_health_v1", MODE_PRIVATE) }
    private val sensorPrefs by lazy { getSharedPreferences("live_heart_rate", MODE_PRIVATE) }

    private val requestSensor = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        if (allowed) startLiveHeartRate()
        else displayState = displayState.copy(heartStatus = "심박수 접근 권한이 필요해요")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HealthScreen(
                state = displayState,
                onStartSensor = { requestHeartRate() },
                onBack = { finish() }
            )
        }
        refreshFromSaved()
        Wearable.getDataClient(this).getDataItems().addOnSuccessListener { items ->
            try {
                for (item in items) {
                    if (item.uri.path == HEALTH_PATH) readDataItem(DataMapItem.fromDataItem(item).dataMap)
                }
            } finally { items.release() }
        }
    }

    override fun onStart() {
        super.onStart()
        HeartRateService.uiVisible = true
        Wearable.getDataClient(this).addListener(this)
        lifecycleScope.launch {
            while (isActive && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                refreshFromSaved()
                delay(2_000L)
            }
        }
    }

    override fun onStop() {
        Wearable.getDataClient(this).removeListener(this)
        HeartRateService.uiVisible = false
        super.onStop()
    }

    private fun requestHeartRate() {
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED)
            startLiveHeartRate()
        else requestSensor.launch(Manifest.permission.BODY_SENSORS)
    }

    private fun startLiveHeartRate() {
        try {
            startForegroundService(Intent(this, HeartRateService::class.java))
        } catch (_: Exception) {
            displayState = displayState.copy(heartStatus = "센서 연결 실패 · 워치 권한을 확인해 주세요")
        }
    }

    private fun refreshFromSaved() {
        val now = System.currentTimeMillis()
        val freshAt = sensorPrefs.getLong("measured_at", 0L)
        val bpm = sensorPrefs.getInt("bpm", 0).takeIf {
            it in 30..240 && now - freshAt in 0L..20_000L
        }
        // Expire shared metrics if the phone has not refreshed them within 24 hours.
        // This avoids silently presenting stale health records as current.
        val lastSynced = prefs.getLong("syncedAt", 0L)
        val consented = prefs.getBoolean("consented", false) &&
            lastSynced > 0L && now - lastSynced in 0L..86_400_000L
        displayState = HealthState(
            liveBpm = bpm,
            heartStatus = sensorPrefs.getString("status", "센서 시작 전") ?: "센서 시작 전",
            sharedBpm = if (consented) prefs.getInt("lastHeartBpm", 0).takeIf { it in 30..240 } else null,
            healthConsent = consented,
            measuredAt = freshAt
        )
    }

    private fun readDataItem(map: com.google.android.gms.wearable.DataMap) {
        val consented = map.getBoolean("consented")
        val editor = prefs.edit().clear().putBoolean("consented", consented)
        if (consented) {
            editor.putInt("lastHeartBpm", map.getInt("lastHeartBpm"))
                .putLong("heartMeasuredAt", map.getLong("heartMeasuredAt"))
                .putLong("syncedAt", map.getLong("syncedAt"))
        }
        editor.apply()
        refreshFromSaved()
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == HEALTH_PATH)
                readDataItem(DataMapItem.fromDataItem(event.dataItem).dataMap)
        }
    }

    companion object {
        const val HEALTH_PATH = "/move60/health"
    }
}

private val brand = Color(0xFF00E676)
private val secondary = Color(0xFFB0B0B0)
private val surface = Color(0xFF121212)
private val foreground = Color.White

@Composable
fun HealthScreen(
    state: WearHealthActivity.HealthState,
    onStartSensor: () -> Unit,
    onBack: () -> Unit
) {
    Box(Modifier.fillMaxSize().testTag("wear_health_root").background(Color.Black), contentAlignment = Alignment.Center) {
        // Horologist preserves the safe square within a round display (Watch4 small).
        Box(Modifier.fillMaxRectangle(), contentAlignment = Alignment.Center) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Text("MOVE60 · 건강", color = brand, fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, maxLines = 1)
                }
                item {
                    HealthCard {
                        Text("현재 심박수", color = secondary, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RingMeter(state.liveBpm?.div(200f)?.coerceIn(0f, 1f) ?: 0f)
                            Column {
                                Text(state.liveBpm?.toString() ?: "—",
                                    color = foreground, fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("bpm · 센서", color = secondary, fontSize = 10.sp)
                            }
                        }
                        Text(if (state.liveBpm != null) "워치 실측값" else state.heartStatus,
                            color = secondary, fontSize = 11.sp, maxLines = 2)
                        Text(
                            if (state.healthConsent)
                                "삼성 헬스 최근 기록: " + (state.sharedBpm?.let { "$it bpm" } ?: "기록 없음")
                            else "삼성 헬스 기록: 동의 필요",
                            color = secondary, fontSize = 10.sp, maxLines = 2
                        )
                    }
                }
                item {
                    androidx.wear.compose.material.Chip(
                        onClick = onStartSensor,
                        label = { Text("심박수 측정 시작", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.wear.compose.material.ChipDefaults.primaryChipColors(
                            backgroundColor = brand,
                            contentColor = Color.Black
                        )
                    )
                }
                item {
                    Text(
                        if (state.healthConsent) "휴대폰에서 동의된 기록만 표시"
                        else "휴대폰 MOVE60 설정 > 건강 데이터 연동에서 동의",
                        fontSize = 10.sp, color = secondary, textAlign = TextAlign.Center,
                        maxLines = 3
                    )
                }
                item {
                    androidx.wear.compose.material.Chip(
                        onClick = onBack,
                        label = { Text("운동 화면으로 돌아가기", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(surface, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}

@Composable
private fun RingMeter(value: Float) {
    Canvas(Modifier.size(52.dp)) {
        val width = 5.dp.toPx()
        drawArc(Color(0xFF303030), -90f, 360f, false,
            style = Stroke(width = width, cap = StrokeCap.Round))
        if (value > 0f) drawArc(brand, -90f, 360f * value.coerceIn(0f, 1f), false,
            style = Stroke(width = width, cap = StrokeCap.Round))
    }
}
