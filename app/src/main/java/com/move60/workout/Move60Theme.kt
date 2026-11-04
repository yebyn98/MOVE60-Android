package com.move60.workout

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.TextView

/**
 * MOVE60 v34 shared design contract (mirrored in the Wear module).
 * Single semantic palette, 8dp spatial grid, 16–24dp cards and type ramp.
 * No screen owns a private hard-coded brand color.
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
        // Compact circular-screen typography still has fixed semantic roles.
        const val watchMetric = 18f
        const val watchLabel = 13f
        const val watchCaption = 11f
        fun scale(legacy: Int): Float = when {
            legacy >= 24 -> title
            legacy >= 19 -> section
            legacy >= 15 -> body
            legacy >= 12 -> detail
            else -> caption
        }
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
    fun surface(context: Context, selected: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(if (selected) Color.elevated else Color.surface)
            cornerRadius = dp(context, Space.cardRadius).toFloat()
            setStroke(dp(context,1), if (selected) Color.accent else Color.divider)
        }
    fun shape(context: Context, fill: Int, radius: Int = Space.controlRadius, border: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(context, radius).toFloat()
            if (border != null) setStroke(dp(context,1),border)
        }
    fun applyTypography(view: TextView, size: Int, bold: Boolean) {
        view.textSize = Type.scale(size)
        view.setTypeface(view.typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
        view.setLineSpacing(dp(view.context, 2).toFloat(), 1.08f)
    }
}
