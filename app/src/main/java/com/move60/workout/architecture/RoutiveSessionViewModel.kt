package com.move60.workout.architecture

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.move60.workout.SetRecordStore
import com.move60.workout.domain.StageKind
import com.move60.workout.domain.StageReadiness
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RoutiveSetUi(val number: Int, val kg: String, val reps: String)
data class RoutiveExerciseUi(
    val id: String, val name: String, val plannedLabel: String, val done: List<RoutiveSetUi>,
    /** Targets chosen by the user in the routine; null = not set (never a default). */
    val targetSets: Int? = null, val targetReps: Int? = null, val restSeconds: Long? = null
) {
    val nextSetNumber: Int get() = (done.maxOfOrNull { it.number } ?: 0) + 1
}

/** What happened to the record after the session ended. Never shown as saved until the commit is confirmed. */
sealed interface RoutiveOutcome {
    data class ResultReady(val sessionId: String) : RoutiveOutcome
    /** Below the validity threshold (under 60 s and no completed set): nothing is recorded. */
    data object NotRecorded : RoutiveOutcome
    /** The record is kept safely and is retried automatically; no result yet. */
    data object RetryLater : RoutiveOutcome
}

/** Rest timer shown on the strength screen; [done] = the rest time is over and the cue stays until the next set. */
data class RoutiveRestUi(val exerciseId: String, val remainingSeconds: Long, val totalSeconds: Long, val done: Boolean)

enum class RoutiveLoad { LOADING, NONE, ACTIVE }

data class RoutiveSessionUiState(
    val load: RoutiveLoad = RoutiveLoad.LOADING,
    val routineName: String = "",
    val running: Boolean = false,
    val over: Boolean = false,
    val stageIndex: Int = 0, val stageCount: Int = 4,
    val stageKind: StageKind = StageKind.WARM_UP,
    val stageTitle: String = "", val nextStageTitle: String? = null,
    val plannedLabel: String = "", val elapsedSeconds: Long = 0,
    /** null = the stage has no planned time ("미정"); never 0. */
    val remainingSeconds: Long? = null, val overtimeSeconds: Long = 0,
    val totalSeconds: Long = 0, val isLast: Boolean = false,
    val readiness: StageReadiness = StageReadiness.IN_PROGRESS,
    val exercises: List<RoutiveExerciseUi> = emptyList(),
    val outcome: RoutiveOutcome? = null,
    val autoPaused: Boolean = false,
    val rest: RoutiveRestUi? = null,
    /** Watch heart rate: a value only while it is valid (30..240) and at most 10 s old, otherwise "수신 대기". Never gates the workout. */
    val heartText: String = com.move60.workout.domain.HeartRateFreshness.WAITING_LABEL
) { val paused: Boolean get() = load == RoutiveLoad.ACTIVE && !running && !over }

/** Pure mapping of the stored session + saved plan + completed sets to what the screen shows. */
internal fun buildRoutiveUi(
    session: RoutiveSession, exercises: List<RoutineExerciseInput>, completed: List<SetRecordStore.CompletedSet>, outcome: RoutiveOutcome?,
    heartBpm: Int = 0, heartAgeMs: Long = Long.MAX_VALUE
): RoutiveSessionUiState {
    val p = session.progress
    val kind = p.kind
    val perExercise = completed.groupBy { it.exerciseId }
    val ui = exercises.map { e ->
        RoutiveExerciseUi(e.id, e.name, formatPlanned(e.plannedSeconds),
            perExercise[e.id].orEmpty().sortedBy { it.setNumber }.map { RoutiveSetUi(it.setNumber, it.kg, it.reps) },
            e.targetSets, e.targetReps, e.restSeconds)
    }
    val allSetsDone = kind == StageKind.STRENGTH && ui.isNotEmpty() && ui.all { it.done.isNotEmpty() }
    return RoutiveSessionUiState(
        load = RoutiveLoad.ACTIVE, routineName = session.routineName, running = session.running, over = session.over,
        stageIndex = p.stageIndex, stageCount = p.plan.size, stageKind = kind,
        stageTitle = kind.listTitle(), nextStageTitle = p.plan.getOrNull(p.stageIndex + 1)?.kind?.listTitle(),
        plannedLabel = p.plannedSeconds()?.let { "계획 ${formatPlanned(it)}" } ?: "계획 시간 미정",
        elapsedSeconds = p.elapsedSeconds[p.stageIndex], remainingSeconds = p.remainingSeconds(), overtimeSeconds = p.overtimeSeconds(),
        totalSeconds = p.totalElapsedSeconds, isLast = p.isLast, readiness = p.readiness(allSetsDone),
        exercises = ui, outcome = outcome, autoPaused = session.autoPaused,
        rest = session.rest?.takeIf { kind == StageKind.STRENGTH }?.let { RoutiveRestUi(it.exerciseId, it.remainingSeconds, it.totalSeconds, it.done) },
        heartText = com.move60.workout.domain.HeartRateFreshness.display(heartBpm, heartAgeMs)
    )
}

/** Rest only sits between sets: none after the exercise's last target set. An unknown target cannot tell, so rest runs. */
internal fun restFollowsSet(targetSets: Int?, completedSetNumber: Int): Boolean = targetSets == null || completedSetNumber < targetSets

