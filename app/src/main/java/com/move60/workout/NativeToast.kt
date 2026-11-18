package com.move60.workout

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/**
 * The same toast as the Compose [com.move60.workout.ui.Move60ToastHost] for the legacy native screens: a short message at ONE fixed
 * place, top-center, 64dp from the top, 3 seconds, replacing the one showing. It replaces `android.widget.Toast` (system toast at the
 * bottom). Call on the main thread; a finished activity is ignored.
 */
object Move60NativeToast {
    private const val TAG = "move60_native_toast"
    private const val TOP_DP = 64
    private const val DURATION_MS = 3_000L

    fun show(activity: Activity, message: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        val host = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        host.findViewWithTag<View>(TAG)?.let { host.removeView(it) }
        val dp = { v: Int -> Move60Theme.dp(activity, v) }
        val toast = TextView(activity).apply {
            tag = TAG
            text = message
            setTextColor(Move60Theme.Colors.textHigh)
            textSize = Move60Theme.Type.body
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            background = GradientDrawable().apply {
                setColor(Move60Theme.Colors.surfaceElevated)
                cornerRadius = dp(Move60Theme.Radius.compact).toFloat()
                setStroke(dp(1), Move60Theme.Colors.outline)
            }
            setPadding(dp(Move60Theme.Space.two), dp(Move60Theme.Space.compact), dp(Move60Theme.Space.two), dp(Move60Theme.Space.compact))
            elevation = dp(8).toFloat()
        }
        host.addView(toast, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            topMargin = dp(TOP_DP)
            marginStart = dp(Move60Theme.Space.two); marginEnd = dp(Move60Theme.Space.two)
        })
        toast.bringToFront()
        toast.postDelayed({ (toast.parent as? ViewGroup)?.removeView(toast) }, DURATION_MS)
    }
}
