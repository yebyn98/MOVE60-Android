package com.move60.workout.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.move60.workout.Move60Theme
import com.move60.workout.ui.Move60Button
import com.move60.workout.ui.Move60Card
import com.move60.workout.ui.Move60ComposeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Data access is only initiated by a visible and explicit user action. */
@AndroidEntryPoint
class HealthConsentActivity : ComponentActivity() {
    @Inject lateinit var gateway: HealthConnectGateway
    private var status by mutableStateOf("연동을 사용하려면 아래에서 읽기 권한에 동의하세요.")
    private var summary by mutableStateOf("개인 건강 데이터는 외부 서버로 전송하지 않아요.")

    private val requestPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        if (granted.containsAll(HealthConnectGateway.readPermissions)) {
            gateway.enable()
            readAndSend()
        } else {
            status = "심박수 읽기 권한이 필요해요."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val palette = Move60Theme.Colors
            Move60ComposeTheme {
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("건강 데이터 연동", style = MaterialTheme.typography.headlineSmall)
                    Text("삼성 헬스 → Health Connect → MOVE60 → 내 워치",
                        color = Color(palette.textMedium))
                    Move60Card {
                        Text("동의 항목: 심박수 기록(읽기 전용)")
                        Text(status, color = Color(palette.primary))
                        Text(summary, color = Color(palette.textMedium))
                    }
                    Move60Button(onClick = { consentOrRead() }, modifier = Modifier.fillMaxWidth()) {
                        Text("동의하고 건강 기록 불러오기")
                    }
                    Move60Button(primary = false, onClick = {
                        gateway.disable()
                        status = "연동 중지 · 워치에 공유한 기록을 지우도록 요청했어요."
                        summary = "Health Connect 권한은 휴대폰 설정에서 별도로 철회할 수 있어요."
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("MOVE60 연동 중지 및 워치 데이터 초기화")
                    }
                    Move60Button(primary = false, onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                        Text("닫기")
                    }
                    Text("삼성 헬스 데이터 SDK의 직접 연동은 별도 파트너 등록과 AAR이 필요해요. 이 화면은 지원되는 Health Connect 동기화 방식입니다.",
                        color = Color(palette.textMedium))
                }
            }
        }
        if (!gateway.supported()) {
            status = "Health Connect가 설치·설정돼 있지 않거나 이 기기에서 지원되지 않아요."
        }
    }

    override fun onStart() {
        super.onStart()
        // Samsung Health/Health Connect permission can be revoked outside MOVE60 at any time.
        lifecycleScope.launch {
            if (gateway.isOptedIn()) {
                try {
                    if (!gateway.permissionsGranted()) {
                        gateway.disable()
                        status = "건강 데이터 권한이 철회되어 워치 공유를 중지했어요."
                        summary = "다시 연결하려면 읽기 권한을 허용해 주세요."
                    } else {
                        // Existing opt-in is sufficient to refresh data when this screen reopens.
                        readAndSend()
                    }
                } catch (_: Exception) {
                    // Fail closed: do not keep showing previously approved health data.
                    gateway.disable()
                    status = "건강 데이터 권한 확인이 불가능해 공유를 중지했어요."
                }
            }
        }
    }

    private fun consentOrRead() {
        if (!gateway.supported()) {
            status = "삼성 헬스와 Health Connect를 설치·업데이트한 뒤 연동해 주세요."
            return
        }
        lifecycleScope.launch {
            try {
                if (gateway.permissionsGranted()) {
                    gateway.enable()
                    readAndSend()
                } else requestPermissions.launch(HealthConnectGateway.readPermissions)
            } catch (error: Exception) {
                status = "권한 확인 실패: Health Connect 연결 상태를 확인해 주세요."
            }
        }
    }

    private fun readAndSend() {
        lifecycleScope.launch {
            status = "동의한 심박수 기록을 확인하고 있어요."
            try {
                val metrics = gateway.readWithConsent()
                gateway.sendToWatch(metrics, true)
                if (metrics.lastHeartBpm != null) {
                    val measured = Instant.ofEpochMilli(metrics.heartMeasuredAt)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))
                    val source = if (metrics.sourcePackage == "com.sec.android.app.shealth")
                        "삼성 헬스" else metrics.sourcePackage ?: "출처 미확인"
                    summary = "최근 심박수: ${metrics.lastHeartBpm} bpm · $measured · $source"
                    status = "Health Connect 읽기 완료 · 워치에 공유를 요청했어요."
                } else {
                    summary = "최근 7일 내 읽을 수 있는 심박수 기록이 없어요. Health Connect의 데이터 및 접근 권한에서 기록 출처와 동기화를 확인해 주세요."
                    status = "권한은 확인됐지만 심박수 데이터가 없어요."
                }
            } catch (error: SecurityException) {
                gateway.disable()
                status = "권한이 변경되어 연동을 중지했어요. 다시 동의해 주세요."
            } catch (error: Exception) {
                status = "기록을 읽지 못했어요. 삼성 헬스 동기화 상태를 확인해 주세요."
            }
        }
    }
}
