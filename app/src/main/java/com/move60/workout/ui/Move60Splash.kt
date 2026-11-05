package com.move60.workout.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.move60.workout.Move60Theme

/**
 * First-screen intro (~1.7s): the ring sweeps, the 60 minute ticks light up clockwise, the
 * dumbbell pops in, the wordmark fades up, then the whole layer fades out over the dashboard.
 * Tapping skips it. The caller decides whether to show it (it is skipped when the system
 * animation scale is 0) and [onFinished] may be called more than once, so it must be idempotent.
 */
@Composable
fun Move60Splash(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val ring = remember { Animatable(0f) }
    val ticks = remember { Animatable(0f) }
    val dumbbell = remember { Animatable(0f) }
    val layerAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { ring.animateTo(1f, tween(550, easing = FastOutSlowInEasing)) }
            launch { delay(250); ticks.animateTo(1f, tween(800, easing = LinearOutSlowInEasing)) }
            launch {
                delay(700)
                dumbbell.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            }
        }
        delay(350)
        layerAlpha.animateTo(0f, tween(250))
        onFinished()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = layerAlpha.value }
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { onFinished() } }
            .semantics { contentDescription = "MOVE60" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Move60LogoMark(
            modifier = Modifier.size(200.dp),
            ringProgress = ring.value,
            tickProgress = ticks.value,
            dumbbellProgress = dumbbell.value,
        )
        Text(
            text = "MOVE60",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .graphicsLayer { alpha = dumbbell.value.coerceIn(0f, 1f) }
                .padding(top = Move60Theme.Space.three.dp),
        )
    }
}
