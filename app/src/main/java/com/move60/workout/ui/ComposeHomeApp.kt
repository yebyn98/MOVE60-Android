package com.move60.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.move60.workout.Move60Theme
import java.util.Locale

private val background = Color(Move60Theme.Colors.background)
private val surface = Color(Move60Theme.Colors.surface)
private val primary = Color(Move60Theme.Colors.primary)
private val textHigh = Color(Move60Theme.Colors.textHigh)
private val textMedium = Color(Move60Theme.Colors.textMedium)

private val move60Colors = darkColorScheme(
    primary = primary,
    onPrimary = Color(Move60Theme.Colors.onPrimary),
    background = background,
    onBackground = textHigh,
    surface = surface,
    onSurface = textHigh,
    onSurfaceVariant = textMedium
)

private val screens = listOf(
    Triple("today", "오늘", Icons.Default.Home),
    Triple("history", "운동 기록", Icons.Default.History),
    Triple("settings", "설정", Icons.Default.Settings)
)

/** Material3 Navigation Compose host for the migrated MOVE60 dashboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeHomeApp(
    state: ComposeHomeState,
    snackbar: SnackbarHostState,
    onStartWorkout: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onSaveWeight: (String) -> Unit
) {
    MaterialTheme(colorScheme = move60Colors) {
        val navController = rememberNavController()
        val entry by navController.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: "today"
        Scaffold(
            containerColor = background,
            topBar = {
                TopAppBar(
                    title = { Text("MOVE60", fontWeight = FontWeight.Bold, color = primary) },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = background
                    )
                )
            },
            bottomBar = {
                NavigationBar(containerColor = surface) {
                    screens.forEach { (path, label, icon) ->
                        NavigationBarItem(
                            modifier = Modifier.testTag("compose_nav_$path"),
                            selected = route == path,
                            onClick = {
                                navController.navigate(path) {
                                    launchSingleTop = true
                                    popUpTo("today") { saveState = true }
                                    restoreState = true
                                }
                            },
                            label = { Text(label) },
                            icon = { Icon(icon, contentDescription = label) }
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbar) }
        ) { insets ->
            NavHost(
                navController = navController,
                startDestination = "today",
                modifier = Modifier.padding(insets)
            ) {
                composable("today") {
                    TodayScreen(state, onStartWorkout)
                }
                composable("history") {
                    HistoryScreen(state)
                }
                composable("settings") {
                    ComposeSettingsScreen(state, onSoundChanged, onSaveWeight, onOpenAdvancedSettings)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Move60Theme.Radius.card.dp),
        colors = CardDefaults.cardColors(containerColor = surface)
    ) {
        Column(
            modifier = Modifier.padding(Move60Theme.Space.two.dp),
            verticalArrangement = Arrangement.spacedBy(Move60Theme.Space.one.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun TodayScreen(state: ComposeHomeState, onStartWorkout: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("compose_today"),
        contentPadding = PaddingValues(Move60Theme.Space.two.dp),
        verticalArrangement = Arrangement.spacedBy(Move60Theme.Space.two.dp)
    ) {
        item {
            Text("오늘의 운동", fontSize = Move60Theme.Type.title.sp, fontWeight = FontWeight.Bold)
            Text(state.todayDate, color = textMedium)
        }
        item {
            SummaryCard {
                Text("오늘 누적 칼로리 · 추정", color = textMedium)
                Text(
                    String.format(Locale.KOREA, "%.1f kcal", state.todayKcal),
                    modifier = Modifier.testTag("compose_today_kcal"),
                    fontSize = Move60Theme.Type.display.sp,
                    fontWeight = FontWeight.Bold,
                    color = primary
                )
                Text("오늘 실제 운동한 시간만 합산", color = textMedium, fontSize = 12.sp)
                LinearProgressIndicator(
                    progress = (state.todayActiveMinutes / 60f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    color = primary,
                    trackColor = Color(Move60Theme.Colors.outline)
                )
                Text("오늘 운동 ${state.todayActiveMinutes}분", color = textHigh)
            }
        }
        item {
            SummaryCard {
                Text("60분 운동 구성", fontWeight = FontWeight.Bold)
                Text("준비운동 → 유산소 → 근력운동 → 마무리 스트레칭", color = textMedium)
                Text("상체·하체 선택과 세트 기록은 운동 화면에서 진행해요.", color = textMedium, fontSize = 12.sp)
            }
        }
        item {
            Button(
                onClick = onStartWorkout,
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("compose_start_workout"),
                colors = ButtonDefaults.buttonColors(containerColor = primary)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.padding(4.dp))
                Text("운동 화면 열기", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HistoryScreen(state: ComposeHomeState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("compose_history"),
        contentPadding = PaddingValues(Move60Theme.Space.two.dp),
        verticalArrangement = Arrangement.spacedBy(Move60Theme.Space.two.dp)
    ) {
        item { Text("운동 기록", fontSize = Move60Theme.Type.title.sp, fontWeight = FontWeight.Bold) }
        if (state.history.isEmpty()) {
            item {
                SummaryCard { Text("아직 저장된 운동 기록이 없어요.", color = textMedium) }
            }
        }
        items(state.history, key = { it.date }) { entry ->
            SummaryCard {
                Text(entry.date, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("${entry.activeMinutes}분", color = textMedium)
                    Text(String.format(Locale.KOREA, "%.1f kcal", entry.kcal), color = primary)
                }
            }
        }
    }
}

@Composable
private fun ComposeSettingsScreen(
    state: ComposeHomeState,
    onSoundChanged: (Boolean) -> Unit,
    onSaveWeight: (String) -> Unit,
    onOpenAdvancedSettings: () -> Unit
) {
    var weightInput by rememberSaveable(state.weightKg) { mutableStateOf(state.weightKg.toString()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("compose_settings"),
        contentPadding = PaddingValues(Move60Theme.Space.two.dp),
        verticalArrangement = Arrangement.spacedBy(Move60Theme.Space.two.dp)
    ) {
        item {
            SummaryCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("앱 정보", fontWeight = FontWeight.Bold)
                    Text("v35", color = primary)
                }
                Text("MOVE60 · 매일 60분 운동", color = textMedium)
            }
        }
        item {
            SummaryCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("구간 전환 알림", fontWeight = FontWeight.Bold)
                    Switch(checked = state.soundEnabled, onCheckedChange = onSoundChanged)
                }
            }
        }
        item {
            SummaryCard {
                Text("칼로리 추정용 체중", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    label = { Text("체중 kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("compose_weight")
                )
                Button(onClick = { onSaveWeight(weightInput) }) { Text("저장") }
            }
        }
        item {
            SummaryCard {
                Text("도움말", fontWeight = FontWeight.Bold)
                Text("오늘 운동 시작 이후 활동 시간만 한국 날짜별로 누적합니다. 같은 날 두 번 운동해도 합산되며, 일시정지·종료 중에는 증가하지 않습니다.", color = textMedium)
                TextButton(onClick = onOpenAdvancedSettings) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Text("고급 운동 설정 · 업데이트")
                }
            }
        }
    }
}
