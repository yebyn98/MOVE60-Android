package com.move60.workout.architecture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.move60.workout.domain.StageKind
import com.move60.workout.domain.StageReadiness
import com.move60.workout.ui.Move60Button
import com.move60.workout.ui.Move60Card

internal fun formatMmSs(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

/**
 * ROUTIVE_4 workout progress (4 fixed stages). Driven only by the real stored session: planned time is a guide and never
 * moves or ends a stage by itself; unknown planned time is shown as "미정", never 0. Heart rate and notifications arrive
 * with the watch/notification phase, so nothing is shown for them here.
 */
@Composable
fun RoutiveSessionScreen(
    onBack: () -> Unit,
    onResult: (sessionId: String) -> Unit,
    viewModel: RoutiveSessionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    var confirmSkipLast by rememberSaveable { mutableStateOf(false) }
    val outcome = state.outcome
    LaunchedEffect(outcome) { if (outcome is RoutiveOutcome.ResultReady) onResult(outcome.sessionId) }
    val active = state.load == RoutiveLoad.ACTIVE && !state.over

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(if (state.paused) "운동 일시정지" else "오늘의 운동", onBack, backTag = "routive_back") },
        bottomBar = {
            if (active) Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).testTag("routive_bar")) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.paused) {
                        Move60Button(onClick = { confirmEnd = true }, primary = false, modifier = Modifier.weight(1f).testTag("routive_end")) { Text("운동 종료") }
                        Move60Button(onClick = viewModel::playPause, modifier = Modifier.weight(1f).testTag("routive_resume")) { Text("운동 재개") }
                    } else {
                        Move60Button(onClick = viewModel::playPause, primary = false, modifier = Modifier.weight(1f).testTag("routive_pause")) { Text("일시정지") }
                        Move60Button(onClick = viewModel::next, modifier = Modifier.weight(1.4f).testTag("routive_next")) { Text(if (state.isLast) "운동 완료" else "다음 단계") }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("이번 운동 누적 시간", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(formatMmSs(state.totalSeconds), fontWeight = FontWeight.Bold, modifier = Modifier.testTag("routive_total_time"))
                }
            }
        }
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("routive_session"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                state.load == RoutiveLoad.LOADING -> Text("불러오는 중…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.load == RoutiveLoad.NONE -> EndNotice("routive_none", "진행 중인 운동이 없어요.", "저장한 루틴에서 운동을 시작할 수 있어요.", onBack)
                state.over -> when (outcome) {
                    RoutiveOutcome.NotRecorded -> EndNotice("routive_not_recorded", "기록하지 않았어요.",
                        "운동이 1분 미만이고 완료한 세트가 없어서 기록 대상이 아니에요.", onBack)
                    RoutiveOutcome.RetryLater -> EndNotice("routive_retry_later", "기록을 안전하게 보관했어요.",
                        "지금은 저장을 마치지 못했어요. 앱을 다시 열면 자동으로 다시 시도해요.", onBack)
                    else -> Text("기록을 저장하는 중…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("routive_saving"))
                }
                else -> ActiveContent(state, viewModel, onSkipLast = { confirmSkipLast = true })
            }
        }
    }

    if (confirmSkipLast) AlertDialog(
        onDismissRequest = { confirmSkipLast = false },
        modifier = Modifier.testTag("routive_skip_last_dialog"),
        title = { Text("마무리를 생략하고 운동을 완료할까요?") },
        text = { Text("마무리 단계는 생략한 것으로 기록하고, 지금까지의 실제 운동 시간과 완료한 세트를 저장해요.") },
        confirmButton = { TextButton(onClick = { confirmSkipLast = false; viewModel.skip() }, modifier = Modifier.heightIn(min = 48.dp).testTag("routive_skip_last_confirm")) { Text("생략하고 완료") } },
        dismissButton = { TextButton(onClick = { confirmSkipLast = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("계속하기") } }
    )

    if (confirmEnd) AlertDialog(
        onDismissRequest = { confirmEnd = false },
        modifier = Modifier.testTag("routive_end_dialog"),
        title = { Text("지금 운동을 종료할까요?") },
        text = { Text("지금까지의 실제 운동 시간과 완료한 세트를 일부 수행으로 기록해요. 1분 미만이고 완료한 세트가 없으면 기록하지 않아요.") },
        confirmButton = { TextButton(onClick = { confirmEnd = false; viewModel.endEarly() }, modifier = Modifier.heightIn(min = 48.dp).testTag("routive_end_confirm")) { Text("기록하고 종료") } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("계속하기") } }
    )
}

@Composable
private fun EndNotice(tag: String, title: String, body: String, onClose: () -> Unit) {
    Move60Card(Modifier.testTag(tag)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Move60Button(onClick = onClose, primary = false, modifier = Modifier.fillMaxWidth().testTag("${tag}_close")) { Text("닫기") }
    }
}

