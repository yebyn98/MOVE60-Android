package com.move60.watch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Watch first-screen intro: the same sequence and timing as the phone's `Move60Splash`
 * (ring sweep, 60 ticks lighting clockwise, dumbbell pop, wordmark, fade out). Tapping skips it.
 * [onFinished] may be called more than once and must be idempotent.
 */
@Composable
fun WatchSplash(onFinished: () -> Unit, modifier: Modifier = Modifier) {
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
            .background(Color(Move60Theme.Colors.background))
            .pointerInput(Unit) { detectTapGestures { onFinished() } }
            .semantics { contentDescription = "MOVE60" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WatchLogoMark(
            modifier = Modifier.size(120.dp),
            ringProgress = ring.value,
            tickProgress = ticks.value,
            dumbbellProgress = dumbbell.value,
        )
        BasicText(
            text = "MOVE60",
            style = TextStyle(color = Color(Move60Theme.Colors.primary), fontSize = 17.sp),
            modifier = Modifier
                .graphicsLayer { alpha = dumbbell.value.coerceIn(0f, 1f) }
                .padding(top = 10.dp),
        )
    }
}

/** Same geometry as the phone's `Move60LogoMark` (200px design grid centered on the ring). */
@Composable
fun WatchLogoMark(
    modifier: Modifier = Modifier,
    ringProgress: Float = 1f,
    tickProgress: Float = 1f,
    dumbbellProgress: Float = 1f,
) {
    val lit = Color(Move60Theme.Colors.primary)
    val track = Color(Move60Theme.Colors.outline)
    val heart = Color(Move60Theme.Colors.heart)
    Canvas(modifier) { drawLogo(lit, track, heart, ringProgress, tickProgress, dumbbellProgress) }
}

private const val LOGO_TICKS = 60
private const val RING_R = 63.84f
private const val RING_W = 7.6f
private const val TICK_IN = 40.30f
private const val TICK_OUT = 50.92f
private const val TICK_W = 2.43f
private const val OUTER_EDGE = RING_R + RING_W / 2

// x, y, w, h, corner (design px relative to the ring center)
private val DUMBBELL = listOf(
    floatArrayOf(-12.4f, -2.9f, 24.8f, 6.8f, 2f),
    floatArrayOf(-21.4f, -14.2f, 9f, 29.3f, 3f),
    floatArrayOf(-30.4f, -8.6f, 9f, 18f, 3f),
    floatArrayOf(12.4f, -14.2f, 9f, 29.3f, 3f),
    floatArrayOf(21.4f, -8.6f, 9f, 18f, 3f),
)

private fun DrawScope.drawLogo(
    lit: Color, track: Color, heart: Color,
    ringProgress: Float, tickProgress: Float, dumbbellProgress: Float,
) {
    val u = size.minDimension / (2 * OUTER_EDGE)
    val c = Offset(size.width / 2, size.height / 2)
    val ringTopLeft = Offset(c.x - RING_R * u, c.y - RING_R * u)
    val ringSize = Size(2 * RING_R * u, 2 * RING_R * u)
    drawArc(track, -90f, 360f, false, ringTopLeft, ringSize, style = Stroke(RING_W * u))
    drawArc(lit, -90f, 360f * ringProgress.coerceIn(0f, 1f), false, ringTopLeft, ringSize,
        style = Stroke(RING_W * u, cap = StrokeCap.Butt))

    val litCount = LOGO_TICKS * tickProgress.coerceIn(0f, 1f)
    for (i in 0 until LOGO_TICKS) {
        val a = (i * 360f / LOGO_TICKS - 90f) * PI.toFloat() / 180f
        val start = Offset(c.x + cos(a) * TICK_IN * u, c.y + sin(a) * TICK_IN * u)
        val end = Offset(c.x + cos(a) * TICK_OUT * u, c.y + sin(a) * TICK_OUT * u)
        drawLine(if (i < litCount) lit else track, start, end, strokeWidth = TICK_W * u)
    }

    val p = dumbbellProgress.coerceIn(0f, 1.2f)
    if (p > 0f) {
        scale(scale = 0.6f + 0.4f * p, pivot = c) {
            val alpha = p.coerceAtMost(1f)
            DUMBBELL.forEach { r ->
                drawRoundRect(
                    color = heart.copy(alpha = alpha),
                    topLeft = Offset(c.x + r[0] * u, c.y + r[1] * u),
                    size = Size(r[2] * u, r[3] * u),
                    cornerRadius = CornerRadius(r[4] * u),
                )
            }
        }
    }
}
