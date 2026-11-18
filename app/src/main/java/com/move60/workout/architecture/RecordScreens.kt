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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
                RecordLoad.Failed -> RecordMessage("기록을 불러오지 못했어요.", "저장된 데이터는 유지되며 다시 확인할 수 있어요.", viewModel::load)
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
            Text("이전 버전 운동 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("운동시간 ${RecordFormat.duration(seconds)}")
            Text("이전에 저장한 운동 기록은 그대로 유지돼요. 하루 합계는 새 기록과 더하지 않아요.",
                color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (record.unlinkedLegacy.isNotEmpty()) SetRecordsSection(LegacySetDay(record.date, record.unlinkedLegacy), name)
    if (record.isEmpty) RecordMessage("이날은 저장된 운동 기록이 없어요.", "운동을 마치면 여기에서 볼 수 있어요.")
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
            Text(s.routineName ?: "운동 세션", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
        Text(
            if (s.exerciseIds.isEmpty()) "완료한 세트 없음" else s.exerciseIds.joinToString(" · ") { name(it) },
            color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall
        )
        if (s.stageSeconds.isNotEmpty()) Text(
            s.stageSeconds.joinToString(" · ") { (label, seconds) -> "$label ${RecordFormat.compact(seconds)}" },
            color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("record_session_stages_${s.sessionId}")
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
                RecordLoad.Failed -> RecordMessage("기록을 불러오지 못했어요.", "저장된 데이터는 유지되며 다시 확인할 수 있어요.", viewModel::load)
                is RecordLoad.Ready -> SessionContent(s.value, viewModel::exerciseName, viewModel::saveCorrections)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SessionContent(
    detail: SessionDetail, name: (String) -> String,
    onSaveCorrections: (List<SetCorrection>, (CorrectionResult) -> Unit) -> Unit
) {
    val s = detail.session
    RecordHeading(
        "ACTIVITY / SESSION", s.routineName ?: "운동 세션",
        listOfNotNull(s.startClock?.let { "$it 시작" }, RecordFormat.completionLabel(s.completion)).joinToString(" · ")
    )
    Move60Card(Modifier.testTag("record_session_total")) {
        Text("실제 운동시간", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Text(RecordFormat.duration(s.totalActiveSeconds), style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, modifier = Modifier.testTag("record_session_total_time"))
    }
    Text("수행한 운동", style = MaterialTheme.typography.titleMedium)
    StageCards(detail, name)
    CorrectionSection(detail, name, onSaveCorrections)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.testTag("record_session_footnote")) {
        Text("유효 기록은 시간형 활성 합계 60초↑ 또는 근력 1세트 완료↑", color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall)
        Text("미달 세션은 종료 확정 시 원시 구간 삭제 · 도전 실적에서 제외", color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StrengthStageCard(
    exercises: List<ExerciseSets>, name: (String) -> String,
    actualSeconds: Long? = null, plannedSeconds: Long? = null
) {
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
            // With stage data the real time and the plan come first; without it the summary is exactly as before.
            val timeParts = if (actualSeconds == null) emptyList() else listOf(
                "실제 ${RecordFormat.compact(actualSeconds)}",
                plannedSeconds?.let { "계획 ${RecordFormat.compact(it)}" } ?: "계획 시간 미정"
            )
            Text(
                (timeParts + if (exercises.isEmpty()) listOf("완료한 세트가 없어요")
                else listOf("${exercises.size}종목 · ${exercises.sumOf { e -> e.sets.count { !it.differsFromStored } }}세트")).joinToString(" · "),
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
        // Policy 6: a corrected value is shown with the stored original beside it.
        base.filter { it.originalKg != null || it.originalReps != null }.forEach {
            Text("${it.setNumber}세트 수정됨 · 원본 ${it.originalKg ?: it.kg}kg × ${it.originalReps ?: it.reps}회",
                color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        }
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

/** Policy 6 (P-22/P-23): correct weight/reps of completed sets, and read the correction history. Originals are never changed. */
@Composable
private fun CorrectionSection(
    detail: SessionDetail, name: (String) -> String,
    onSave: (List<SetCorrection>, (CorrectionResult) -> Unit) -> Unit
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    // Only sets stored in this session can be corrected; v58 values live in SharedPreferences and stay as they were saved.
    val editable = detail.exercises.flatMap { it.sets }.filter { it.origin == SetOrigin.STORED }
    Column(Modifier.fillMaxWidth().testTag("record_correction_section"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (editable.isNotEmpty()) {
            Move60Button(onClick = { editing = true }, primary = false,
                modifier = Modifier.fillMaxWidth().testTag("record_edit_open")) { Text("실제 운동 기록 수정") }
            Text("기록을 수정하면 이전 값과 수정 내역이 보존돼요.", color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall)
        }
        Move60Button(onClick = { showHistory = true }, primary = false,
            modifier = Modifier.fillMaxWidth().testTag("record_history_open")) { Text("수정 이력 보기") }
    }
    if (editing) CorrectionDialog(editable, name, onDismiss = { editing = false }, onSave = onSave)
    if (showHistory) HistoryDialog(detail.history, name) { showHistory = false }
}

@Composable
private fun CorrectionDialog(
    sets: List<ShownSet>, name: (String) -> String, onDismiss: () -> Unit,
    onSave: (List<SetCorrection>, (CorrectionResult) -> Unit) -> Unit
) {
    fun keyOf(s: ShownSet) = "${s.exerciseId}#${s.setNumber}"
    val kg = remember { mutableStateMapOf<String, String>().also { m -> sets.forEach { m[keyOf(it)] = it.kg } } }
    val reps = remember { mutableStateMapOf<String, String>().also { m -> sets.forEach { m[keyOf(it)] = it.reps } } }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Only values that differ from what is shown become corrections.
    val pending = sets.mapNotNull { s ->
        val k = kg[keyOf(s)].orEmpty().trim()
        val r = reps[keyOf(s)].orEmpty().trim()
        val kChanged = k != s.kg
        val rChanged = r != s.reps
        if (!kChanged && !rChanged) null
        else SetCorrection(s.exerciseId, s.setNumber, if (kChanged) k else null, if (rChanged) r else null)
    }
    val valid = pending.all {
        (it.weightKg == null || CorrectionRules.weight(it.weightKg) != null) &&
            (it.repetitions == null || CorrectionRules.reps(it.repetitions) != null)
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = Modifier.testTag("record_edit_dialog"),
        title = { Text("실제 운동 기록 수정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("기록을 수정하면 이전 값과 수정 내역이 보존돼요.", color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall)
                sets.forEach { s ->
                    val key = keyOf(s)
                    val k = kg[key].orEmpty().trim()
                    val r = reps[key].orEmpty().trim()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${name(s.exerciseId)} · ${s.setNumber}세트", fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                kg[key].orEmpty(), { kg[key] = it.filter { c -> c.isDigit() || c == '.' }.take(6); message = null },
                                label = { Text("중량(kg)") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                isError = k != s.kg && CorrectionRules.weight(k) == null,
                                modifier = Modifier.weight(1f).testTag("record_edit_kg_$key")
                            )
                            OutlinedTextField(
                                reps[key].orEmpty(), { reps[key] = it.filter { c -> c.isDigit() }.take(3); message = null },
                                label = { Text("횟수") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = r != s.reps && CorrectionRules.reps(r) == null,
                                modifier = Modifier.weight(1f).testTag("record_edit_reps_$key")
                            )
                        }
                    }
                }
                message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("record_edit_message"))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pending.isNotEmpty() && valid && !saving,
                onClick = {
                    saving = true
                    onSave(pending) { result ->
                        saving = false
                        when (result) {
                            is CorrectionResult.Saved -> onDismiss()
                            CorrectionResult.Unchanged -> message = "바뀐 값이 없어요."
                            else -> message = "수정을 저장하지 못했어요. 기존 기록은 그대로예요."
                        }
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag("record_edit_save")
            ) { Text("수정 저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp).testTag("record_edit_cancel")) { Text("취소") }
        }
    )
}

@Composable
private fun HistoryDialog(history: List<CorrectionEntry>, name: (String) -> String, onClose: () -> Unit) {
    val zone = remember { java.time.ZoneId.systemDefault() }
    val format = remember { java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.testTag("record_history_dialog"),
        title = { Text("수정 이력 보기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (history.isEmpty()) Text("수정 이력이 없어요.", modifier = Modifier.testTag("record_history_empty"))
                // Newest first; each row keeps the value before and after the change and when it was saved.
                history.asReversed().forEach { h ->
                    val weight = h.attribute == CorrectionField.WEIGHT
                    val unit = if (weight) "kg" else "회"
                    Column(Modifier.testTag("record_history_item"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${name(h.exerciseId)} · ${h.setNumber}세트 · ${if (weight) "중량" else "횟수"}", fontWeight = FontWeight.Medium)
                        Text("${h.oldValue.orEmpty()}$unit → ${h.newValue.orEmpty()}$unit", modifier = Modifier.testTag("record_history_change"))
                        Text(java.time.Instant.ofEpochMilli(h.createdAtEpochMs).atZone(zone).format(format),
                            color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") } }
    )
}

/**
 * Stage cards (Figma 27, docs/v61 D5). A session saved with stage data shows one card per stage that has real time, in stage
 * order; strength keeps its real sets. A session without stage data shows only the strength card, exactly as before.
 */
@Composable
private fun StageCards(detail: SessionDetail, name: (String) -> String) {
    if (detail.stages.isEmpty()) {
        StrengthStageCard(detail.exercises, name)
        return
    }
    var strengthShown = false
    detail.stages.forEach { stage ->
        if (stage.kind == SegmentKind.STRENGTH) {
            strengthShown = true
            StrengthStageCard(detail.exercises, name, stage.actualSeconds, stage.plannedSeconds)
        } else PlanStageCard(stage, if (stage.kind == SegmentKind.CARDIO_STEADY) detail.cardio else null)
    }
    // Sets exist but no strength time was stored: still show them rather than hide recorded sets.
    if (!strengthShown && detail.exercises.isNotEmpty()) StrengthStageCard(detail.exercises, name)
}

/**
 * A non-strength stage: the REAL time, the plan time next to it ("계획 시간 미정" when unknown) and the planned items.
 * Item times are plans, labelled "계획", never presented as measured. No cardio console values are stored, so the cardio
 * stage says "수치 미기록" instead of showing numbers.
 */
@Composable
private fun PlanStageCard(stage: StageRecord, cardioRecord: CardioStageRecord? = null) {
    val cardio = stage.kind == SegmentKind.CARDIO_STEADY
    // Cardio starts collapsed (Figma FINAL 27); the other stages keep their current default until that is decided for the app.
    var open by rememberSaveable(stage.index) { mutableStateOf(!cardio) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().testTag("record_stage_${stage.kind.name}")
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clickable(onClickLabel = if (open) "접기" else "펼치기", role = Role.Button) { open = !open }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stage.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) "접기" else "펼치기")
            }
            Text(
                listOfNotNull(
                    "실제 ${RecordFormat.compact(stage.actualSeconds)}",
                    stage.plannedSeconds?.let { "계획 ${RecordFormat.compact(it)}" } ?: "계획 시간 미정",
                    if (stage.items.isNotEmpty()) "계획 항목 ${stage.items.size}개" else null
                ).joinToString(" · "),
                color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall
            )
        }
        if (open && (stage.items.isNotEmpty() || cardio)) {
            Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stage.items.forEach { item ->
                    val itemShape = RoundedCornerShape(8.dp)
                    Column(
                        Modifier.fillMaxWidth().testTag("record_plan_item")
                            .background(MaterialTheme.colorScheme.surface, itemShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, itemShape).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.width(3.dp).height(22.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                            Text(item.name, fontWeight = FontWeight.Medium)
                        }
                        Text(item.plannedSeconds?.let { "계획 ${RecordFormat.compact(it)}" } ?: "계획 시간 미정",
                            color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (cardio && cardioRecord == null) Text("수치 미기록", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("record_cardio_no_values"))
                if (cardio && cardioRecord != null) CardioIntervalList(cardioRecord)
            }
        }
    }
}

/** Read-only real intensity intervals of the cardio stage: time range, the typed values, duration. Not-recorded time says so. */
@Composable
private fun CardioIntervalList(record: CardioStageRecord) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.fillMaxWidth().testTag("record_cardio_intervals")
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("실제 강도 구간", fontWeight = FontWeight.Medium)
        record.rows.forEach { row ->
            val machine = if (record.multipleEquipment) RecordFormat.cardioEquipmentLabel(row.equipmentKey)?.let { "$it · " } else null
            Row(
                Modifier.fillMaxWidth().testTag("record_cardio_interval"),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(RecordFormat.cardioRange(row.startSeconds, row.endSeconds),
                        color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                    Text((machine ?: "") + (row.values?.let(RecordFormat::cardioValues) ?: "미기록"),
                        color = if (row.recorded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondary)
                }
                Text(RecordFormat.compact(row.seconds.toLong()), color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
