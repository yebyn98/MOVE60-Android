package com.move60.workout.architecture

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val legacy = com.move60.workout.Move60Theme.Colors
    val colors = darkColorScheme(
        primary = Color(legacy.primary),
        onPrimary = Color(legacy.onPrimary),
        background = Color(legacy.background),
        surface = Color(legacy.surface),
        onBackground = Color(legacy.textHigh),
        onSurface = Color(legacy.textHigh),
        secondary = Color(legacy.textMedium),
        outline = Color(legacy.outline)
    )
    MaterialTheme(colorScheme = colors) {
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
