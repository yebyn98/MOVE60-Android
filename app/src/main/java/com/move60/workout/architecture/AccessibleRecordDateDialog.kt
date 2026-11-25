package com.move60.workout.architecture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.YearMonth

/**
 * Date-only input. Never normalizes an impossible day into another month.
 * No product restriction on past/future dates is invented here.
 */
internal fun parseRoutiveDate(raw: String): LocalDate? {
    val input = raw.trim().trimEnd('.').replace('-', '.').replace('/', '.')
    val fields = input.split('.').map { it.trim() }
    if (fields.size != 3 || fields[0].length != 4 ||
        fields[1].length !in 1..2 || fields[2].length !in 1..2 ||
        fields.any { field -> field.isEmpty() || field.any { !it.isDigit() } }
    ) return null
    return runCatching {
        LocalDate.of(fields[0].toInt(), fields[1].toInt(), fields[2].toInt())
    }.getOrNull()
}

/**
 * ROUTIVE Record 11-G with 11-D/E/F input, error and confirmation states.
 * 3-column day options remain at least 52dp high; unlike the 7-column
 * calendar grid, each day is wide enough to be a reliable touch target.
 * The real selected date (not a Figma demo value) is sent to the caller.
 */
@Composable
internal fun AccessibleRecordDateDialog(
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onDateConfirmed: (LocalDate) -> Unit
) {
    var shownMonthText by rememberSaveable { mutableStateOf(YearMonth.from(selectedDate).toString()) }
    val shownMonth = remember(shownMonthText) { YearMonth.parse(shownMonthText) }
    var inputMode by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var candidateIso by rememberSaveable { mutableStateOf<String?>(null) }
    val enteredDate = remember(draft) { parseRoutiveDate(draft) }
    val invalid = draft.isNotBlank() && enteredDate == null

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                .widthIn(max = 382.dp).testTag("calendar_date_picker_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("날짜 선택", style = MaterialTheme.typography.titleLarge)

                when {
                    candidateIso != null -> {
                        val candidate = LocalDate.parse(candidateIso!!)
                        Text("선택한 날짜 확인", style = MaterialTheme.typography.titleMedium)
                        Text("${candidate.year}년 ${candidate.monthValue}월 ${candidate.dayOfMonth}일")
                        Text("기록이 없는 날짜도 조회할 수 있어요.",
                            style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { candidateIso = null },
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                                    .testTag("calendar_picker_back")
                            ) { Text("뒤로") }
                            Button(
                                onClick = { onDateConfirmed(candidate) },
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                                    .testTag("calendar_date_confirm")
                            ) { Text("날짜 보기") }
                        }
                    }

                    inputMode -> {
                        Text("날짜 직접 입력", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.fillMaxWidth().testTag("calendar_date_text_input"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            label = { Text("날짜 (YYYY.MM.DD)") },
                            placeholder = { Text("2026.11.09") },
                            isError = invalid,
                            supportingText = {
                                Text(if (invalid) "존재하지 않는 날짜이거나 형식이 잘못됐어요."
                                    else "연도.월.일 형식으로 입력해 주세요.")
                            }
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { inputMode = false },
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                                    .testTag("calendar_picker_back_to_list")
                            ) { Text("목록으로") }
                            Button(
                                onClick = { candidateIso = enteredDate?.toString() },
                                enabled = enteredDate != null,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                                    .testTag("calendar_date_prepare_confirmation")
                            ) { Text("날짜 확인") }
                        }
                    }

                    else -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${shownMonth.year}년 ${shownMonth.monthValue}월",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium)
                            IconButton(
                                onClick = { shownMonthText = shownMonth.minusMonths(1).toString() },
                                modifier = Modifier.size(48.dp).testTag("calendar_picker_previous_month")
                            ) {
                                Icon(Icons.Outlined.ChevronLeft, contentDescription = "이전 달")
                            }
                            IconButton(
                                onClick = { shownMonthText = shownMonth.plusMonths(1).toString() },
                                modifier = Modifier.size(48.dp).testTag("calendar_picker_next_month")
                            ) {
                                Icon(Icons.Outlined.ChevronRight, contentDescription = "다음 달")
                            }
                        }
                        Text("날짜를 선택하거나 직접 입력하세요.",
                            style = MaterialTheme.typography.bodySmall)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxWidth().height(270.dp)
                                .testTag("calendar_date_list"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 4.dp)
                        ) {
                            items((1..shownMonth.lengthOfMonth()).toList()) { day ->
                                val date = shownMonth.atDay(day)
                                val currentlySelected = date == selectedDate
                                val label = "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"
                                val dateModifier = Modifier.fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .testTag("calendar_date_$date")
                                    .semantics {
                                        contentDescription = label
                                        stateDescription =
                                            if (currentlySelected) "현재 선택한 날짜" else "날짜 선택 가능"
                                    }
                                if (currentlySelected) {
                                    Button(
                                        onClick = { candidateIso = date.toString() },
                                        modifier = dateModifier,
                                        contentPadding = PaddingValues(0.dp)
                                    ) { Text("${day}일") }
                                } else {
                                    OutlinedButton(
                                        onClick = { candidateIso = date.toString() },
                                        modifier = dateModifier,
                                        contentPadding = PaddingValues(0.dp)
                                    ) { Text("${day}일") }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                                    .testTag("calendar_picker_cancel")
                            ) { Text("취소") }
                            Button(
                                onClick = { inputMode = true },
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                                    .testTag("calendar_picker_direct_input")
                            ) { Text("직접 입력") }
                        }
                    }
                }
            }
        }
    }
}
