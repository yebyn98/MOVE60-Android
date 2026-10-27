package com.move60.workout

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

// All workout content and exercise descriptions are the unchanged curated data from the v5 app.
data class WorkoutTask(
    val name: String, val detail: String, val kind: String,
    val id: String = "", val seconds: Int = 0,
    val rpe: String = "", val description: String = "", val phaseIndex: Int = -1
)
data class WorkoutStage(val name: String, val subtitle: String, val minutes: Int, val tasks: List<WorkoutTask>)
data class MovementDetails(val area: String, val equipment: String, val steps: List<String>, val caution: String, val alternative: String)
data class DailyRoutine(
    val day: Int, val name: String, val short: String, val focus: String,
    val desc: String, val tag: String, val stages: List<WorkoutStage>
)

class WorkoutData(context: Context) {
    private val root = JSONObject(context.assets.open("workouts.json").bufferedReader().use { it.readText() })
    private val plan = root.getJSONObject("plan")
    private val exercise = root.getJSONObject("exercise")
    private val pools = root.getJSONObject("pools")
    private val reps = root.getJSONObject("reps")
    private val stretches = root.getJSONArray("baseStretch")
    private val korea = ZoneId.of("Asia/Seoul")

    fun localToday(): LocalDate = LocalDate.now(korea)
    fun weekMonday(): LocalDate = localToday().with(DayOfWeek.MONDAY)
    fun weekKey(): String = weekMonday().toString()
    fun weekOrdinal(): Long = Math.floorDiv(weekMonday().toEpochDay(), 7L)
    fun todayDay(): Int = localToday().dayOfWeek.value.coerceIn(1, 5)

    fun detail(id: String): MovementDetails? {
        if (id.isEmpty()) return null
        val ex = exercise.optJSONArray(id) ?: return null
        return MovementDetails(
            ex.getString(1), ex.getString(2), ex.getJSONArray(3).strings(),
            ex.getString(4), ex.getString(5)
        )
    }

    fun routine(day: Int): DailyRoutine {
        val d = day.coerceIn(1, 5)
        val obj = plan.getJSONObject(d.toString())
        val warm = obj.getJSONArray("warmTasks").pairs().map { (name, note) -> WorkoutTask(name, note, "준비운동") }
        val cardioOptions = obj.optJSONArray("cardioOptions")?.strings() ?: listOf("실내 사이클")
        val cardioMachine = cardioOptions[Math.floorMod(weekOrdinal() + d, cardioOptions.size.toLong()).toInt()]
        val phases = mutableListOf<WorkoutTask>()
        val phaseData = obj.getJSONArray("cyclePhases")
        fun addPhase(phase: JSONObject, round: String = "") {
            val index = phases.size
            val name = phase.getString("label")
            val rpe = phase.getString("rpe")
            val note = phase.getString("detail")
            val minutes = phase.getInt("m")
            phases += WorkoutTask(
                name = if (round.isNotEmpty()) "$round · $name" else name,
                detail = "${minutes}분 · RPE $rpe · $note", kind = cardioMachine,
                seconds = minutes * 60, rpe = rpe, description = note, phaseIndex = index
            )
        }
        for (i in 0 until phaseData.length()) {
            val p = phaseData.getJSONObject(i)
            if (p.has("repeat")) {
                val repeat = p.getInt("repeat")
                val parts = p.getJSONArray("parts")
                for (r in 1..repeat) for (j in 0 until parts.length()) addPhase(parts.getJSONObject(j), "$r/${repeat}회차")
            } else addPhase(p)
        }
        val pool = pools.getJSONArray(d.toString())
        val repsForDay = reps.getJSONArray(d.toString())
        val used = mutableSetOf<String>()
        val strength = (0 until pool.length()).map { slot ->
            val options = pool.getJSONArray(slot).strings()
            val hash = fnv1a("exercise-choice-v4-$d-$slot")
            val base = (hash + weekOrdinal())
            var picked = options[Math.floorMod(base, options.size.toLong()).toInt()]
            for (j in 1 until options.size) {
                if (picked !in used) break
                picked = options[Math.floorMod(base + j, options.size.toLong()).toInt()]
            }
            used += picked
            WorkoutTask(exercise.getJSONArray(picked).getString(0), repsForDay.getString(slot), "근력운동", id = picked)
        }
        val finish = stretches.pairs().map { (name, note) -> WorkoutTask(name, note, "마무리 스트레칭") }
        val stages = listOf(
            WorkoutStage("준비운동", "관절 가동성 · 워밍업", obj.getInt("warm"), warm),
            WorkoutStage(cardioMachine, "이번 주 유산소 · 실시간 구간 체크리스트", obj.getInt("cycle"), phases),
            WorkoutStage("근력운동", "이번 주 랜덤 운동 · 세트/횟수", obj.getInt("strength"), strength),
            WorkoutStage("마무리 스트레칭", "호흡 회복 · 유연성", obj.getInt("stretch"), finish)
        )
        require(stages.sumOf { it.minutes } == 60) { "Daily routine must be exactly 60 minutes" }
        require(phases.sumOf { it.seconds } == stages[1].minutes * 60) { "Cardio phase mismatch" }
        if (d == 2) require(phases.size == 18) { "Tuesday requires 18 phases" }
        return DailyRoutine(d, obj.getString("day"), obj.getString("short"), obj.getString("focus"), obj.getString("desc"), obj.getString("tag"), stages)
    }

    private fun fnv1a(s: String): Long {
        var hash = 0x811c9dc5u.toInt()
        for (c in s) {
            hash = (hash xor c.code) * 16777619 // 32-bit overflow mirrors Math.imul in the original app.
        }
        return hash.toLong() and 0xffffffffL
    }
}

private fun JSONArray.strings() = (0 until length()).map { getString(it) }
private fun JSONArray.pairs() = (0 until length()).map { getJSONArray(it).let { a -> a.getString(0) to a.getString(1) } }
