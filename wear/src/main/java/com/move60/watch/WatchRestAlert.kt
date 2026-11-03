package com.move60.watch

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/** Deduplicate across UI/service and suppress delayed alerts after reconnect. */
object WatchRestAlert {
    fun deliver(context: Context, state: WatchWorkoutState, now: Long = System.currentTimeMillis()): Boolean {
        if (!state.running || state.restId <= 0 || state.restDeadline <= 0 || now < state.restDeadline) return false
        val prefs = context.getSharedPreferences("watch_rest_alerts_v26",Context.MODE_PRIVATE)
        if (prefs.getLong("notified",0L) == state.restId) return false
        prefs.edit().putLong("notified",state.restId).commit()
        if (now-state.restDeadline > 10_000L) return false
        (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(
            VibrationEffect.createWaveform(longArrayOf(0,150,100,150),-1))
        return true
    }
}
