package com.move60.watch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** One-glance non-interactive progress ring for the safe center of a round screen. */
class WorkoutProgressRing(context: Context) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Move60Theme.Colors.outline
        style = Paint.Style.STROKE
        strokeWidth = Move60Theme.dp(context, 4).toFloat()
        strokeCap = Paint.Cap.ROUND
    }
    private val valuePaint = Paint(track).apply {
        color = Move60Theme.Colors.primary
    }
    private val bounds = RectF()
    var progress: Int = 0
        set(value) {
            field = value.coerceIn(0, 1000)
            invalidate()
        }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = track.strokeWidth / 2f + Move60Theme.dp(context, 2)
        bounds.set(inset, inset, width - inset, height - inset)
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        canvas.drawArc(bounds, -90f, 360f, false, track)
        if (progress > 0) canvas.drawArc(bounds, -90f, progress / 1000f * 360f, false, valuePaint)
    }
}