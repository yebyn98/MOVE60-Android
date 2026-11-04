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
import androidx.compose.foundation.layout.padding
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
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("MOVE60", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row {
                    TextButton(onClick = onCalendar) { Text("기록") }
                    TextButton(onClick = onSettings) { Text("설정") }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Move60Card {
                    Text("오늘 누적 ${String.format(Locale.KOREA, "%.1f", state.todayKcal)} kcal · 추정",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = viewModel::previous,
                            enabled = state.stage > 0
                        ) { Text("이전") }
                        Button(
                            modifier = Modifier.weight(1.35f),
                            onClick = viewModel::playPause
                        ) {
                            Text(when {
                                state.finished -> "새 운동 시작"
                                state.running -> "일시정지"
                                else -> "운동 시작"
                            })
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = viewModel::next,
                            enabled = !state.finished
                        ) { Text(if (state.stage == 3) "종료" else "다음") }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Move60Card {
                    Text("이번 주 운동", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val days = listOf("월","화","수","목","금")
                        days.forEachIndexed { index, title ->
                            if (state.day == index+1)
                                Button(modifier = Modifier.weight(1f),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    onClick = { viewModel.selectDay(index+1) }) { Text(title) }
                            else
                                OutlinedButton(modifier = Modifier.weight(1f),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    enabled = !state.running,
                                    onClick = { viewModel.selectDay(index+1) }) { Text(title) }
                        }
                    }
                    Text("오늘 운동 부위", color = secondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("upper" to "상체", "lower" to "하체").forEach { (id,label) ->
                            if(state.bodyFocus == id)
                                Button(onClick = { viewModel.selectBody(id) }, enabled = !state.running) {
                                    Text(label)
                                }
                            else
                                OutlinedButton(onClick = { viewModel.selectBody(id) },
                                    enabled = !state.running) { Text(label) }
                        }
                    }
                }
            }
            item {
                Move60Card {
                    Text(state.stageName, style = MaterialTheme.typography.titleLarge)
                    Text(if (state.running) "진행 중" else if (state.finished) "운동 완료"
                        else "대기 / 일시정지", color = secondary)
                    Text(formatSeconds(state.remainingSeconds),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold)
                    Text("실제 운동시간 ${formatSeconds(state.activeSeconds)}",
                        color = MaterialTheme.colorScheme.primary)
                    LinearProgressIndicator(
                        progress = { (state.activeSeconds / 3600f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("단계 ${state.stage+1}/4 · ${state.stageNames.joinToString(" · ")}",
                        style = MaterialTheme.typography.bodySmall, color = secondary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (state.restSeconds > 0)
                        Text("세트 휴식 ${formatSeconds(state.restSeconds)}",
                            color = MaterialTheme.colorScheme.primary)
                }
            }
            item {
                Move60Card {
                    Text("운동 · 세트 기록", style = MaterialTheme.typography.titleLarge)
                    Text("근력 세트 ${state.completedSets}/${state.totalSets}",
                        color = secondary)
                    LinearProgressIndicator(
                        progress = { state.completedSets.toFloat() / state.totalSets.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            items(state.stageTasks, key = { task -> "${state.stage}-${task.id}-${task.name}" }) { task ->
                Move60Card {
                    Text(task.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(task.detail.replace(Regex("""RPE\s*[\d~–-]+\s*[·]?"""), "").trim(),
                        color = secondary, style = MaterialTheme.typography.bodySmall)
                    if (state.stage == 2 && task.sets > 0) {
                        task.entries.forEach { entry ->
                            SetEntry(
                                entry = entry,
                                sessionId = state.sessionId,
                                date = state.date,
                                onChanged = { kg,reps,done ->
                                    viewModel.saveSet(task.id, entry.number, kg, reps, done)
                                }
                            )
                        }
                    }
                }
            }
            item {
                Move60Card {
                    Text("상세 운동 화면", style = MaterialTheme.typography.titleMedium)
                    Text("운동 교체·동작 영상 등 이전 화면에 있는 기능은 전환 기간에도 유지합니다.",
                        style = MaterialTheme.typography.bodySmall, color = secondary)
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(context, MainActivity::class.java))
                    }) { Text("상세 기능 열기") }
                }
            }
        }
    }
}

@Composable
private fun SetEntry(
    entry: WorkoutSetUi,
    sessionId: Long,
    date: String,
    onChanged: (String, String, Boolean) -> Unit
) {
    var weight by remember(date, sessionId, entry.number) { mutableStateOf(entry.kg) }
    var reps by remember(date, sessionId, entry.number) { mutableStateOf(entry.reps) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("${entry.number}세트", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = weight, onValueChange = {
                weight = it
                if (!entry.completed) onChanged(weight, reps, false)
            },
            modifier = Modifier.weight(1f),
            label = { Text("kg") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        OutlinedTextField(
            value = reps, onValueChange = {
                reps = it
                if (!entry.completed) onChanged(weight, reps, false)
            },
            modifier = Modifier.weight(1f),
            label = { Text("횟수") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Checkbox(
            checked = entry.completed,
            onCheckedChange = { onChanged(weight, reps, it) },
            enabled = sessionId > 0
        )
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
        topBar = { Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onBack) { Text("←") }
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
        topBar = { Row(Modifier.padding(16.dp), verticalAlignment=Alignment.CenterVertically) {
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