@Composable
private fun ActiveContent(state: RoutiveSessionUiState, vm: RoutiveSessionViewModel, onSkipLast: () -> Unit) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Text(if (state.running) "● 진행 중" else "● 일시정지", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag("routive_status"))
    Text("${state.stageIndex + 1} / ${state.stageCount} 단계 · ${state.stageTitle}", color = secondary, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag("routive_stage_label"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0 until state.stageCount).forEach { i ->
            Box(Modifier.weight(1f).height(4.dp).background(if (i <= state.stageIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape))
        }
    }
    if (state.autoPaused && state.paused) Move60Card(Modifier.testTag("routive_auto_paused")) {
        Text("자동으로 일시정지했어요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("앱이나 알림 서비스가 멈춰서 확인할 수 없는 시간은 운동 시간에 넣지 않았어요. 준비되면 재개해 주세요.", color = secondary, style = MaterialTheme.typography.bodySmall)
    }
    Move60Card(Modifier.testTag("routive_stage_card")) {
        Text(state.stageTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(state.routineName, color = secondary, style = MaterialTheme.typography.bodySmall)
        Text("이 단계 경과", color = secondary, style = MaterialTheme.typography.bodySmall)
        Text(formatMmSs(state.elapsedSeconds), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("routive_elapsed"))
        Text(state.plannedLabel, color = secondary, modifier = Modifier.testTag("routive_planned"))
        val remaining = state.remainingSeconds
        when {
            remaining == null -> Text("남은 시간 미정", color = secondary, modifier = Modifier.testTag("routive_remaining_unknown"))
            state.overtimeSeconds > 0 -> Text("계획보다 ${formatMmSs(state.overtimeSeconds)} 더 진행 중", color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.testTag("routive_overtime"))
            else -> Text("남은 시간 ${formatMmSs(remaining)}", modifier = Modifier.testTag("routive_remaining"))
        }
        readinessHint(state.readiness)?.let {
            Text(it, color = secondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("routive_hint"))
        }
    }

    if (state.stageKind == StageKind.STRENGTH) state.exercises.forEach { StrengthExercise(it, vm) }
    else state.exercises.forEach { e ->
        Move60Card(Modifier.testTag("routive_exercise_${e.id}")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(e.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(e.plannedLabel, color = secondary)
            }
        }
    }
    if (state.stageKind == StageKind.STRENGTH)
        Text("완료한 세트만 운동 기록에 반영돼요.", color = secondary, style = MaterialTheme.typography.bodySmall)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = vm::back, enabled = state.stageIndex > 0, modifier = Modifier.heightIn(min = 48.dp).weight(1f).testTag("routive_back_stage")) { Text("이전 단계") }
        if (!state.isLast) TextButton(onClick = vm::skip, modifier = Modifier.heightIn(min = 48.dp).weight(1f).testTag("routive_skip")) { Text("이 단계 건너뛰기") }
        else TextButton(onClick = onSkipLast, modifier = Modifier.heightIn(min = 48.dp).weight(1f).testTag("routive_skip_last")) { Text("마무리 생략") }
    }
    state.nextStageTitle?.let {
        Text("다음 단계", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Move60Card(Modifier.testTag("routive_next_card")) { Text(it) }
    }
}

private fun readinessHint(r: StageReadiness): String? = when (r) {
    StageReadiness.PLAN_REACHED -> "계획한 시간이 됐어요. 준비되면 직접 다음 단계로 넘어가세요."
    StageReadiness.SETS_DONE -> "기록한 세트가 있어요. 준비되면 직접 다음 단계로 넘어가세요."
    StageReadiness.MANUAL -> "시간이 정해지지 않은 단계예요. 준비되면 직접 다음 단계로 넘어가세요."
    else -> null
}

@Composable
private fun StrengthExercise(e: RoutiveExerciseUi, vm: RoutiveSessionViewModel) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Move60Card(Modifier.testTag("routive_exercise_${e.id}")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(e.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${e.done.size}세트 완료", color = secondary, modifier = Modifier.testTag("routive_sets_${e.id}"))
        }
        e.done.forEach { Text("${it.number}세트 · ${it.kg}kg × ${it.reps}회", style = MaterialTheme.typography.bodyMedium) }
        var kg by remember(e.id, e.nextSetNumber) { mutableStateOf("") }
        var reps by remember(e.id, e.nextSetNumber) { mutableStateOf("") }
        Text("${e.nextSetNumber}세트", color = secondary, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(kg, { kg = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, singleLine = true, label = { Text("kg") },
                modifier = Modifier.weight(1f).testTag("routive_kg_${e.id}"))
            OutlinedTextField(reps, { reps = it.filter(Char::isDigit).take(3) }, singleLine = true, label = { Text("회") },
                modifier = Modifier.weight(1f).testTag("routive_reps_${e.id}"))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (e.done.isNotEmpty()) Move60Button(onClick = { vm.undoLastSet(e.id) }, primary = false,
                modifier = Modifier.weight(1f).testTag("routive_set_undo_${e.id}")) { Text("마지막 세트 취소") }
            Move60Button(onClick = { vm.completeSet(e.id, kg, reps) }, enabled = kg.isNotBlank() && reps.isNotBlank(),
                modifier = Modifier.weight(1f).testTag("routive_set_done_${e.id}")) { Text("세트 완료") }
        }
    }
}
