package com.move60.workout.architecture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.move60.workout.WorkoutData
import com.move60.workout.domain.PlanValidationIssue
import com.move60.workout.domain.StageKind
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ComposeSaveState { EDITING, SAVING, SAVED, FAILED }

data class RoutineComposeUiState(
    val name: String = "내 루틴",
    /** Raw text of the optional total target in minutes; blank = unknown (never 0). */
    val targetMinutes: String = "",
    /** Raw minutes text per chosen exercise, keyed "STAGE:exerciseId". Blank = unknown (null), never 0. */
    val exerciseMinutes: Map<String, String> = emptyMap(),
    /** Raw strength target texts per chosen strength exercise (keyed like [exerciseMinutes]). Blank = not set (null). */
    val strengthTargets: Map<String, StrengthTargetText> = emptyMap(),
    val selected: Map<StageKind, List<RoutineExerciseInput>> = emptyMap(),
    val save: ComposeSaveState = ComposeSaveState.EDITING,
    val error: String? = null
) {
    val exerciseCount: Int get() = selected.values.sumOf { it.size }
    /** Stage plan derived from its exercises: known only when every exercise has a valid time (no separate stage input). */
    fun derivedStageMinutes(kind: StageKind): Long? {
        val items = selected[kind].orEmpty()
        if (items.isEmpty()) return null
        val secs = items.map { minutesToSeconds(exerciseMinutes[exKey(kind, it.id)].orEmpty()).getOrNull() ?: return null }
        return secs.sum() / 60
    }
    val canConfirm: Boolean get() = exerciseCount >= 1 && name.isNotBlank() && save != ComposeSaveState.SAVING && save != ComposeSaveState.SAVED
}

/** Raw text of one strength exercise's targets: sets, reps and rest seconds. Blank = not set (null, never a default). */
data class StrengthTargetText(val sets: String = "", val reps: String = "", val rest: String = "")

internal fun exKey(kind: StageKind, id: String) = "${kind.name}:$id"

/** Blank -> null (not set). Otherwise a number inside [range] or a failure with a user-facing message. */
internal fun boundedNumber(text: String, range: LongRange, label: String): Result<Long?> {
    val t = text.trim()
    if (t.isEmpty()) return Result.success(null)
    val n = t.toLongOrNull() ?: return Result.failure(IllegalArgumentException("$label: 숫자만 입력해 주세요"))
    return if (n in range) Result.success(n) else Result.failure(IllegalArgumentException("$label: ${range.first}~${range.last} 사이로 입력해 주세요"))
}

/** Approved exercises doable with [registered] (one space), named from the app's own data. Order follows the rule table; no values are invented. */
internal fun candidateInputs(data: WorkoutData, registered: Set<String>): List<RoutineExerciseInput> =
    com.move60.workout.domain.ExerciseEquipmentRules.candidates(com.move60.workout.domain.ExerciseEquipmentRules.RULES.keys, registered)
        .mapNotNull { id -> data.exerciseName(id)?.let { RoutineExerciseInput(id, it) } }

/** Maps a minutes text field to seconds. Blank -> null (unknown). Returns Result.failure for non-positive/garbage input. */
internal fun minutesToSeconds(text: String): Result<Long?> {
    val t = text.trim()
    if (t.isEmpty()) return Result.success(null)
    val m = t.toLongOrNull() ?: return Result.failure(IllegalArgumentException("숫자만 입력해 주세요"))
    if (m <= 0L || m > 24 * 60) return Result.failure(IllegalArgumentException("1분 이상 입력해 주세요"))
    return Result.success(m * 60)
}

