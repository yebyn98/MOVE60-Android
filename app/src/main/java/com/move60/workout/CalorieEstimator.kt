package com.move60.workout

import kotlin.math.roundToInt

/** Estimates gross exercise energy from MET values; not a measured calorie count. */
object CalorieEstimator {
    private fun cardioMet(machine: String, rpe: String): Double {
        val midpoint = Regex("\\d+").findAll(rpe).map { it.value.toDouble() }.toList().average().takeIf { !it.isNaN() } ?: 5.0
        val base = when (machine) {
            "로잉머신" -> 3.5
            "스텝밀" -> 4.5
            "일립티컬" -> 3.5
            "평지 걷기" -> 2.5
            else -> 3.0
        }
        return (base + (midpoint - 3.0) * 0.65).coerceIn(2.0, 9.0)
    }

    fun stageKcal(stage: WorkoutStage, weightKg: Double): Int {
        val metMinutes = when (stage.name) {
            "준비운동" -> stage.minutes * 2.5
            "근력운동" -> stage.minutes * 3.5
            "마무리 스트레칭" -> stage.minutes * 2.3
            else -> stage.tasks.sumOf { (it.seconds / 60.0) * cardioMet(stage.name, it.rpe) }
        }
        return (metMinutes * 3.5 * weightKg / 200.0).roundToInt()
    }

    fun totalKcal(routine: DailyRoutine, weightKg: Double): Int =
        routine.stages.sumOf { stageKcal(it, weightKg) }
}
