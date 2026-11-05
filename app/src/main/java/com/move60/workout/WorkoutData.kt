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

class WorkoutData(private val context: Context) {
    private fun contextPreferencesCondition() = context.getSharedPreferences("preferences", Context.MODE_PRIVATE).getString("condition", "보통")
    private val choices = ExercisePreferences(context)
    private val prefs = context.getSharedPreferences("move60_duration_v1", Context.MODE_PRIVATE)
    fun setCardioMinutes(minutes: Int) { require(minutes in 20..40 && minutes % 5 == 0); prefs.edit().putInt("cardio_minutes", minutes).apply() }
    fun cardioMinutes(): Int = prefs.getInt("cardio_minutes", 30)
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

    private fun strengthPool(day: Int, focus: String?): JSONArray = when (focus) {
        "upper" -> JSONArray("""[["chestPress","dbChestPress","inclinePushup"],["latPulldown","seatedRow","cableRow"],["facePull","lateralRaise"],["tricepPushdown","bicepCurl"]]""")
        "lower" -> JSONArray("""[["legPress","gobletSquat","boxSquat"],["hipThrust","cableKickback","hipAbduction"],["legCurl","rdl"],["calfRaise","stepUp","reverseLunge"]]""")
        else -> pools.getJSONArray(day.toString())
    }
    fun alternatives(day: Int, slot: Int, focus: String? = null): List<Pair<String, String>> =
        strengthPool(day, focus).getJSONArray(slot).strings().map { id ->
            id to exercise.getJSONArray(id).getString(0)
        }

    fun replaceExercise(day: Int, slot: Int, id: String, focus: String? = null): Boolean {
        if (alternatives(day, slot, focus).none { it.first == id }) return false
        choices.setChoice(day, slot, id)
        return true
    }

