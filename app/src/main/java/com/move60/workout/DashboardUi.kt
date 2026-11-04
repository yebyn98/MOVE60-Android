package com.move60.workout

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Single source for native phone cards, text, action surfaces and 8dp spacing. */
object DashboardUi {
    fun label(context: Context, text: String, requestedSize: Int, bold: Boolean,
        textColor: Int = Move60Theme.Colors.textHigh): TextView = TextView(context).apply {
        this.text = text
        setTextColor(textColor)
        textSize = when {
            requestedSize >= 28 -> Move60Theme.Type.display
            requestedSize >= 23 -> Move60Theme.Type.title
            requestedSize >= 18 -> Move60Theme.Type.heading
            requestedSize >= 14 -> Move60Theme.Type.body
            else -> Move60Theme.Type.caption
        }
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = true
        setLineSpacing(dp(context, 2).toFloat(), 1.04f)
    }
    fun column(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    fun row(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }
    fun card(context: Context) = column(context).apply {
        background = Move60Theme.shape(context)
        val p = dp(context, Move60Theme.Space.two)
        setPadding(p, p, p, p)
        clipToPadding = false
    }
    fun actionButton(context: Context, title: String, primary: Boolean,
        compact: Boolean, onClick: () -> Unit): TextView = TextView(context).apply {
        text = title
        textSize = if (primary || !compact) Move60Theme.Type.body
            else Move60Theme.Type.caption
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (primary) Move60Theme.Colors.onPrimary else Move60Theme.Colors.textHigh)
        background = Move60Theme.shape(context,
            if (primary) Move60Theme.Colors.primary else Move60Theme.Colors.surfaceElevated,
            Move60Theme.Radius.control,
            if (primary) Move60Theme.Colors.primary else Move60Theme.Colors.outline)
        gravity = Gravity.CENTER
        minHeight = dp(context, 48)
        val py = dp(context, Move60Theme.Space.one)
        val px = dp(context, if (compact) Move60Theme.Space.one else Move60Theme.Space.two)
        setPadding(px, py, px, py)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }
    fun rounded(context: Context, color: Int, radius: Int,
        stroke: Int? = null): GradientDrawable =
        Move60Theme.shape(context, color, when {
            radius < 8 -> radius
            radius < Move60Theme.Radius.control -> Move60Theme.Radius.control
            else -> Move60Theme.Radius.card
        }, stroke)
    fun gap(context: Context, parent: LinearLayout, requested: Int) {
        val aligned = ((requested + 4) / Move60Theme.Space.one * Move60Theme.Space.one)
            .coerceAtLeast(Move60Theme.Space.one)
        parent.addView(View(context), LinearLayout.LayoutParams(1, dp(context, aligned)))
    }
    private fun dp(context: Context, n: Int) = Move60Theme.dp(context, n)
}
