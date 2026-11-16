package com.move60.workout.architecture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.move60.workout.SetRecordStore
import com.move60.workout.WorkoutData
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Load state of a record screen. A read error is shown as [Failed] (with retry), never as an empty record. */
sealed interface RecordLoad<out T> {
    data object Loading : RecordLoad<Nothing>
    data class Ready<T>(val value: T) : RecordLoad<T>
    /** The requested date or session does not exist (for example a stale link). */
    data object Missing : RecordLoad<Nothing>
    data object Failed : RecordLoad<Nothing>
}

/** Figma 26: all sessions of one date. Read-only. */
@HiltViewModel
class RecordDayViewModel @Inject constructor(
    savedState: SavedStateHandle,
    db: WorkoutDatabase,
    sets: SetRecordStore,
    private val data: WorkoutData
) : ViewModel() {
    private val date: LocalDate? = runCatching { LocalDate.parse(savedState.get<String>("date") ?: "") }.getOrNull()
    private val reader = RecordReader(db) { sets.completedStoredSets() }
    private val _state = MutableStateFlow<RecordLoad<DayRecord>>(RecordLoad.Loading)
    val state: StateFlow<RecordLoad<DayRecord>> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = RecordLoad.Loading
        viewModelScope.launch {
            _state.value = try {
                val d = date
                if (d == null) RecordLoad.Missing else RecordLoad.Ready(reader.day(d))
            } catch (_: Exception) { RecordLoad.Failed }
        }
    }

    /** Stored ids are shown as saved when the curated name is unknown. */
    fun exerciseName(id: String): String = data.exerciseName(id) ?: id
}

/** Figma 27: one stored session. Read-only. */
@HiltViewModel
class RecordSessionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val db: WorkoutDatabase,
    sets: SetRecordStore,
    private val data: WorkoutData
) : ViewModel() {
    private val sessionId: String = savedState.get<String>("sessionId") ?: ""
    private val reader = RecordReader(db) { sets.completedStoredSets() }
    private val _state = MutableStateFlow<RecordLoad<SessionDetail>>(RecordLoad.Loading)
    val state: StateFlow<RecordLoad<SessionDetail>> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = RecordLoad.Loading
        viewModelScope.launch {
            _state.value = try {
                reader.session(sessionId)?.let { RecordLoad.Ready(it) } ?: RecordLoad.Missing
            } catch (_: Exception) { RecordLoad.Failed }
        }
    }

    fun exerciseName(id: String): String = data.exerciseName(id) ?: id

    /**
     * Policy 6: saves corrections as new history rows (the stored originals are never changed). The screen keeps showing the
     * old record until the save is confirmed; only [CorrectionResult.Saved] reloads it.
     */
    fun saveCorrections(edits: List<SetCorrection>, onResult: (CorrectionResult) -> Unit) {
        viewModelScope.launch {
            val result = SessionCorrectionService(db).apply(sessionId, edits)
            if (result is CorrectionResult.Saved) load()
            onResult(result)
        }
    }
}