@HiltViewModel
class RoutiveSessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plans: RoutinePlanService,
    committer: SessionCommitter
) : ViewModel() {
    private val store = RoutiveSessionStore(context)
    private val sets = SetRecordStore(context)
    private val outbox = PendingSessionOutbox(context)
    private val sync = TimerSessionSync(committer) { ZoneId.systemDefault().id }

    private val _state = MutableStateFlow(RoutiveSessionUiState())
    val state: StateFlow<RoutiveSessionUiState> = _state.asStateFlow()
    private var exerciseRoutineId: String? = null
    private var stageExercises: Map<StageKind, List<RoutineExerciseInput>> = emptyMap()
    private var outcome: RoutiveOutcome? = null
    private var committing = false

    init {
        viewModelScope.launch {
            while (isActive) {
                try { if (store.consumeRestCue() && !RoutiveCue.watchHandlesCue(context)) RoutiveCue.vibrateOnce(context); publish(store.reconcile()) } catch (c: CancellationException) { throw c } catch (_: Exception) { /* keep last state */ }
                delay(1000)
            }
        }
    }

    fun playPause() = act { s ->
        if (s.running) store.pause() else store.resume().also { RoutiveSessionService.ensure(context) }
    }
    fun next() = act { store.advance() }
    fun skip() = act { store.skip() }
    fun back() = act { store.back() }
    /** Only called from the confirm dialog. */
    fun endEarly() = act { store.endEarly() }

    fun completeSet(exerciseId: String, kg: String, reps: String) {
        val s = store.active() ?: return
        if (s.over || s.progress.kind != StageKind.STRENGTH || kg.isBlank() || reps.isBlank()) return
        val next = (sets.completedSetsForSession(s.startedAtEpochMs).filter { it.exerciseId == exerciseId }.maxOfOrNull { it.setNumber } ?: 0) + 1
        sets.write(s.day, exerciseId, next, SetRecordStore.Record(kg, reps, "5", true), s.recordDate, s.startedAtEpochMs)
        // Rest starts only when the user chose a rest time for this exercise; nothing is assumed otherwise.
        // Rest only sits between sets: none after the exercise's last target set (unknown target = cannot tell, rest runs).
        val exercise = stageExercises[StageKind.STRENGTH].orEmpty().firstOrNull { it.id == exerciseId }
        val rest = exercise?.restSeconds
        if (rest != null && restFollowsSet(exercise.targetSets, next)) store.startRest(exerciseId, rest) else store.skipRest()
        viewModelScope.launch { publish(store.active()) }
    }

    /** Undoes only the most recent completed set of an exercise. */
    fun undoLastSet(exerciseId: String) {
        val s = store.active() ?: return
        if (s.over) return
        val last = sets.completedSetsForSession(s.startedAtEpochMs).filter { it.exerciseId == exerciseId }.maxByOrNull { it.setNumber } ?: return
        sets.write(s.day, exerciseId, last.setNumber, SetRecordStore.Record(last.kg, last.reps, "5", false), s.recordDate, s.startedAtEpochMs)
        if (s.rest?.exerciseId == exerciseId) store.skipRest()
        viewModelScope.launch { publish(store.active()) }
    }

    /** Ends the rest early or dismisses the "rest finished" cue. */
    fun skipRest() = act { store.skipRest() }

    private fun act(change: (RoutiveSession) -> RoutiveSession?) {
        val s = store.reconcile() ?: return
        change(s)
        viewModelScope.launch { publish(store.active()) }
    }

    private suspend fun publish(session: RoutiveSession?) {
        if (session == null) { _state.value = RoutiveSessionUiState(load = RoutiveLoad.NONE); return }
        if (exerciseRoutineId != session.routineId) {
            stageExercises = try { plans.list().firstOrNull { it.routineId == session.routineId }?.exercises.orEmpty() } catch (_: Exception) { emptyMap() }
            exerciseRoutineId = session.routineId
            outcome = null; committing = false
        }
        if (session.over && outcome == null && !committing) commit(session)
        val heart = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        _state.value = buildRoutiveUi(session, stageExercises[session.progress.kind].orEmpty(),
            sets.completedSetsForSession(session.startedAtEpochMs), outcome,
            heart.getInt("watch_heart_rate", 0), System.currentTimeMillis() - heart.getLong("watch_heart_rate_at", 0L))
    }

    /** Buffer first (durable), then drain through the idempotent commit; the result is shown only for a confirmed outcome. */
    private suspend fun commit(session: RoutiveSession) {
        committing = true
        val snap = store.snapshot(session, sets)
        val id = sync.sessionId(snap)
        outbox.enqueue(snap)
        val outcomes = sync.drain(outbox)
        outcome = when {
            outcomes.any { (it is SyncOutcome.Committed && it.sessionId == id) || (it is SyncOutcome.AlreadyCommitted && it.sessionId == id) } -> RoutiveOutcome.ResultReady(id)
            outcomes.any { it is SyncOutcome.Discarded && it.sessionId == id } -> RoutiveOutcome.NotRecorded
            outbox.pending().none { sync.sessionId(it) == id } -> RoutiveOutcome.NotRecorded
            else -> RoutiveOutcome.RetryLater
        }
        if (outcome != RoutiveOutcome.RetryLater) store.markCommitted(session.startedAtEpochMs)
        committing = false
    }
}
