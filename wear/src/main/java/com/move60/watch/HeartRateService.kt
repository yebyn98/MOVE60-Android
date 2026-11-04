package com.move60.watch

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import com.google.android.gms.wearable.*

/** Owns live heart-rate sampling independently of WatchActivity's lifecycle. */
class HeartRateService : Service(), SensorEventListener, DataClient.OnDataChangedListener {
    companion object {
        const val ACTION_STOP = "com.move60.watch.STOP_HEART_RATE"
        private const val CHANNEL = "move60_watch_heart_rate"
        private const val NOTIFICATION = 603
        @Volatile var uiVisible = false
    }
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("live_heart_rate", MODE_PRIVATE) }
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private var wakeLock: PowerManager.WakeLock? = null
    private var registered = false
    private var running = false
    private var sessionId = 0L
    private var hiddenSince = 0L
    private var lastSentAt = 0L
    private val startedAt = SystemClock.elapsedRealtime()
    private val watchdog = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            if (uiVisible || running) hiddenSince = 0L
            else if (hiddenSince == 0L) hiddenSince = now
            // No unattended idle sensing; an ongoing workout continues with the screen off.
            if ((hiddenSince > 0L && now - hiddenSince > 90_000L) || now - startedAt > 3 * 60 * 60_000L) {
                stopSelf()
                return
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL, "운동 심박수 측정", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            else startForeground(NOTIFICATION, notification())
            if (!registered) startSensor()
            Wearable.getDataClient(this).addListener(this)
            Wearable.getDataClient(this).getDataItems().addOnSuccessListener { items ->
                try { for (item in items) readWorkout(item) } finally { items.release() }
            }
            handler.removeCallbacks(watchdog)
            handler.post(watchdog)
        } catch (e: Exception) {
            prefs.edit().putString("status", "센서 연결 실패").apply()
            stopSelf()
        }
        // A fresh visible launch is required after system termination to satisfy sensor permissions.
        return START_NOT_STICKY
    }

    private fun startSensor() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (sensor == null) {
            prefs.edit().putString("status", "심박 센서를 찾을 수 없습니다").apply()
            stopSelf()
            return
        }
        registered = sensorManager.registerListener(this, sensor, 1_000_000, 0)
        if (!registered) {
            prefs.edit().putString("status", "심박 센서 등록 실패").apply()
            stopSelf()
            return
        }
        prefs.edit().putString("status", "측정 중").apply()
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MOVE60:HeartRate").apply {
                setReferenceCounted(false)
                acquire(3 * 60 * 60_000L)
            }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_HEART_RATE || event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) return
        val value = event.values.firstOrNull() ?: return
        if (!value.isFinite()) return
        val bpm = value.toInt()
        if (bpm !in 30..240) return
        val ageMs = ((SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000L).coerceAtLeast(0L)
        if (ageMs > 10_000L) return
        val measuredAt = System.currentTimeMillis() - ageMs
        val edit = prefs.edit().putInt("bpm", bpm).putLong("measured_at", measuredAt).putString("status", "측정 중")
        // The displayed average uses actual, in-session sensor samples (not estimated BPM).
        // Sample at most once per second to avoid over-weighting device-specific event bursts.
        if (running && sessionId > 0L && prefs.getLong("session",0L) == sessionId &&
            measuredAt - prefs.getLong("last_avg_at",0L) >= 1_000L) {
            edit.putLong("sample_total",prefs.getLong("sample_total",0L)+bpm)
                .putInt("sample_count",prefs.getInt("sample_count",0)+1)
                .putLong("last_avg_at",measuredAt)
        }
        edit.apply()
        if (measuredAt - lastSentAt >= 500L) {
            lastSentAt = measuredAt
            sendHeartRate(bpm, measuredAt)
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            prefs.edit().putInt("bpm", 0).putLong("measured_at", 0L).putString("status", "워치 착용 상태 확인").apply()
            sendHeartRate(0, System.currentTimeMillis())
        }
    }
    private fun sendHeartRate(bpm: Int, measuredAt: Long) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) Wearable.getMessageClient(this).sendMessage(
                node.id, "/move60/command", "heart:$bpm:$measuredAt".toByteArray(Charsets.UTF_8))
        }
    }
    private fun readWorkout(item: DataItem) {
        if (item.uri.path != "/move60/workout") return
        val map = DataMapItem.fromDataItem(item).dataMap
        val newSession = map.getLong("session")
        if (newSession > 0L && newSession != sessionId) {
            sessionId = newSession
            prefs.edit().putLong("session",newSession).putLong("sample_total",0L)
                .putInt("sample_count",0).putLong("last_avg_at",0L).apply()
        }
        running = map.getBoolean("running")
    }
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) if (event.type == DataEvent.TYPE_CHANGED) readWorkout(event.dataItem)
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, WatchActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, HeartRateService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("MOVE60 심박수 측정 중")
            .setContentText("화면이 꺼져도 운동 심박수를 전송합니다")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "측정 중지", stop).build()).build()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Wearable.getDataClient(this).removeListener(this)
        if (registered) sensorManager.unregisterListener(this)
        registered = false
        wakeLock?.let { if (it.isHeld) it.release() }
        prefs.edit().putInt("bpm", 0).putLong("measured_at", 0L).apply()
        sendHeartRate(0, System.currentTimeMillis())
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
