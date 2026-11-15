package com.move60.workout.architecture

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.move60.workout.Move60Application
import com.move60.workout.SetRecordStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedState: SavedStateHandle,
    db: WorkoutDatabase,
    sets: SetRecordStore,
    private val timer: com.move60.workout.SessionStore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val sessionId: String = savedState["sessionId"] ?: ""
    private val reader = WorkoutResultReader(db) { sets.completedForSession(it).size }
    private val _result = MutableStateFlow(WorkoutResult(sessionId, null, 0, 0, null, ResultSaveState.SAVING))
    val result: StateFlow<WorkoutResult> = _result.asStateFlow()

    init { load() }

    /** Polls the store for up to ~8 s (the commit runs asynchronously), then reports FAILED so the user can retry. */
    private fun load() {
        viewModelScope.launch {
            _result.value = _result.value.copy(saveState = ResultSaveState.SAVING)
            repeat(8) { attempt ->
                // Only trust the live timer when it still belongs to this exact session.
                val live = timer.takeIf { sessionId.endsWith("-${it.sessionStartedAt}") }?.accumulatedActiveSeconds?.toLong()
                val r = reader.read(sessionId, giveUp = attempt == 7, liveActiveSeconds = live)
                _result.value = r
                if (r.saveState != ResultSaveState.SAVING) return@launch
                delay(1_000)
            }
        }
    }

    /** E-03 A: retry re-runs the idempotent commit; the stored result never changes on failure. */
    fun retry() {
        (context.applicationContext as? Move60Application)?.syncTimerSessionAsync()
        load()
    }
}
