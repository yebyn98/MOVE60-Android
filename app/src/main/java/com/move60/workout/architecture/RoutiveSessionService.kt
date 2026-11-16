package com.move60.workout.architecture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.move60.workout.R

/** What the ongoing notification says; pure so it can be tested without a device. */
internal data class RoutiveNotificationText(val title: String, val body: String, val actionLabel: String)

internal fun routiveNotificationText(s: RoutiveSession): RoutiveNotificationText {
    val p = s.progress
    val state = if (s.running) "진행 중" else if (s.autoPaused) "자동 일시정지" else "일시정지"
    return RoutiveNotificationText(
        title = "ROUTIVE · ${p.stageIndex + 1}/${p.plan.size} ${p.kind.listTitle()}",
        body = "$state · 이 단계 ${formatMmSs(p.elapsedSeconds[p.stageIndex])} · 총 ${formatMmSs(p.totalElapsedSeconds)}",
        actionLabel = if (s.running) "일시정지" else "재개"
    )
}

/**
 * Keeps a ROUTIVE_4 session tracked while the app is in the background: one ongoing notification (stage, elapsed time,
 * pause/resume) and a one-second reconcile of the stored session. It owns no time itself: all time lives in
 * [RoutiveSessionStore]. It stops by itself when the session is over or gone.
 */
class RoutiveSessionService : Service() {
    companion object {
        const val ACTION_TICK = "com.move60.workout.ROUTIVE_TICK"
        const val ACTION_TOGGLE = "com.move60.workout.ROUTIVE_TOGGLE"
        const val EXTRA_OPEN_SESSION = "move60_open_routive_session"
        const val NOTIFICATION_ID = 4260
        private const val CHANNEL = "move60_routive_session_v1"

        /** Starts the service only while a session is in progress. Never throws (a refused start must not crash the app). */
        fun ensure(context: Context) {
            try {
                if (RoutiveSessionStore(context).active()?.over != false) return
                val intent = Intent(context, RoutiveSessionService::class.java).setAction(ACTION_TICK)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            } catch (_: Exception) { /* background-start restrictions: the session is still stored and recovered on return */ }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { update() } }
    private lateinit var store: RoutiveSessionStore
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        store = RoutiveSessionStore(this)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "운동 진행 · ROUTIVE", NotificationManager.IMPORTANCE_LOW).apply {
            description = "진행 중인 운동의 단계와 시간을 보여줘요"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null); enableVibration(false)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE) {
            val s = store.reconcile()
            if (s != null && !s.over) { if (s.running) store.pause() else store.resume() }
        }
        update()
        return START_STICKY
    }

    private fun update() {
        handler.removeCallbacks(tick)
        val s = try { store.reconcile() } catch (_: Exception) { null }
        if (s == null || s.over) { stop(); return }
        val notification = build(s)
        if (!foreground) {
            try {
                if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                else startForeground(NOTIFICATION_ID, notification)
                foreground = true
            } catch (_: Exception) { stopSelf(); return }
        } else (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification)
        // While paused nothing changes, so a slower refresh is enough; running updates every second.
        handler.postDelayed(tick, if (s.running) 1_000L else 5_000L)
    }

    private fun build(s: RoutiveSession): Notification {
        val text = routiveNotificationText(s)
        val open = Intent(this, ComposeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_SESSION, true)
        val openPending = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val toggle = PendingIntent.getService(this, 2, Intent(this, RoutiveSessionService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_fitness)
            .setContentTitle(text.title).setContentText(text.body)
            .setContentIntent(openPending).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, text.actionLabel, toggle).build())
            .apply { if (Build.VERSION.SDK_INT >= 31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) }
            .build()
    }

    private fun stop() {
        handler.removeCallbacks(tick)
        if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false }
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        // If the system stops the service while the session still runs, nothing tracks it any more: mark that explicitly.
        try { store.releaseTracking() } catch (_: Exception) { }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
