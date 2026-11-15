package com.move60.workout.architecture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/** Figma 08 workout result. Judgement (completion) and save status are shown separately. */
@Composable
fun WorkoutResultScreen(onViewRecord: () -> Unit, viewModel: ResultViewModel = hiltViewModel()) {
    val r by viewModel.result.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar("운동 결과", null) }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp).testTag("result_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val (title, sub) = when (r.completion) {
                SessionCompletion.COMPLETED -> "오늘 운동을 마쳤어요" to "수행한 운동이 기록으로 저장돼요."
                SessionCompletion.PARTIAL -> "운동을 일부 마쳤어요" to "중간까지 수행한 실제 시간이 기록으로 저장돼요."
                SessionCompletion.NOT_PERFORMED -> "기록할 운동이 없어요" to "수행한 운동이 없어 저장하지 않았어요."
                null -> when (r.saveState) {
                    ResultSaveState.SAVING -> "결과를 저장하고 있어요" to "잠시만 기다려 주세요."
                    else -> "결과를 저장하지 못했어요" to "다시 시도하면 같은 운동이 중복 없이 저장돼요."
                }
            }
            Move60Card(Modifier.testTag("result_summary")) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when (r.saveState) {
                        ResultSaveState.SAVING -> "저장 중"
                        ResultSaveState.SAVED -> "저장됨"
                        ResultSaveState.FAILED -> "저장 실패"
                        ResultSaveState.NOT_STORED -> "저장 안 함"
                    },
                    modifier = Modifier.testTag("result_save_state"),
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
                )
            }
            if (r.saveState == ResultSaveState.SAVED) {
                Text("실제 운동시간", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${r.activeSeconds / 60}분 ${r.activeSeconds % 60}초",
                    style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("result_active_time"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Move60Card(Modifier.weight(1f).testTag("result_sets")) {
                        Text("완료한 세트", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${r.completedSets}세트", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                r.challengeLine?.let {
                    Move60Card(Modifier.testTag("result_challenge")) { Text(it) }
                }
            }
            if (r.saveState == ResultSaveState.FAILED) {
                Move60Button(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth().testTag("result_retry")) { Text("다시 저장") }
            }
            Move60Button(onClick = onViewRecord, primary = r.saveState != ResultSaveState.FAILED,
                modifier = Modifier.fillMaxWidth().testTag("result_view_record")) { Text("기록 보기") }
        }
    }
}
