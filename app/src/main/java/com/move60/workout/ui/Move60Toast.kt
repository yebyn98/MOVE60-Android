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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Failure/feedback toast at ONE fixed place: top-center, 64dp from the top (Figma toast component). A short message only; it never
 * carries buttons and is not a dialog. Put it in a Box that fills the screen, above the content. Auto-dismisses after [durationMs].
 * Screen-level for now (record detail); an app-wide host is a separate change.
 */
@Composable
fun BoxToast(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier, durationMs: Long = 3_000L) {
    if (message == null) return
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message) { delay(durationMs); dismiss() }
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.fillMaxWidth().padding(top = 64.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            message, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("move60_toast").semantics { liveRegion = LiveRegionMode.Polite }
                .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
