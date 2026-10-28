package com.move60.workout

import android.content.Intent
import android.os.Build
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Commands from the paired Wear OS device; phone owns the authoritative timer. */
class WatchCommandService : WearableListenerService() {
    override fun onMessageReceived(message: MessageEvent) {
        if (message.path != "/move60/command") return
        val command = message.data.toString(Charsets.UTF_8)
        if (command.startsWith("heart:")) {
            val bpm = command.substringAfter(':').toIntOrNull()?.coerceIn(30, 240) ?: return
            getSharedPreferences("preferences", MODE_PRIVATE).edit()
                .putInt("watch_heart_rate", bpm)
                .putLong("watch_heart_rate_at", System.currentTimeMillis()).apply()
            return
        }
        if (command !in setOf("start", "pause", "next")) return
        val store = SessionStore(this, WorkoutData(this))
        store.reconcile()
        when (command) {
            "start" -> store.start()
            "pause" -> store.pause()
            "next" -> {
                store.nextStage()
            }
        }
        val sync = Intent(this, WorkoutService::class.java).apply {
            action = WorkoutService.ACTION_SYNC
            putExtra(WorkoutService.EXTRA_SNAPSHOT, store.snapshot())
        }
        if (store.running) {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(sync) else startService(sync)
        } else {
            // Preserve stopped state for the next app launch and update watch display.
            getSharedPreferences("timer", MODE_PRIVATE).edit()
                .putString("snapshot", store.snapshot()).apply()
            stopService(Intent(this, WorkoutService::class.java))
            WorkoutWidget.refresh(this)
            WatchStatePublisher.publish(this, false, "", 0L)
        }
    }
}
