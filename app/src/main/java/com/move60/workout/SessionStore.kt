package com.move60.workout

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

/** State transitions use absolute wall-clock deadlines; app closure does not pause an active workout. */
class SessionStore(private val context: Context, private val data: WorkoutData) {
    private val preferences = context.getSharedPreferences("move60_native_sessions_v8", Context.MODE_PRIVATE)
    var day: Int = data.todayDay()
        private set
    var routine: DailyRoutine = data.routine(day)
        private set
    var stage: Int = 0
        private set
    var remaining: Int = routine.stages[0].minutes * 60
        private set
    var deadlineAt: Long = 0L
        private set
    var running = false
        private set
    var finished = false
        private set
    var sessionStartedAt: Long = 0L
        private set
    val checked = mutableSetOf<String>()
    val completed = mutableSetOf<Int>()

    init { load(day) }
    private fun key() = "move60_${data.weekKey()}_${day}"
    fun taskKey(stage: Int, index: Int) = "$stage-$index"
    fun refreshRoutine() { routine = data.routine(day); save() }
    fun setDay(next: Int) { if (next == day) return; save(); load(next.coerceIn(1, 5)) }

    private fun load(d: Int) {
        day = d
        routine = data.routine(day)
        checked.clear()
        completed.clear()
        val state = preferences.getString(key(), "")
        if (state.isNullOrEmpty()) {
            resetFields()
            return
        }
        try {
            val o = JSONObject(state)
            stage = o.optInt("stage", 0).coerceIn(0, 3)
            remaining = o.optInt("remaining", routine.stages[stage].minutes * 60).coerceIn(0, stageSeconds())
            deadlineAt = o.optLong("deadlineAt", 0L)
            running = o.optBoolean("running", false) && deadlineAt > 0L
            finished = o.optBoolean("finished", false)
            sessionStartedAt = o.optLong("sessionStartedAt", 0L)
            val tasks = o.optJSONArray("checked") ?: JSONArray()
            repeat(tasks.length()) { checked += tasks.optString(it) }
            val stages = o.optJSONArray("completed") ?: JSONArray()
            repeat(stages.length()) { completed += stages.optInt(it) }
            reconcile()
        } catch (e: Exception) { resetFields() }
    }
    private fun resetFields() {
        stage = 0
        remaining = stageSeconds()
        deadlineAt = 0
        running = false
        finished = false
        sessionStartedAt = 0
        checked.clear()
        completed.clear()
    }
    private fun stageSeconds(s: Int = stage) = routine.stages[s].minutes * 60
    fun save() {
        val checkedArray = JSONArray(); checked.sorted().forEach { checkedArray.put(it) }
        val completedArray = JSONArray(); completed.sorted().forEach { completedArray.put(it) }
        val state = JSONObject().put("stage", stage).put("remaining", remaining)
            .put("deadlineAt", deadlineAt).put("running", running).put("finished", finished)
            .put("sessionStartedAt", sessionStartedAt).put("checked", checkedArray)
            .put("completed", completedArray)
        preferences.edit().putString(key(), state.toString()).apply()
    }
    fun reconcile(now: Long = System.currentTimeMillis()): Boolean {
        if (!running || finished) return false
        var changed = false
        while (deadlineAt <= now && stage < 4) {
            changed = true
            completed += stage
            if (stage == 3) {
                running = false
                finished = true
                remaining = 0
                deadlineAt = 0L
                break
            }
            stage++
            deadlineAt += stageSeconds() * 1000L
        }
        if (running) remaining = ceil((deadlineAt - now).toDouble() / 1000.0).toInt().coerceIn(0, stageSeconds())
        if (changed) save()
        return changed
    }
    fun start() {
        if (finished) resetFields()
        if (running) return
        if (remaining <= 0) remaining = stageSeconds()
        running = true
        deadlineAt = System.currentTimeMillis() + remaining * 1000L
        if (sessionStartedAt == 0L) sessionStartedAt = System.currentTimeMillis()
        save()
    }
    fun pause() {
        reconcile()
        if (running) {
            remaining = ceil((deadlineAt - System.currentTimeMillis()) / 1000.0).toInt().coerceIn(0, stageSeconds())
            deadlineAt = 0L
            running = false
        }
        save()
    }
    fun resetStage() {
        pause()
        remaining = stageSeconds()
        finished = false
        completed.remove(stage)
        save()
    }
    fun jumpTo(target: Int) {
        if (target !in 0..3) return
        pause()
        stage = target
        remaining = stageSeconds()
        finished = false
        save()
    }
    fun nextStage() {
        if (stage >= 3) return
        completed += stage
        jumpTo(stage + 1)
    }
    fun resetAll() { pause(); resetFields(); save() }
    fun toggleChecked(key: String, value: Boolean) { if (value) checked.add(key) else checked.remove(key); save() }
    fun elapsed(): Int {
        if (finished) return 3600
        val earlier = (0 until stage).sumOf { stageSeconds(it) }
        return (earlier + stageSeconds() - remaining).coerceIn(0, 3600)
    }
    fun phase(): Pair<Int, WorkoutTask>? {
        if (stage != 1 || finished) return null
        var elapsed = stageSeconds(1) - remaining
        for ((index, task) in routine.stages[1].tasks.withIndex()) {
            if (elapsed < task.seconds) return index to task
            elapsed -= task.seconds
        }
        return null
    }
    fun phaseSecondsRemaining(): Int {
        if (stage != 1) return remaining
        var elapsed = stageSeconds(1) - remaining
        for (task in routine.stages[1].tasks) {
            if (elapsed < task.seconds) return task.seconds - elapsed
            elapsed -= task.seconds
        }
        return 0
    }
    fun snapshot(): String {
        val times = JSONArray()
        routine.stages.forEach { times.put(it.minutes * 60) }
        val phases = JSONArray()
        routine.stages[1].tasks.forEach { t ->
            phases.put(JSONObject().put("name", t.name).put("seconds", t.seconds)
                .put("rpe", t.rpe).put("description", t.description))
        }
        return JSONObject().put("running", running).put("finished", finished)
            .put("day", day).put("stage", stage).put("remaining", remaining)
            .put("deadlineAt", deadlineAt).put("stageSeconds", times).put("phases", phases).toString()
    }
}
