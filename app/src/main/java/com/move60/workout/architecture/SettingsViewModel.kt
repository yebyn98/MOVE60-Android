package com.move60.workout.architecture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferenceRepository
) : ViewModel() {
    val state = preferences.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings()
    )
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun setSound(enabled: Boolean) { preferences.updateSound(enabled) }

    fun setWeight(value: String) {
        val kg = value.toFloatOrNull()
        if (kg == null || kg !in 30f..300f) {
            viewModelScope.launch { _messages.emit("30~300kg 사이의 숫자를 입력해 주세요") }
            return
        }
        preferences.updateWeight(kg)
    }
}
