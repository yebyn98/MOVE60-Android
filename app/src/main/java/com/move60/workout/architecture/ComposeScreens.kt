package com.move60.workout.architecture

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import com.move60.workout.WorkoutData
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.move60.workout.WorkoutWidget
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
import com.move60.workout.Move60Theme
import com.move60.workout.ui.Move60LogoMark
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
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

/** Figma card / section title: 20sp bold. */
private val cardTitle: TextStyle
    @Composable get() = MaterialTheme.typography.titleLarge

/** Figma screen_title: 24sp bold. */
private val screenTitle: TextStyle
    @Composable get() = MaterialTheme.typography.headlineMedium

private val muted: Color get() = Color(Move60Theme.Colors.textMuted)

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
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            // Figma TopBar: 52dp logo mark on the left, 48dp calendar / settings buttons (24dp icons) on the right.
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Move60LogoMark(Modifier.size(52.dp).testTag("home_logo")
                    .semantics { contentDescription = "MOVE60" })
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Figma order: the weekly plan first, then today's routine.
            item {
                Move60Card(Modifier.testTag("dashboard_plan_card")) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("이번 주 운동 계획", style = cardTitle, modifier = Modifier.weight(1f))
                        Text(listOf("월", "화", "수", "목", "금").getOrElse(state.day - 1) { "" } + "요일",
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val days = listOf("월", "화", "수", "목", "금")
                        days.forEachIndexed { index, title ->
                            Move60Button(
                                modifier = Modifier.weight(1f),
                                primary = state.day == index + 1,
                                contentPadding = PaddingValues(0.dp),
                                enabled = !state.running,
                                onClick = { viewModel.selectDay(index + 1) }
                            ) { Text(title, maxLines = 1, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Text("오늘 운동 부위", color = secondary, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("upper" to "상체", "lower" to "하체").forEach { (id, title) ->
                            Move60Button(
                                modifier = Modifier.weight(1f),
                                primary = state.bodyFocus == id,
                                enabled = !state.running,
                                onClick = { viewModel.selectBody(id) }
                            ) { Text(title, maxLines = 1, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            item {
                Move60Card(Modifier.clickable(onClickLabel = "상세 운동 화면 열기", onClick = openDetail)
                    .testTag("dashboard_routine_card")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("오늘의 운동 구성", style = cardTitle, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                            tint = secondary, modifier = Modifier.testTag("dashboard_session_caption"))
                    }
                    state.stageNames.forEachIndexed { index, name ->
                        if (index > 0) Move60Divider()
                        val current = index == state.stage
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).background(
                                if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text("${index + 1}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
                                    color = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            }
                            Text(name + if (index == 2 && state.bodyFocus == "lower") " · 하체"
                                else if (index == 2 && state.bodyFocus == "upper") " · 상체" else "",
                                modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
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
        // Figma: stage + elapsed on the left, 24dp heart pill on the right, today's kcal on its own line.
        Column(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(onClickLabel = "상세 운동 화면 열기", onClick = onOpenDetail)
                .testTag("dock_summary"),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(listOf(state.stageName, state.itemName).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text("${formatSeconds(state.activeSeconds)} / 60:00", color = secondary,
                        style = MaterialTheme.typography.bodySmall)
                }
                Box(
                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("dock_heart_pill")
                        .semantics { contentDescription = "심박수 $heartText" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("♥ $heartText", color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text("오늘 누적 " + String.format(Locale.KOREA, "%.1f", state.todayKcal) + " kcal · 추정",
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall)
        }
        // Figma 01/02: before the workout starts the start button is the only action (no previous/next).
        val started = state.running || state.activeSeconds > 0 || state.finished
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (started) Move60Button(
                modifier = Modifier.weight(1f), primary = false, minHeight = 48,
                enabled = state.stage > 0 && !state.finished,
                contentPadding = PaddingValues(horizontal = 8.dp),
                onClick = viewModel::previous
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("이전", maxLines = 1, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
            Move60Button(
                modifier = Modifier.weight(if (started) 1.35f else 1f).testTag("dock_play"), minHeight = 48,
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
            if (started) Move60Button(
                modifier = Modifier.weight(1f), primary = false, minHeight = 48,
                enabled = !state.finished,
                contentPadding = PaddingValues(horizontal = 8.dp),
                onClick = viewModel::next
            ) {
                Text(if (state.stage >= lastStage) "종료" else "다음", maxLines = 1,
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
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

/** Figma TopBar: 24dp back arrow (48dp touch target), 24sp bold screen title. */
@Composable
private fun ScreenTopBar(title: String, onBack: () -> Unit, backTag: String? = null) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = if (backTag != null) Modifier.testTag(backTag) else Modifier) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로 가기")
        }
        Spacer(Modifier.size(4.dp))
        Text(title, style = screenTitle, maxLines = 1)
    }
}

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
                    contentColor = MaterialTheme.colorScheme.secondary)
            }
        },
        topBar = { ScreenTopBar("운동 달력", onBack) }
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner).testTag("calendar_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                val summary = viewModel.monthSummary(month)
                val week = viewModel.weekTrainedDays()
                Move60Card(Modifier.testTag("calendar_habit")) {
                    Text("운동 습관", style = cardTitle)
                    HabitProgress("이번 주", week, WorkoutHistory.WEEKLY_GOAL_DAYS, "calendar_week_progress")
                    HabitProgress("이번 달 목표", summary.trainedDays, WorkoutHistory.MONTHLY_GOAL_DAYS, "calendar_month_progress")
                }
            }
            item { MonthGrid(viewModel, month, selected, onDayClick) }
            item {
                val summary = viewModel.monthSummary(month)
                Column(Modifier.fillMaxWidth().testTag("calendar_summary"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.testTag("calendar_legend"), horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            LegendDot(upperDot, "상체")
                            LegendDot(lowerDot, "하체")
                            LegendDot(null, "부위 미기록")
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
                DayDetailCard(viewModel.dayDetail(selected))
            }
        }
    }
}

private val upperDot: Color @Composable get() = MaterialTheme.colorScheme.primary
// Figma "하체" dot: a dimmer green of the primary token.
private val lowerDot: Color @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)

private fun formatStageTime(seconds: Long): String =
    "%02d:%02d".format(Locale.KOREA, seconds.coerceAtLeast(0) / 60, seconds.coerceAtLeast(0) % 60)

/** Selected-day record (Figma "summary_card"): date, body-part chip, totals, then one row per workout stage. */
@Composable
private fun DayDetailCard(detail: DayDetail) {
    val weekday = listOf("월", "화", "수", "목", "금", "토", "일")[detail.date.dayOfWeek.value - 1]
    val focusLabel = when (detail.focus) { "upper" -> "상체"; "lower" -> "하체"; else -> "부위 미기록" }
    Move60Card(Modifier.testTag("calendar_day_detail"), spacing = 8.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${detail.date.monthValue}월 ${detail.date.dayOfMonth}일 ($weekday)",
                        style = MaterialTheme.typography.titleMedium)
                    Box(Modifier.background(MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 2.dp)) {
                        Text(focusLabel, color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (detail.stages.isNotEmpty()) Text("수행 내역 · ${detail.stages.size}개 항목", color = secondary,
                    style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${detail.activeMinutes}분", color = MaterialTheme.colorScheme.primary,
                    style = cardTitle)
                Text("약 ${detail.kcal} kcal", color = secondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (detail.stages.isEmpty()) {
            Text("세부 운동 기록은 이번 버전 이후의 운동부터 저장돼요.", color = secondary,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("calendar_day_no_stages"))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            detail.stages.forEach { stage ->
                Row(Modifier.fillMaxWidth().testTag("calendar_stage_card")
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stage.name)
                        if (stage.note != null) Text(stage.note, color = muted,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Box(Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
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

/** Six-week Monday-first grid. Every row shares seven equal cells, even in 6-week months. */
@Composable
private fun MonthGrid(
    viewModel: HistoryViewModel, month: java.time.YearMonth, selected: java.time.LocalDate,
    onDayClick: (java.time.LocalDate) -> Unit
) {
    Move60Card(Modifier.testTag("calendar_month_grid")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${month.year}년 ${month.monthValue}월", modifier = Modifier.weight(1f),
                style = cardTitle, color = MaterialTheme.colorScheme.onSurface)
            IconButton(onClick = viewModel::previousMonth, modifier = Modifier.size(48.dp).testTag("calendar_prev")) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "이전 달", modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = viewModel::nextMonth, modifier = Modifier.size(48.dp).testTag("calendar_next")) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "다음 달", modifier = Modifier.size(24.dp))
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("월", "화", "수", "목", "금", "토", "일").forEach { name ->
                Text(name, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    color = secondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        val first = month.atDay(1)
        val firstCell = first.minusDays((first.dayOfWeek.value - 1).toLong())
        val dates = List(42) { i -> firstCell.plusDays(i.toLong()) }
        dates.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(viewModel, date, selected, date.month != month.month,
                        onDayClick, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    viewModel: HistoryViewModel,
    date: java.time.LocalDate,
    selected: java.time.LocalDate,
    outsideMonth: Boolean,
    onDayClick: (java.time.LocalDate) -> Unit,
    modifier: Modifier
) {
    val trained = viewModel.trained(date)
    val focus = if (trained) viewModel.focus(date) else null
    val focusLabel = when (focus) { "upper" -> " · 상체"; "lower" -> " · 하체"; else -> "" }
    val isSelected = date == selected
    val future = date.isAfter(java.time.LocalDate.now())
    // One 48dp touch region owns date, selection circle and record dot; no pixel offsets.
    Column(
        modifier.height(48.dp)
            .clickable(onClickLabel = "날짜 선택") { onDayClick(date) }
            .semantics { contentDescription = "$date · ${if (trained) "운동 기록 있음" else "운동 기록 없음"}$focusLabel" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(30.dp)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("${date.dayOfMonth}",
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    outsideMonth || future -> muted
                    else -> MaterialTheme.colorScheme.onSurface
                })
        }
        if (trained) Box(Modifier.size(5.dp).testTag("calendar-dot-$date")
            .background(MaterialTheme.colorScheme.primary, CircleShape))
        else Spacer(Modifier.height(5.dp))
    }
}

@Composable
private fun LegendDot(color: Color?, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(6.dp).let {
            if (color != null) it.background(color, CircleShape)
            else it.border(1.dp, MaterialTheme.colorScheme.secondary, CircleShape)
        })
        Text(label, color = secondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HabitProgress(label: String, done: Int, goal: Int, tag: String) {
    val fraction = (done.toFloat() / goal).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = "$label ${done}일 / 목표 ${goal}일" },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = secondary)
            Text("$done / ${goal}일", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        // Figma progress: 6dp track with fully rounded ends.
        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.outline, CircleShape)) {
            Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

@Composable
fun WorkoutSettings(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val split by viewModel.split.collectAsStateWithLifecycle()
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
    val widgetScope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { diagnostics = notificationDiagnostics(context, viewModel.sessionRunning()) }
    LaunchedEffect(viewModel) { viewModel.messages.collect { message.showSnackbar(it) } }
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(message) },
        topBar = { ScreenTopBar("앱 설정", onBack, backTag = "settings_back") }
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner).testTag("settings_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Move60Group("운동", Modifier.testTag("settings_group_workout")) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("오늘 컨디션 루틴")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "피곤" to "오늘 컨디션을 '피곤'으로 기록했어요",
                                "보통" to "오늘 컨디션을 '보통'으로 기록했어요",
                                "좋음" to "오늘 컨디션을 '좋음'으로 기록했어요"
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("유산소 · 근력 시간 비율")
                        RatioSlider(split.first, split.second) { first, second ->
                            viewModel.setCardioSplit(first, second)
                        }
                    }
                }
            }
            item {
                Move60Group("내 정보", Modifier.testTag("settings_group_profile")) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("칼로리 계산 체중", style = MaterialTheme.typography.titleMedium)
                        // Figma weight_field: black 48dp field, "63 kg" value, small elevated save button.
                        Row(Modifier.fillMaxWidth().height(48.dp)
                            .background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.small)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .padding(start = 16.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                BasicTextField(value = weight, onValueChange = { weight = it },
                                    modifier = Modifier.widthIn(min = 24.dp, max = 120.dp).width(IntrinsicSize.Min)
                                        .testTag("settings_weight_field"),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                                Text(" kg")
                            }
                            // 60x36 visual button inside a 48dp-high touch area.
                            Box(Modifier.height(48.dp).clickable(role = Role.Button, onClickLabel = "체중 저장") {
                                viewModel.setWeight(weight)
                            }.testTag("settings_weight_save"), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(width = 60.dp, height = 36.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center) {
                                    Text("저장", fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
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
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("settings_sound_toggle"))
                    }
                    Move60Divider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp)
                        .testTag("settings_widget_row"), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("홈 화면 위젯")
                            Text("앱 업데이트 후에도 홈에서 사용하려면 추가가 필요해요.",
                                color = secondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Move60Button(onClick = {
                            val manager = AppWidgetManager.getInstance(context)
                            if (Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported) {
                                val provider = ComponentName(context, WorkoutWidget::class.java)
                                val requested = manager.requestPinAppWidget(provider, null, null)
                                widgetScope.launch { message.showSnackbar(
                                    if (requested) "런처의 위젯 추가 요청을 확인해 주세요."
                                    else "홈 화면을 길게 눌러 위젯에서 MOVE60을 추가해 주세요."
                                ) }
                            } else widgetScope.launch {
                                message.showSnackbar("홈 화면을 길게 눌러 위젯에서 MOVE60을 추가해 주세요.")
                            }
                        }, primary = false, modifier = Modifier.testTag("settings_widget_pin")) {
                            Text("위젯 추가", maxLines = 1)
                        }
                    }
                    Move60Divider()
                    // Fixed row height: the update button replaces the version text without moving anything.
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp)
                        .testTag("settings_version_row"),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("앱 버전", modifier = Modifier.weight(1f))
                        if (updateAvailable == true) {
                            // 99x40 visual button inside a 48dp-high touch area (Figma "업데이트").
                            Box(Modifier.height(48.dp).clickable(role = Role.Button, onClickLabel = "업데이트") {
                                updateManager.check(true)
                            }.testTag("settings_update_button"), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(width = 99.dp, height = 40.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center) {
                                    Text("업데이트", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        } else {
                            Text("v$version" + if (updateAvailable == false) " · 최신" else "",
                                color = MaterialTheme.colorScheme.secondary,
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


/**
 * Figma "split_slider": one track, two handles. ① 유산소 / ② 유산소 / 근력 are set individually in 5-minute
 * steps and always add up to the 50 adjustable minutes (준비 5 + 스트레칭 5 stay fixed).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RatioSlider(first: Int, second: Int, onCommit: (Int, Int) -> Unit) {
    val total = WorkoutData.ADJUSTABLE_MINUTES
    var a by remember(first, second) { mutableStateOf(first.toFloat()) }
    var b by remember(first, second) { mutableStateOf((first + second).toFloat()) }
    val primary = MaterialTheme.colorScheme.primary
    val dark = primary.copy(alpha = 0.55f)
    val rest = secondary
    val firstMin = a.toInt()
    val secondMin = (b - a).toInt()
    val strengthMin = total - b.toInt()
    Column(Modifier.testTag("settings_ratio_block"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.RangeSlider(
            value = a..b,
            onValueChange = { range ->
                val nb = range.endInclusive.coerceIn((WorkoutData.MIN_CARDIO * 2).toFloat(), (total - WorkoutData.MIN_STRENGTH).toFloat())
                val na = range.start.coerceIn(WorkoutData.MIN_CARDIO.toFloat(), nb - WorkoutData.MIN_CARDIO)
                a = na; b = nb
            },
            onValueChangeFinished = { onCommit(a.toInt(), (b - a).toInt()) },
            valueRange = 0f..total.toFloat(),
            steps = total / WorkoutData.STEP_MINUTES - 1,
            modifier = Modifier.fillMaxWidth().testTag("settings_ratio_slider"),
            startThumb = { RatioThumb() },
            endThumb = { RatioThumb() },
            track = {
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                    val xa = size.width * a / total
                    val xb = size.width * b / total
                    val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                    drawRoundRect(rest, size = size, cornerRadius = radius)
                    drawRoundRect(dark, size = androidx.compose.ui.geometry.Size(xb, size.height), cornerRadius = radius)
                    drawRoundRect(primary, size = androidx.compose.ui.geometry.Size(xa, size.height), cornerRadius = radius)
                }
            }
        )
        Row(Modifier.fillMaxWidth().testTag("settings_ratio_summary").semantics(mergeDescendants = true) {
            contentDescription = "유산소 ① ${firstMin}분, 유산소 ② ${secondMin}분, 근력 ${strengthMin}분"
        }, horizontalArrangement = Arrangement.SpaceBetween) {
            RatioLegend("유산소 ①", firstMin, primary)
            RatioLegend("유산소 ②", secondMin, dark)
            RatioLegend("근력", strengthMin, rest)
        }
    }
}

@Composable
private fun RatioThumb() {
    Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.background, CircleShape)
        .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape))
}

@Composable
private fun RatioLegend(name: String, minutes: Int, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(name, color = secondary, style = MaterialTheme.typography.bodySmall)
        }
        Text("${minutes}분", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}
