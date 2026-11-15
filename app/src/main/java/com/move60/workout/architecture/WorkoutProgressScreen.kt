package com.move60.workout.architecture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.move60.workout.ui.Move60Button
import com.move60.workout.ui.Move60Card

/**
 * Figma 05/06/07/58/59 workout progress. Driven only by the real timer state; the stage count is the engine's
 * (the 4-stage routine model is a separate change). Pause shows only the end/resume actions; ending early requires
 * the confirm dialog and is saved as PARTIAL from real active time.
 */
@Composable
fun WorkoutProgressScreen(
    onBack: () -> Unit,
    onResult: (sessionId: String) -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    val started = state.running || state.activeSeconds > 0
    val paused = started && !state.running && !state.finished && !state.endedEarly
    val lastStage = (state.stageNames.size - 1).coerceAtLeast(0)
    val onLast = state.stage >= lastStage

    LaunchedEffect(state.finished, state.endedEarly, state.sessionId) {
        if ((state.finished || state.endedEarly) && state.sessionId > 0 && state.recordDate.isNotEmpty())
            onResult("timer-${state.recordDate}-d${state.day}-${state.sessionId}")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(if (paused) "운동 일시정지" else "오늘의 운동", onBack, backTag = "workout_back") },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).testTag("workout_progress_bar")) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (paused) {
                        Move60Button(onClick = { confirmEnd = true }, primary = false,
                            modifier = Modifier.weight(1f).testTag("workout_end")) { Text("운동 종료") }
                        Move60Button(onClick = viewModel::playPause,
                            modifier = Modifier.weight(1f).testTag("workout_resume")) { Text("운동 재개") }
                    } else {
                        Move60Button(onClick = viewModel::playPause, primary = false,
                            modifier = Modifier.weight(1f).testTag("workout_pause")) {
                            Text(if (state.running) "일시정지" else "운동 시작")
                        }
                        Move60Button(
                            onClick = { if (onLast) viewModel.finish() else viewModel.next() },
                            enabled = started && !state.finished && !state.endedEarly,
                            modifier = Modifier.weight(1.4f).testTag("workout_next")
                        ) { Text(if (onLast) "운동 종료" else "다음 단계") }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("오늘 누적 운동시간", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Text(formatHms(state.activeSeconds), fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("workout_total_time"))
                }
            }
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).testTag("workout_progress"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (paused) "● 일시정지" else if (state.running) "● 진행 중" else "● 시작 전",
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("workout_status"))
            Text("${state.stage + 1} / ${state.stageNames.size.coerceAtLeast(1)} 단계 · ${state.stageName.substringBefore(" · ")}",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.stageNames.indices.forEach { i ->
                    Box(Modifier.weight(1f).height(4.dp).background(
                        if (i <= state.stage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape))
                }
            }
            Move60Card(Modifier.testTag("workout_stage_card")) {
                Text(state.stageName.substringBefore(" · "), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (state.itemName.isNotBlank()) Text(state.itemName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.stage != 2) {
                    Text("이 구간 남은 시간", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(formatSeconds(state.remainingSeconds), style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.testTag("workout_remaining"))
                    val total = state.stageTotalSeconds.coerceAtLeast(1)
                    LinearProgressIndicator(progress = { (1f - state.remainingSeconds / total.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline)
                }
                HeartRow()
            }
            if (state.stage == 2) StrengthSets(state, viewModel)
            state.stageNames.getOrNull(state.stage + 1)?.let {
                Text("다음 단계", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Move60Card { Text(it.substringBefore(" · ")) }
            }
        }
    }

    if (confirmEnd) AlertDialog(
        onDismissRequest = { confirmEnd = false },
        modifier = Modifier.testTag("workout_end_dialog"),
        title = { Text("지금 운동을 종료할까요?") },
        text = {
            Text(if (state.activeSeconds >= 60) "실제 운동 ${state.activeSeconds / 60}분을 일부 수행으로 기록해요."
            else "실제 운동이 1분 미만이면 기록하지 않아요.")
        },
        confirmButton = {
            TextButton(onClick = { confirmEnd = false; viewModel.endEarly() },
                modifier = Modifier.testTag("workout_end_confirm")) { Text("기록하고 종료") }
        },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("계속하기") } }
    )
}

/** Heart rate comes from a fresh watch sample only; an old value is never shown as current. */
@Composable
private fun HeartRow() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("preferences", android.content.Context.MODE_PRIVATE)
    val heart = prefs.getInt("watch_heart_rate", 0)
    val age = System.currentTimeMillis() - prefs.getLong("watch_heart_rate_at", 0L)
    val text = if (com.move60.workout.domain.HeartRateFreshness.isFresh(heart, age)) "$heart BPM · 측정 중" else "-- BPM · ${com.move60.workout.domain.HeartRateFreshness.WAITING_LABEL}"
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
        .padding(12.dp).testTag("workout_heart"), verticalAlignment = Alignment.CenterVertically) {
        Text("♥ $text", color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun StrengthSets(state: WorkoutUiState, viewModel: WorkoutViewModel) {
    state.stageTasks.forEach { task ->
        Move60Card(Modifier.testTag("workout_task_${task.id}")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(task.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${task.done} / ${task.sets}세트", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            task.entries.forEach { set ->
                var kg by remember(task.id, set.number, set.kg) { mutableStateOf(set.kg) }
                var reps by remember(task.id, set.number, set.reps) { mutableStateOf(set.reps) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${set.number}세트", modifier = Modifier.weight(0.7f))
                    OutlinedTextField(kg, { kg = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, singleLine = true,
                        label = { Text("kg") }, enabled = !set.completed,
                        modifier = Modifier.weight(1f).testTag("set_kg_${task.id}_${set.number}"))
                    OutlinedTextField(reps, { reps = it.filter(Char::isDigit).take(3) }, singleLine = true,
                        label = { Text("회") }, enabled = !set.completed,
                        modifier = Modifier.weight(1f).testTag("set_reps_${task.id}_${set.number}"))
                    Move60Button(onClick = { viewModel.saveSet(task.id, set.number, kg, reps, !set.completed) },
                        primary = !set.completed, enabled = set.completed || (kg.isNotBlank() && reps.isNotBlank()),
                        modifier = Modifier.testTag("set_done_${task.id}_${set.number}")) { Text(if (set.completed) "취소" else "완료") }
                }
            }
        }
    }
    Text("완료한 세트만 운동 기록에 반영돼요.", color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall)
}

private fun formatHms(seconds: Int): String =
    "%02d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
