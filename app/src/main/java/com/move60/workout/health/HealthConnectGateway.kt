package com.move60.workout.health

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
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
            HealthPermission.getReadPermission(HeartRateRecord::class)
        )
        const val DATA_PATH = "/move60/health"
        private const val PREFS = "move60_health_opt_in_v1"
    }

    data class Metrics(
        val lastHeartBpm: Int? = null,
        val heartMeasuredAt: Long = 0L,
        val sourcePackage: String? = null
    )

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
        // Data is often synced from a watch hours or days later. Do not restrict
        // records to a single writer package: Health Connect can expose heart-rate
        // samples under multiple app origins, even when Samsung Health is installed.
        val timeRange = TimeRangeFilter.between(end.minus(Duration.ofDays(7)), end)
        var nextPage: String? = null
        var latest: Triple<Instant, Int, String>? = null
        do {
            val page = client().readRecords(
                ReadRecordsRequest(
                    HeartRateRecord::class,
                    timeRangeFilter = timeRange,
                    pageSize = 100,
                    pageToken = nextPage
                )
            )
            for (record in page.records) {
                val origin = record.metadata.dataOrigin.packageName
                for (sample in record.samples) {
                    val bpm = sample.beatsPerMinute.toInt()
                    if (bpm in 30..240 && (latest == null || sample.time.isAfter(latest.first))) {
                        latest = Triple(sample.time, bpm, origin)
                    }
                }
            }
            nextPage = page.pageToken
        } while (!nextPage.isNullOrEmpty())
        return Metrics(
            lastHeartBpm = latest?.second,
            heartMeasuredAt = latest?.first?.toEpochMilli() ?: 0L,
            sourcePackage = latest?.third
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
        request.dataMap.putLong("syncedAt", System.currentTimeMillis())
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent())
    }
}
