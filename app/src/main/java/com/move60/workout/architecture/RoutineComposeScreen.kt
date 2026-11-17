package com.move60.workout.architecture

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.move60.workout.domain.StageKind
import com.move60.workout.ui.Move60Button
import com.move60.workout.ui.Move60Card

private fun StageKind.title() = when (this) {
    StageKind.WARM_UP -> "준비운동"; StageKind.STRENGTH -> "근력운동"
    StageKind.CARDIO -> "유산소"; StageKind.COOL_DOWN -> "마무리"
}
private fun StageKind.hint() = when (this) {
    StageKind.WARM_UP -> "걷기·가동성 운동 / 제외 가능"; StageKind.STRENGTH -> "상체·하체 종목 선택"
    StageKind.CARDIO -> "유산소 기구 선택"; StageKind.COOL_DOWN -> "스트레칭 / 제외 가능"
}
private val stageOrder = listOf(StageKind.WARM_UP, StageKind.STRENGTH, StageKind.CARDIO, StageKind.COOL_DOWN)

/** Figma FINAL 41 · 루틴 직접 구성·편집. Plan times are optional (E-02 B); nothing here is a recommendation. */
@Composable
fun RoutineComposeScreen(onBack: () -> Unit, viewModel: RoutineComposeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val space by viewModel.space.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<StageKind?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar("루틴 구성", onBack, backTag = "routine_back") },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Errors sit next to the confirm button so they are visible without scrolling the list.
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("routine_error")) }
                if (state.save == ComposeSaveState.SAVED) {
                    Text("루틴을 저장했어요.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("routine_saved"))
                    Move60Button(onClick = onBack, modifier = Modifier.fillMaxWidth().testTag("routine_done")) { Text("완료") }
                } else {
                    if (state.exerciseCount < 1) Text("운동을 1개 이상 추가해야 루틴을 확정할 수 있어요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("routine_need_one"))
                    Move60Button(onClick = viewModel::confirm, enabled = state.canConfirm,
                        modifier = Modifier.fillMaxWidth().testTag("routine_confirm")) {
                        Text(if (state.save == ComposeSaveState.FAILED) "다시 저장" else "루틴 확정")
                    }
                }
            }
        }
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).testTag("routine_compose"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("운동 추가·준비·마무리는 선택", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            item {
                Move60Card(Modifier.testTag("routine_summary")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (state.targetMinutes.isBlank()) "목표 시간 미정" else "목표 ${state.targetMinutes}분",
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("routine_target_label"))
                        Text("구성 ${state.exerciseCount}개", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("routine_count"))
                    }
                    Text(if (state.exerciseCount == 0) "운동 항목 0개 · 구성 필요" else "운동 항목 ${state.exerciseCount}개 구성됨",
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("종목 시간을 모르면 비워 두어도 돼요. 입력한 시간의 합이 목표를 넘으면 안 돼요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Move60Card(Modifier.testTag("routine_equipment")) {
                    Text("운동 후보 기준 공간", style = MaterialTheme.typography.titleMedium)
                    when (val sp = space) {
                        CandidateSpace.Loading -> Text("운동 공간을 불러오고 있어요.", style = MaterialTheme.typography.bodyMedium)
                        CandidateSpace.NoSpace -> Text("기본 운동 공간이 없어요. 마이 > 운동 공간에서 등록하면 보유 기구에 맞는 운동 후보를 볼 수 있어요.",
                            style = MaterialTheme.typography.bodyMedium)
                        CandidateSpace.Failed -> Text("운동 공간을 불러오지 못했어요. 운동은 직접 구성해서 고를 수 있어요.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        is CandidateSpace.Ready -> {
                            Text(sp.name, style = MaterialTheme.typography.bodyMedium)
                            Text(if (sp.equipment.isEmpty()) "등록된 기구가 없어요 · 맨몸 운동만 후보가 돼요"
                                else "등록 기구: " + sp.equipment.mapNotNull { Equipment.fromKey(it)?.label }.sorted().joinToString(", "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("승인된 운동만 후보로 보여요. 후보에 없는 운동도 직접 구성에서 고를 수 있어요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                OutlinedTextField(state.name, viewModel::setName, label = { Text("루틴 이름") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("routine_name"))
            }
            item {
                OutlinedTextField(state.targetMinutes, viewModel::setTarget, label = { Text("목표 시간(분) · 비우면 미정") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("routine_target"))
            }
            item { Text("어떤 운동부터 시작할까요?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            item { Text("근력만·유산소만·둘 다 구성할 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            for (kind in stageOrder) item(key = kind.name) {
                val chosen = state.selected[kind].orEmpty()
                Move60Card(Modifier.clickable { picking = kind }.testTag("routine_stage_${kind.name}")) {
                    Text(if (chosen.isEmpty()) "${kind.title()} 추가" else "${kind.title()} ${chosen.size}개", style = MaterialTheme.typography.titleMedium)
                    Text(if (chosen.isEmpty()) kind.hint() else "탭해서 종목 바꾸기",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    chosen.forEach { e ->
                        OutlinedTextField(state.exerciseMinutes[exKey(kind, e.id)].orEmpty(), { viewModel.setExerciseMinutes(kind, e.id, it) }, singleLine = true,
                            label = { Text(if (kind == StageKind.STRENGTH) "${e.name} · 참고 시간(분)" else "${e.name} · 시간(분) · 비우면 미정") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("routine_minutes_${kind.name}_${e.id}"))
                        if (kind == StageKind.STRENGTH) {
                            val t = state.strengthTargets[exKey(kind, e.id)] ?: StrengthTargetText()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(t.sets, { viewModel.setTargetSets(e.id, it) }, singleLine = true, label = { Text("세트") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f).testTag("routine_sets_${e.id}"))
                                OutlinedTextField(t.reps, { viewModel.setTargetReps(e.id, it) }, singleLine = true, label = { Text("반복") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f).testTag("routine_reps_${e.id}"))
                                OutlinedTextField(t.rest, { viewModel.setRestSeconds(e.id, it) }, singleLine = true, label = { Text("휴식(초)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1.2f).testTag("routine_rest_${e.id}"))
                            }
                        }
                    }
                    if (kind == StageKind.STRENGTH && chosen.isNotEmpty())
                        Text("세트·반복·휴식은 비워 두면 정하지 않은 값으로 저장돼요. 휴식을 비우면 휴식 타이머 없이 진행해요.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (chosen.isNotEmpty()) {
                        // Derived from the exercises above; there is no separate stage-time input.
                        val total = state.derivedStageMinutes(kind)
                        Text(if (total != null) "단계 합계 ${total}분" else "단계 시간 미정",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("routine_stage_total_${kind.name}"))
                    }
                }
            }
        }
    }
    picking?.let { kind ->
        val options = viewModel.catalog[kind].orEmpty()
        var current by remember(kind) { mutableStateOf(state.selected[kind].orEmpty()) }
        AlertDialog(
            onDismissRequest = { picking = null },
            modifier = Modifier.testTag("routine_picker"),
            title = { Text("${kind.title()} 선택") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (options.isEmpty()) Text("선택할 수 있는 항목이 없어요.")
                    @Composable fun OptionRow(o: RoutineExerciseInput) {
                        val on = current.any { it.id == o.id }
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                            current = if (on) current.filterNot { it.id == o.id } else current + o
                        }.testTag("routine_option_${o.id}"), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Text(o.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (kind == StageKind.STRENGTH) {
                        val candidates = viewModel.candidates(kind)
                        val candidateIds = candidates.map { it.id }.toSet()
                        Text("보유 기구에 맞는 운동", style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("routine_candidates_title"))
                        if (candidates.isEmpty()) Text(
                            if (space is CandidateSpace.Ready) "이 공간의 기구로 고를 수 있는 승인된 운동이 아직 없어요. 아래에서 직접 구성해 주세요."
                            else "운동 공간을 확인할 수 없어 후보를 보여 줄 수 없어요. 아래에서 직접 구성해 주세요.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("routine_candidates_empty"))
                        candidates.forEach { OptionRow(it) }
                        Text("직접 구성 · 모든 운동", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp).testTag("routine_manual_title"))
                        // Already chosen items stay visible (and removable) even when they are not on today's list or no longer a candidate.
                        (options + current).distinctBy { it.id }.filter { it.id !in candidateIds }.forEach { OptionRow(it) }
                    } else options.forEach { OptionRow(it) }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.setSelection(kind, current); picking = null }, modifier = Modifier.testTag("routine_picker_ok")) { Text("선택 완료") } },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("취소") } }
        )
    }
}
