package com.move60.workout

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Simple offline daily active-minute bar chart. */
class MonthlyActivityChart(context: Context, private val minutes: List<Long>) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (150 * resources.displayMetrics.density).toInt())
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val gap = 3f * resources.displayMetrics.density
        val unit = (w - gap * (minutes.size - 1)) / minutes.size
        val max = maxOf(60L, minutes.maxOrNull() ?: 0L).toFloat()
        paint.color = Move60Theme.Color.divider
        canvas.drawRect(0f, h - 8f, w, h - 6f, paint)
        for ((index, value) in minutes.withIndex()) {
            val x = index * (unit + gap)
            val bar = ((h - 16f) * value.toFloat() / max).coerceAtLeast(if (value > 0L) 2f else 0f)
            paint.color = if (value > 0L) Move60Theme.Color.accent else Move60Theme.Color.elevated
            canvas.drawRoundRect(x, h - 9f - bar, x + unit, h - 9f, 2f, 2f, paint)
        }
    }
}
