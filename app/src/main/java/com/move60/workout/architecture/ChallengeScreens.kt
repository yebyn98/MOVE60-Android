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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.move60.workout.ui.Move60Button
import com.move60.workout.ui.Move60Card
import java.time.LocalDate

/**
 * Challenge / project tab (Figma 도전·프로젝트 group). Start day is always today (confirmed policy), the end date is
 * inclusive, and the target is a cumulative count of credited days. Abandon is only possible through the confirm dialog.
 */
@Composable
fun ChallengeScreen(onBack: () -> Unit, viewModel: ChallengeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ScreenTopBar("도전", null) }
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).testTag("challenge_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val active = state.active
            if (active != null) item { ActiveChallenge(active, viewModel) }
            else if (!state.loading) item { CreateChallenge(viewModel) }
            if (state.ended.isNotEmpty()) {
                item {
                    Text("지난 도전", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(state.ended, key = { it.id }) { EndedChallenge(it) }
            }
            item { Column(Modifier.navigationBarsPadding()) {} }
        }
    }
}

@Composable
private fun ActiveChallenge(card: ChallengeCard, viewModel: ChallengeViewModel) {
    var confirmAbandon by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable(card.id) { mutableStateOf(card.name) }
    Move60Card(Modifier.testTag("challenge_active")) {
        if (editing) {
            OutlinedTextField(draftName, { draftName = it.take(ChallengeService.MAX_NAME) },
                label = { Text("도전 이름") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("challenge_name_field"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Move60Button(onClick = { viewModel.rename(card.id, draftName); editing = false },
                    modifier = Modifier.testTag("challenge_name_save")) { Text("저장") }
                Move60Button(onClick = { draftName = card.name; editing = false }, primary = false) { Text("취소") }
            }
        } else {
            Text(card.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text("${card.startDate} ~ ${card.endDate} · ${card.daysLeft}일 남음",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { card.rate }, modifier = Modifier.fillMaxWidth().testTag("challenge_progress"),
            color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outline)
        Text("${card.creditedDays} / ${card.targetDays}일 달성 (누적)", style = MaterialTheme.typography.titleMedium)
        Text("하루 10분 이상 운동하면 1일로 인정돼요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!editing) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Move60Button(onClick = { editing = true }, primary = false,
                modifier = Modifier.testTag("challenge_rename")) { Text("이름 수정") }
            Move60Button(onClick = { confirmAbandon = true }, primary = false,
                modifier = Modifier.testTag("challenge_abandon")) { Text("도전 포기") }
        }
    }
    if (confirmAbandon) AlertDialog(
        onDismissRequest = { confirmAbandon = false },
        modifier = Modifier.testTag("challenge_abandon_dialog"),
        title = { Text("도전을 포기할까요?") },
        text = { Text("포기한 도전은 지난 도전에 남고, 운동 기록은 유지돼요. 포기는 되돌릴 수 없어요.") },
        confirmButton = {
            TextButton(onClick = { confirmAbandon = false; viewModel.abandon(card.id) },
                modifier = Modifier.testTag("challenge_abandon_confirm")) { Text("도전 포기") }
        },
        dismissButton = { TextButton(onClick = { confirmAbandon = false }) { Text("취소") } }
    )
}

@Composable
private fun CreateChallenge(viewModel: ChallengeViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf("") }
    val targetN = target.toIntOrNull()
    val daysN = days.toIntOrNull()
    // Inclusive period: start = today, end = today + (days - 1).
    val valid = name.isNotBlank() && targetN != null && daysN != null && targetN >= 1 && daysN >= 1 && targetN <= daysN
    Move60Card(Modifier.testTag("challenge_create")) {
        Text("새 도전 시작", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("시작일은 오늘로 고정돼요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(name, { name = it.take(ChallengeService.MAX_NAME) }, label = { Text("도전 이름") },
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("challenge_new_name"))
        OutlinedTextField(days, { days = it.filter(Char::isDigit).take(3) }, label = { Text("기간 (일, 오늘 포함)") },
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("challenge_new_days"))
        OutlinedTextField(target, { target = it.filter(Char::isDigit).take(3) }, label = { Text("목표 일수 (누적)") },
            singleLine = true, isError = targetN != null && daysN != null && targetN > daysN,
            supportingText = { if (targetN != null && daysN != null && targetN > daysN) Text("목표 일수는 기간보다 클 수 없어요.") },
            modifier = Modifier.fillMaxWidth().testTag("challenge_new_target"))
        Move60Button(
            onClick = { viewModel.create(name, targetN!!, LocalDate.now().plusDays(daysN!! - 1L)) },
            enabled = valid, modifier = Modifier.fillMaxWidth().testTag("challenge_start")
        ) { Text("도전 시작") }
    }
}

@Composable
private fun EndedChallenge(card: ChallengeCard) {
    val label = when (card.status) {
        ChallengeStatus.COMPLETE -> "달성"
        ChallengeStatus.EXPIRED -> "기간 종료"
        ChallengeStatus.ABANDONED -> "포기"
        ChallengeStatus.ACTIVE -> "진행 중"
    }
    Move60Card(Modifier.testTag("challenge_ended_${card.status.name}")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Text("${card.startDate} ~ ${card.endDate}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${card.creditedDays} / ${card.targetDays}일 · 달성률 ${(card.rate * 100).toInt()}%")
        card.targetReachedDate?.let { Text("목표 달성일 $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
