package com.move60.workout.architecture

import com.move60.workout.domain.CardioCorrection
import com.move60.workout.domain.CardioInput
import com.move60.workout.domain.CardioIntervals
import com.move60.workout.domain.CardioMetric
import com.move60.workout.domain.CardioWindow

/**
 * Phase 3-2 step 4 (docs/v71 §5): the real cardio intensity intervals of one stored session, for the record detail.
 *
 * Nothing here writes. Intervals are calculated (never stored) from the saved input events of the cardio stage on top of the
 * untouched originals, with the latest correction per input and metric applied. Times are the stage's ACTIVE seconds
 * (`session_cardio_inputs.atActiveSeconds` holds the stage active time, so the window is `[0, stage actual seconds]`).
 */
data class CardioIntervalRow(
    val startSeconds: Int,
    val endSeconds: Int,
    /** null = not recorded (before the first input). Nothing is estimated or filled in. */
    val values: Map<CardioMetric, String>?,
    /** `Equipment` key of the input that started this interval; null for a not-recorded interval. */
    val equipmentKey: String?
) {
    val seconds: Int get() = endSeconds - startSeconds
    val recorded: Boolean get() = values != null
}

data class CardioStageRecord(
    val stageIndex: Int,
    val rows: List<CardioIntervalRow>,
    /** More than one machine was used in this stage: rows then name their machine so interleaved inputs are not mixed up. */
    val multipleEquipment: Boolean
)

object CardioStageRecords {
    /**
     * Null when the session has no saved cardio input for the stage (the screen keeps saying "수치 미기록").
     * [inputs] and [adjustments] may contain rows of other stages and non-cardio fields; they are ignored.
     */
    fun build(
        stageIndex: Int,
        stageActiveSeconds: Long,
        inputs: List<SessionCardioInputEntity>,
        adjustments: List<SessionAdjustmentEntity>
    ): CardioStageRecord? {
        val stageRows = inputs.filter { it.stageIndex == stageIndex }
        if (stageRows.isEmpty()) return null
        val equipmentOf = HashMap<Int, String>()
        val parsed = stageRows.groupBy { it.inputIndex }.mapNotNull { (index, rows) ->
            val values = LinkedHashMap<CardioMetric, String>()
            for (r in rows.sortedBy { it.metricKey }) CardioMetric.fromKey(r.metricKey)?.let { values[it] = r.valueText }
            if (values.isEmpty()) return@mapNotNull null
            equipmentOf[index] = rows.first().equipmentKey
            CardioInput(index, rows.minOf { it.atActiveSeconds }, values)
        }
        if (parsed.isEmpty()) return null
        // Corrections are replayed in saved order; adjustmentId (strictly increasing) is the order key, so two corrections of the
        // same value saved within one millisecond still resolve to the later one.
        val corrections = adjustments.mapNotNull { a ->
            val f = CorrectionField.parseCardio(a.field) ?: return@mapNotNull null
            val new = a.newValue ?: return@mapNotNull null
            CardioCorrection(f.inputIndex, f.metric, new, a.adjustmentId)
        }
        val effective = CardioIntervals.effective(parsed, corrections)
        val window = CardioWindow(0, stageActiveSeconds.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
        val rows = CardioIntervals.compute(window, effective).intervals.map {
            CardioIntervalRow(it.startSeconds, it.endSeconds, it.values, it.inputIndex?.let(equipmentOf::get))
        }
        if (rows.none { it.recorded }) return null
        return CardioStageRecord(stageIndex, rows, rows.mapNotNull { it.equipmentKey }.distinct().size > 1)
    }
}
