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

    /** 유산소 ① / 유산소 ② minutes; 근력 is the rest of the 50 adjustable minutes. */
    private val _split = MutableStateFlow(data.cardioFirstMinutes() to data.cardioSecondMinutes())
    val split: StateFlow<Pair<Int, Int>> = _split.asStateFlow()

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
    fun setCardioSplit(first: Int, second: Int) {
        if (!WorkoutData.isValidSplit(first, second)) return
        if (first == _split.value.first && second == _split.value.second) return
        session.reloadIfChanged()
        if (session.running || session.elapsed() > 0) {
            _split.value = data.cardioFirstMinutes() to data.cardioSecondMinutes() // snap the slider back
            viewModelScope.launch { _messages.emit("운동 시작 전 초기 상태에서 변경해 주세요.") }
            return
        }
        data.setCardioSplit(first, second)
        session.refreshRoutine()
        session.resetAll()
        _split.value = first to second
        viewModelScope.launch {
            _messages.emit("유산소 ① ${first}분 · ② ${second}분 · 근력 ${WorkoutData.ADJUSTABLE_MINUTES - first - second}분으로 변경했어요")
        }
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