    fun routine(day: Int, week: Long = weekOrdinal(), focus: String? = null): DailyRoutine {
        val d = day.coerceIn(1, 5)
        val obj = plan.getJSONObject(d.toString())
        val warm = obj.getJSONArray("warmTasks").pairs().map { (name, note) -> WorkoutTask(name, note, "준비운동") }
        val cardioOptions = obj.optJSONArray("cardioOptions")?.strings() ?: listOf("실내 사이클")
        val cardioMachine = cardioOptions[Math.floorMod(week + d, cardioOptions.size.toLong()).toInt()]
        val phases = mutableListOf<WorkoutTask>()
        val phaseData = obj.getJSONArray("cyclePhases")
        fun addPhase(phase: JSONObject, round: String = "") {
            val index = phases.size
            val originalName = phase.getString("label")
            val name = if (cardioMachine == "실내 사이클") originalName else originalName.replace("페달링", "운동").replace("페달", "동작")
            val rpe = phase.getString("rpe")
            val note = if (cardioMachine == "실내 사이클") phase.getString("detail") else phase.getString("detail").replace("저항", "강도").replace("페달", "동작")
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
        val cardioTarget = cardioMinutes() * 60
        val originalCardioSeconds = phases.sumOf { it.seconds }
        if (originalCardioSeconds > cardioTarget) {
            var remainingSeconds = cardioTarget
            val trimmed = mutableListOf<WorkoutTask>()
            for (phase in phases) {
                if (remainingSeconds <= 0) break
                val seconds = minOf(phase.seconds, remainingSeconds)
                trimmed += phase.copy(seconds = seconds, detail = phase.detail.replace(Regex("^\\d+분"), "${seconds / 60}분"))
                remainingSeconds -= seconds
            }
            phases.clear()
            phases.addAll(trimmed)
        } else if (originalCardioSeconds < cardioTarget) {
            val extra = cardioTarget - originalCardioSeconds
            phases += WorkoutTask("추가 유산소 · 편안한 페이스", "${extra / 60}분 · RPE 4", cardioMachine, seconds = extra, rpe = "4", description = "무리하지 않는 일정한 속도", phaseIndex = phases.size)
        }
        // Preserve the cardio budget while capping stair climbing to 10–15 minutes.
        if (cardioMachine == "스텝밀") {
            val stairMinutes = if (contextPreferencesCondition() == "피곤") 10 else 15
            val stairs = minOf(stairMinutes, cardioMinutes())
            val cycle = cardioMinutes() - stairs
            phases.clear()
            fun segment(machine: String, label: String, minutes: Int, rpe: String) {
                if (minutes <= 0) return
                phases += WorkoutTask("$machine · $label", "${minutes}분 · RPE $rpe", machine,
                    seconds = minutes * 60, rpe = rpe, description = "편안한 자세와 일정한 호흡 유지", phaseIndex = phases.size)
            }
            segment("스텝밀", "도입", 2, "3")
            segment("스텝밀", "유지", stairs - 4, "4~5")
            segment("스텝밀", "정리", 2, "3")
            segment("실내 사이클", "전환 · 도입", minOf(3, cycle), "3")
            segment("실내 사이클", "유지", cycle - 5, "4~5")
            segment("실내 사이클", "정리", minOf(2, (cycle-3).coerceAtLeast(0)), "3")
        }
        // 유산소 - 근력 - 유산소: cardio is split around strength; the second block is the cool-down.
        val firstCardioMinutes = (cardioMinutes() / 2 + 2) / 5 * 5
        val firstSeconds = firstCardioMinutes.coerceAtLeast(5) * 60
        val cardioFirst = mutableListOf<WorkoutTask>()
        val cardioSecond = mutableListOf<WorkoutTask>()
        var cursor = 0
        for (phase in phases) {
            val head = (firstSeconds - cursor).coerceIn(0, phase.seconds)
            fun part(seconds: Int) = phase.copy(seconds = seconds,
                detail = phase.detail.replace(Regex("^\\d+분"), "${seconds / 60}분"))
            if (head > 0) cardioFirst += part(head).copy(phaseIndex = cardioFirst.size)
            if (phase.seconds - head > 0) cardioSecond += part(phase.seconds - head).copy(phaseIndex = cardioSecond.size)
            cursor += phase.seconds
        }
        val pool = strengthPool(d, focus)
        val repsForDay = reps.getJSONArray(d.toString())
        val used = mutableSetOf<String>()
        val strength = (0 until pool.length()).map { slot ->
            val options = pool.getJSONArray(slot).strings()
            val hash = fnv1a("exercise-choice-v4-$d-$slot")
            val base = (hash + week)
            var picked = options[Math.floorMod(base, options.size.toLong()).toInt()]
            for (j in 1 until options.size) {
                if (picked !in used) break
                picked = options[Math.floorMod(base + j, options.size.toLong()).toInt()]
            }
            val saved = choices.choice(d, slot)
            if (saved != null && saved in options) picked = saved
            used += picked
            val prescription = if (focus == null) repsForDay.getString(slot)
                else if (picked in listOf("stepUp", "reverseLunge", "cableKickback")) "2세트 × 좌우 각 10회 · 휴식 45초"
                else "2세트 × 12회 · 휴식 45초"
            WorkoutTask(exercise.getJSONArray(picked).getString(0), prescription, "근력운동", id = picked)
        }
        val stretchMinutes = obj.getInt("stretch")
        val stretchPairs = stretches.pairs()
        val perStretch = stretchMinutes * 60 / stretchPairs.size
        val finish = stretchPairs.mapIndexed { i, (name, note) ->
            val seconds = if (i == stretchPairs.lastIndex) stretchMinutes * 60 - perStretch * i else perStretch
            WorkoutTask(name, note, "마무리 스트레칭", seconds = seconds, description = note, phaseIndex = cardioSecond.size + i)
        }
        val machineName = if (cardioMachine == "스텝밀") "스텝밀 + 실내 사이클" else cardioMachine
        val firstMinutes = cardioFirst.sumOf { it.seconds } / 60
        val secondMinutes = cardioSecond.sumOf { it.seconds } / 60
        val stages = listOf(
            WorkoutStage("준비운동", "관절 가동성 · 워밍업", obj.getInt("warm"), warm),
            WorkoutStage(machineName, "유산소 ① · 실시간 구간 체크리스트", firstMinutes, cardioFirst),
            WorkoutStage("근력운동", when (focus) { "upper" -> "상체 · 가슴/등/어깨/팔"; "lower" -> "하체 · 허벅지/둔근/종아리"; else -> "이번 주 랜덤 운동 · 세트/횟수" }, 50 - cardioMinutes(), strength),
            WorkoutStage("유산소 ② · 마무리", "유산소 정리 + 스트레칭", secondMinutes + stretchMinutes, cardioSecond + finish)
        )
        require(stages.sumOf { it.minutes } == 60) { "Daily routine must be exactly 60 minutes" }
        require(cardioFirst.sumOf { it.seconds } + cardioSecond.sumOf { it.seconds } == cardioMinutes() * 60) { "Cardio phase mismatch" }
        require(stages[3].tasks.sumOf { it.seconds } == stages[3].minutes * 60) { "Final block phase mismatch" }

        return DailyRoutine(d, obj.getString("day"), obj.getString("short"), when (focus) { "upper" -> "오늘 선택 · 상체"; "lower" -> "오늘 선택 · 하체"; else -> obj.getString("focus") }, obj.getString("desc"), obj.getString("tag"), stages)
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


