package com.move60.workout

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.move60.workout.ui.ComposeHomeApp
import com.move60.workout.ui.ComposeHomeViewModel
import com.move60.workout.ui.HomeEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Compose entry point. Existing MainActivity remains the proven workout control surface
 * until its timer, set editor and Watch4 state commands have equivalent Compose coverage.
 */
@AndroidEntryPoint
class ComposeHomeActivity : ComponentActivity() {
    private val viewModel: ComposeHomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Move60Theme.Colors.background
        window.navigationBarColor = Move60Theme.Colors.background
        setContent { HomeContent() }
    }

    @Composable
    private fun HomeContent() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        val lifecycleOwner = LocalLifecycleOwner.current

        // When the screen is backgrounded both event collection and live refresh suspend.
        LaunchedEffect(viewModel, lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            HomeEvent.OpenWorkout ->
                                startActivity(Intent(this@ComposeHomeActivity, MainActivity::class.java))
                            HomeEvent.OpenAdvancedSettings ->
                                startActivity(Intent(this@ComposeHomeActivity, SettingsActivity::class.java))
                            is HomeEvent.Message -> snackbar.showSnackbar(event.value)
                        }
                    }
                }
                launch {
                    while (isActive) {
                        viewModel.refreshVisible()
                        delay(2_000)
                    }
                }
            }
        }
        ComposeHomeApp(
            state = state,
            snackbar = snackbar,
            onStartWorkout = viewModel::openWorkout,
            onOpenAdvancedSettings = viewModel::openAdvancedSettings,
            onSoundChanged = viewModel::updateSound,
            onSaveWeight = viewModel::updateWeight
        )
    }
}
