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
/** Five stages: 0 준비운동, 1 유산소 ①, 2 근력운동, 3 유산소 ②, 4 스트레칭. */
const val STAGE_COUNT = 5
const val LAST_STAGE = STAGE_COUNT - 1
/** Snapshot JSON key that carries the timed phases of a stage (null = untimed stage). */
fun phasesKeyForStage(stage: Int): String? = when (stage) { 1 -> "phases"; 3 -> "phases3"; 4 -> "phases4"; else -> null }
data class DailyRoutine(
    val day: Int, val name: String, val short: String, val focus: String,
    val desc: String, val tag: String, val stages: List<WorkoutStage>
)

class WorkoutData(private val context: Context) {
    companion object {
        const val CARDIO_FIRST = "실내 사이클"
        const val CARDIO_SECOND = "일립티컬"
        const val ADJUSTABLE_MINUTES = 50
        const val STEP_MINUTES = 5
        const val MIN_CARDIO = 5
        const val MIN_STRENGTH = 10
        fun isValidSplit(first: Int, second: Int): Boolean =
            first % STEP_MINUTES == 0 && second % STEP_MINUTES == 0 &&
                first >= MIN_CARDIO && second >= MIN_CARDIO &&
                ADJUSTABLE_MINUTES - first - second >= MIN_STRENGTH
    }
    private val choices = ExercisePreferences(context)
    private val prefs = context.getSharedPreferences("move60_duration_v1", Context.MODE_PRIVATE)
    /** 유산소 ①/② are set individually (5-minute steps); 근력 takes the rest of the 50 adjustable minutes. */
    fun setCardioSplit(first: Int, second: Int) {
        require(isValidSplit(first, second)) { "invalid split $first/$second" }
        prefs.edit().putInt("cardio_first_minutes", first).putInt("cardio_second_minutes", second)
            .putInt("cardio_minutes", first + second).apply()
    }
    /** Upgrade path: older builds stored one total (first block = half, rounded to 5). */
    fun cardioFirstMinutes(): Int =
        if (prefs.contains("cardio_first_minutes")) prefs.getInt("cardio_first_minutes", 15)
        else (legacyCardioTotal() / 2 + 2) / 5 * 5
    fun cardioSecondMinutes(): Int =
        if (prefs.contains("cardio_second_minutes")) prefs.getInt("cardio_second_minutes", 15)
        else legacyCardioTotal() - cardioFirstMinutes()
    fun cardioMinutes(): Int = cardioFirstMinutes() + cardioSecondMinutes()
    fun strengthMinutes(): Int = ADJUSTABLE_MINUTES - cardioMinutes()
    private fun legacyCardioTotal(): Int = prefs.getInt("cardio_minutes", 30)
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
        val phases = mutableListOf<WorkoutTask>()
        val phaseData = obj.getJSONArray("cyclePhases")
        // The interval program is written for the bike; the elliptical block re-words it in forMachine().
        fun addPhase(phase: JSONObject, round: String = "") {
            val minutes = phase.getInt("m")
            val rpe = phase.getString("rpe")
            val note = phase.getString("detail")
            val name = phase.getString("label")
            phases += WorkoutTask(
                name = if (round.isNotEmpty()) "$round · $name" else name,
                detail = "${minutes}분 · RPE $rpe · $note", kind = CARDIO_FIRST,
                seconds = minutes * 60, rpe = rpe, description = note, phaseIndex = phases.size
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
        val cardioTarget = (cardioFirstMinutes() + cardioSecondMinutes()) * 60
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
            phases += WorkoutTask("추가 유산소 · 편안한 페이스", "${extra / 60}분 · RPE 4", CARDIO_FIRST, seconds = extra, rpe = "4", description = "무리하지 않는 일정한 속도", phaseIndex = phases.size)
        }
        // 유산소 - 근력 - 유산소: cardio is split around strength, one machine per block (bike first, elliptical second).
        val firstSeconds = cardioFirstMinutes() * 60
        val firstBlock = mutableListOf<WorkoutTask>()
        val secondBlock = mutableListOf<WorkoutTask>()
        var cursor = 0
        for (phase in phases) {
            val head = (firstSeconds - cursor).coerceIn(0, phase.seconds)
            fun part(seconds: Int) = phase.copy(seconds = seconds,
                detail = phase.detail.replace(Regex("^\\d+분"), "${seconds / 60}분"))
            if (head > 0) firstBlock += part(head)
            if (phase.seconds - head > 0) secondBlock += part(phase.seconds - head)
            cursor += phase.seconds
        }
        // Round labels restart in each block, so a block never ends at "2/5" while the next one carries on with "3/5".
        val cardioFirst = renumberRounds(firstBlock).mapIndexed { i, t -> forMachine(t, CARDIO_FIRST).copy(phaseIndex = i) }
        val cardioSecond = renumberRounds(secondBlock).mapIndexed { i, t -> forMachine(t, CARDIO_SECOND).copy(phaseIndex = i) }
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
            WorkoutTask(name, note, "마무리 스트레칭", seconds = seconds, description = note, phaseIndex = i)
        }
        val firstMinutes = cardioFirst.sumOf { it.seconds } / 60
        val secondMinutes = cardioSecond.sumOf { it.seconds } / 60
        val stages = listOf(
            WorkoutStage("준비운동", "관절 가동성 · 워밍업", obj.getInt("warm"), warm),
            WorkoutStage("유산소 ① · $CARDIO_FIRST", "실시간 구간 체크리스트", firstMinutes, cardioFirst),
            WorkoutStage("근력운동", when (focus) { "upper" -> "상체 · 가슴/등/어깨/팔"; "lower" -> "하체 · 허벅지/둔근/종아리"; else -> "이번 주 랜덤 운동 · 세트/횟수" }, strengthMinutes(), strength),
            WorkoutStage("유산소 ② · $CARDIO_SECOND", "실시간 구간 체크리스트", secondMinutes, cardioSecond),
            WorkoutStage("스트레칭", "마무리 스트레칭", stretchMinutes, finish)
        )
        require(stages.sumOf { it.minutes } == 60) { "Daily routine must be exactly 60 minutes" }
        require(cardioFirst.sumOf { it.seconds } + cardioSecond.sumOf { it.seconds } == cardioMinutes() * 60) { "Cardio phase mismatch" }
        require(stages[3].tasks.sumOf { it.seconds } == stages[3].minutes * 60) { "Second cardio phase mismatch" }
        require(stages[4].tasks.sumOf { it.seconds } == stages[4].minutes * 60) { "Stretch phase mismatch" }

