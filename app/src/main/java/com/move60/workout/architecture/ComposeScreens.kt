package com.move60.workout.architecture

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.move60.workout.MainActivity
import com.move60.workout.health.HealthConsentActivity
import com.move60.workout.Move60Theme
import java.util.Locale

private val tileShape = RoundedCornerShape(Move60Theme.Radius.card.dp)
private val colorBorder @Composable get() = MaterialTheme.colorScheme.outline
private val secondary @Composable get() = MaterialTheme.colorScheme.secondary

@Composable
private fun Move60Card(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = tileShape,
        border = BorderStroke(1.dp, colorBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
}

@Composable
fun WorkoutDashboard(
    onCalendar: () -> Unit,
    onSettings: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is WorkoutUiEvent.Message) snackbar.showSnackbar(event.message)
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("MOVE60", modifier = Modifier.weight(1f), maxLines = 1,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onCalendar, modifier = Modifier.testTag("compose_calendar_icon")) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = "운동 달력",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onSettings, modifier = Modifier.testTag("compose_settings_icon")) {
                        Icon(Icons.Outlined.Settings, contentDescription = "앱 설정",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Button(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    onClick = { context.startActivity(Intent(context, MainActivity::class.java)) }
                ) {
                    Text(if (state.running) "운동 화면으로 이동" else "운동 상세 및 시작")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Move60Card {
                    Text("이번 주 운동 계획", style = MaterialTheme.typography.titleLarge)
                    Text("요일과 운동 부위를 선택하면 상세 운동 화면에 적용돼요.",
                        color = secondary, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val days = listOf("월", "화", "수", "목", "금")
                        days.forEachIndexed { index, title ->
                            val selected = state.day == index + 1
                            if (selected) {
                                Button(
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    enabled = !state.running,
                                    onClick = { viewModel.selectDay(index + 1) }
                                ) { Text(title, maxLines = 1) }
                            } else {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    enabled = !state.running,
                                    onClick = { viewModel.selectDay(index + 1) }
                                ) { Text(title, maxLines = 1) }
                            }
                        }
                    }
                    Text("오늘 운동 부위", color = secondary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("upper" to "상체", "lower" to "하체").forEach { (id, title) ->
                            if (state.bodyFocus == id) {
                                Button(
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    enabled = !state.running,
                                    onClick = { viewModel.selectBody(id) }
                                ) { Text(title, maxLines = 1) }
                            } else {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    enabled = !state.running,
                                    onClick = { viewModel.selectBody(id) }
                                ) { Text(title, maxLines = 1) }
                            }
                        }
                    }
                }
            }
            item {
                Move60Card {
                    Text("운동 현황", style = MaterialTheme.typography.titleLarge)
                    Text(state.stageName, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(when {
                        state.finished -> "오늘 운동 완료"
                        state.running -> "운동 진행 중"
                        else -> "운동 대기 또는 일시정지"
                    }, color = secondary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("실제 운동시간", color = secondary)
                        Text(formatSeconds(state.activeSeconds),
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator(
                        progress = { (state.activeSeconds / 3600f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("근력 세트 ${state.completedSets}/${state.totalSets} · 오늘 누적 " +
                        String.format(Locale.KOREA, "%.1f", state.todayKcal) + " kcal (추정)",
                        color = secondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Move60Card {
                    Text("오늘의 운동 구성", style = MaterialTheme.typography.titleLarge)
                    state.stageNames.forEachIndexed { index, name ->
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold)
                            Text(name, modifier = Modifier.weight(1f),
                                color = if (index == state.stage)
                                    MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface)
                            if (index == state.stage) Text("현재", color = secondary,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text("운동별 자세 안내, 대체 운동, 중량·횟수 및 세트 기록은 상세 화면에서 관리해요.",
                        color = secondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun formatSeconds(value: Int): String = "%02d:%02d".format(
    Locale.KOREA, value.coerceAtLeast(0) / 60, value.coerceAtLeast(0) % 60
)

@Composable
fun WorkoutCalendar(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { Row(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로 가기")
            }
            Text("운동 기록",style=MaterialTheme.typography.titleLarge)
        } }
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days, key = { it.date }) { item ->
                Move60Card {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.date, fontWeight = FontWeight.Bold)
                        Text("${String.format(Locale.KOREA,"%.1f",item.estimatedKcal)} kcal",
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Text("실제 운동시간 ${item.activeSeconds / 60}분",
                        color=secondary)
                }
            }
        }
    }
}

@Composable
fun WorkoutSettings(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val message = remember { SnackbarHostState() }
    val context = LocalContext.current
    var weight by remember(settings.weightKg) {
        mutableStateOf(settings.weightKg.toInt().toString())
    }
    LaunchedEffect(viewModel) { viewModel.messages.collect { message.showSnackbar(it) } }
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    }
    Scaffold(containerColor=MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(message) },
        topBar = { Row(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onBack) { Text("←") }
            Text("앱 설정",style=MaterialTheme.typography.titleLarge)
        } }
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {
                Move60Card {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("알림 · 업데이트",style=MaterialTheme.typography.titleMedium)
                        Switch(checked=settings.soundEnabled,
                            onCheckedChange=viewModel::setSound)
                    }
                    Text("구간 전환 알림음 설정",style=MaterialTheme.typography.bodySmall,
                        color=secondary)
                }
            }
            item {
                Move60Card {
                    Text("건강 데이터 연동", style = MaterialTheme.typography.titleMedium)
                    Text("삼성 헬스 심박수 기록을 동의 후 불러오기", color = secondary)
                    Button(onClick = { context.startActivity(Intent(context, HealthConsentActivity::class.java)) }) {
                        Text("연동 권한 및 기록 확인")
                    }
                }
            }
            item {
                Move60Card {
                    Text("칼로리 계산 체중",style=MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value=weight,onValueChange={weight=it},
                        label={Text("체중(kg)")},singleLine=true,
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                    Button(onClick={viewModel.setWeight(weight)}) { Text("체중 저장") }
                }
            }
            item {
                Move60Card {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("앱 정보",style=MaterialTheme.typography.titleMedium)
                        Text("v$version",color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                Move60Card {
                    Text("도움말",style=MaterialTheme.typography.titleMedium)
                    Text("칼로리 계산 기준",color=MaterialTheme.colorScheme.primary)
                    Text("오늘 한국시간에 실제 운동한 시간만 누적합니다. 같은 날 두 번 운동하면 합산하고, 일시정지·종료 중에는 증가하지 않습니다. 다음 날 0부터 시작합니다. 체중과 운동 종류(MET)를 이용한 추정치입니다.",
                        color=secondary)
                }
            }
        }
    }
}
