package com.move60.workout.architecture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.border
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocalFireDepartment
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import com.move60.workout.Move60Theme
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
fun MyHomeScreen(onOpenSpaces: () -> Unit, onOpenSettings: () -> Unit, onOpenReport: () -> Unit = {}, onOpenPurpose: () -> Unit = {}, viewModel: MyHomeViewModel = hiltViewModel()) {
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
            state.weekly?.let { TrendCard(it) }
            NavRow("운동 성과 리포트", "주간 변화와 실제 수행 기록 자세히 보기", "my_report_open", onOpenReport)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard("저장된 운동 세션", state.sessionCount?.let { "${it}회" } ?: "-", "my_stat_sessions", Modifier.weight(1f))
                StatCard("완료한 도전", state.completedChallenges?.let { "${it}개" } ?: "-", "my_stat_challenges", Modifier.weight(1f))
            }
            Text("나의 운동 설정", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            NavRow("운동 목적", state.purposeLabel ?: "아직 선택하지 않았어요", "my_purpose_open", onOpenPurpose)
            NavRow("내 운동 공간", state.defaultSpaceName ?: "아직 등록하지 않았어요", "my_spaces_open", onOpenSpaces)
            val milestones = state.milestones
            if (milestones.isNotEmpty()) {
                Text("나의 마일스톤", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Column(Modifier.testTag("my_milestones"), verticalArrangement = Arrangement.spacedBy(8.dp)) { milestones.forEach { MilestoneRow(it) } }
            }
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
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    // The message (e.g. "same name already exists") sits below the list; scroll it into view when it appears so the user always sees why
    // nothing happened, however long the list is or whether the keyboard is still closing.
    val spacesScroll = rememberScrollState()
    LaunchedEffect(message) {
        if (message != null) {
            withFrameNanos { }                       // let the message take its place in the layout first
            spacesScroll.animateScrollTo(spacesScroll.maxValue)
        }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { ScreenTopBar("내 운동 공간", onBack, "my_back") }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(spacesScroll).padding(horizontal = 16.dp).testTag("my_spaces"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("공간별로 실제 이용할 기구를 등록해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
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
                        Text("이 공간에서 이용할 보유 기구를 관리해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                if (default != null && default.space.kind == SpaceKind.GYM.name) RenameButton(default.space) { renaming = default.space.spaceId }
                list.filter { !it.space.isDefault }.forEach { s ->
                    NavRow(s.space.name, "${kindLabel(s.space.kind)} · ${equipmentSummary(s.equipmentCount)} · 보유 기구 관리", "my_space_${s.space.spaceId}") { onOpenSpace(s.space.spaceId) }
                    if (s.space.kind == SpaceKind.GYM.name) RenameButton(s.space) { renaming = s.space.spaceId }
                    TextButton(onClick = { viewModel.setDefault(s.space.spaceId) }, modifier = Modifier.heightIn(min = 48.dp).testTag("my_set_default_${s.space.spaceId}")) {
                        Text("‘${s.space.name}’ 기본 운동 공간으로 설정")
                    }
                }
                if (list.none { it.space.kind == SpaceKind.HOME.name })
                    NavRow("집 · 개인 운동 공간 등록", "집에서 쓰는 기구를 직접 등록", "my_add_home") { viewModel.addHome() }
                NavRow("헬스장 추가 등록", "헬스장 이름을 직접 등록", "my_add_gym") { addGym = true }
            }
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("my_spaces_message"))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (addGym) AddGymDialog(onDismiss = { addGym = false }, onConfirm = { viewModel.addGym(it); addGym = false })
    renaming?.let { id ->
        val current = spaces?.firstOrNull { it.space.spaceId == id }?.space?.name.orEmpty()
        GymNameDialog("헬스장 이름 수정", current, "저장", "my_rename", { renaming = null }) { viewModel.rename(id, it); renaming = null }
    }
    androidx.compose.runtime.LaunchedEffect(message) { if (message != null) { kotlinx.coroutines.delay(4_000); viewModel.messageShown() } }
}

@Composable
private fun RenameButton(space: WorkoutSpaceEntity, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp).testTag("my_rename_${space.spaceId}")) { Text("이름 수정") }
}

private fun kindLabel(kind: String) = if (kind == SpaceKind.HOME.name) "집" else "헬스장"
private fun equipmentSummary(count: Int) = if (count == 0) "등록된 기구 없음" else "등록된 기구 ${count}종"

@Composable
internal fun AddGymDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) =
    GymNameDialog("헬스장 직접 등록", "", "등록", "my_add_gym", onDismiss, onConfirm)

