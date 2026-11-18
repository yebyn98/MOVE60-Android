package com.move60.workout.architecture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.move60.workout.domain.CardioMetric
import com.move60.workout.ui.Move60Button
import java.math.BigDecimal

/**
 * docs/v71 step 5 / Figma 27-B: correct the value of one real intensity interval. Pick the interval, change each value with the
 * stepper (1 per step) or by typing it, then save. Save stays off until a value really differs. Cancel and dismiss write nothing.
 * A failed save keeps the sheet and the typed values; [onFailure] shows the toast. Only the value changes, never the time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardioCorrectionSheet(
    record: CardioStageRecord,
    onDismiss: () -> Unit,
    onSave: (List<CardioValueEdit>, (CardioCorrectionResult) -> Unit) -> Unit,
    onFailure: () -> Unit
) {
    val choices = record.rows.filter { it.recorded && it.inputIndex != null }
    var selected by rememberSaveable { mutableStateOf(choices.singleOrNull()?.inputIndex ?: -1) }
    val row = choices.firstOrNull { it.inputIndex == selected }
    val originals = row?.values.orEmpty()
    val metrics = CardioMetric.entries.filter { it in originals }
    val typed = remember(selected) { mutableStateMapOf<CardioMetric, String>().also { m -> originals.forEach { (k, v) -> m[k] = v } } }
    var saving by remember { mutableStateOf(false) }

    fun num(t: String) = t.trim().replace(',', '.').toDoubleOrNull()
    fun same(a: String, b: String) = num(a).let { it != null && it == num(b) }
    val changed = metrics.filter { m -> !same(typed[m].orEmpty(), originals.getValue(m)) }
    val valid = metrics.all { CorrectionRules.cardio(typed[it].orEmpty()) != null }
    val canSave = row != null && changed.isNotEmpty() && valid && !saving

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, modifier = Modifier.testTag("cardio_correction_sheet")) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("강도 구간 값 정정", style = MaterialTheme.typography.titleLarge)
            Text("정정할 구간", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { c ->
                    val shape = RoundedCornerShape(12.dp)
                    val on = c.inputIndex == selected
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cardio_pick_${c.inputIndex}")
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                            .border(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
                            .clickable(role = Role.RadioButton, onClickLabel = "정정할 구간으로 선택") { if (!saving) selected = c.inputIndex!! }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${RecordFormat.cardioRange(c.startSeconds, c.endSeconds)} · ${RecordFormat.compact(c.seconds.toLong())}")
                        Text(RecordFormat.cardioValues(c.values.orEmpty()), fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (row != null) {
                Text("정정할 값", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                metrics.forEach { m ->
                    ValueStepper(m.label, typed[m].orEmpty(), { typed[m] = it }, "cardio_stepper_${m.key}", enabled = !saving)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Move60Button(onClick = onDismiss, primary = false, enabled = !saving,
                    modifier = Modifier.weight(1f).testTag("cardio_cancel")) { Text("취소") }
                Move60Button(
                    onClick = {
                        val index = row?.inputIndex ?: return@Move60Button
                        saving = true
                        onSave(changed.map { CardioValueEdit(index, it, typed[it].orEmpty().trim()) }) { result ->
                            saving = false
                            if (result is CardioCorrectionResult.Saved) onDismiss() else onFailure()
                        }
                    },
                    enabled = canSave, modifier = Modifier.weight(1f).testTag("cardio_save")
                ) { Text("정정 저장") }
            }
        }
    }
}

/** − / value / +. The value in the middle is tapped to type a number. 1 per step, never below 0. */
@Composable
private fun ValueStepper(label: String, value: String, onChange: (String) -> Unit, tag: String, enabled: Boolean) {
    var editing by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val current = value.trim().replace(',', '.').toBigDecimalOrNull()
    fun step(delta: Long) {
        val base = current ?: return
        val next = base.add(BigDecimal.valueOf(delta)).max(BigDecimal.ZERO)
        onChange(next.stripTrailingZeros().toPlainString())
    }
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { step(-1) }, enabled = enabled && current != null && current > BigDecimal.ZERO,
            modifier = Modifier.size(48.dp).testTag("${tag}_minus")) { Icon(Icons.Outlined.Remove, contentDescription = "$label 1 줄이기") }
        Box(
            Modifier.weight(1f).heightIn(min = 48.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .clickable(enabled = enabled && !editing, role = Role.Button, onClickLabel = "$label 직접 입력") { editing = true },
            contentAlignment = Alignment.Center
        ) {
            if (editing) {
                LaunchedEffect(Unit) { focus.requestFocus() }
                OutlinedTextField(
                    value = value, onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() || it == '.' }) onChange(v) },
                    singleLine = true, isError = CorrectionRules.cardio(value) == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { editing = false }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { if (!it.isFocused) editing = false }.testTag("${tag}_input")
                )
            } else Text("$label $value", modifier = Modifier.testTag("${tag}_value"))
        }
        IconButton(onClick = { step(1) }, enabled = enabled && current != null,
            modifier = Modifier.size(48.dp).testTag("${tag}_plus")) { Icon(Icons.Outlined.Add, contentDescription = "$label 1 늘리기") }
    }
}
