package com.move60.workout.architecture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.move60.workout.WorkoutData
import com.move60.workout.domain.PlanValidationIssue
import com.move60.workout.domain.StageKind
import com.move60.workout.domain.StructureVersion
import com.move60.workout.domain.WorkoutStructures
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
    val stageMinutes: Map<StageKind, String> = emptyMap(),
    val selected: Map<StageKind, List<RoutineExerciseInput>> = emptyMap(),
    val save: ComposeSaveState = ComposeSaveState.EDITING,
    val error: String? = null
) {
    val exerciseCount: Int get() = selected.values.sumOf { it.size }
    val canConfirm: Boolean get() = exerciseCount >= 1 && name.isNotBlank() && save != ComposeSaveState.SAVING && save != ComposeSaveState.SAVED
}

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
    private val service: RoutinePlanService
) : ViewModel() {
    private val _state = MutableStateFlow(RoutineComposeUiState())
    val state: StateFlow<RoutineComposeUiState> = _state.asStateFlow()

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
    fun setStageMinutes(kind: StageKind, v: String) =
        _state.update { it.copy(stageMinutes = it.stageMinutes + (kind to v.filter(Char::isDigit).take(4)), error = null) }
    fun setSelection(kind: StageKind, items: List<RoutineExerciseInput>) =
        _state.update { it.copy(selected = it.selected + (kind to items), error = null) }

    fun confirm() {
        val s = _state.value
        if (!s.canConfirm) return
        val target = minutesToSeconds(s.targetMinutes).getOrElse { return fail("목표 시간: ${it.message}") }
        val planned = HashMap<StageKind, Long?>()
        for (kind in WorkoutStructures.stages(StructureVersion.ROUTIVE_4).map { it.kind }) {
            planned[kind] = minutesToSeconds(s.stageMinutes[kind].orEmpty()).getOrElse { return fail("계획시간: ${it.message}") }
        }
        _state.update { it.copy(save = ComposeSaveState.SAVING, error = null) }
        viewModelScope.launch {
            when (val r = service.save(UUID.randomUUID().toString(), s.name, target, planned, s.selected.filterValues { it.isNotEmpty() })) {
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
