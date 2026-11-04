package com.move60.workout.architecture

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.move60.workout.EnergyLedger
import com.move60.workout.WorkoutHistory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Durable, idempotent history materialization for Room / calendar display.
 * Active exercise timing remains in the foreground service, never WorkManager.
 */
@HiltWorker
class WorkoutHistorySyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: WorkoutDao,
    private val history: WorkoutHistory,
    private val energy: EnergyLedger
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
            for (index in 0..34) {
                if (isStopped) return@withContext Result.retry()
                val date = today.minusDays(index.toLong())
                val seconds = history.record(date).activeSeconds
                // Materialize 0-day rows too, so the calendar starts from zero.
                dao.upsertDay(
                    DailyWorkoutEntity(
                        date.toString(),
                        seconds,
                        energy.estimate(date).coerceAtLeast(0.0),
                        System.currentTimeMillis()
                    )
                )
            }
            Result.success()
        } catch (error: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
