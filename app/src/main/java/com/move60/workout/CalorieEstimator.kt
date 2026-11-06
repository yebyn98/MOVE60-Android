package com.move60.workout

import kotlin.math.roundToInt

/** Estimates gross exercise energy from MET values; not a measured calorie count. */
object CalorieEstimator {
    fun cardioMet(machine: String, rpe: String): Double {
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

    fun snapshotMet(state: org.json.JSONObject, now: Long): Double = when (state.optInt("stage")) {
        0 -> 2.5
        2 -> 3.5
        else -> {
            val stageIndex = state.optInt("stage")
            val phases = state.optJSONArray(if (stageIndex == 3) "phases3" else "phases")
            val duration = state.optJSONArray("stageSeconds")?.optInt(stageIndex, 1800) ?: 1800
            val remaining = if (state.optBoolean("running")) ((state.optLong("deadlineAt") - now) / 1000).toInt().coerceAtLeast(0) else state.optInt("remaining")
            var elapsed = (duration - remaining).coerceAtLeast(0)
            var result = 4.5
            if (phases != null) for (i in 0 until phases.length()) {
                val phase = phases.optJSONObject(i) ?: continue
                if (elapsed < phase.optInt("seconds", 60) || i == phases.length() - 1) {
                    val name = phase.optString("name")
                    if (phase.optString("kind") == "마무리 스트레칭") { result = 2.3; break }
                    val machines = listOf("스텝밀", "실내 사이클", "일립티컬", "로잉머신", "평지 걷기")
                    val machine = machines.firstOrNull { name.contains(it) }
                        ?: phase.optString("kind").takeIf { it in machines }
                        ?: state.optString("cardioMachine", "실내 사이클")
                    result = cardioMet(machine, phase.optString("rpe", "5"))
                    break
                }
                elapsed -= phase.optInt("seconds", 60)
            }
            result
        }
    }

    fun stageKcal(stage: WorkoutStage, weightKg: Double): Int {
        val metMinutes = when (stage.name) {
            "준비운동" -> stage.minutes * 2.5
            "근력운동" -> stage.minutes * 3.5
            else -> stage.tasks.sumOf { (it.seconds / 60.0) *
                (if (it.kind == "마무리 스트레칭") 2.3 else cardioMet(it.kind, it.rpe)) }
        }
        return (metMinutes * 3.5 * weightKg / 200.0).roundToInt()
    }

    fun totalKcal(routine: DailyRoutine, weightKg: Double): Int =
        routine.stages.sumOf { stageKcal(it, weightKg) }
}

