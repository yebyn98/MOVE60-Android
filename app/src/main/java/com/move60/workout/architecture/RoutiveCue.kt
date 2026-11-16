package com.move60.workout.architecture

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** End-of-rest cue: one short vibration, no sound. The system vibration settings and the device decide whether it is felt. */
object RoutiveCue {
    const val REST_END_VIBRATION_MS = 400L

    fun vibrateOnce(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31)
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createOneShot(REST_END_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) { /* no vibrator or no permission: the on-screen "휴식 끝" remains */ }
    }
}
