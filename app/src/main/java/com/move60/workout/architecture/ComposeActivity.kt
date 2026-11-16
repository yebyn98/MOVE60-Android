package com.move60.workout.architecture

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.move60.workout.health.HealthConnectGateway
import javax.inject.Inject
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.move60.workout.ui.Move60ComposeTheme
import com.move60.workout.ui.Move60Splash
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint

/**
 * Compose-first launcher. The existing detailed Android View editor stays reachable
 * during migration and remains the foreground-service notification destination.
 */
@AndroidEntryPoint
class ComposeActivity : ComponentActivity() {
    /** Counts notification taps (including taps while the app is already open) so each one navigates to the existing session. */
    private var sessionOpenRequest by androidx.compose.runtime.mutableIntStateOf(0)

    @Inject lateinit var healthGateway: HealthConnectGateway

    override fun onResume() {
        super.onResume()
        // Refresh an existing explicit opt-in only; never ask for health permissions silently.
        if (!healthGateway.isOptedIn()) return
        val prefs = getSharedPreferences("move60_health_refresh_v1", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_success_at", 0L) < 15 * 60_000L) return
        lifecycleScope.launch {
            try {
                if (!healthGateway.permissionsGranted()) {
                    healthGateway.disable()
                    return@launch
                }
                val metrics = healthGateway.readWithConsent()
                healthGateway.sendToWatch(metrics)
                prefs.edit().putLong("last_success_at", System.currentTimeMillis()).apply()
            } catch (_: Exception) {
                // Health Connect may be unavailable; manual retry and details remain in settings.
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent.getBooleanExtra(RoutiveSessionService.EXTRA_OPEN_SESSION, false)) sessionOpenRequest = 1
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3001)
        }
        setContent { Move60ComposeApp(
            showIntro = savedInstanceState == null && !animationsDisabled() && !intent.getBooleanExtra("move60_open_calendar", false),
            openCalendar = intent.getBooleanExtra("move60_open_calendar", false),
            openResultId = intent.getStringExtra("move60_open_result"),
            openSessionRequest = sessionOpenRequest
        ) }
    }

    public override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(RoutiveSessionService.EXTRA_OPEN_SESSION, false)) sessionOpenRequest++
    }

    /** Respect the system "remove animations" setting; the intro is skipped when it is 0. */
    private fun animationsDisabled(): Boolean =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

@Composable
fun Move60ComposeApp(showIntro: Boolean = false, openCalendar: Boolean = false, openResultId: String? = null, openSessionRequest: Int = 0) {
    val nav = rememberNavController()
    var introVisible by rememberSaveable { mutableStateOf(showIntro) }
    var workoutRunning by rememberSaveable { mutableStateOf(false) }
    val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("move60_result_seen_v1", android.content.Context.MODE_PRIVATE)
    val openResult: (String) -> Unit = { id ->
        // Show the result once per finished/ended session (never again after it was viewed).
        if (prefs.getString("last_result_seen", null) != id) {
            prefs.edit().putString("last_result_seen", id).apply()
            nav.navigate("result/$id") { popUpTo("dashboard"); launchSingleTop = true }
        }
    }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    androidx.compose.runtime.LaunchedEffect(openResultId) {
        // Deep link used by tests and by re-opening the last result; never required for the normal flow.
        if (openResultId != null) nav.navigate("result/$openResultId") { launchSingleTop = true }
    }
    androidx.compose.runtime.LaunchedEffect(openSessionRequest) {
        // Tapping the ongoing workout notification returns to the existing session screen (every tap, also when the app is open).
        if (openSessionRequest > 0) nav.navigate("routive_session") { launchSingleTop = true }
    }
    Move60ComposeTheme {
        Box {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                NavHost(
                    navController = nav,
                    startDestination = if (openCalendar) "calendar" else "dashboard",
                    modifier = Modifier.weight(1f)
                ) {
                    composable("dashboard") { WorkoutDashboard(
                        onRunningChanged = { workoutRunning = it },
                        onWorkoutFinished = openResult,
                        onOpenWorkout = { nav.navigate("workout") { launchSingleTop = true } },
                        onOpenChallenge = { nav.navigate("challenge") { popUpTo("dashboard") { saveState = true }; launchSingleTop = true; restoreState = true } },
                        onComposeRoutine = { nav.navigate("routine_compose") },
                        onOpenRoutines = { nav.navigate("routine_list") }
                    ) }
                    composable("workout") {
                        WorkoutProgressScreen(onBack = { nav.popBackStack() }, onResult = openResult)
                    }
                    composable("result/{sessionId}") {
                        WorkoutResultScreen(onViewRecord = {
                            nav.navigate("calendar") { popUpTo("dashboard") { saveState = true }; launchSingleTop = true; restoreState = true }
                        })
                    }
                    composable("challenge") { ChallengeScreen(onBack = { nav.popBackStack() }) }
                    composable("routine_compose") { RoutineComposeScreen(onBack = { nav.popBackStack() }) }
                    composable("routine_list") { RoutineListScreen(
                        onBack = { nav.popBackStack() }, onCompose = { nav.navigate("routine_compose") },
                        onOpenSession = { nav.navigate("routive_session") { launchSingleTop = true } }
                    ) }
                    composable("routive_session") { RoutiveSessionScreen(onBack = { nav.popBackStack() }, onResult = openResult) }
                    composable("calendar") { WorkoutCalendar(onBack = { nav.popBackStack() }, onOpenDay = { nav.navigate("record_day/$it") }) }
                    composable("record_day/{date}") {
                        RecordDayScreen(onBack = { nav.popBackStack() }, onOpenSession = { nav.navigate("record_session/$it") })
                    }
                    composable("record_session/{sessionId}") { RecordSessionScreen(onBack = { nav.popBackStack() }) }
                    composable("settings") { WorkoutSettings(onBack = { nav.popBackStack() }) }
                }
                // Provisional rule (pending policy confirmation): hide the tab bar while a workout is running
                // so it never competes with the workout dock.
                if (route != "workout" && route != "routive_session" && !(workoutRunning && route == "dashboard")) {
                    Move60TabBar(route) { tab ->
                        nav.navigate(tab.route) {
                            popUpTo("dashboard") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
            if (introVisible) Move60Splash(onFinished = { introVisible = false })
        }
    }
}
