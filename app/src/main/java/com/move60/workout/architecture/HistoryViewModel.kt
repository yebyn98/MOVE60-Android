package com.move60.workout.architecture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.move60.workout.EnergyLedger
import com.move60.workout.WorkoutData
import com.move60.workout.WorkoutHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

/** Month summary shown under the calendar grid. */
data class MonthSummary(val trainedDays: Int, val activeMinutes: Long, val kcal: Int)

/** Selected-day detail shown under the calendar grid. */
data class DaySummary(val date: LocalDate, val activeMinutes: Long, val kcal: Int)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    repository: WorkoutRepository,
    private val history: WorkoutHistory,
    private val ledger: EnergyLedger,
    data: WorkoutData
) : ViewModel() {
    val days = repository.recentDays().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    private val today = data.localToday()
    private val _month = MutableStateFlow(YearMonth.from(today))
    val month: StateFlow<YearMonth> = _month.asStateFlow()
    private val _selected = MutableStateFlow(today)
    val selected: StateFlow<LocalDate> = _selected.asStateFlow()

    init { repository.syncHistoricalData() }

    fun previousMonth() = moveTo(_month.value.minusMonths(1))
    fun nextMonth() = moveTo(_month.value.plusMonths(1))
    fun select(date: LocalDate) { _selected.value = date }

    private fun moveTo(month: YearMonth) {
        _month.value = month
        _selected.value = month.atDay(1)
    }

    /** True only when real active time was recorded for the date. */
    fun trained(date: LocalDate): Boolean = history.record(date).activeSeconds > 0L

    fun monthSummary(month: YearMonth): MonthSummary = MonthSummary(
        trainedDays = history.trainedDays(month),
        activeMinutes = history.totalActiveSeconds(month) / 60,
        kcal = history.month(month).sumOf { ledger.estimate(it.date) }.toInt()
    )

    fun daySummary(date: LocalDate): DaySummary = DaySummary(
        date = date,
        activeMinutes = history.record(date).activeMinutes,
        kcal = ledger.estimate(date).toInt()
    )
}
