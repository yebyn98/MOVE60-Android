package com.move60.watch

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.google.android.gms.wearable.*

/** Keeps the workout visible/returnable without depending on heart-rate permission. */
class WorkoutDisplayService : Service(), DataClient.OnDataChangedListener {
    companion object {
        private const val CHANNEL = "move60_watch_workout"
        private const val ID = 604
    }
    private var ongoing: OngoingActivity? = null
    private var state = WatchWorkoutState()
    private var lastLabel = ""
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopSelf() }

    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL, "운동 진행 화면", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            })
        Wearable.getDataClient(this).addListener(this)
        handler.postDelayed(timeout, 3 * 60 * 60_000L)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        state = WatchWorkoutState.load(this)
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, notification)
        if (!state.ongoing) {
            stopSelf()
            return START_NOT_STICKY
        }
        Wearable.getDataClient(this).getDataItems().addOnSuccessListener { items ->
            try { for (item in items) readState(item) } finally { items.release() }
        }
        return START_NOT_STICKY
    }
    private fun label() = state.stageLabel + if (state.running) " · 운동 중" else " · 일시정지"
    private fun status() = Status.Builder().addTemplate("#stage#")
        .addPart("stage", Status.TextPart(label())).build()
    private fun createNotification(): Notification {
        val open = PendingIntent.getActivity(this, 24,
            Intent(this, WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_workout)
            .setContentTitle("MOVE60 운동 진행")
            .setContentText("눌러서 현재 운동 화면으로 돌아가기")
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true).setOnlyAlertOnce(true)
        ongoing = OngoingActivity.Builder(this, ID, builder)
            .setStaticIcon(R.drawable.ic_workout)
            .setTouchIntent(open)
            .setStatus(status()).build().also { it.apply(this) }
        lastLabel = label()
        return builder.build()
    }
    private fun readState(item: DataItem) {
        if (item.uri.path != "/move60/workout") return
        state = WatchWorkoutState.fromData(DataMapItem.fromDataItem(item).dataMap)
        state.save(this)
        if (!state.ongoing) { stopSelf(); return }
        if (lastLabel != label()) {
            lastLabel = label()
            ongoing?.update(this, status())
        }
    }
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) if (event.type == DataEvent.TYPE_CHANGED) readState(event.dataItem)
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Wearable.getDataClient(this).removeListener(this)
        ongoing = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
