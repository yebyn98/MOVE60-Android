package com.move60.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The one app-wide toast (Figma toast component): a short feedback/failure message at ONE fixed place, top-center, 64dp from the
 * top, on every screen. No buttons, never a dialog. A new message replaces the one showing, and showing the same text again
 * restarts it (every tap shows it). Call `LocalMove60Toast.current.show(...)`; [Move60ToastHost] at the app root draws it.
 */
@Stable
class Move60ToastState {
    var message by mutableStateOf<String?>(null)
        private set
    /** Bumped by every [show], so an identical message is shown (and timed) again. */
    var token by mutableIntStateOf(0)
        private set

    fun show(text: String) { message = text; token++ }
    fun dismiss() { message = null }
}

/** Without a host (previews, tests that render one screen) a state exists but nothing draws it. */
val LocalMove60Toast = compositionLocalOf { Move60ToastState() }

/** Put once in a Box that fills the screen, above the content. Auto-dismisses after [durationMs]. */
@Composable
fun Move60ToastHost(state: Move60ToastState, modifier: Modifier = Modifier, durationMs: Long = 3_000L) {
    val message = state.message ?: return
    LaunchedEffect(state.token) { delay(durationMs); state.dismiss() }
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.fillMaxWidth().padding(top = 64.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            message, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("move60_toast").semantics { liveRegion = LiveRegionMode.Polite }
                .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
