package com.move60.workout.architecture

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** End-of-rest cue: one short vibration, no sound. The system vibration settings and the device decide whether it is felt. */
object RoutiveCue {
    const val REST_END_VIBRATION_MS = 400L

    /**
     * The watch gives the end-of-rest vibration itself while it is actively connected: it sent a heart-rate sample in the
     * last 30 seconds, which only happens while its workout app runs next to this session. Then the phone stays silent so
     * the same event is not felt twice; without such a signal the phone vibrates.
     */
    fun watchHandlesCue(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val at = context.getSharedPreferences("preferences", Context.MODE_PRIVATE).getLong("watch_heart_rate_at", 0L)
        return now - at in 0L..WATCH_ACTIVE_WINDOW_MS
    }
    private const val WATCH_ACTIVE_WINDOW_MS = 30_000L

    fun vibrateOnce(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31)
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createOneShot(REST_END_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) { /* no vibrator or no permission: the on-screen "휴식 끝" remains */ }
    }
}
