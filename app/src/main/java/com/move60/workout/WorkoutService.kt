package com.move60.workout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibrationEffect
import android.view.View
import android.widget.RemoteViews
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Kotlin foreground timer service; keeps countdown and notification synchronized when UI is closed. */
class WorkoutService : Service() {
    companion object {
        const val ACTION_SYNC = "com.move60.workout.ACTION_SYNC"
        const val EXTRA_SNAPSHOT = "snapshot"
        const val CHANNEL_PROGRESS = "move60_native_timer_v12"
        private const val CHANNEL_PHASE = "move60_native_phase_v12"
        private const val ONGOING_ID = 601
        private const val PHASE_ID = 602
    }
    private val stageNames = listOf("준비운동", "유산소", "근력운동", "마무리 스트레칭")
    private val dayNames = listOf("월요일", "화요일", "수요일", "목요일", "금요일")
    private val handler = Handler(Looper.getMainLooper())
    private var snapshot = JSONObject()
    private var lastPhase = ""
    private val boundary = Runnable { updateAndSchedule(true) }
    private val prefs by lazy { getSharedPreferences("timer", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_PROGRESS, "운동 진행 · 잠금화면 타이머", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "MOVE60: 안정형 진행 알림과 잠금화면 카운트다운"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        })
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_PHASE, "운동 구간 전환", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (intent?.action == ACTION_SYNC) {
                if (snapshot.length() == 0) snapshot = JSONObject(prefs.getString("snapshot", "{}") ?: "{}")
                accountActiveTime(System.currentTimeMillis())
                snapshot = JSONObject(intent.getStringExtra(EXTRA_SNAPSHOT) ?: "{}")
                prefs.edit().putString("snapshot", snapshot.toString()).apply()
            } else {
                snapshot = JSONObject(prefs.getString("snapshot", "{}") ?: "{}")
            }
            if (snapshot.optBoolean("running", false) && prefs.getLong("history_checkpoint_ms", 0L) == 0L) {
                prefs.edit().putLong("history_checkpoint_ms", System.currentTimeMillis()).apply()
            }
            if (!snapshot.optBoolean("running", false) || snapshot.optBoolean("finished", false)) {
                stopWork()
                return START_NOT_STICKY
            }
            val now = System.currentTimeMillis()
            // The foreground-service contract always starts with a platform notification.
            // Custom RemoteViews are rendered asynchronously by SystemUI, so a local try/catch
            // cannot protect OEM devices from a later inflation failure.
            val first = buildFallbackNotification(now)
            prefs.edit().putString("card_status", "안정형 기본 알림 사용 중").apply()
            if (Build.VERSION.SDK_INT >= 34) startForeground(ONGOING_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ONGOING_ID, first)
            updateAndSchedule(false)
            return START_STICKY
        } catch (e: Exception) {
            logFailure("Service failed: " + e.javaClass.simpleName + ": " + e.message)
            try { stopWork() } catch (_: Exception) { stopSelf() }
            return START_NOT_STICKY
        }
    }

    private fun accountActiveTime(now: Long) {
        val old = snapshot
        val last = prefs.getLong("history_checkpoint_ms", 0L)
        if (last <= 0L || !old.optBoolean("running", false) || old.optBoolean("finished", false)) return
        val stage = old.optInt("stage", 0).coerceIn(0, 3)
        val durations = old.optJSONArray("stageSeconds")
        val until = now
        if (until <= last) return
        val zone = java.time.ZoneId.of("Asia/Seoul")
        val history = WorkoutHistory(this)
        var cursor = last
        try {
            while (cursor < until) {
                val date = java.time.Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
                val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val sliceEnd = min(until, nextMidnight)
                val seconds = (sliceEnd - cursor) / 1000L
                if (seconds > 0L) {
                    history.addActiveSeconds(date, seconds)
                    prefs.edit().putLong("history_checkpoint_ms", cursor + seconds * 1000L).commit()
                }
                if (seconds == 0L) break
                cursor += seconds * 1000L
            }
            // Checkpoints are persisted after each successfully written daily slice.
        } catch (e: Exception) {
            logFailure("Workout history save failed: " + e.message)
        }
    }

    private fun duration(stage: Int): Int {
        val durations = snapshot.optJSONArray("stageSeconds")
        return durations?.optInt(stage, 300) ?: 300
    }

    private fun reconcile(now: Long) {
        // Time reaching a plan target never changes the stage. Only an explicit
        // phone/watch "next" command updates it in SessionStore.
        prefs.edit().putString("snapshot", snapshot.toString()).apply()
    }

    private data class Cue(
        val stageName: String, val title: String, val detail: String,
        val round: String, val effort: String, val ends: Long,
        val progress: Int, val dayName: String
    )

    private fun cue(now: Long): Cue {
        val stage = snapshot.optInt("stage", 0).coerceIn(0, 3)
        val day = snapshot.optInt("day", 1).coerceIn(1, 5)
        val stageElapsed = snapshot.optInt("stageElapsedSeconds", 0).coerceAtLeast(0)
        val progress = snapshot.optInt("activeSeconds", 0).coerceAtLeast(0)
        val start = now - stageElapsed * 1000L
        val end = start + duration(stage) * 1000L
        var title = stageNames[stage]
        var detail = when (stage) {
            0 -> "관절을 천천히 풀고 호흡을 준비하세요"
            1 -> "선택한 유산소 기구의 리듬을 유지하세요"
            2 -> "운동 자세와 세트·횟수를 확인하세요"
            else -> "호흡을 정리하며 천천히 스트레칭하세요"
        }
        var round = "${stage + 1}/4 단계"
        var effort = "운동 진행 중"
        var countdownEnd = end
        if (stage == 1) {
            val phases = snapshot.optJSONArray("phases") ?: JSONArray()
            var from = start
            for (i in 0 until phases.length()) {
                val phase = phases.optJSONObject(i) ?: continue
                val until = from + phase.optInt("seconds", 60) * 1000L
                if (now < until || i == phases.length() - 1) {
                    title = phase.optString("name", "유산소")
                    detail = phase.optString("description", "유산소 운동 진행 중")
                    round = "${i + 1}/${phases.length()} 구간"
                    effort = "RPE ${phase.optString("rpe", "—")}"
                    countdownEnd = until
                    break
                }
                from = until
            }
        }
        return Cue(stageNames[stage], title, detail, round, effort, countdownEnd, progress, dayNames[day - 1])
    }

    private fun builder(channel: String): Notification.Builder {
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_fitness)
            .setColor(Color.rgb(180, 245, 110))
            .setContentIntent(pending)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .apply { if (Build.VERSION.SDK_INT >= 31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) }
    }

    private fun remoteView(layout: Int, c: Cue, now: Long): RemoteViews {
        val result = RemoteViews(packageName, layout)
        result.setTextViewText(R.id.n_brand, "MOVE60")
        result.setTextViewText(R.id.n_day, c.dayName)
        result.setTextViewText(R.id.n_stage_chip, c.stageName)
        result.setTextViewText(R.id.n_title, c.title)
        result.setTextViewText(R.id.n_detail, c.detail)
        result.setTextViewText(R.id.n_badge, c.round)
        result.setTextViewText(R.id.n_accent, c.effort)
        result.setTextViewText(R.id.n_total, "총 운동 ${c.progress / 60}분 · 목표 60분")
        result.setTextViewText(R.id.n_remaining_label, "남은 시간")
        result.setProgressBar(R.id.n_progress, max(3600, c.progress), c.progress, false)
        val secondsLeft = max(0L, ceil((c.ends - now) / 1000.0).toLong())
        result.setChronometer(R.id.n_timer, SystemClock.elapsedRealtime() + secondsLeft * 1000L, null, true)
        if (Build.VERSION.SDK_INT >= 24) result.setChronometerCountDown(R.id.n_timer, true)
        return result
    }

    private fun buildFallbackNotification(now: Long): Notification {
        val c = cue(now)
        return builder(CHANNEL_PROGRESS).setContentTitle("MOVE60 · " + c.title)
            .setContentText(c.effort + " · " + c.round + " · 총 운동 " + (c.progress / 60) + "분")
            .setWhen(c.ends).setShowWhen(true)
            .setUsesChronometer(true).setChronometerCountDown(true)
            .setProgress(max(3600, c.progress), c.progress, false)
            .setOngoing(true).setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun buildNotification(now: Long): Notification {
        val c = cue(now)
        // Android may choose a public version on the lock screen.
        // Make it the same widget rather than falling back to plain text there.
        val publicVersion = builder(CHANNEL_PROGRESS)
            .setContentTitle("MOVE60 · ${c.title}")
            .setContentText("${c.effort} · ${c.round}")
            .setCustomContentView(remoteView(R.layout.notification_workout_compact, c, now))
            .setCustomBigContentView(remoteView(R.layout.notification_workout_expanded, c, now))
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build()
        return builder(CHANNEL_PROGRESS)
            .setContentTitle("MOVE60 · ${c.title}")
            .setContentText("${c.effort} · ${c.round}")
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(remoteView(R.layout.notification_workout_compact, c, now))
            .setCustomBigContentView(remoteView(R.layout.notification_workout_expanded, c, now))
            .setPublicVersion(publicVersion)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setShowWhen(true).setWhen(c.ends)
            .setUsesChronometer(true).setChronometerCountDown(true)
            .build()
    }

    private fun updateAndSchedule(transition: Boolean) {
        accountActiveTime(System.currentTimeMillis())
        handler.removeCallbacks(boundary)
        val now = System.currentTimeMillis()
        reconcile(now)
        if (!snapshot.optBoolean("running", false) || snapshot.optBoolean("finished", false)) {
            if (snapshot.optBoolean("finished", false)) showComplete()
            stopWork()
            return
        }
        val c = cue(now)
        WorkoutWidget.refresh(this)
        try {
            val data = PutDataMapRequest.create("/move60/workout")
            data.dataMap.putBoolean("running", true)
            data.dataMap.putString("stage", c.title)
            data.dataMap.putLong("deadline", c.ends)
            data.dataMap.putLong("updated", now)
            Wearable.getDataClient(this).putDataItem(data.asPutDataRequest().setUrgent())
        } catch (e: Exception) { logFailure("Watch sync failed: " + e.message) }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val enhancedCardEnabled = prefs.getBoolean("enhanced_card_enabled", false)
        try {
            if (enhancedCardEnabled) {
                nm.notify(ONGOING_ID, buildNotification(now))
                prefs.edit().putString("card_status", "실험형 카드 알림 게시 요청 완료").apply()
            } else {
                nm.notify(ONGOING_ID, buildFallbackNotification(now))
                prefs.edit().putString("card_status", "안정형 기본 알림 사용 중").apply()
            }
        } catch (e: Exception) {
            logFailure("Notification update error: " + e.javaClass.simpleName + ": " + e.message)
            // A failed enhanced notification is disabled for future process starts.
            prefs.edit().putBoolean("enhanced_card_enabled", false)
                .putString("card_status", "카드 오류로 안정형 기본 알림 전환").apply()
            try { nm.notify(ONGOING_ID, buildFallbackNotification(now)) }
            catch (ignored: Exception) { logFailure("Notification fallback error: " + ignored.message) }
        }
        val phaseSignature = "${c.stageName}:${c.title}:${c.round}"
        if (transition && phaseSignature != lastPhase) {
            vibratePhaseTransition()
            nm.notify(PHASE_ID, builder(CHANNEL_PHASE)
                .setContentTitle("다음 운동 · ${c.title}")
                .setContentText("${c.effort} · ${c.round}")
                .setStyle(Notification.BigTextStyle().bigText(c.detail))
                .setAutoCancel(true).build())
        }
        lastPhase = phaseSignature
        val nextTime = max(now + 1000L, c.ends)
        handler.postDelayed(boundary, (nextTime - now + 40).coerceIn(1000L, 60_000L))
    }

    private fun vibratePhaseTransition() {
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 160, 100, 160), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(350L)
            }
        } catch (e: Exception) {
            logFailure("Phase vibration failed: " + e.message)
        }
    }

    private fun showComplete() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(PHASE_ID,
            builder(CHANNEL_PHASE)
                .setContentTitle("MOVE60 · 60분 운동 완료")
                .setContentText("오늘 운동을 마쳤어요. 체크리스트를 확인해 보세요.")
                .setAutoCancel(true).build()
        )
    }
    private fun logFailure(message: String) {
        try { getSharedPreferences("move60_service_diagnostics", MODE_PRIVATE).edit()
            .putString("latest_error", message.take(800)).apply() } catch (_: Exception) {}
    }

    private fun stopWork() {
        try {
            val data = PutDataMapRequest.create("/move60/workout")
            data.dataMap.putBoolean("running", false)
            data.dataMap.putString("stage", "")
            data.dataMap.putLong("deadline", 0L)
            data.dataMap.putLong("updated", System.currentTimeMillis())
            Wearable.getDataClient(this).putDataItem(data.asPutDataRequest().setUrgent())
        } catch (e: Exception) { logFailure("Watch stop sync failed: " + e.message) }
        WorkoutWidget.refresh(this)
        prefs.edit().putLong("history_checkpoint_ms", 0L).apply()
        handler.removeCallbacks(boundary)
        if (Build.VERSION.SDK_INT >= 33) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
        stopSelf()
    }
    override fun onDestroy() {
        accountActiveTime(System.currentTimeMillis())
        prefs.edit().putLong("history_checkpoint_ms", 0L).apply()
        handler.removeCallbacks(boundary)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
