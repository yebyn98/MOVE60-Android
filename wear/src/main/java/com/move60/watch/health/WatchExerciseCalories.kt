package com.move60.watch.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate

/**
 * Opt-in workout calories measured by the watch (Health Services exercise session).
 *
 * - Off by default; only runs after the user turns it on AND grants ACTIVITY_RECOGNITION.
 * - The value is Health Services' total calories: active energy PLUS basal metabolism for the session length.
 *   It is reported to the phone as such and never mixed into the MET-based daily estimate.
 * - Nothing is invented: when the device does not support the data type or the session cannot start,
 *   [status] says so and no value is sent.
 */
class WatchExerciseCalories(
    private val context: Context,
    private val sessionId: Long,
    private val onTotal: (totalKcal: Double, measuredAt: Long) -> Unit
) {
    companion object {
        const val PREFS = "move60_watch_kcal_v1"
        private const val KEY_OPT_IN = "optin"
        private const val KEY_STATUS = "status"
        fun isOptedIn(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OPT_IN, false)
        fun setOptIn(context: Context, on: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_OPT_IN, on)
                .putString(KEY_STATUS, if (on) "켜짐 · 운동 시작 시 측정" else "꺼짐").apply()
        }
        fun status(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STATUS, "꺼짐") ?: "꺼짐"
        fun hasPermission(context: Context) = context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private val client = HealthServices.getClient(context).exerciseClient
    private val executor = ContextCompat.getMainExecutor(context)
    private var started = false
    private var starting = false
    private var paused = false
    private var lastSentAt = 0L
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun setStatus(text: String) { prefs.edit().putString(KEY_STATUS, text).apply() }

    private val callback = object : ExerciseUpdateCallback {
        override fun onRegistered() {}
        override fun onRegistrationFailed(throwable: Throwable) { setStatus("측정 연결 실패") }
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val total = update.latestMetrics.getData(DataType.CALORIES_TOTAL)?.total ?: return
            if (!total.isFinite() || total < 0.0) return
            val now = System.currentTimeMillis()
            if (now - lastSentAt < 3_000L) return
            lastSentAt = now
            setStatus("측정 중")
            onTotal(total, now)
        }
        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {}
    }

    /** Starts the session the first time, resumes it afterwards. Safe to call repeatedly. */
    fun startOrResume() {
        if (starting) return
        if (started) {
            if (paused) {
                paused = false
                client.resumeExerciseAsync()
            }
            return
        }
        starting = true
        val capabilities = client.getCapabilitiesAsync()
        capabilities.addListener({
            try {
                val caps = capabilities.get()
                val type = listOf(ExerciseType.WORKOUT, ExerciseType.STRENGTH_TRAINING)
                    .firstOrNull { it in caps.supportedExerciseTypes &&
                        DataType.CALORIES_TOTAL in caps.getExerciseTypeCapabilities(it).supportedDataTypes }
                if (type == null) { setStatus("이 워치는 칼로리 측정을 지원하지 않아요"); starting = false; return@addListener }
                client.setUpdateCallback(executor, callback)
                val config = ExerciseConfig.builder(type)
                    .setDataTypes(setOf(DataType.CALORIES_TOTAL))
                    .setIsAutoPauseAndResumeEnabled(false)
                    .setIsGpsEnabled(false)
                    .build()
                val start = client.startExerciseAsync(config)
                start.addListener({
                    try { start.get(); started = true; setStatus("측정 중") }
                    catch (e: Exception) { setStatus("측정 시작 실패 · 다른 운동 앱을 종료해 주세요") }
                    finally { starting = false }
                }, executor)
            } catch (e: Exception) {
                setStatus("칼로리 측정 확인 실패")
                starting = false
            }
        }, executor)
    }

    fun pause() {
        if (!started || paused) return
        paused = true
        client.pauseExerciseAsync()
    }

    /** Ends the session and releases the callback. Idempotent. */
    fun end() {
        if (started) {
            try { client.endExerciseAsync() } catch (_: Exception) {}
        }
        try { client.clearUpdateCallbackAsync(callback) } catch (_: Exception) {}
        started = false; starting = false; paused = false
    }
}
