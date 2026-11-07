package com.move60.workout

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Firm vibration for moments the user must notice mid-workout. Alarm-usage attributes keep the
 * pulses from being dropped, and the maximum amplitude is used when the motor supports it.
 */
object Haptics {
    /** Two firm pulses when the strength card moves on to the next exercise. */
    fun exerciseChanged(context: Context) = pulse(context, longArrayOf(0, 300, 120, 300))

    private fun pulse(context: Context, timings: LongArray) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (!vibrator.hasVibrator()) return
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            if (Build.VERSION.SDK_INT >= 26) {
                val effect = if (vibrator.hasAmplitudeControl())
                    VibrationEffect.createWaveform(timings, IntArray(timings.size) { if (it % 2 == 1) 255 else 0 }, -1)
                else VibrationEffect.createWaveform(timings, -1)
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, attributes)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1, attributes)
            }
        } catch (e: Exception) {
            android.util.Log.w("MOVE60.Haptics", "Vibration failed: " + e.message)
        }
    }
}
