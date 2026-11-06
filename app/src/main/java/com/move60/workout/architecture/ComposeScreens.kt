package com.move60.workout.architecture

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.move60.workout.MainActivity
import com.move60.workout.WorkoutHistory
import com.move60.workout.ui.Move60Button
import com.move60.workout.ui.Move60Card
import com.move60.workout.ui.Move60Divider
import com.move60.workout.ui.Move60Group
import java.util.Locale

private val secondary @Composable get() = MaterialTheme.colorScheme.secondary

private val cardTitle: TextStyle
    @Composable get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal)

@Composable
fun WorkoutDashboard(
    onCalendar: () -> Unit,
    onSettings: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val openDetail = { context.startActivity(Intent(context, MainActivity::class.java)) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is WorkoutUiEvent.Message) snackbar.showSnackbar(event.message)
        }
    }
    // Today's routine comes first; the weekly plan stays collapsed once a body part has been chosen.
    var planOpen by remember { mutableStateOf(state.bodyFocus == null) }
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
                        Icon(Icons.Outlined.Today, contentDescription = "운동 달력",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onSettings, modifier = Modifier.testTag("compose_settings_icon")) {
                        Icon(Icons.Outlined.Settings, contentDescription = "앱 설정",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        bottomBar = { WorkoutDock(state, viewModel, openDetail) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("dashboard_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Move60Card(Modifier.clickable(onClickLabel = "상세 운동 화면 열기", onClick = openDetail)
                    .testTag("dashboard_routine_card")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("오늘의 운동 구성", style = cardTitle, modifier = Modifier.weight(1f))
                        Text("근력 세트 ${state.completedSets}/${state.totalSets} · 단계 " +
                            "${state.stage + 1}/${state.stageNames.size.coerceAtLeast(1)}",
                            color = secondary, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            modifier = Modifier.testTag("dashboard_session_caption"))
                    }
                    state.stageNames.forEachIndexed { index, name ->
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold)
                            Text(name, modifier = Modifier.weight(1f),
                                fontWeight = if (index == state.stage) FontWeight.Bold else FontWeight.Normal,
                                color = if (index == state.stage)
                                    MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface)
                            if (index == state.stage) Text("현재", color = secondary,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            item {
                Move60Card(Modifier.testTag("dashboard_plan_card")) {
                    val dayName = listOf("월", "화", "수", "목", "금").getOrNull(state.day - 1)
                    val focusName = when (state.bodyFocus) { "upper" -> "상체"; "lower" -> "하체"; else -> "부위 미선택" }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(onClickLabel = if (planOpen) "주간 계획 접기" else "주간 계획 펼치기") { planOpen = !planOpen }
                        .testTag("dashboard_plan_header"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("이번 주 운동 계획", style = cardTitle)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!planOpen) Text(listOfNotNull(dayName?.plus("요일"), focusName).joinToString(" · "),
                                color = secondary, style = MaterialTheme.typography.bodySmall)
                            Icon(if (planOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = if (planOpen) "접기" else "펼치기", tint = secondary)
                        }
                    }
                    if (planOpen) {
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
            }
        }
    }
}

/** Fixed bottom dock (Figma "FixedBottomDock"): stage/time/heart summary plus step controls. */
@Composable
private fun WorkoutDock(state: WorkoutUiState, viewModel: WorkoutViewModel, onOpenDetail: () -> Unit) {
    val context = LocalContext.current
    // Heart rate is only shown when the watch reported a fresh sample (never an old value).
    val prefs = context.getSharedPreferences("preferences", android.content.Context.MODE_PRIVATE)
    val heart = prefs.getInt("watch_heart_rate", 0)
    val heartAge = System.currentTimeMillis() - prefs.getLong("watch_heart_rate_at", 0L)
    val heartText = if (heart in 30..240 && heartAge in 0L..10_000L) "$heart bpm" else "연결대기"
    val lastStage = (state.stageNames.size - 1).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).testTag("workout_dock")) {
    // Today's progress lives on the dock's top edge (replaces the former "운동 현황" card).
    val progress = (state.activeSeconds / 3600f).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.outline)
        .testTag("dock_progress")
        .semantics { contentDescription = "오늘 운동 진행 ${(progress * 100).toInt()}%" }) {
        Box(Modifier.fillMaxWidth(progress).height(4.dp).background(MaterialTheme.colorScheme.primary))
    }
    Column(
        Modifier.navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(onClickLabel = "상세 운동 화면 열기", onClick = onOpenDetail)
                .testTag("dock_summary"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("${state.stageName} · ${state.stage + 1}/${state.stageNames.size.coerceAtLeast(1)} 단계",
                    style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("${formatSeconds(state.activeSeconds)} / 60:00", color = secondary,
                    style = MaterialTheme.typography.bodySmall)
                Text("오늘 누적 " + String.format(Locale.KOREA, "%.1f", state.todayKcal) + " kcal · 추정",
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall)
            }
            Box(
                Modifier.heightIn(min = 40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics { contentDescription = "심박수 $heartText" },
                contentAlignment = Alignment.Center
            ) {
                Text("♥ $heartText", color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Move60Button(
                modifier = Modifier.weight(1f), primary = false, minHeight = 48,
                enabled = state.stage > 0 && !state.finished,
                contentPadding = PaddingValues(horizontal = 8.dp),
                onClick = viewModel::previous
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("이전", maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
            Move60Button(
                modifier = Modifier.weight(1.35f).testTag("dock_play"), minHeight = 48,
                onClick = {
                    val starting = !state.running
                    viewModel.playPause()
                    // Starting opens the detail screen where sets are entered.
                    if (starting) onOpenDetail()
                }
            ) {
                Icon(if (state.running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(6.dp))
                Text(when {
                    state.finished -> "새 운동 시작"
                    state.running -> "일시정지"
                    else -> "운동 시작"
                }, maxLines = 1)
            }
            Move60Button(
                modifier = Modifier.weight(1f), primary = false, minHeight = 48,
                enabled = !state.finished,
                contentPadding = PaddingValues(horizontal = 8.dp),
                onClick = viewModel::next
            ) {
                Text(if (state.stage >= lastStage) "종료" else "다음", maxLines = 1,
                    style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.size(6.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null,
                    modifier = Modifier.size(18.dp))
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
    val month by viewModel.month.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Tapping a day selects it; a day without a record also shows a short toast (every tap, not only the first).
    val onDayClick: (java.time.LocalDate) -> Unit = { date ->
        viewModel.select(date)
        if (!viewModel.trained(date)) scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar("운동한 기록이 없습니다.", duration = SnackbarDuration.Short)
        }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(data, modifier = Modifier.testTag("calendar_toast"),
                    shape = RoundedCornerShape(24.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface)
            }
        },
        topBar = { Row(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로 가기")
            }
            Text("운동 달력", style = cardTitle)
        } }
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner).testTag("calendar_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                val summary = viewModel.monthSummary(month)
                val week = viewModel.weekTrainedDays()
                Move60Card(Modifier.testTag("calendar_habit")) {
                    Text("운동 습관", style = MaterialTheme.typography.titleMedium)
                    HabitProgress("이번 주", week, WorkoutHistory.WEEKLY_GOAL_DAYS, "calendar_week_progress")
                    HabitProgress("이번 달 목표", summary.trainedDays, WorkoutHistory.MONTHLY_GOAL_DAYS, "calendar_month_progress")
                }
            }
            item { MonthGrid(viewModel, month, selected, onDayClick) }
            item {
                val summary = viewModel.monthSummary(month)
                Column(Modifier.fillMaxWidth().testTag("calendar_summary"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(Modifier.testTag("calendar_legend"), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            LegendDot(MaterialTheme.colorScheme.primary, "상체")
                            LegendDot(MaterialTheme.colorScheme.onSurface, "하체")
                            LegendDot(MaterialTheme.colorScheme.secondary, "부위 미기록")
                        }
                        Text("이번 달 ${summary.trainedDays}일 · ${summary.activeMinutes}분 · 약 ${summary.kcal} kcal",
                            color = secondary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!viewModel.trained(selected)) {
                        Row(Modifier.fillMaxWidth().testTag("calendar_day_empty"),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(selected.toString(), style = MaterialTheme.typography.bodySmall)
                            Text("기록 없음", color = secondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (viewModel.trained(selected)) item(key = "day_detail_$selected") {
                DayDetailCards(viewModel.dayDetail(selected))
            }
        }
    }
}

private fun formatStageTime(seconds: Long): String =
    if (seconds < 60L) "1분 미만" else "${(seconds + 30L) / 60L}분"

/** Selected-day record: a summary card, then one card per workout stage with its time and kcal. */
@Composable
private fun DayDetailCards(detail: DayDetail) {
    val weekday = listOf("월", "화", "수", "목", "금", "토", "일")[detail.date.dayOfWeek.value - 1]
    val focusLabel = when (detail.focus) { "upper" -> "상체"; "lower" -> "하체"; else -> "부위 미기록" }
    Column(Modifier.fillMaxWidth().testTag("calendar_day_detail"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Move60Card {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${detail.date.monthValue}월 ${detail.date.dayOfMonth}일 ($weekday)",
                            fontWeight = FontWeight.Bold)
                        Box(Modifier.background(MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 2.dp)) {
                            Text(focusLabel, color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (detail.stages.isNotEmpty()) Text("운동 ${detail.stages.size}개 완료", color = secondary,
                        style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${detail.activeMinutes}분", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("약 ${detail.kcal} kcal", color = secondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (detail.stages.isEmpty()) {
            Text("세부 운동 기록은 이번 버전 이후의 운동부터 저장돼요.", color = secondary,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("calendar_day_no_stages"))
        }
        detail.stages.forEach { stage ->
            Move60Card(Modifier.testTag("calendar_stage_card")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stage.name)
                        if (stage.note != null) Text(stage.note, color = secondary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Box(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(formatStageTime(stage.seconds), color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                    Text("약 ${stage.kcal} kcal", color = secondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Monday-first month grid; a dot marks days with recorded active time (real records only). */
@Composable
private fun MonthGrid(
    viewModel: HistoryViewModel, month: java.time.YearMonth, selected: java.time.LocalDate,
    onDayClick: (java.time.LocalDate) -> Unit
) {
    val navShape = MaterialTheme.shapes.small
    val navModifier = Modifier.size(48.dp)
        .background(MaterialTheme.colorScheme.surface, navShape)
        .border(1.dp, MaterialTheme.colorScheme.outline, navShape)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = viewModel::previousMonth, modifier = navModifier.testTag("calendar_prev")) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "이전 달",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Text("${month.year}년 ${month.monthValue}월", modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary)
        IconButton(onClick = viewModel::nextMonth, modifier = navModifier.testTag("calendar_next")) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "다음 달",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
    Spacer(Modifier.size(8.dp))
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
            week.forEach { date -> DayCell(viewModel, date, selected, onDayClick, Modifier.weight(1f)) }
            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DayCell(
    viewModel: HistoryViewModel,
    date: java.time.LocalDate?,
    selected: java.time.LocalDate,
    onDayClick: (java.time.LocalDate) -> Unit,
    modifier: Modifier
) {
    if (date == null) { Spacer(modifier.heightIn(min = 48.dp)); return }
    val trained = viewModel.trained(date)
    val focus = if (trained) viewModel.focus(date) else null
    val focusLabel = when (focus) { "upper" -> " · 상체"; "lower" -> " · 하체"; else -> "" }
    val dotColor = when (focus) {
        "upper" -> MaterialTheme.colorScheme.primary
        "lower" -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.secondary
    }
    val isSelected = date == selected
    Column(
        modifier.heightIn(min = 48.dp)
            .background(if (isSelected) MaterialTheme.colorScheme.outline else Color.Transparent,
                MaterialTheme.shapes.extraSmall)
            .clickable(onClickLabel = "날짜 선택") { onDayClick(date) }
            .semantics { contentDescription = "$date · ${if (trained) "운동 기록 있음" else "운동 기록 없음"}$focusLabel" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("${date.dayOfMonth}", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
        if (trained) {
            Box(Modifier.padding(top = 4.dp).size(6.dp).testTag("calendar-dot-$date")
                .background(dotColor, CircleShape))
        } else Spacer(Modifier.size(10.dp))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, color = secondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HabitProgress(label: String, done: Int, goal: Int, tag: String) {
    Column(Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = "$label ${done}일 / 목표 ${goal}일" },
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = secondary)
            Text("$done / ${goal}일", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }
        LinearProgressIndicator(progress = { (done.toFloat() / goal).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth())
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
    // null = unknown (offline / not checked), true = newer build published, false = latest.
    var updateAvailable by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(updateManager) { updateManager.peekAvailability { updateAvailable = it } }
    var weight by remember(settings.weightKg) {
        mutableStateOf(settings.weightKg.toInt().toString())
    }
    var diagnostics by remember { mutableStateOf<String?>(null) }
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Move60Group("운동", Modifier.testTag("settings_group_workout")) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("오늘 컨디션 루틴")
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
                    }
                    Move60Divider()
                    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("settings_ratio"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("유산소 · 근력 시간 비율")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(20, 25, 30, 35, 40).forEach { minutes ->
                                Move60Button(
                                    modifier = Modifier.weight(1f).testTag("settings_ratio_$minutes"),
                                    primary = cardioMinutes == minutes,
                                    contentPadding = PaddingValues(0.dp),
                                    onClick = { viewModel.setCardioMinutes(minutes) }
                                ) { Text("${minutes}분", maxLines = 1) }
                            }
                        }
                        Text("유산소 ${cardioMinutes}분 · 근력 ${50 - cardioMinutes}분 · 운동 시작 전에 바꿀 수 있어요",
                            color = secondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Move60Group("내 정보", Modifier.testTag("settings_group_profile")) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("칼로리 계산 체중")
                        OutlinedTextField(value = weight, onValueChange = { weight = it },
                            modifier = Modifier.fillMaxWidth().testTag("settings_weight_field"),
                            singleLine = true,
                            suffix = { Text("kg") },
                            trailingIcon = { TextButton(onClick = { viewModel.setWeight(weight) }) { Text("저장") } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                }
            }
            item {
                Move60Group("앱", Modifier.testTag("settings_group_app")) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("알림", modifier = Modifier.weight(1f))
                        Switch(checked = settings.soundEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setSound(enabled)
                                // Turning notifications on leads straight to the notification diagnostics.
                                if (enabled) {
                                    if (Build.VERSION.SDK_INT >= 33 &&
                                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                        PackageManager.PERMISSION_GRANTED) {
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        diagnostics = notificationDiagnostics(context, viewModel.sessionRunning())
                                    }
                                }
                            },
                            modifier = Modifier.testTag("settings_sound_toggle"))
                    }
                    Move60Divider()
                    // Fixed row height: the update button replaces the version text without moving anything.
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp)
                        .testTag("settings_version_row"),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("앱 버전", modifier = Modifier.weight(1f))
                        if (updateAvailable == true) {
                            Move60Button(onClick = { updateManager.check(true) },
                                modifier = Modifier.testTag("settings_update_button")) { Text("업데이트") }
                        } else {
                            Text("v$version" + if (updateAvailable == false) " · 최신" else "",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("settings_version"))
                        }
                    }
                }
            }
        }
    }
    diagnostics?.let { text ->
        AlertDialog(
            onDismissRequest = { diagnostics = null },
            title = { Text("운동 알림 진단") },
            text = { DialogDim(); Text(text) },
            dismissButton = { TextButton(onClick = { diagnostics = null }) { Text("닫기") } },
            confirmButton = {
                TextButton(onClick = { diagnostics = null; openNotificationSettings(context) }) {
                    Text("알림 설정 열기")
                }
            }
        )
    }
}


/** Dims everything behind a Compose dialog so popups read as modal. */
@Composable
private fun DialogDim() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.setDimAmount(0.72f)
    }
}
