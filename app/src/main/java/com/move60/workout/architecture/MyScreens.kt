package com.move60.workout.architecture

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.move60.workout.ui.Move60Button
import com.move60.workout.ui.Move60Card

/**
 * ROUTIVE v2 MY tab: Figma 10 (hub), 61 (내 운동 공간), 62/73 (보유 기구).
 *
 * Only real values are drawn. Not in this slice (no confirmed rule or no stored data): LV badge, weekly trend chart,
 * performance report, milestones, 운동 목적, 워치 연동 status. Nothing is seeded: a space or equipment exists only after
 * the user saves it.
 */
@Composable
private fun NavRow(title: String, supporting: String, tag: String, onClick: () -> Unit) {
    Move60Card(Modifier.clickable(role = Role.Button, onClick = onClick).testTag(tag)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
}

/** Figma 10 · 마이 / 나의 성장. */
@Composable
fun MyHomeScreen(onOpenSpaces: () -> Unit, onOpenSettings: () -> Unit, viewModel: MyHomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.load() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("마이", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp).testTag("my_settings_open")) {
                Icon(Icons.Outlined.Settings, contentDescription = "설정")
            }
        }
    }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("my_home"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MY JOURNEY", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                Text("나의 성장", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("나의 성장을 볼 수 있어요.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            }
            Move60Card(Modifier.testTag("my_growth")) {
                Text("누적 운동한 날", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(state.cumulativeDays?.toString() ?: "-", style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold, modifier = Modifier.testTag("my_cumulative_days"))
                    Spacer(Modifier.size(6.dp))
                    if (state.cumulativeDays != null) Text("일", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    if (state.cumulativeDays == null) "기록을 불러오지 못했어요. 저장된 기록은 그대로예요." else "이전 버전 기록과 저장된 운동 세션의 날짜를 한 번씩 세어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary
                )
            }
            Text("나의 운동 설정", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            NavRow("내 운동 공간", state.defaultSpaceName ?: "아직 등록하지 않았어요", "my_spaces_open", onOpenSpaces)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Figma 61 · 내 운동 공간. */
@Composable
fun MySpacesScreen(onBack: () -> Unit, onOpenSpace: (String) -> Unit, viewModel: MySpacesViewModel = hiltViewModel()) {
    val spaces by viewModel.spaces.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var addGym by rememberSaveable { mutableStateOf(false) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { ScreenTopBar("내 운동 공간", onBack, "my_back") }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("my_spaces"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("공간별 기구를 등록하면 추천·직접 구성에 적용돼요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            val list = spaces
            if (list == null) Text("운동 공간을 불러오고 있어요.", color = MaterialTheme.colorScheme.secondary)
            else {
                val default = list.firstOrNull { it.space.isDefault }
                if (default == null) {
                    Move60Card(Modifier.testTag("my_spaces_empty")) {
                        Text("등록한 운동 공간이 없어요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("집이나 헬스장을 직접 등록하면 그 공간의 기구를 고를 수 있어요.", color = MaterialTheme.colorScheme.secondary)
                    }
                } else {
                    Move60Card(Modifier.clickable(role = Role.Button) { onOpenSpace(default.space.spaceId) }.testTag("my_default_space")) {
                        Text("내 기본 운동 공간", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(default.space.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(equipmentSummary(default.equipmentCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Text("등록된 기구를 루틴 추천·직접 구성에 적용", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                list.filter { !it.space.isDefault }.forEach { s ->
                    NavRow(s.space.name, "${kindLabel(s.space.kind)} · ${equipmentSummary(s.equipmentCount)} · 보유 기구 관리", "my_space_${s.space.spaceId}") { onOpenSpace(s.space.spaceId) }
                    TextButton(onClick = { viewModel.setDefault(s.space.spaceId) }, modifier = Modifier.heightIn(min = 48.dp).testTag("my_set_default_${s.space.spaceId}")) {
                        Text("${s.space.name}을(를) 기본 운동 공간으로 설정")
                    }
                }
                if (list.none { it.space.kind == SpaceKind.HOME.name })
                    NavRow("집 · 개인 운동 공간 등록", "집에서 쓰는 기구를 직접 등록", "my_add_home") { viewModel.addHome() }
                NavRow("헬스장 추가 등록", "헬스장 이름을 직접 등록", "my_add_gym") { addGym = true }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("my_spaces_message")) }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (addGym) AddGymDialog(onDismiss = { addGym = false }, onConfirm = { viewModel.addGym(it); addGym = false })
    androidx.compose.runtime.LaunchedEffect(message) { if (message != null) { kotlinx.coroutines.delay(4_000); viewModel.messageShown() } }
}

private fun kindLabel(kind: String) = if (kind == SpaceKind.HOME.name) "집" else "헬스장"
private fun equipmentSummary(count: Int) = if (count == 0) "등록된 기구 없음" else "등록된 기구 ${count}종"

@Composable
private fun AddGymDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val valid = name.trim().length in 1..SpaceService.MAX_NAME
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("my_add_gym_dialog"),
        title = { Text("헬스장 직접 등록") },
        text = {
            OutlinedTextField(name, { if (it.length <= SpaceService.MAX_NAME) name = it }, singleLine = true,
                label = { Text("헬스장 이름") }, supportingText = { Text("${name.length}/${SpaceService.MAX_NAME}") },
                modifier = Modifier.fillMaxWidth().testTag("my_add_gym_name"))
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = valid, modifier = Modifier.heightIn(min = 48.dp).testTag("my_add_gym_confirm")) { Text("등록") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("취소") } }
    )
}

/** Figma 62 (헬스장) / 73 (집) · 보유 기구 체크리스트. */
@Composable
fun SpaceEquipmentScreen(onBack: () -> Unit, viewModel: SpaceEquipmentViewModel = hiltViewModel()) {
    val load by viewModel.load.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var modelDialog by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(saved) { if (saved) onBack() }
    val ready = load as? EquipmentLoad.Ready
    val isGym = ready?.kind == SpaceKind.GYM
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(if (ready == null) "보유 기구" else if (isGym) "헬스장 보유기구" else "집 보유 기구", onBack, "my_back") },
        bottomBar = {
            if (ready != null) Move60Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("my_equipment_save")) {
                Text(if (isGym) "선택한 기구 저장" else "집 기구 저장하기")
            }
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("my_equipment"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val l = load) {
                EquipmentLoad.Loading -> Text("기구를 불러오고 있어요.", color = MaterialTheme.colorScheme.secondary)
                EquipmentLoad.Missing -> Text("운동 공간을 찾을 수 없어요. 목록에서 다시 선택해 주세요.", color = MaterialTheme.colorScheme.secondary)
                is EquipmentLoad.Ready -> {
                    Text(if (isGym) "선택한 헬스장에서 실제 이용할 기구만 등록해요." else "집에서 실제 사용할 수 있는 기구만 등록해요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Text(l.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Move60Card(spacing = 6.dp) {
                        Equipment.choices(l.kind).forEach { eq ->
                            val on = eq.name in selected
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .clickable(role = Role.Checkbox) { viewModel.toggle(eq.name) }
                                    .semantics(mergeDescendants = true) { stateDescription = if (on) "선택됨" else "선택 안 됨" }
                                    .testTag("my_eq_${eq.name}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(if (on) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, contentDescription = null,
                                    tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.size(12.dp))
                                Text(eq.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    val cardioSelected = Equipment.choices(l.kind).filter { it.cardio && it.name in selected }
                    NavRow(
                        if (isGym) "헬스장 유산소 기구 모델 · 선택" else "집 유산소 기구 모델 · 선택",
                        if (cardioSelected.isEmpty()) "유산소 기구를 선택하면 브랜드·모델을 직접 입력할 수 있어요" else "브랜드·모델 직접 입력 · 나중에 등록 가능",
                        "my_model_open"
                    ) { if (cardioSelected.isNotEmpty()) modelDialog = true }
                    if (error) Text("저장하지 못했어요. 다시 시도해 주세요.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("my_equipment_error"))
                    if (modelDialog) ModelDialog(cardioSelected, models, onDismiss = { modelDialog = false }, onChange = viewModel::setModel)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModelDialog(
    cardio: List<Equipment>, models: Map<String, Pair<String, String>>, onDismiss: () -> Unit, onChange: (String, String, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss, modifier = Modifier.testTag("my_model_dialog"),
        title = { Text("유산소 기구 모델") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                cardio.forEach { eq ->
                    val (brand, model) = models[eq.name] ?: ("" to "")
                    Text(eq.label, fontWeight = FontWeight.Medium)
                    OutlinedTextField(brand, { if (it.length <= SpaceService.MAX_TEXT) onChange(eq.name, it, model) }, singleLine = true, label = { Text("브랜드 (선택)") },
                        modifier = Modifier.fillMaxWidth().testTag("my_brand_${eq.name}"))
                    OutlinedTextField(model, { if (it.length <= SpaceService.MAX_TEXT) onChange(eq.name, brand, it) }, singleLine = true, label = { Text("모델 (선택)") },
                        modifier = Modifier.fillMaxWidth().testTag("my_model_${eq.name}"))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag("my_model_done")) { Text("확인") } }
    )
}
