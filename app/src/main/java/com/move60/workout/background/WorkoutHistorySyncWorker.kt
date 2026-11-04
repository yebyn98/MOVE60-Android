package com.move60.workout.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.move60.workout.data.WorkoutDashboardRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Synchronize only completed/recorded calendar summaries.
 * The live workout timer must continue to run in the existing foreground service.
 */
@HiltWorker
class WorkoutHistorySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: WorkoutDashboardRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        repository.importHistory()
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (exception: Exception) {
        Result.retry()
    }
}
