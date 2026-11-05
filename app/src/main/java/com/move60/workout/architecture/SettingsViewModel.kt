package com.move60.workout.architecture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.move60.workout.SessionStore
import com.move60.workout.WorkoutData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferenceRepository,
    private val data: WorkoutData,
    private val session: SessionStore
) : ViewModel() {
    val state = preferences.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings()
    )
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val _cardioMinutes = MutableStateFlow(data.cardioMinutes())
    val cardioMinutes: StateFlow<Int> = _cardioMinutes.asStateFlow()

    fun setSound(enabled: Boolean) { preferences.updateSound(enabled) }

    fun setWeight(value: String) {
        val kg = value.toFloatOrNull()
        if (kg == null || kg !in 30f..300f) {
            viewModelScope.launch { _messages.emit("30~300kg 사이의 숫자를 입력해 주세요") }
            return
        }
        preferences.updateWeight(kg)
        viewModelScope.launch { _messages.emit("저장했어요") }
    }

    /** Cardio/strength split can only change before today's workout starts. */
    fun setCardioMinutes(minutes: Int) {
        session.reloadIfChanged()
        if (session.running || session.elapsed() > 0) {
            viewModelScope.launch { _messages.emit("운동 시작 전 초기 상태에서 변경해 주세요.") }
            return
        }
        data.setCardioMinutes(minutes)
        session.refreshRoutine()
        session.resetAll()
        _cardioMinutes.value = minutes
        viewModelScope.launch { _messages.emit("유산소 ${minutes}분 · 근력 ${50 - minutes}분으로 변경했어요") }
    }

    fun setCondition(condition: String, summary: String) {
        preferences.updateCondition(condition)
        viewModelScope.launch { _messages.emit(summary) }
    }

    fun sessionRunning(): Boolean {
        session.reloadIfChanged()
        return session.running
    }
}
