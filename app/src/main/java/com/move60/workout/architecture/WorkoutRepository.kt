package com.move60.workout.architecture

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.move60.workout.EnergyLedger
import com.move60.workout.LAST_STAGE
import com.move60.workout.SessionStore
import com.move60.workout.SetRecordStore
import com.move60.workout.TrainingProgress
import com.move60.workout.WatchStatePublisher
import com.move60.workout.WorkoutData
import com.move60.workout.WorkoutService
import com.move60.workout.WorkoutHistory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class WorkoutSetUi(val number: Int, val kg: String, val reps: String, val completed: Boolean)

data class WorkoutTaskUi(
    val id: String,
    val name: String,
    val detail: String,
    val sets: Int = 0,
    val done: Int = 0,
    val entries: List<WorkoutSetUi> = emptyList()
)
data class WorkoutUiState(
    val day: Int = 1,
    val date: String = "",
    val sessionId: Long = 0,
    val bodyFocus: String? = null,
    val running: Boolean = false,
    val finished: Boolean = false,
    val stage: Int = 0,
    val stageName: String = "준비운동",
    val remainingSeconds: Int = 0,
    val activeSeconds: Int = 0,
    val todayKcal: Double = 0.0,
    val stageNames: List<String> = emptyList(),
    val stageTasks: List<WorkoutTaskUi> = emptyList(),
    val totalSets: Int = 0,
    val completedSets: Int = 0,
    val restSeconds: Int = 0
)

interface WorkoutRepository {
    val state: StateFlow<WorkoutUiState>
    fun recentDays(): Flow<List<DailyWorkoutEntity>>
    suspend fun refresh()
    suspend fun selectDay(day: Int)
    suspend fun selectBody(focus: String)
    suspend fun startPause()
    suspend fun nextStage()
    suspend fun previousStage()
    suspend fun saveSet(exerciseId: String, set: Int, kg: String, reps: String, done: Boolean)
    suspend fun finishWorkout()
    fun syncHistoricalData()
}

