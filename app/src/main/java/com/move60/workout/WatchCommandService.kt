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
            val fields = command.split(':')
            val bpm = fields.getOrNull(1)?.toIntOrNull() ?: return
            if (bpm != 0 && bpm !in 30..240) return
            val now = System.currentTimeMillis()
            // Preserve measurement time, so delayed/replayed messages cannot look live.
            val measuredAt = fields.getOrNull(2)?.toLongOrNull() ?: now
            if (measuredAt > now + 2_000L || now - measuredAt > 10_000L) return
            getSharedPreferences("preferences", MODE_PRIVATE).edit()
                .putInt("watch_heart_rate", bpm)
                .putLong("watch_heart_rate_at", measuredAt.coerceAtMost(now)).apply()
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
        WatchStatePublisher.publishSnapshot(this, store.snapshot())
        if (store.running) {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(sync) else startService(sync)
        } else {
            // Preserve stopped state for the next app launch and update watch display.
            getSharedPreferences("timer", MODE_PRIVATE).edit()
                .putString("snapshot", store.snapshot()).apply()
            stopService(Intent(this, WorkoutService::class.java))
            WorkoutWidget.refresh(this)
        }
    }
}
