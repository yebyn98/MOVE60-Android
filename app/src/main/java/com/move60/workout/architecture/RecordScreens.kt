package com.move60.workout.architecture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.move60.workout.ui.Move60Button
import com.move60.workout.ui.Move60Card
import java.time.LocalDate

/**
 * ROUTIVE v2 record screens: Figma 26 (day record) and 27 (session detail), plus the Figma 11 selected-day card.
 *
 * Everything shown is a stored value. A committed session does not keep a routine name, per-stage or per-exercise
 * time, or calories, so those parts of the Figma layout are deliberately not drawn (nothing is estimated).
 */
private val WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")

private fun LocalDate.title(): String = "${monthValue}월 ${dayOfMonth}일 · ${WEEKDAYS[dayOfWeek.value - 1]}요일"

@Composable
private fun RecordHeading(eyebrow: String, title: String, description: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(description, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RecordMessage(title: String, body: String, onRetry: (() -> Unit)? = null) {
    Move60Card(Modifier.testTag("record_message")) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(body, color = MaterialTheme.colorScheme.secondary)
        if (onRetry != null) Move60Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag("record_retry")) { Text("다시 불러오기") }
    }
}

/** Figma 26 · 날짜별 운동 기록. */
@Composable
fun RecordDayScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: RecordDayViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar("날짜별 운동 기록", onBack, "record_back") }) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).testTag("record_day_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val s = state) {
                RecordLoad.Loading -> Text("기록을 불러오고 있어요.", color = MaterialTheme.colorScheme.secondary)
                RecordLoad.Missing -> RecordMessage("기록을 찾을 수 없어요", "날짜를 다시 선택해 주세요.")
                RecordLoad.Failed -> RecordMessage("기록을 불러오지 못했어요", "저장된 기록은 그대로예요. 다시 시도해 주세요.", viewModel::load)
                is RecordLoad.Ready -> DayRecordContent(s.value, viewModel::exerciseName, onOpenSession)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DayRecordContent(record: DayRecord, name: (String) -> String, onOpenSession: (String) -> Unit) {
    RecordHeading(
        "ACTIVITY / DAY", record.date.title(),
        if (record.sessions.isNotEmpty()) "시간순으로 기록한 ${record.sessions.size}개 운동 세션을 선택해요."
        else "이 날짜에 저장된 운동 세션은 없어요."
    )
    if (record.sessions.isNotEmpty()) {
        Move60Card(Modifier.testTag("record_day_total")) {
            Text("실제 운동시간", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Text(RecordFormat.duration(record.sessionSeconds), style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.testTag("record_day_total_time"))
        }
        Text("수행한 운동", style = MaterialTheme.typography.titleMedium)
        record.sessions.forEach { s -> RecordSessionCard(s, name) { onOpenSession(s.sessionId) } }
    }
    record.legacyActiveSeconds?.let { seconds ->
        Move60Card(Modifier.testTag("record_day_legacy")) {
            Text("이전 버전 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("운동시간 ${RecordFormat.duration(seconds)}")
            Text("앱 업데이트 이전에 저장된 하루 합계예요. 새 기록과 더하지 않아요.",
                color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (record.unlinkedLegacy.isNotEmpty()) SetRecordsSection(LegacySetDay(record.date, record.unlinkedLegacy), name)
    if (record.isEmpty) RecordMessage("이 날짜에는 기록이 없어요", "운동을 마치면 여기에서 볼 수 있어요.")
}

@Composable
private fun RecordSessionCard(s: RecordSession, name: (String) -> String, onOpen: () -> Unit) {
    Move60Card(
        Modifier.testTag("record_session_${s.sessionId}")
            .clickable(onClickLabel = "운동 세션 상세 보기", role = Role.Button, onClick = onOpen),
        spacing = 8.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(9.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Text(s.startClock?.let { "$it 시작" } ?: "시작 시각 기록 없음",
                color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("운동 세션", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
        Text(
            if (s.exerciseIds.isEmpty()) "완료한 세트 없음" else s.exerciseIds.joinToString(" · ") { name(it) },
            color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall
        )
        Text("${RecordFormat.completionLabel(s.completion)} · 완료 세트 ${s.completedSets}개",
            color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        Row(
            Modifier.background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(RecordFormat.duration(s.activeSecondsOnDate), fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("record_session_time_${s.sessionId}"))
        }
    }
}

/** Figma 27 · 운동 기록 상세. */
@Composable
fun RecordSessionScreen(
    onBack: () -> Unit,
    viewModel: RecordSessionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar("운동 기록 상세", onBack, "record_back") }) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).testTag("record_session_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val s = state) {
                RecordLoad.Loading -> Text("기록을 불러오고 있어요.", color = MaterialTheme.colorScheme.secondary)
                RecordLoad.Missing -> RecordMessage("기록을 찾을 수 없어요", "목록에서 다시 선택해 주세요.")
                RecordLoad.Failed -> RecordMessage("기록을 불러오지 못했어요", "저장된 기록은 그대로예요. 다시 시도해 주세요.", viewModel::load)
                is RecordLoad.Ready -> SessionContent(s.value, viewModel::exerciseName)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SessionContent(detail: SessionDetail, name: (String) -> String) {
    val s = detail.session
    RecordHeading(
        "ACTIVITY / SESSION", "운동 세션",
        listOfNotNull(s.startClock?.let { "$it 시작" }, RecordFormat.completionLabel(s.completion)).joinToString(" · ")
    )
    Move60Card(Modifier.testTag("record_session_total")) {
        Text("실제 운동시간", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Text(RecordFormat.duration(s.totalActiveSeconds), style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, modifier = Modifier.testTag("record_session_total_time"))
    }
    Text("수행한 운동", style = MaterialTheme.typography.titleMedium)
    StrengthStageCard(detail.exercises, name)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.testTag("record_session_footnote")) {
        Text("유효 기록은 시간형 활성 합계 60초↑ 또는 근력 1세트 완료↑", color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall)
        Text("미달 세션은 종료 확정 시 원시 구간 삭제 · 도전 실적에서 제외", color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StrengthStageCard(exercises: List<ExerciseSets>, name: (String) -> String) {
    var open by rememberSaveable { mutableStateOf(true) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().testTag("record_stage_strength")
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
    ) {
        Column(
            Modifier.fillMaxWidth().testTag("record_stage_toggle")
                .clickable(onClickLabel = if (open) "접기" else "펼치기", role = Role.Button) { open = !open }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("근력운동", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) "접기" else "펼치기")
            }
            Text(
                if (exercises.isEmpty()) "완료한 세트가 없어요"
                else "${exercises.size}종목 · ${exercises.sumOf { e -> e.sets.count { !it.differsFromStored } }}세트",
                color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("record_stage_summary")
            )
        }
        if (open && exercises.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                exercises.forEach { ExerciseCard(it, name) }
            }
        }
    }
}

@Composable
private fun ExerciseCard(ex: ExerciseSets, name: (String) -> String) {
    val shape = RoundedCornerShape(8.dp)
    val base = ex.sets.filter { !it.differsFromStored }
    val differing = ex.sets.filter { it.differsFromStored }
    val legacyCount = base.count { it.origin == SetOrigin.LEGACY }
    Column(
        Modifier.fillMaxWidth().testTag("record_exercise_${ex.exerciseId}")
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(3.dp).height(22.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
            Text(name(ex.exerciseId), fontWeight = FontWeight.Medium)
        }
        Text(RecordFormat.setSummary(base), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("record_exercise_sets_${ex.exerciseId}"))
        if (legacyCount > 0) Text("이전 버전 기록 ${legacyCount}세트 포함", color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall)
        differing.forEach {
            Text("${it.setNumber}세트: 이전 버전 값 ${it.kg}kg × ${it.reps}회 · 저장된 값과 달라요",
                color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Figma 11 selected-day card: opens the day record. Shown only when the date has stored sessions. */
@Composable
internal fun RecordDaySummaryCard(record: DayRecord, onOpen: () -> Unit) {
    Move60Card(
        Modifier.testTag("record_day_summary")
            .clickable(onClickLabel = "날짜별 운동 기록 열기", role = Role.Button, onClick = onOpen),
        spacing = 8.dp
    ) {
        Text(record.date.title(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text("운동 ${record.sessions.size}회", color = MaterialTheme.colorScheme.secondary)
        Text("운동시간 ${RecordFormat.duration(record.sessionSeconds)}", color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium)
    }
}
