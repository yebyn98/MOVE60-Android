package com.move60.workout.architecture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface WorkoutUiEvent {
    data class Message(val message: String) : WorkoutUiEvent
}

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val repository: WorkoutRepository
) : ViewModel() {
    val state = repository.state
    private val _events = MutableSharedFlow<WorkoutUiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            // The foreground WorkoutService owns elapsed time; this only observes it.
            while (isActive) {
                try {
                    repository.refresh()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _events.emit(WorkoutUiEvent.Message(error.message ?: "운동 상태를 읽지 못했어요"))
                }
                delay(1000)
            }
        }
        repository.syncHistoricalData()
    }

    fun selectDay(day: Int) = perform { repository.selectDay(day) }
    fun selectBody(focus: String) = perform { repository.selectBody(focus) }
    fun playPause() = perform { repository.startPause() }
    fun previous() = perform { repository.previousStage() }
    fun next() = perform { repository.nextStage() }
    fun finish() = perform { repository.finishWorkout() }
    fun saveSet(id: String, set: Int, kg: String, reps: String, checked: Boolean) =
        perform { repository.saveSet(id, set, kg, reps, checked) }

    private fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _events.emit(WorkoutUiEvent.Message(error.message ?: "요청을 처리하지 못했어요"))
            }
        }
    }
}