@Singleton
class DefaultWorkoutRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionStore,
    private val data: WorkoutData,
    private val energy: EnergyLedger,
    private val history: WorkoutHistory,
    private val setStore: SetRecordStore,
    private val dao: WorkoutDao,
    private val workManager: WorkManager
) : WorkoutRepository {
    private val lock = Mutex()
    private val _state = MutableStateFlow(WorkoutUiState())
    override val state: StateFlow<WorkoutUiState> = _state.asStateFlow()

    override fun recentDays(): Flow<List<DailyWorkoutEntity>> = dao.observeRecentDays()

    override suspend fun refresh(): Unit = withContext(Dispatchers.IO) {
        lock.withLock {
            session.reloadIfChanged()
            session.reconcile()
            val today = data.localToday()
            val workoutStage = session.routine.stages[session.stage]
            val tasks = workoutStage.tasks.map { task ->
                val setCount = if (session.stage == 2)
                    Regex("(\\d+)\\s*세트").find(task.detail)?.groupValues?.get(1)?.toIntOrNull() ?: 3
                else 0
                val completedCount = (1..setCount).count {
                    "strength-${session.day}-${task.id}-${it}" in session.checked
                }
                val entries = (1..setCount).map { index ->
                    val record = setStore.read(session.day, task.id, index, session.recordDate,
                        token = session.sessionStartedAt)
                    WorkoutSetUi(index, record.kg, record.reps, record.completed)
                }
                WorkoutTaskUi(task.id, task.name, task.detail, setCount, completedCount, entries)
            }
            val strength = session.routine.stages[2].tasks
            val total = strength.sumOf {
                Regex("(\\d+)\\s*세트").find(it.detail)?.groupValues?.get(1)?.toIntOrNull() ?: 3
            }
            val done = strength.sumOf { task ->
                val count = Regex("(\\d+)\\s*세트").find(task.detail)?.groupValues?.get(1)?.toIntOrNull() ?: 3
                (1..count).count { "strength-${session.day}-${task.id}-$it" in session.checked }
            }
            _state.value = WorkoutUiState(
                day = session.day,
                date = today.toString(),
                sessionId = session.sessionStartedAt,
                bodyFocus = session.bodyFocus,
                running = session.running,
                finished = session.finished,
                stage = session.stage,
                stageName = workoutStage.name,
                remainingSeconds = session.remaining,
                activeSeconds = session.elapsed(),
                todayKcal = energy.estimate(today, JSONObject(session.snapshot())).coerceAtLeast(0.0),
                stageNames = session.routine.stages.map { it.name },
                stageTasks = tasks,
                totalSets = total,
                completedSets = done,
                restSeconds = session.restSeconds()
            )
        }
    }

    override suspend fun selectDay(day: Int) = mutate {
        if (!session.running) session.setDay(day)
    }
    override suspend fun selectBody(focus: String) = mutate {
        session.selectBodyFocus(focus)
    }
    override suspend fun startPause() = mutate {
        if (session.running) session.pause() else {
            if (session.finished || session.elapsed() == 0) session.refreshRoutine()
            session.start()
            setStore.beginSession(session.day, session.sessionStartedAt)
        }
        publishToService()
    }
    override suspend fun nextStage() = mutate {
        session.nextStage()
        publishToService()
        if (session.finished) syncHistoricalData()
    }
    override suspend fun previousStage() = mutate {
        session.jumpTo(session.stage - 1)
        publishToService()
    }
    override suspend fun finishWorkout() = mutate {
        // Explicit final-stage exit, never auto-advance on a countdown.
        if (!session.finished && session.stage == LAST_STAGE) session.nextStage()
        publishToService()
        if (session.finished) syncHistoricalData()
    }

    override suspend fun saveSet(
        exerciseId: String, set: Int, kg: String, reps: String, done: Boolean
    ) = mutate {
        val task = session.routine.stages[2].tasks.firstOrNull { it.id == exerciseId }
            ?: return@mutate
        if (session.sessionStartedAt == 0L || session.stage != 2) return@mutate
        val record = SetRecordStore.Record(kg, reps, "5", done)
        if (done && !record.valid) throw IllegalArgumentException("중량과 횟수를 확인해 주세요")
        val token = session.sessionStartedAt
        val date = session.recordDate
        setStore.write(session.day, task.id, set, record, date, token)
        val checkedKey = "strength-${session.day}-${task.id}-$set"
        session.toggleChecked(checkedKey, done && record.valid)
        dao.upsertSet(
            WorkoutSetEntity(
                key = "$date:$token:${task.id}:$set",
                date = date.toString(), sessionId = token,
                exerciseId = task.id, setNumber = set, weightKg = kg,
                repetitions = reps, completed = done && record.valid
            )
        )
        if (done && session.running) session.startRest(45)
        publishToService()
    }

    private suspend fun mutate(block: suspend () -> Unit) {
        withContext(Dispatchers.IO) {
            lock.lock()
            try {
                session.reloadIfChanged()
                block() // suspend Room writes are allowed while holding the coroutine mutex
            } finally {
                lock.unlock()
            }
        }
        refresh()
    }

    /** The existing foreground timer is authoritative; WorkManager never drives a live timer. */
    private fun publishToService() {
        val snapshot = session.snapshot()
        WatchStatePublisher.publishSnapshot(context, snapshot)
        val intent = Intent(context, WorkoutService::class.java)
        if (session.running && !session.finished) {
            intent.action = WorkoutService.ACTION_SYNC
            intent.putExtra(WorkoutService.EXTRA_SNAPSHOT, snapshot)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        } else context.stopService(intent)
    }

    override fun syncHistoricalData() {
        workManager.enqueueUniqueWork(
            "move60-history-room-sync",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WorkoutHistorySyncWorker>().build()
        )
    }
}
