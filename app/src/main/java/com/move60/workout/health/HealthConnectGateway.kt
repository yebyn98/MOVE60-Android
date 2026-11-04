package com.move60.workout.health

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opt-in bridge: Samsung Health watch records -> phone Samsung Health ->
 * user-enabled Health Connect -> MOVE60 phone -> Galaxy Watch.
 *
 * The official Samsung Health Data SDK is phone-only, distributed as a partner AAR,
 * and requires package/signature registration. This bridge uses the documented
 * Health Connect interoperability path until a registered SDK is supplied.
 */
@Singleton
class HealthConnectGateway @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val readPermissions: Set<String> = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )
        const val DATA_PATH = "/move60/health"
        private const val PREFS = "move60_health_opt_in_v1"
    }

    data class Metrics(
        val lastHeartBpm: Int? = null,
        val heartMeasuredAt: Long = 0L,
        val sleepMinutes: Long? = null,
        val sleepEndedAt: Long = 0L
    )

    private val samsungOrigin = setOf(DataOrigin("com.sec.android.app.shealth"))

    private val preferences by lazy {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun supported(): Boolean =
        Build.VERSION.SDK_INT >= 29 &&
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    fun isOptedIn(): Boolean = preferences.getBoolean("enabled", false)

    suspend fun permissionsGranted(): Boolean =
        supported() && client().permissionController.getGrantedPermissions().containsAll(readPermissions)

    suspend fun readWithConsent(): Metrics {
        check(isOptedIn()) { "MOVE60 health sharing is not enabled." }
        check(permissionsGranted()) { "Health Connect read permissions were not granted." }
        val end = Instant.now()
        val sleepStart = end.minus(Duration.ofDays(3))
        val heart = client().readRecords(
            ReadRecordsRequest(
                HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(end.minus(Duration.ofHours(24)), end),
                pageSize = 100,
                dataOriginFilter = samsungOrigin
            )
        ).records
        val mostRecent = heart.flatMap { item ->
            item.samples.map { it.time to it.beatsPerMinute.toInt() }
        }.filter { it.second in 30..240 }.maxByOrNull { it.first }
        val sleep = client().readRecords(
            ReadRecordsRequest(
                SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(sleepStart, end),
                pageSize = 100,
                dataOriginFilter = samsungOrigin
            )
        ).records.maxByOrNull { it.endTime }
        return Metrics(
            lastHeartBpm = mostRecent?.second,
            heartMeasuredAt = mostRecent?.first?.toEpochMilli() ?: 0L,
            sleepMinutes = sleep?.let { Duration.between(it.startTime, it.endTime).toMinutes().coerceAtLeast(0) },
            sleepEndedAt = sleep?.endTime?.toEpochMilli() ?: 0L
        )
    }

    fun enable() {
        check(preferences.edit().putBoolean("enabled", true).commit())
    }

    /** Clearing the bridge does not revoke platform permission; direct users to Health Connect settings. */
    fun disable() {
        check(preferences.edit().putBoolean("enabled", false).commit())
        sendToWatch(Metrics(), false)
    }

    fun sendToWatch(metrics: Metrics, consented: Boolean = isOptedIn()) {
        // Wearable Data Layer is device-paired transport, not an external server.
        val request = PutDataMapRequest.create(DATA_PATH)
        request.dataMap.putBoolean("consented", consented)
        request.dataMap.putInt("lastHeartBpm", metrics.lastHeartBpm ?: 0)
        request.dataMap.putLong("heartMeasuredAt", metrics.heartMeasuredAt)
        request.dataMap.putLong("sleepMinutes", metrics.sleepMinutes ?: -1L)
        request.dataMap.putLong("sleepEndedAt", metrics.sleepEndedAt)
        request.dataMap.putString("vascularLoad", "unsupported_watch4")
        request.dataMap.putLong("syncedAt", System.currentTimeMillis())
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent())
    }
}
