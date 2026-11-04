package com.move60.watch

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView

/**
 * Generated-equivalent semantic theme from design/tokens.json.
 * Both APK modules intentionally use identical tokens; CI verifies this contract.
 */
object Move60Theme {
    object Colors {
        val background = Color.parseColor("#000000")
        val surface = Color.parseColor("#121212")
        val surfaceElevated = Color.parseColor("#1E1E1E")
        val outline = Color.parseColor("#303030")
        val primary = Color.parseColor("#00E676")
        val onPrimary = Color.parseColor("#000000")
        val textHigh = Color.parseColor("#FFFFFF")
        val textMedium = Color.parseColor("#B0B0B0")
        val textMuted = Color.parseColor("#797979")
        val heart = Color.parseColor("#FF6B81")
    }
    object Type {
        const val display = 32f
        const val title = 24f
        const val heading = 20f
        const val body = 16f
        const val caption = 12f
        const val metric = 28f
        const val wearHeadline = 26f
        const val wearTitle = 16f
        const val wearBody = 12f
        const val wearCaption = 10f
    }
    object Space {
        const val one = 8
        const val two = 16
        const val three = 24
        const val four = 32
    }
    object Radius {
        const val card = 20
        const val control = 16
        const val chip = 16
    }
    fun dp(context: Context, value: Int) =
        (context.resources.displayMetrics.density * value + 0.5f).toInt()
    fun shape(context: Context, color: Int = Colors.surface,
        radius: Int = Radius.card, outline: Int? = Colors.outline) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radius).toFloat()
            if (outline != null) setStroke(dp(context, 1), outline)
        }
    fun headline(view: TextView) {
        view.setTextColor(Colors.textHigh)
        view.setTypeface(view.typeface, Typeface.BOLD)
        view.textSize = Type.heading
    }
    fun caption(view: TextView) {
        view.setTextColor(Colors.textMedium)
        view.textSize = Type.caption
    }
    fun progressTint() = ColorStateList.valueOf(Colors.primary)
    fun surface(view: View) {
        view.background = shape(view.context)
    }
}
