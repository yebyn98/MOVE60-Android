package com.move60.workout.architecture

import java.time.LocalDate

/** One Monday-based week of the MY tab trend: dates with a record and their real minutes. */
data class WeekPoint(val weekStart: LocalDate, val days: Int, val minutes: Long)

data class Milestone(val id: String, val title: String, val supporting: String, val achieved: Boolean)

/** Pure calculations for the MY tab. Every number comes from stored records; nothing is estimated. */
object MyInsights {
    fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    /**
     * The last [weeks] weeks, oldest first, ending with the week of [today]. A date counts once; like the calendar month line,
     * a date with stored sessions uses the STORED seconds and a legacy-only date its v58 seconds (never added together).
     */
    fun weekly(today: LocalDate, weeks: Int, legacySeconds: Map<LocalDate, Long>, storedSeconds: Map<LocalDate, Long>): List<WeekPoint> {
        val current = weekStart(today)
        return (weeks - 1 downTo 0).map { back ->
            val start = current.minusWeeks(back.toLong())
            val dates = (0L..6L).map { start.plusDays(it) }.filter { it in legacySeconds || it in storedSeconds }
            WeekPoint(start, dates.size, dates.sumOf { storedSeconds[it] ?: legacySeconds[it] ?: 0L } / 60)
        }
    }

    /** Thresholds are the Figma 10 titles (첫 운동, 누적 20일, 첫 도전 완료). A milestone whose input could not be read is left out. */
    fun milestones(cumulativeDays: Int?, completedChallenges: Int?): List<Milestone> = buildList {
        if (cumulativeDays != null) {
            add(Milestone("first", "첫 운동", "처음 시작한 날을 기억해요", cumulativeDays >= 1))
            add(Milestone("days20", "누적 20일", "꾸준한 움직임을 쌓았어요", cumulativeDays >= 20))
        }
        if (completedChallenges != null) add(Milestone("challenge1", "첫 도전 완료", "내가 세운 목표를 이뤘어요", completedChallenges >= 1))
    }

    /** `4주 전`-style labels for a trend of [count] weeks (oldest first). */
    fun weekLabel(index: Int, count: Int): String = when (val back = count - 1 - index) {
        0 -> "이번 주"
        1 -> "지난 주"
        else -> "${back}주 전"
    }
}
