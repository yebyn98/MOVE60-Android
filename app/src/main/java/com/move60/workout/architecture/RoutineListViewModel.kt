package com.move60.workout.architecture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.move60.workout.domain.StageKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** "미정" for an unknown time (never 0), otherwise minutes (and seconds when not a whole minute). */
internal fun formatPlanned(seconds: Long?): String = when {
    seconds == null -> "미정"
    seconds % 60L == 0L -> "${seconds / 60}분"
    seconds < 60L -> "${seconds}초"
    else -> "${seconds / 60}분 ${seconds % 60}초"
}

internal fun StageKind.listTitle() = when (this) {
    StageKind.WARM_UP -> "준비운동"; StageKind.STRENGTH -> "근력운동"
    StageKind.CARDIO -> "유산소"; StageKind.COOL_DOWN -> "마무리"
}

data class RoutineListItem(
    val routineId: String, val name: String, val targetLabel: String, val exerciseCount: Int,
    /** (stage title, planned label) in the fixed 4-stage order. */
    val stages: List<Pair<String, String>>
)

internal fun SavedRoutinePlan.toListItem() = RoutineListItem(
    routineId = routineId, name = name,
    targetLabel = targetSeconds?.let { "목표 ${formatPlanned(it)}" } ?: "목표 시간 미정",
    exerciseCount = exercises.values.sumOf { it.size },
    stages = stages.map { it.kind.listTitle() to formatPlanned(it.plannedSeconds) }
)

enum class RoutineListLoad { LOADING, READY, FAILED }

data class RoutineListUiState(val load: RoutineListLoad = RoutineListLoad.LOADING, val items: List<RoutineListItem> = emptyList())

@HiltViewModel
class RoutineListViewModel @Inject constructor(private val service: RoutinePlanService) : ViewModel() {
    private val _state = MutableStateFlow(RoutineListUiState())
    val state: StateFlow<RoutineListUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(load = RoutineListLoad.LOADING)
        viewModelScope.launch {
            _state.value = try {
                RoutineListUiState(RoutineListLoad.READY, service.list().map { it.toListItem() })
            } catch (_: Exception) {
                RoutineListUiState(RoutineListLoad.FAILED, _state.value.items)   // keep what was shown, no invented data
            }
        }
    }
}