/** One text field for a gym name (1..[SpaceService.MAX_NAME] characters); the button stays disabled for a blank name. */
@Composable
private fun GymNameDialog(title: String, initial: String, confirmLabel: String, tag: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val valid = name.trim().length in 1..SpaceService.MAX_NAME
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("${tag}_dialog"),
        title = { Text(title) },
        text = {
            OutlinedTextField(name, { if (it.length <= SpaceService.MAX_NAME) name = it }, singleLine = true,
                label = { Text("헬스장 이름") }, supportingText = { Text("${name.length}/${SpaceService.MAX_NAME}") },
                modifier = Modifier.fillMaxWidth().testTag("${tag}_name"))
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = valid, modifier = Modifier.heightIn(min = 48.dp).testTag("${tag}_confirm")) { Text(confirmLabel) } },
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
                    if (isGym) TextButton(onClick = viewModel::selectAll, modifier = Modifier.heightIn(min = 48.dp).testTag("my_eq_select_all")) {
                        Text("전체 선택")
                    }
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

@Composable
private fun StatCard(label: String, value: String, tag: String, modifier: Modifier) {
    Move60Card(modifier.testTag(tag)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MilestoneRow(m: Milestone) {
    val icon = when (m.id) { "first" -> Icons.Outlined.EmojiEvents; "days20" -> Icons.Outlined.LocalFireDepartment; else -> Icons.Outlined.Flag }
    Move60Card(Modifier.testTag("my_milestone_${m.id}")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (m.achieved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(m.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    when (m.status) {
                        MilestoneStatus.ACHIEVED -> m.supporting
                        MilestoneStatus.NOT_ACHIEVED -> "아직 달성하지 않았어요."
                        MilestoneStatus.UNKNOWN -> "불러오지 못했어요."
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(if (m.achieved) "달성" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Bars for [labels]/[values]; every bar also shows its number so the chart never depends on color or height alone. */
@Composable
private fun BarChart(labels: List<String>, values: List<Long>, unit: String, tag: String) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(Modifier.fillMaxWidth().height(132.dp).testTag(tag), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        labels.forEachIndexed { i, label ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text("${values[i]}$unit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(maxOf(4f, 72f * values[i] / max).dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)))
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

/** Figma 10 · 성취 흐름: the last 4 weeks, days or minutes. */
@Composable
private fun TrendCard(weekly: List<WeekPoint>) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Move60Card(Modifier.testTag("my_trend")) {
        Text("성취 흐름", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text("최근 4주 · 주간 기록", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip("운동일수", tab == 0, "my_trend_days") { tab = 0 }
            TabChip("운동시간", tab == 1, "my_trend_minutes") { tab = 1 }
        }
        BarChart(
            weekly.indices.map { MyInsights.weekLabel(it, weekly.size) },
            weekly.map { if (tab == 0) it.days.toLong() else it.minutes }, if (tab == 0) "일" else "분", "my_trend_chart"
        )
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Tab, onClick = onClick).semantics { stateDescription = if (selected) "선택됨" else "선택 안 됨" }.testTag(tag),
        contentAlignment = Alignment.Center) {
        Box(Modifier.background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

/** Figma 68 · 운동 성과 리포트: real numbers only (the Figma values are examples). */
@Composable
fun MyReportScreen(onBack: () -> Unit, onOpenSession: (String) -> Unit, viewModel: MyReportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { ScreenTopBar("운동 성과 리포트", onBack, "my_back") }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("my_report"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val s = state) {
                RecordLoad.Loading -> Text("리포트를 불러오고 있어요.", color = MaterialTheme.colorScheme.secondary)
                RecordLoad.Missing, RecordLoad.Failed -> Move60Card(Modifier.testTag("my_report_failed")) {
                    Text("리포트를 불러오지 못했어요.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("저장된 기록은 그대로예요. 다시 확인해 주세요.", color = MaterialTheme.colorScheme.secondary)
                    Move60Button(onClick = viewModel::load, modifier = Modifier.fillMaxWidth().testTag("my_report_retry")) { Text("다시 불러오기") }
                }
                is RecordLoad.Ready -> ReportContent(s.value, viewModel::exerciseName, onOpenSession)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ReportContent(report: MyReport, name: (String) -> String, onOpenSession: (String) -> Unit) {
    Text("이번 주 운동 흐름", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("주간 운동한 날과 실제 운동 내역을 함께 확인해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    Move60Card(Modifier.testTag("my_report_weeks")) {
        Text("주간 운동한 날", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        BarChart(listOf("지난 주", "이번 주"), listOf(report.lastWeek.days.toLong(), report.thisWeek.days.toLong()), "일", "my_report_chart")
    }
    Text("최근 수행 세션", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    val latest = report.latest
    if (latest == null) {
        Move60Card(Modifier.testTag("my_report_no_session")) {
            Text("저장된 운동 세션이 아직 없어요.", color = MaterialTheme.colorScheme.secondary)
        }
        return
    }
    val title = "${latest.date.monthValue}월 ${latest.date.dayOfMonth}일 · ${latest.session.routineName ?: "운동 세션"}"
    Move60Card(Modifier.clickable(role = Role.Button) { onOpenSession(latest.session.sessionId) }.testTag("my_report_latest")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text("${RecordFormat.duration(latest.session.totalActiveSeconds)} · 운동 기록 상세 보기", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.testTag("my_report_latest_duration"))
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
    val cardio = latest.stages.firstOrNull { it.kind == SegmentKind.CARDIO_STEADY }?.actualSeconds ?: 0L
    val parts = buildList {
        if (latest.session.completedSets > 0) add("근력 ${latest.session.completedSets}세트")
        if (cardio > 0) add("유산소 ${RecordFormat.compact(cardio)}")
    }
    if (parts.isNotEmpty()) Text("세션 구성 · ${parts.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary, modifier = Modifier.testTag("my_report_composition"))
    Text("종목별 수행 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    if (latest.exercises.isEmpty()) Text("저장된 세트 기록이 없어요.", color = MaterialTheme.colorScheme.secondary)
    latest.exercises.forEach { ex ->
        Move60Card(Modifier.testTag("my_report_ex_${ex.exerciseId}"), spacing = 6.dp) {
            Text(name(ex.exerciseId), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(RecordFormat.setSummary(ex.sets), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

// ---- 최초 운동 설정 (Figma FINAL/01, FINAL/02) ----

private fun purposeIcon(p: WorkoutPurpose) = when (p) {
    WorkoutPurpose.WEIGHT_MANAGEMENT -> Icons.Outlined.TrackChanges
    WorkoutPurpose.STRENGTH -> Icons.Outlined.FitnessCenter
    WorkoutPurpose.FITNESS -> Icons.AutoMirrored.Outlined.ShowChart
    WorkoutPurpose.HABIT -> Icons.Outlined.CalendarMonth
}

/** Single choice (radio), nothing preselected. */
@Composable
private fun PurposeChoices(selected: WorkoutPurpose?, onSelect: (WorkoutPurpose) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WorkoutPurpose.entries.forEach { p ->
            val on = p == selected
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp)
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(p) })
                    .padding(12.dp).testTag("purpose_${p.name}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(purposeIcon(p), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(p.supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                RadioButton(selected = on, onClick = null)
            }
        }
    }
}

@Composable
private fun SetupHeader(eyebrow: String, title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ROUTIVE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(8.dp))
        Text(eyebrow, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(description, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
    }
}

/** Figma 01 · 최초 운동 목적 설정. The button stays disabled until a purpose is chosen. */
@Composable
fun FirstRunPurposeScreen(onContinue: () -> Unit, viewModel: FirstRunViewModel = hiltViewModel()) {
    val purpose by viewModel.purpose.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Move60Button(onClick = { if (viewModel.savePurpose()) onContinue() }, enabled = purpose != null,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).testTag("setup_purpose_continue")) { Text("운동 공간 선택") }
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp).testTag("setup_purpose"),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SetupHeader("YOUR FIRST MOVE", "오늘, 어떤 변화를\n만들고 싶나요?", "운동 목적 하나면 충분해요.\n나중에 언제든 변경할 수 있어요.")
            PurposeChoices(purpose, viewModel::choose)
        }
    }
}

/** Figma 02 · 첫 운동 공간 선택. A space is required: the setup is done only after one is saved. */
@Composable
fun FirstRunSpaceScreen(onDone: (String) -> Unit, viewModel: FirstRunViewModel = hiltViewModel()) {
    val error by viewModel.error.collectAsStateWithLifecycle()
    var addGym by rememberSaveable { mutableStateOf(false) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp).testTag("setup_space"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SetupHeader("02 / YOUR SPACE", "주로 어디에서\n운동하시나요?", "처음 운동할 공간을 반드시 선택해요.\n선택 전에는 운동을 시작할 수 없어요.")
            NavRow("집에서 운동하기", "집에 있는 기구를 선택해요", "setup_home") { viewModel.finishWithSpace(SpaceKind.HOME, onSaved = onDone) }
            NavRow("헬스장에서 운동하기", "이용하는 헬스장을 등록해요", "setup_gym") { addGym = true }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("setup_space_error")) }
            Text("확정한 공간이 기본 운동 공간이 돼요.\n기구와 모델은 나중에 등록할 수 있어요.", style = MaterialTheme.typography.bodySmall, color = Color(Move60Theme.Colors.textMuted))   // Figma 02 #8C9F90 (the border color was too dark to read)
        }
    }
    if (addGym) AddGymDialog(onDismiss = { addGym = false }, onConfirm = { name -> addGym = false; viewModel.finishWithSpace(SpaceKind.GYM, name, onDone) })
}

/** 마이 > 운동 목적: the same choices, changed later (Figma 10 "나의 운동 설정"). */
@Composable
fun MyPurposeScreen(onBack: () -> Unit, viewModel: FirstRunViewModel = hiltViewModel()) {
    val purpose by viewModel.purpose.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar("운동 목적", onBack, "my_back") },
        bottomBar = {
            Move60Button(onClick = { if (viewModel.savePurpose()) onBack() }, enabled = purpose != null,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("my_purpose_save")) { Text("저장") }
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("my_purpose"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("운동 목적 하나면 충분해요. 나중에 언제든 변경할 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            PurposeChoices(purpose, viewModel::choose)
            Spacer(Modifier.height(16.dp))
        }
    }
}
