package com.move60.workout.architecture

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.move60.workout.UpdateManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.move60.workout.MainActivity
import com.move60.workout.health.HealthConsentActivity
import com.move60.workout.ui.Move60Button
import com.move60.workout.ui.Move60Card
import java.util.Locale

private val secondary @Composable get() = MaterialTheme.colorScheme.secondary

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
                Move60Button(
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 52,
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
                            Move60Button(
                                modifier = Modifier.weight(1f),
                                primary = state.day == index + 1,
                                contentPadding = PaddingValues(0.dp),
                                enabled = !state.running,
                                onClick = { viewModel.selectDay(index + 1) }
                            ) { Text(title, maxLines = 1) }
                        }
                    }
                    Text("오늘 운동 부위", color = secondary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("upper" to "상체", "lower" to "하체").forEach { (id, title) ->
                            Move60Button(
                                modifier = Modifier.weight(1f),
                                primary = state.bodyFocus == id,
                                enabled = !state.running,
                                onClick = { viewModel.selectBody(id) }
                            ) { Text(title, maxLines = 1) }
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
    val month by viewModel.month.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
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
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Move60Card {
                    MonthGrid(viewModel, month, selected)
                }
            }
            item {
                val summary = viewModel.monthSummary(month)
                val day = viewModel.daySummary(selected)
                Move60Card(Modifier.testTag("calendar_summary")) {
                    Text("${summary.trainedDays}일 · ${summary.activeMinutes}분 · 약 ${summary.kcal} kcal",
                        style = MaterialTheme.typography.titleMedium)
                    Text("● 운동한 날", color = secondary, style = MaterialTheme.typography.bodySmall)
                    Text("${day.date} · ${day.activeMinutes}분 · 약 ${day.kcal} kcal",
                        fontWeight = FontWeight.Bold)
                }
            }
            if (days.isNotEmpty()) item {
                Text("최근 운동 기록", style = MaterialTheme.typography.titleMedium)
            }
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

/** Monday-first month grid; a dot marks days with recorded active time (real records only). */
@Composable
private fun MonthGrid(viewModel: HistoryViewModel, month: java.time.YearMonth, selected: java.time.LocalDate) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = viewModel::previousMonth, modifier = Modifier.testTag("calendar_prev")) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "이전 달")
        }
        Text("${month.year}년 ${month.monthValue}월", modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary)
        IconButton(onClick = viewModel::nextMonth, modifier = Modifier.testTag("calendar_next")) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "다음 달")
        }
    }
    Row(Modifier.fillMaxWidth()) {
        listOf("월", "화", "수", "목", "금", "토", "일").forEach { name ->
            Text(name, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                color = secondary, style = MaterialTheme.typography.labelMedium)
        }
    }
    val lead = month.atDay(1).dayOfWeek.value - 1
    val cells: List<java.time.LocalDate?> =
        List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            week.forEach { date -> DayCell(viewModel, date, selected, Modifier.weight(1f)) }
            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DayCell(
    viewModel: HistoryViewModel,
    date: java.time.LocalDate?,
    selected: java.time.LocalDate,
    modifier: Modifier
) {
    if (date == null) { Spacer(modifier.heightIn(min = 48.dp)); return }
    val trained = viewModel.trained(date)
    val isSelected = date == selected
    Column(
        modifier.heightIn(min = 48.dp)
            .background(if (isSelected) MaterialTheme.colorScheme.outline else Color.Transparent,
                MaterialTheme.shapes.extraSmall)
            .clickable(onClickLabel = "날짜 선택") { viewModel.select(date) }
            .semantics { contentDescription = "$date · ${if (trained) "운동 기록 있음" else "운동 기록 없음"}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("${date.dayOfMonth}", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
        if (trained) {
            Box(Modifier.padding(top = 4.dp).size(6.dp).testTag("calendar-dot-$date")
                .background(MaterialTheme.colorScheme.primary, CircleShape))
        } else Spacer(Modifier.size(10.dp))
    }
}

@Composable
fun WorkoutSettings(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val cardioMinutes by viewModel.cardioMinutes.collectAsStateWithLifecycle()
    val message = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val updateManager = remember(activity) { UpdateManager(activity) }
    LifecycleResumeEffect(updateManager) {
        updateManager.resumePendingInstall()
        onPauseOrDispose { }
    }
    DisposableEffect(updateManager) { onDispose { updateManager.onDestroy() } }
    var weight by remember(settings.weightKg) {
        mutableStateOf(settings.weightKg.toInt().toString())
    }
    var diagnostics by remember { mutableStateOf<String?>(null) }
    var showRatio by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { diagnostics = notificationDiagnostics(context, viewModel.sessionRunning()) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { message.showSnackbar(it) } }
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(message) },
        topBar = {
            Row(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로 가기")
                }
                Text("앱 설정", style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner).testTag("settings_list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Move60Card {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("알림 · 업데이트", style = MaterialTheme.typography.titleMedium)
                        Switch(checked = settings.soundEnabled,
                            onCheckedChange = viewModel::setSound,
                            modifier = Modifier.testTag("settings_sound_toggle"))
                    }
                    Text("구간 전환 알림음 설정", style = MaterialTheme.typography.bodySmall,
                        color = secondary)
                    Move60Button(primary = false, modifier = Modifier.fillMaxWidth(), onClick = {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            diagnostics = notificationDiagnostics(context, viewModel.sessionRunning())
                        }
                    }) { Text("알림 설정 · 진단") }
                    Move60Button(primary = false, modifier = Modifier.fillMaxWidth(),
                        onClick = { updateManager.check(true) }) { Text("업데이트 확인") }
                }
            }
            item {
                Move60Card {
                    Text("운동 설정", style = MaterialTheme.typography.titleMedium)
                    Move60Button(primary = false, modifier = Modifier.fillMaxWidth(),
                        onClick = { showRatio = true }) {
                        Text("유산소 · 근력 시간 비율 변경 (유산소 ${cardioMinutes}분)")
                    }
                    Text("오늘 컨디션에 맞춘 루틴", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "피곤" to "스텝밀 최대 10분 · 편안한 강도",
                            "보통" to "스텝밀 최대 15분 · 계획대로 진행",
                            "좋음" to "스텝밀 최대 15분 · 자세 유지"
                        ).forEach { (name, summary) ->
                            Move60Button(
                                modifier = Modifier.weight(1f),
                                primary = settings.condition == name,
                                contentPadding = PaddingValues(0.dp),
                                onClick = { viewModel.setCondition(name, summary) }
                            ) { Text(name, maxLines = 1) }
                        }
                    }
                    Text("운동 중 변경한 컨디션은 다음 운동부터 적용됩니다.",
                        style = MaterialTheme.typography.bodySmall, color = secondary)
                }
            }
            item {
                Move60Card {
                    Text("건강 데이터 연동", style = MaterialTheme.typography.titleMedium)
                    Text("Health Connect 심박수 기록을 동의 후 불러오기", color = secondary)
                    Move60Button(
                        onClick = { context.startActivity(Intent(context, HealthConsentActivity::class.java)) }) {
                        Text("연동 권한 및 기록 확인")
                    }
                }
            }
            item {
                Move60Card {
                    Text("칼로리 계산 체중", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = weight, onValueChange = { weight = it },
                        label = { Text("체중(kg)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Text("운동 칼로리는 추정치이며 실제 소모량과 다를 수 있어요.",
                        style = MaterialTheme.typography.bodySmall, color = secondary)
                    Move60Button(onClick = { viewModel.setWeight(weight) }) { Text("체중 저장") }
                }
            }
            item {
                Move60Card(Modifier.testTag("settings_about_card")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("앱 정보", style = MaterialTheme.typography.titleMedium)
                        Text("v$version", color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("settings_version"))
                    }
                    Text("MOVE60 · 60분 운동을 매일 더 간결하게",
                        style = MaterialTheme.typography.bodySmall, color = secondary)
                }
            }
            item {
                Move60Card(Modifier.testTag("settings_help_card")) {
                    Text("도움말", style = MaterialTheme.typography.titleMedium)
                    Text("칼로리 계산 기준", color = MaterialTheme.colorScheme.primary)
                    Text("오늘 한국시간에 실제 운동한 시간만 누적합니다. 같은 날 두 번 운동하면 합산하고, 일시정지·종료 중에는 증가하지 않습니다. 다음 날 0부터 시작합니다. 체중과 운동 종류(MET)를 이용한 추정치입니다.\n\n근력 세트의 횟수를 참고하며 입력 중량을 kcal로 직접 환산하지 않습니다. 심박수는 실시간 참고 정보이며 별도의 개인 보정 없이 kcal에 더하지 않습니다.",
                        color = secondary)
                }
            }
        }
    }
    diagnostics?.let { text ->
        AlertDialog(
            onDismissRequest = { diagnostics = null },
            title = { Text("운동 알림 진단") },
            text = { Text(text) },
            dismissButton = { TextButton(onClick = { diagnostics = null }) { Text("닫기") } },
            confirmButton = {
                TextButton(onClick = { diagnostics = null; openNotificationSettings(context) }) {
                    Text("알림 설정 열기")
                }
            }
        )
    }
    if (showRatio) {
        AlertDialog(
            onDismissRequest = { showRatio = false },
            title = { Text("60분 운동 시간 배분") },
            text = {
                Column {
                    listOf(20, 25, 30, 35, 40).forEach { minutes ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                selected = minutes == cardioMinutes, role = Role.RadioButton,
                                onClick = { showRatio = false; viewModel.setCardioMinutes(minutes) }),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = minutes == cardioMinutes, onClick = null)
                            Text("유산소 ${minutes}분 · 근력 ${50 - minutes}분",
                                modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRatio = false }) { Text("취소") } }
        )
    }
}
