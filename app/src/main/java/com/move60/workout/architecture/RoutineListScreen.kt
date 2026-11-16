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
import androidx.compose.material3.MaterialTheme
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

/** Saved routines (read-only). Starting a workout from a saved routine is a later step; nothing here is a recommendation. */
@Composable
fun RoutineListScreen(onBack: () -> Unit, onCompose: () -> Unit, viewModel: RoutineListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                        Text("저장한 루틴이 없어요.", style = MaterialTheme.typography.titleMedium)
                        Text("운동과 계획시간을 직접 골라 첫 루틴을 만들어 보세요.", color = secondary, style = MaterialTheme.typography.bodySmall)
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
                    }
                }
            }
        }
    }
}
