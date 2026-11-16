package com.move60.workout.architecture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.move60.workout.ui.Move60Button
import com.move60.workout.ui.Move60Card

/** Saved routines. A routine is started only by an explicit confirmation; nothing here is a recommendation. */
@Composable
fun RoutineListScreen(
    onBack: () -> Unit, onCompose: () -> Unit, onOpenSession: () -> Unit,
    viewModel: RoutineListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) { viewModel.refreshActive(); onPauseOrDispose { } }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar("저장한 루틴", onBack, backTag = "routine_list_back") },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                Move60Button(onClick = onCompose, modifier = Modifier.fillMaxWidth().testTag("routine_list_compose")) { Text("루틴 만들기") }
            }
        }
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).testTag("routine_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.activeRoutineName?.let { name ->
                item {
                    Move60Card(Modifier.testTag("routine_active_banner")) {
                        Text("진행 중인 운동이 있어요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(name, color = secondary, style = MaterialTheme.typography.bodySmall)
                        Move60Button(onClick = onOpenSession, modifier = Modifier.fillMaxWidth().testTag("routine_active_continue")) { Text("이어서 운동하기") }
                    }
                }
            }
            when {
                state.load == RoutineListLoad.FAILED && state.items.isEmpty() -> item {
                    Move60Card(Modifier.testTag("routine_list_failed")) {
                        Text("저장한 루틴을 불러오지 못했어요.", style = MaterialTheme.typography.titleMedium)
                        Text("저장된 루틴은 그대로 있어요. 다시 시도해 주세요.", color = secondary, style = MaterialTheme.typography.bodySmall)
                        Move60Button(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth().testTag("routine_list_retry")) { Text("다시 시도") }
                    }
                }
                state.load == RoutineListLoad.READY && state.items.isEmpty() -> item {
                    Move60Card(Modifier.testTag("routine_list_empty")) {
                        Text("아직 만든 운동 루틴이 없어요", style = MaterialTheme.typography.titleMedium)
                        Text("운동을 추가하고 나만의 루틴을 만들어 보세요.", color = secondary, style = MaterialTheme.typography.bodySmall)
                        Move60Button(onClick = onCompose, modifier = Modifier.fillMaxWidth().testTag("routine_empty_cta")) { Text("루틴 만들기") }
                    }
                }
                else -> items(state.items, key = { it.routineId }) { item ->
                    Move60Card(Modifier.testTag("routine_item_${item.routineId}")) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${item.targetLabel} · 운동 ${item.exerciseCount}개", color = secondary, style = MaterialTheme.typography.bodySmall)
                        item.stages.forEach { (title, time) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(title, style = MaterialTheme.typography.bodyMedium)
                                Text(time, style = MaterialTheme.typography.bodyMedium, color = secondary)
                            }
                        }
                        Move60Button(onClick = { confirm = item.routineId }, modifier = Modifier.fillMaxWidth().testTag("routine_start_${item.routineId}")) { Text("이 루틴으로 시작") }
                    }
                }
            }
        }
    }

    confirm?.let { id ->
        val name = state.items.firstOrNull { it.routineId == id }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { confirm = null },
            modifier = Modifier.testTag("routine_start_dialog"),
            title = { Text("이 루틴으로 운동을 시작할까요?") },
            text = { Text("‘$name’ 루틴을 지금 시작해요. 4단계(준비운동·근력운동·유산소·마무리)로 진행하고, 시간이 정해지지 않은 단계는 직접 넘겨요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    viewModel.start(id) { result ->
                        when (result) {
                            is LaunchResult.Started -> onOpenSession()
                            is LaunchResult.AlreadyActive -> notice = "active"
                            LaunchResult.LegacyActive -> notice = "legacy"
                            LaunchResult.NotFound -> { notice = "missing"; viewModel.refresh() }
                            LaunchResult.InvalidPlan -> notice = "invalid"
                            LaunchResult.Failed -> notice = "failed"
                        }
                    }
                }, modifier = Modifier.heightIn(min = 48.dp).testTag("routine_start_confirm")) { Text("시작") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("취소") } }
        )
    }
    notice?.let { kind ->
        val text = when (kind) {
            "active" -> "이미 진행 중인 운동이 있어요. 이어서 하거나 먼저 종료해 주세요."
            "legacy" -> "기존 방식으로 진행 중인 운동이 있어요. 먼저 마치거나 종료해 주세요."
            "missing" -> "저장한 루틴을 찾지 못했어요. 목록을 새로 불러왔어요."
            "invalid" -> "이 루틴은 4단계 형태가 아니라 시작할 수 없어요."
            else -> "운동을 시작하지 못했어요. 잠시 후 다시 시도해 주세요."
        }
        AlertDialog(
            onDismissRequest = { notice = null },
            modifier = Modifier.testTag("routine_start_notice"),
            title = { Text("운동을 시작하지 않았어요") },
            text = { Text(text) },
            confirmButton = {
                if (kind == "active") TextButton(onClick = { notice = null; onOpenSession() }, modifier = Modifier.heightIn(min = 48.dp).testTag("routine_notice_continue")) { Text("이어하기") }
                else TextButton(onClick = { notice = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("확인") }
            },
            dismissButton = { if (kind == "active") TextButton(onClick = { notice = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") } }
        )
    }
}