        return DailyRoutine(d, obj.getString("day"), obj.getString("short"), when (focus) { "upper" -> "오늘 선택 · 상체"; "lower" -> "오늘 선택 · 하체"; else -> obj.getString("focus") }, obj.getString("desc"), obj.getString("tag"), stages)
    }

    /** Re-words the bike program for the elliptical (no pedals/resistance); the bike keeps the original text. */
    private fun forMachine(task: WorkoutTask, machine: String): WorkoutTask {
        if (machine == CARDIO_FIRST) return task.copy(kind = machine)
        fun w(text: String) = text.replace("페달링", "운동").replace("페달", "동작").replace("저항", "강도")
        return task.copy(name = w(task.name), detail = w(task.detail), description = w(task.description), kind = machine)
    }

    /** "3/5회차" -> "1/3회차": numbers the rounds that actually fall into this block. */
    private fun renumberRounds(block: List<WorkoutTask>): List<WorkoutTask> {
        val label = Regex("^(\\d+)/\\d+회차 · ")
        val rounds = block.mapNotNull { label.find(it.name)?.groupValues?.get(1)?.toInt() }.distinct()
        return block.map { task ->
            val match = label.find(task.name) ?: return@map task
            val index = rounds.indexOf(match.groupValues[1].toInt()) + 1
            task.copy(name = "$index/${rounds.size}회차 · " + task.name.removePrefix(match.value))
        }
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


