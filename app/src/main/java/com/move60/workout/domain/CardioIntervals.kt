package com.move60.workout.domain

/**
 * Phase 3-2 step 1 (docs/v71): pure calculation of the real cardio intensity intervals. No database, no Android.
 *
 * PO decisions of 2026-11-17: the input sheet asks for the metrics the equipment actually has (both level and incline where it has
 * both, only the one it has otherwise); values are numbers entered by the user; only the value can be corrected afterwards, never
 * the time; saving happens only on an explicit save.
 *
 * Rules (policy P-08, cases A-E):
 *  - Times are ACTIVE elapsed seconds, so a pause is excluded without any extra handling.
 *  - Before the first saved input, and after an input that omits a metric, the interval is "not recorded". Nothing is estimated,
 *    carried back, or filled in from the target intensity.
 *  - A correction never touches the original input: it is applied on top, the latest correction per input and metric wins.
 */
enum class CardioMetric(val key: String, val label: String) {
    LEVEL("level", "레벨"), INCLINE("incline", "경사");

    companion object { fun fromKey(key: String): CardioMetric? = entries.firstOrNull { it.key == key } }
}

object CardioMetrics {
    /** The metrics the user is asked for, per cardio equipment key (`Equipment.name`). Unknown equipment asks for nothing. */
    fun forEquipment(equipmentKey: String): List<CardioMetric> = when (equipmentKey) {
        "INDOOR_CYCLE" -> listOf(CardioMetric.LEVEL)
        "ELLIPTICAL" -> listOf(CardioMetric.LEVEL, CardioMetric.INCLINE)
        else -> emptyList()
    }

    /** Parses what the user typed: a finite, non-negative number. Anything else is rejected (null) instead of guessed. */
    fun parse(text: String): Double? {
        val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return if (value.isFinite() && value >= 0.0) value else null
    }
}

/** One saved input event: at [atActiveSeconds] the user entered [values] (original text per metric). Never edited. */
data class CardioInput(val inputIndex: Int, val atActiveSeconds: Int, val values: Map<CardioMetric, String>) {
    init {
        require(inputIndex >= 0) { "inputIndex" }
        require(atActiveSeconds >= 0) { "atActiveSeconds" }
    }
}

/** A later value correction: [newValue] replaces [metric] of input [inputIndex] when read; [createdAt] orders corrections. */
data class CardioCorrection(val inputIndex: Int, val metric: CardioMetric, val newValue: String, val createdAt: Long)

/** The active window of one cardio stage, in active elapsed seconds. */
data class CardioWindow(val startSeconds: Int, val endSeconds: Int) {
    init { require(startSeconds >= 0 && endSeconds >= startSeconds) { "window" } }
    val seconds: Int get() = endSeconds - startSeconds
}

/** [values] is null for a not-recorded interval; a metric missing from a recorded interval is not recorded either. */
data class CardioInterval(val startSeconds: Int, val endSeconds: Int, val inputIndex: Int?, val values: Map<CardioMetric, String>?) {
    val seconds: Int get() = endSeconds - startSeconds
    val recorded: Boolean get() = values != null
}

data class CardioIntervalSummary(val intervals: List<CardioInterval>) {
    val recordedSeconds: Int get() = intervals.filter { it.recorded }.sumOf { it.seconds }
    val unrecordedSeconds: Int get() = intervals.filterNot { it.recorded }.sumOf { it.seconds }
    /** Recorded + not recorded is always the stage's active time (AC-P08-04). */
    val totalSeconds: Int get() = recordedSeconds + unrecordedSeconds
}

object CardioIntervals {
    /** The inputs as they read now: originals with the latest correction per input and metric applied. The originals are untouched. */
    fun effective(inputs: List<CardioInput>, corrections: List<CardioCorrection>): List<CardioInput> {
        val latest = corrections.groupBy { it.inputIndex to it.metric }.mapValues { (_, list) -> list.maxByOrNull { it.createdAt }!! }
        return inputs.map { input ->
            val merged = input.values.toMutableMap()
            for (metric in input.values.keys) latest[input.inputIndex to metric]?.let { merged[metric] = it.newValue }
            input.copy(values = merged)
        }
    }

    /**
     * Intervals of one stage: first input to the next input (or the stage end); the part before the first input is not recorded.
     * Inputs outside the window are ignored. Inputs at the same second: the later input (higher index) wins.
     */
    fun compute(window: CardioWindow, inputs: List<CardioInput>): CardioIntervalSummary {
        val inside = inputs.filter { it.atActiveSeconds in window.startSeconds until window.endSeconds }
            .sortedWith(compareBy({ it.atActiveSeconds }, { it.inputIndex }))
            .groupBy { it.atActiveSeconds }.map { (_, same) -> same.last() }
        if (window.seconds == 0) return CardioIntervalSummary(emptyList())
        val out = ArrayList<CardioInterval>()
        val first = inside.firstOrNull()?.atActiveSeconds ?: window.endSeconds
        if (first > window.startSeconds) out += CardioInterval(window.startSeconds, first, null, null)
        inside.forEachIndexed { i, input ->
            val end = inside.getOrNull(i + 1)?.atActiveSeconds ?: window.endSeconds
            out += CardioInterval(input.atActiveSeconds, end, input.inputIndex, input.values)
        }
        return CardioIntervalSummary(out)
    }
}