@HiltViewModel
class RoutineComposeViewModel @Inject constructor(
    private val data: WorkoutData,
    private val service: RoutinePlanService,
    private val spaceSource: DefaultSpaceEquipment
) : ViewModel() {
    private val _state = MutableStateFlow(RoutineComposeUiState())
    val state: StateFlow<RoutineComposeUiState> = _state.asStateFlow()

    private val _space = MutableStateFlow<CandidateSpace>(CandidateSpace.Loading)
    /** The default space whose equipment decides the candidates (never merged with other spaces). */
    val space: StateFlow<CandidateSpace> = _space.asStateFlow()
    init { viewModelScope.launch { _space.value = spaceSource.load() } }

    /**
     * "Exercises that fit the equipment": every one of the app's own exercises that the PO approved and that the DEFAULT space's equipment
     * allows. This is NOT limited to today's pool; the manual list below stays as it was. Only strength exercises have rules.
     */
    fun candidates(kind: StageKind): List<RoutineExerciseInput> {
        if (kind != StageKind.STRENGTH) return emptyList()
        val ready = _space.value as? CandidateSpace.Ready ?: return emptyList()
        return candidateInputs(data, ready.equipment)
    }

    /** Candidate exercises per stage, taken from the app's own curated catalog (no invented items). */
    val catalog: Map<StageKind, List<RoutineExerciseInput>> by lazy {
        val day = data.todayDay()
        val routine = data.routine(day)
        fun tasks(stage: Int) = routine.stages.getOrNull(stage)?.tasks.orEmpty()
            .map { RoutineExerciseInput(it.name, it.name) }.distinctBy { it.id }
        val strength = listOf("upper", "lower").flatMap { focus ->
            (0..3).flatMap { slot -> data.alternatives(day, slot, focus) }
        }.distinctBy { it.first }.map { (id, name) -> RoutineExerciseInput(id, name) }
        mapOf(
            StageKind.WARM_UP to tasks(0),
            StageKind.STRENGTH to strength,
            StageKind.CARDIO to listOf(WorkoutData.CARDIO_FIRST, WorkoutData.CARDIO_SECOND).map { RoutineExerciseInput(it, it) },
            StageKind.COOL_DOWN to tasks(4)
        )
    }

    fun setName(v: String) = _state.update { it.copy(name = v.take(RoutinePlanService.MAX_NAME), error = null) }
    fun setTarget(v: String) = _state.update { it.copy(targetMinutes = v.filter(Char::isDigit).take(4), error = null) }
    fun setExerciseMinutes(kind: StageKind, id: String, v: String) =
        _state.update { it.copy(exerciseMinutes = it.exerciseMinutes + (exKey(kind, id) to v.filter(Char::isDigit).take(4)), error = null) }
    private fun updateTarget(kind: StageKind, id: String, change: (StrengthTargetText) -> StrengthTargetText) = _state.update {
        val key = exKey(kind, id)
        it.copy(strengthTargets = it.strengthTargets + (key to change(it.strengthTargets[key] ?: StrengthTargetText())), error = null)
    }
    fun setTargetSets(id: String, v: String) = updateTarget(StageKind.STRENGTH, id) { it.copy(sets = v.filter(Char::isDigit).take(2)) }
    fun setTargetReps(id: String, v: String) = updateTarget(StageKind.STRENGTH, id) { it.copy(reps = v.filter(Char::isDigit).take(3)) }
    fun setRestSeconds(id: String, v: String) = updateTarget(StageKind.STRENGTH, id) { it.copy(rest = v.filter(Char::isDigit).take(3)) }
    fun setSelection(kind: StageKind, items: List<RoutineExerciseInput>) =
        _state.update { it.copy(selected = it.selected + (kind to items), error = null) }

    fun confirm() {
        val s = _state.value
        if (!s.canConfirm) return
        val target = minutesToSeconds(s.targetMinutes).getOrElse { return fail("목표 시간: ${it.message}") }
        val withTimes = HashMap<StageKind, List<RoutineExerciseInput>>()
        for ((kind, items) in s.selected) {
            withTimes[kind] = items.map { e ->
                val planned = minutesToSeconds(s.exerciseMinutes[exKey(kind, e.id)].orEmpty()).getOrElse { return fail("${e.name} 계획시간: ${it.message}") }
                if (kind != StageKind.STRENGTH) return@map e.copy(plannedSeconds = planned)
                val t = s.strengthTargets[exKey(kind, e.id)] ?: StrengthTargetText()
                val sets = boundedNumber(t.sets, RoutinePlanService.SETS_RANGE.let { it.first.toLong()..it.last.toLong() }, "${e.name} 목표 세트").getOrElse { return fail(it.message.orEmpty()) }
                val reps = boundedNumber(t.reps, RoutinePlanService.REPS_RANGE.let { it.first.toLong()..it.last.toLong() }, "${e.name} 목표 반복").getOrElse { return fail(it.message.orEmpty()) }
                val rest = boundedNumber(t.rest, RoutinePlanService.REST_RANGE, "${e.name} 휴식(초)").getOrElse { return fail(it.message.orEmpty()) }
                e.copy(plannedSeconds = planned, targetSets = sets?.toInt(), targetReps = reps?.toInt(), restSeconds = rest)
            }
        }
        _state.update { it.copy(save = ComposeSaveState.SAVING, error = null) }
        viewModelScope.launch {
            when (val r = service.save(UUID.randomUUID().toString(), s.name, target, withTimes.filterValues { it.isNotEmpty() })) {
                is RoutinePlanResult.Saved -> _state.update { it.copy(save = ComposeSaveState.SAVED) }
                is RoutinePlanResult.Rejected -> _state.update { it.copy(save = ComposeSaveState.EDITING, error = rejectMessage(r)) }
                is RoutinePlanResult.Failed -> _state.update { it.copy(save = ComposeSaveState.FAILED, error = "저장하지 못했어요. 입력한 내용은 그대로 두었어요. 다시 시도해 주세요.") }
            }
        }
    }

    private fun fail(message: String) { _state.update { it.copy(error = message) } }

    private fun rejectMessage(r: RoutinePlanResult.Rejected) = when (r.issue) {
        PlanValidationIssue.KNOWN_TIME_EXCEEDS_TARGET -> "입력한 단계 시간의 합이 목표 시간을 넘어요."
        PlanValidationIssue.TOTAL_TIME_MISMATCH -> "모든 단계 시간을 입력했다면 합이 목표 시간과 같아야 해요."
        PlanValidationIssue.INVALID_TARGET_DURATION, PlanValidationIssue.INVALID_EXERCISE_DURATION -> "시간은 1분 이상으로 입력해 주세요."
        else -> "입력한 내용을 확인해 주세요."
    }
}
