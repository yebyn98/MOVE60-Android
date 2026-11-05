package com.move60.workout.architecture

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.move60.workout.health.HealthConnectGateway
import javax.inject.Inject
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.move60.workout.ui.Move60ComposeTheme
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
        setContent { Move60ComposeApp() }
    }
}

@Composable
fun Move60ComposeApp() {
    val nav = rememberNavController()
    Move60ComposeTheme {
        NavHost(navController = nav, startDestination = "dashboard") {
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
    }
}
