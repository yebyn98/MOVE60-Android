package com.move60.workout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * MOVE60 logo mark: an outer ring, 60 minute ticks (the first [LOGO_LIT_TICKS] lit) and a dumbbell.
 * Geometry mirrors the approved design and scripts/generate-logo-assets.py (launcher icon), in a
 * 200px design grid centered on the ring. Colors come from the shared theme tokens.
 *
 * The three progress values let the first-screen intro build the mark up; defaults draw the
 * finished logo.
 */
@Composable
fun Move60LogoMark(
    modifier: Modifier = Modifier,
    ringProgress: Float = 1f,
    tickProgress: Float = 1f,
    dumbbellProgress: Float = 1f,
) {
    val lit = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outline
    val heart = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) { drawLogo(lit, track, heart, ringProgress, tickProgress, dumbbellProgress) }
}

const val LOGO_TICKS = 60
const val LOGO_LIT_TICKS = 45

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
    lit: androidx.compose.ui.graphics.Color,
    track: androidx.compose.ui.graphics.Color,
    heart: androidx.compose.ui.graphics.Color,
    ringProgress: Float,
    tickProgress: Float,
    dumbbellProgress: Float,
) {
    val u = size.minDimension / (2 * OUTER_EDGE)
    val c = Offset(size.width / 2, size.height / 2)

    // Outer ring: gray track, then the green sweep from 12 o'clock.
    val ringTopLeft = Offset(c.x - RING_R * u, c.y - RING_R * u)
    val ringSize = Size(2 * RING_R * u, 2 * RING_R * u)
    drawArc(track, -90f, 360f, false, ringTopLeft, ringSize, style = Stroke(RING_W * u))
    drawArc(lit, -90f, 360f * ringProgress.coerceIn(0f, 1f), false, ringTopLeft, ringSize,
        style = Stroke(RING_W * u, cap = StrokeCap.Butt))

    // 60 minute ticks; the lit ones fill in clockwise.
    val litCount = LOGO_LIT_TICKS * tickProgress.coerceIn(0f, 1f)
    for (i in 0 until LOGO_TICKS) {
        val a = (i * 360f / LOGO_TICKS - 90f) * PI.toFloat() / 180f
        val start = Offset(c.x + cos(a) * TICK_IN * u, c.y + sin(a) * TICK_IN * u)
        val end = Offset(c.x + cos(a) * TICK_OUT * u, c.y + sin(a) * TICK_OUT * u)
        drawLine(if (i < litCount) lit else track, start, end, strokeWidth = TICK_W * u)
    }

    // Dumbbell pops in from the center.
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
