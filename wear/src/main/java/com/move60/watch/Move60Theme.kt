package com.move60.watch

import android.content.Context
import android.graphics.drawable.GradientDrawable

/**
 * Identical semantic palette and 8dp scale to Android's Move60Theme.
 * Kept in the Wear module to avoid heavyweight UI dependencies on a watch.
 */
object Move60Theme {
    object Color {
        val background: Int = 0xFF000000.toInt()
        val surface: Int = 0xFF121212.toInt()
        val elevated: Int = 0xFF1C1C1C.toInt()
        val divider: Int = 0xFF303030.toInt()
        val accent: Int = 0xFF00E676.toInt()
        val accentDim: Int = 0xFF103722.toInt()
        val textHigh: Int = 0xFFFFFFFF.toInt()
        val textMedium: Int = 0xFFB0B0B0.toInt()
        val error: Int = 0xFFFF6B6B.toInt()
        val onAccent: Int = 0xFF002A15.toInt()
    }
    object Type {
        const val display = 30f
        const val title = 24f
        const val section = 20f
        const val body = 16f
        const val detail = 14f
        const val caption = 12f
    }
    object Space {
        const val x1 = 8
        const val x2 = 16
        const val x3 = 24
        const val x4 = 32
        const val cardRadius = 20
        const val controlRadius = 16
        const val touchMin = 48
    }
    fun dp(context: Context, value: Int) = (context.resources.displayMetrics.density * value + 0.5f).toInt()
    fun surface(context: Context): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.surface)
            cornerRadius = dp(context,Space.cardRadius).toFloat()
            setStroke(dp(context,1),Color.divider)
        }
}
