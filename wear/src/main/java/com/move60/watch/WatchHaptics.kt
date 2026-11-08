package com.move60.watch

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Firm watch vibration. Alarm-usage attributes keep the pulses from being dropped, and maximum
 * amplitude is used when the motor supports it (the earlier 150 ms pulses were hard to feel).
 */
object WatchHaptics {
    enum class Transition { NONE, STAGE, EXERCISE }

    /** Three long pulses: a stage or cardio interval has changed. */
    private val STAGE = longArrayOf(0, 350, 150, 350, 150, 500)
    /** Two long pulses: the strength card moved on to the next exercise, or the rest timer ended. */
    private val EXERCISE = longArrayOf(0, 400, 150, 400)

    /** Which kind of change (if any) the new phone state brings while a session keeps running. */
    fun transition(old: WatchWorkoutState, new: WatchWorkoutState): Transition {
        if (!old.ongoing || !new.ongoing || !new.running || old.session != new.session) return Transition.NONE
        if (new.stageIndex != old.stageIndex) return Transition.STAGE
        if (isTimedStage(new.stageIndex) && new.intervalIndex != old.intervalIndex) return Transition.STAGE
        if (new.stageIndex == 2 && old.exerciseId.isNotEmpty() && new.exerciseId.isNotEmpty() &&
            new.exerciseId != old.exerciseId) return Transition.EXERCISE
        return Transition.NONE
    }

    fun deliver(context: Context, transition: Transition) {
        when (transition) {
            Transition.STAGE -> pulse(context, STAGE)
            Transition.EXERCISE -> pulse(context, EXERCISE)
            Transition.NONE -> Unit
        }
    }

    fun restEnded(context: Context) = pulse(context, EXERCISE)

    private fun pulse(context: Context, timings: LongArray) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (!vibrator.hasVibrator()) return
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            val effect = if (Build.VERSION.SDK_INT >= 26 && vibrator.hasAmplitudeControl())
                VibrationEffect.createWaveform(timings, IntArray(timings.size) { if (it % 2 == 1) 255 else 0 }, -1)
            else VibrationEffect.createWaveform(timings, -1)
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, attributes)
        } catch (e: Exception) {
            android.util.Log.w("MOVE60.WatchHaptics", "Vibration failed: " + e.message)
        }
    }
}
