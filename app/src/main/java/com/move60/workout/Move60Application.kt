package com.move60.workout

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.move60.workout.background.WorkoutHistorySyncWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class Move60Application : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        // Historical Room summaries are a safe cache. Never background-run the live timer here.
        val request = PeriodicWorkRequestBuilder<WorkoutHistorySyncWorker>(
            12, TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "move60_history_cache_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
