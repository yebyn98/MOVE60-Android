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
            if (measuredAt < getSharedPreferences("preferences",MODE_PRIVATE).getLong("watch_heart_rate_at",0L)) return
            getSharedPreferences("preferences", MODE_PRIVATE).edit()
                .putInt("watch_heart_rate", bpm)
                .putLong("watch_heart_rate_at", measuredAt.coerceAtMost(now)).apply()
            return
        }
        if (command.startsWith("kcal:")) {
            WatchKcal.parse(command, System.currentTimeMillis())?.let { WatchKcal.save(this, it) }
            return
        }
        android.os.Handler(mainLooper).post {
            val request = try { org.json.JSONObject(command) } catch (_: Exception) { return@post }
            val resync = request.optString("op") == "resync"
            val accepted = resync || WatchCommandProcessor.apply(this, request)
            val store = SessionStore(this, WorkoutData(this))
            // Rejected commands also receive the current authoritative state.
            WatchStatePublisher.publishSnapshot(this, store.snapshot())
            if (!resync && accepted) {
                val sync = Intent(this, WorkoutService::class.java).apply {
                    action = WorkoutService.ACTION_SYNC
                    putExtra(WorkoutService.EXTRA_SNAPSHOT, store.snapshot())
                }
                if (store.running) {
                    try { startForegroundService(sync) }
                    catch (e: Exception) { CrashReporter.log(this, "워치 운동 서비스: " + e.message) }
                } else {
                    getSharedPreferences("timer", MODE_PRIVATE).edit().putString("snapshot", store.snapshot()).apply()
                    stopService(Intent(this, WorkoutService::class.java))
                    WorkoutWidget.refresh(this)
                }
            }
            val ack = org.json.JSONObject().put("id",request.optString("id")).put("accepted",accepted)
                .put("version",store.controlVersion).toString()
            com.google.android.gms.wearable.Wearable.getMessageClient(this)
                .sendMessage(message.sourceNodeId, "/move60/ack", ack.toByteArray(Charsets.UTF_8))
        }
    }
}
