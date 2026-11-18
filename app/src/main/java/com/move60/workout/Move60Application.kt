package com.move60.workout

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.move60.workout.architecture.RoutiveOverCommit
import com.move60.workout.architecture.SessionCommitter
import com.move60.workout.architecture.TimerSessionSync
import com.move60.workout.architecture.timerSnapshot
import dagger.hilt.android.HiltAndroidApp
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hilt application graph and WorkManager worker factory; existing crash handler is retained. */
@HiltAndroidApp
class Move60Application : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var committer: SessionCommitter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startedActivities = 0

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { if (startedActivities++ == 0) { syncTimerSessionAsync(settle = true); com.move60.workout.architecture.RoutiveSessionService.ensure(this@Move60Application) } }
            override fun onActivityStopped(activity: Activity) { if (startedActivities > 0) startedActivities-- }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /**
     * Commits a finished legacy-timer session to the Room v2 store (idempotent by session id) and, on foreground,
     * settles challenges whose end boundary has passed. Failures never reach the workout flow.
     */
    fun syncTimerSessionAsync(settle: Boolean = false, onDone: () -> Unit = {}) {
        scope.launch {
            try {
                val sync = TimerSessionSync(committer) { ZoneId.systemDefault().id }
                val snapshot = SessionStore(this@Move60Application, WorkoutData(this@Move60Application)).timerSnapshot(com.move60.workout.SetRecordStore(this@Move60Application))
                if (settle) sync.onForeground(snapshot) else sync.syncFinished(snapshot)
                // P1-1: sessions copied to the outbox before their timer slot was replaced are retried here.
                // A ROUTIVE_4 session that ended but was never confirmed committed is buffered, then drained (idempotent).
                // Same class (and lock) as the session screen, the tracking service and the watch handler use.
                RoutiveOverCommit(this@Move60Application, committer) { ZoneId.systemDefault().id }.commitPending()
            } catch (_: Throwable) { /* retried on next foreground */ }
            finally { try { onDone() } catch (_: Throwable) { } }
        }
    }
}
