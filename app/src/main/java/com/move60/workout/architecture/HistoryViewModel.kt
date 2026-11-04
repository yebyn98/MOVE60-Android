package com.move60.workout.architecture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HistoryViewModel @Inject constructor(
    repository: WorkoutRepository
) : ViewModel() {
    val days = repository.recentDays().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    init { repository.syncHistoricalData() }
}
