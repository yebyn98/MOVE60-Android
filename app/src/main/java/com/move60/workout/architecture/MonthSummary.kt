package com.move60.workout.architecture

import java.time.LocalDate

/** The calendar's month line: days with a record and real minutes. No calories (policy 7: hidden until a policy exists). */
data class MonthSummaryUi(val trainedDays: Int, val activeMinutes: Long)

object MonthSummaryCombiner {
    /**
     * v58 history and stored sessions both describe the same real workouts when they fall on one date, so they are never
     * added together (C-20261115-04): a date with stored sessions uses the STORED seconds, a legacy-only date uses its legacy
     * seconds. Every date that has either counts as one day.
     */
    fun combine(legacySeconds: Map<LocalDate, Long>, storedSeconds: Map<LocalDate, Long>): MonthSummaryUi {
        val dates = legacySeconds.keys + storedSeconds.keys
        val seconds = dates.sumOf { storedSeconds[it] ?: legacySeconds[it] ?: 0L }
        return MonthSummaryUi(dates.size, seconds / 60)
    }
}
