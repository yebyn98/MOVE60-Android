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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
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
 * First-screen intro (~2.3s): the ring sweeps, the 60 minute ticks light up clockwise, the
 * dumbbell pops in, the wordmark then appears slowly from left to right (a soft edge sweeps over the letters
 * while the word drifts up a few dp), it holds for a moment, and the whole layer fades out over the dashboard.
 * Tapping skips it. The caller decides whether to show it (it is skipped when the system
 * animation scale is 0) and [onFinished] may be called more than once, so it must be idempotent.
 */
@Composable
fun Move60Splash(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val ring = remember { Animatable(0f) }
    val ticks = remember { Animatable(0f) }
    val dumbbell = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val layerAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { ring.animateTo(1f, tween(550, easing = FastOutSlowInEasing)) }
            launch { delay(250); ticks.animateTo(1f, tween(800, easing = LinearOutSlowInEasing)) }
            launch {
                delay(700)
                dumbbell.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            }
            launch { delay(650); word.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
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
            .semantics { contentDescription = "ROUTIVE" },
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
            text = "ROUTIVE",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = Move60Theme.Space.three.dp)
                .softReveal(word.value),
        )
    }
}

/**
 * Shows the content from left to right: a soft edge ([progress] 0 = nothing visible, 1 = everything) moves over it while it
 * drifts up from 8dp below. At 1 the content is drawn untouched (no mask, no edge artifacts).
 */
internal fun Modifier.softReveal(progress: Float): Modifier = this
    .graphicsLayer {
        translationY = (1f - progress.coerceIn(0f, 1f)) * 8.dp.toPx()
        compositingStrategy = CompositingStrategy.Offscreen
    }
    .drawWithContent {
        drawContent()
        if (progress < 1f) {
            val fade = size.width * 0.4f
            val edge = progress.coerceIn(0f, 1f) * (size.width + fade)
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = edge - fade, endX = edge),
                blendMode = BlendMode.DstIn,
            )
        }
    }
