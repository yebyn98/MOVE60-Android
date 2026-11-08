package com.move60.workout.architecture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.move60.workout.EnergyLedger
import com.move60.workout.SetRecordStore
import com.move60.workout.StageLog
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

/** One workout stage of a recorded day: measured time and its share of the day's estimated kcal. */
data class StageDetail(val name: String, val seconds: Long, val kcal: Int, val note: String?)

/**
 * Selected-day record. [stages] is empty for days recorded before v43 (only the day total exists then),
 * so the UI never invents a per-exercise breakdown.
 */
data class DayDetail(
    val date: LocalDate,
    val focus: String?,
    val activeMinutes: Long,
    val kcal: Int,
    val stages: List<StageDetail>
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    repository: WorkoutRepository,
    private val history: WorkoutHistory,
    private val ledger: EnergyLedger,
    private val stageLog: StageLog,
    private val sets: SetRecordStore,
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

    /** True only when at least one full minute of real active time was recorded (0 min = not trained). */
    fun trained(date: LocalDate): Boolean = history.isTrained(date)

    /** "upper", "lower" or null (no body part recorded for that day). */
    fun focus(date: LocalDate): String? = history.focus(date)

    fun weekTrainedDays(): Int = history.weekTrainedDays(today)

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

    /** Day record with the per-stage split. Stage kcal share the ledger's day estimate by MET-weighted seconds. */
    fun dayDetail(date: LocalDate): DayDetail {
        val total = ledger.estimate(date)
        val entries = stageLog.entries(date)
        val weights = entries.sumOf { it.metSeconds }
        val completedSets = sets.completed(date).size
        val stages = entries.map { entry ->
            StageDetail(
                name = STAGE_NAMES[entry.stage],
                seconds = entry.seconds,
                kcal = if (weights > 0.0) (total * entry.metSeconds / weights).toInt() else 0,
                note = if (entry.stage == 2 && completedSets > 0) "완료 세트 ${completedSets}개" else null
            )
        }
        return DayDetail(date, history.focus(date), history.record(date).activeMinutes, total.toInt(), stages)
    }

    private companion object {
        val STAGE_NAMES = listOf("준비운동", "유산소 ①", "근력운동", "유산소 ②", "스트레칭")
    }
}
