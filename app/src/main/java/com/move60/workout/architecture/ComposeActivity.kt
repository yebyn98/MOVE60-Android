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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3001)
        }
        setContent { Move60ComposeApp(
            showIntro = savedInstanceState == null && !animationsDisabled() && !intent.getBooleanExtra("move60_open_calendar", false),
            openCalendar = intent.getBooleanExtra("move60_open_calendar", false)
        ) }
    }

    /** Respect the system "remove animations" setting; the intro is skipped when it is 0. */
    private fun animationsDisabled(): Boolean =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

@Composable
fun Move60ComposeApp(showIntro: Boolean = false, openCalendar: Boolean = false) {
    val nav = rememberNavController()
    var introVisible by rememberSaveable { mutableStateOf(showIntro) }
    Move60ComposeTheme {
        Box {
            NavHost(navController = nav, startDestination = if (openCalendar) "calendar" else "dashboard") {
                composable("dashboard") {
                    WorkoutDashboard(
                        onCalendar = { nav.navigate("calendar") },
                        onSettings = { nav.navigate("settings") }
                    )
                }
                composable("calendar") {
                    WorkoutCalendar(onBack = { nav.popBackStack() })
                }
                composable("settings") {
                    WorkoutSettings(onBack = { nav.popBackStack() })
                }
            }
            if (introVisible) Move60Splash(onFinished = { introVisible = false })
        }
    }
}
