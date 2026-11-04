package com.move60.watch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/** Round-safe progress ring: no text and no touch target, purely status at a glance. */
class WorkoutRingGauge(context: Context) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Move60Theme.Color.divider
        style = Paint.Style.STROKE
        strokeWidth = Move60Theme.dp(context,4).toFloat()
        strokeCap = Paint.Cap.ROUND
    }
    private val active = Paint(track).apply { color = Move60Theme.Color.accent }
    var progressPermille: Int = 0
        set(value) { field = value.coerceIn(0,1000); invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = track.strokeWidth / 2 + Move60Theme.dp(context,2)
        val size = min(width,height).toFloat() - pad * 2
        if (size <= 0) return
        val x = (width - size)/2
        val y = (height - size)/2
        val oval = RectF(x,y,x+size,y+size)
        canvas.drawArc(oval, -90f, 360f, false, track)
        if (progressPermille > 0) canvas.drawArc(oval,-90f,progressPermille*0.36f,false,active)
    }
}
