package com.move60.workout.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.move60.workout.data.WorkoutDashboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryItem(val date: String, val activeMinutes: Long, val kcal: Double)

data class ComposeHomeState(
    val todayDate: String = "",
    val todayActiveMinutes: Long = 0,
    val todayKcal: Double = 0.0,
    val history: List<HistoryItem> = emptyList(),
    val soundEnabled: Boolean = true,
    val weightKg: Float = 63f,
    val appVersion: String = "-"
)

sealed interface HomeEvent {
    data object OpenWorkout : HomeEvent
    data object OpenAdvancedSettings : HomeEvent
    data class Message(val value: String) : HomeEvent
}

@HiltViewModel
class ComposeHomeViewModel @Inject constructor(
    private val repository: WorkoutDashboardRepository
) : ViewModel() {
    private val sound = MutableStateFlow(repository.soundEnabled())
    private val weight = MutableStateFlow(repository.weightKg())
    private val date = MutableStateFlow(repository.currentDate().toString())
    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    val state: StateFlow<ComposeHomeState> =
        combine(repository.observeRecent(), sound, weight, date) { records, alerts, kg, today ->
            val todayRecord = records.firstOrNull { it.date == today }
            ComposeHomeState(
                todayDate = today,
                todayActiveMinutes = (todayRecord?.activeSeconds ?: 0L) / 60,
                todayKcal = todayRecord?.estimatedKcal ?: 0.0,
                history = records.filter { it.activeSeconds > 0L }
                    .map { HistoryItem(it.date, it.activeSeconds / 60, it.estimatedKcal) },
                soundEnabled = alerts,
                weightKg = kg
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            ComposeHomeState(todayDate = repository.currentDate().toString()))

    init {
        viewModelScope.launch {
            try {
                repository.importHistory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _events.emit(HomeEvent.Message("운동 기록을 불러오지 못했어요."))
            }
        }
    }

    /** Called only while ComposeHomeActivity is STARTED, never in an uncontrolled global loop. */
    suspend fun refreshVisible() {
        try {
            date.value = repository.currentDate().toString()
            repository.refreshToday()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _events.emit(HomeEvent.Message("운동 정보 갱신에 실패했어요."))
        }
    }

    fun openWorkout() { viewModelScope.launch { _events.emit(HomeEvent.OpenWorkout) } }
    fun openAdvancedSettings() { viewModelScope.launch { _events.emit(HomeEvent.OpenAdvancedSettings) } }

    fun updateSound(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setSoundEnabled(enabled)
                sound.value = enabled
                _events.emit(HomeEvent.Message("알림음 설정이 저장됐어요."))
            } catch (error: Exception) {
                _events.emit(HomeEvent.Message("알림음 설정을 저장하지 못했어요."))
            }
        }
    }

    fun updateWeight(value: String) {
        viewModelScope.launch {
            val parsed = value.toFloatOrNull()
            if (parsed == null || parsed !in 25f..300f) {
                _events.emit(HomeEvent.Message("체중은 25~300kg 범위로 입력하세요."))
                return@launch
            }
            try {
                repository.setWeightKg(parsed)
                weight.value = parsed
                repository.refreshToday()
                _events.emit(HomeEvent.Message("칼로리 계산 체중이 저장됐어요."))
            } catch (error: Exception) {
                _events.emit(HomeEvent.Message("체중 설정을 저장하지 못했어요."))
            }
        }
    }
}
