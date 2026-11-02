package com.move60.workout

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

/** Stages only change on an explicit user action. Active time excludes pauses and may exceed 60 minutes. */
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
    var accumulatedActiveSeconds: Int = 0
        private set
    var stageElapsedSeconds: Int = 0
        private set
    private var activeCheckpointAt: Long = 0L
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
            accumulatedActiveSeconds = o.optInt("activeSeconds", o.optInt("elapsed", 0)).coerceAtLeast(0)
            stageElapsedSeconds = o.optInt("stageElapsedSeconds", stageSeconds() - o.optInt("remaining", stageSeconds())).coerceAtLeast(0)
            remaining = (stageSeconds() - stageElapsedSeconds).coerceAtLeast(0)
            deadlineAt = o.optLong("deadlineAt", 0L)
            running = o.optBoolean("running", false) && deadlineAt > 0L
            finished = o.optBoolean("finished", false)
            sessionStartedAt = o.optLong("sessionStartedAt", 0L)
            activeCheckpointAt = if (running) o.optLong("activeCheckpointAt", System.currentTimeMillis()) else 0L
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
        accumulatedActiveSeconds = 0
        stageElapsedSeconds = 0
        activeCheckpointAt = 0L
        checked.clear()
        completed.clear()
    }
    private fun stageSeconds(s: Int = stage) = routine.stages[s].minutes * 60
    fun save() {
        val checkedArray = JSONArray(); checked.sorted().forEach { checkedArray.put(it) }
        val completedArray = JSONArray(); completed.sorted().forEach { completedArray.put(it) }
        val state = JSONObject().put("stage", stage).put("remaining", remaining)
            .put("deadlineAt", deadlineAt).put("running", running).put("finished", finished)
            .put("sessionStartedAt", sessionStartedAt).put("activeSeconds", accumulatedActiveSeconds)
            .put("stageElapsedSeconds", stageElapsedSeconds).put("activeCheckpointAt", activeCheckpointAt)
            .put("checked", checkedArray)
            .put("completed", completedArray)
        preferences.edit().putString(key(), state.toString()).apply()
    }
    fun reconcile(now: Long = System.currentTimeMillis()): Boolean {
        if (!running || finished) return false
        if (activeCheckpointAt <= 0L) activeCheckpointAt = now
        val delta = ((now - activeCheckpointAt) / 1000L).coerceAtLeast(0L).toInt()
        if (delta <= 0) return false
        accumulatedActiveSeconds = (accumulatedActiveSeconds + delta).coerceAtMost(Int.MAX_VALUE)
        stageElapsedSeconds = (stageElapsedSeconds + delta).coerceAtMost(Int.MAX_VALUE)
        activeCheckpointAt += delta * 1000L
        remaining = (stageSeconds() - stageElapsedSeconds).coerceAtLeast(0)
        deadlineAt = if (remaining > 0) now + remaining * 1000L else now
        save()
        return true
    }
    fun start() {
        if (finished) resetFields()
        if (running) return
        running = true
        val now = System.currentTimeMillis()
        deadlineAt = now + remaining * 1000L
        activeCheckpointAt = now
        if (sessionStartedAt == 0L) sessionStartedAt = now
        save()
    }
    fun pause() {
        reconcile()
        if (running) {
            remaining = ceil((deadlineAt - System.currentTimeMillis()) / 1000.0).toInt().coerceIn(0, stageSeconds())
            deadlineAt = 0L
            running = false
            activeCheckpointAt = 0L
        }
        save()
    }
    fun resetStage() {
        pause()
        remaining = stageSeconds()
        stageElapsedSeconds = 0
        finished = false
        completed.remove(stage)
        save()
    }
    fun jumpTo(target: Int) {
        if (target !in 0..3) return
        val wasRunning = running
        reconcile()
        stage = target
        remaining = stageSeconds()
        stageElapsedSeconds = 0
        finished = false
        running = wasRunning
        if (wasRunning) {
            activeCheckpointAt = System.currentTimeMillis()
            deadlineAt = activeCheckpointAt + remaining * 1000L
        } else deadlineAt = 0L
        save()
    }
    fun nextStage() {
        if (stage >= 3) {
            reconcile()
            completed += stage
            running = false
            finished = true
            deadlineAt = 0L
            activeCheckpointAt = 0L
            save()
            return
        }
        completed += stage
        jumpTo(stage + 1)
    }
    fun resetAll() { pause(); resetFields(); save() }
    fun toggleChecked(key: String, value: Boolean) { if (value) checked.add(key) else checked.remove(key); save() }
    fun elapsed(): Int {
        if (running) reconcile()
        return accumulatedActiveSeconds
    }
    fun phase(): Pair<Int, WorkoutTask>? {
        if (stage != 1 || finished) return null
        var elapsed = stageElapsedSeconds.coerceAtMost(stageSeconds(1) - 1)
        for ((index, task) in routine.stages[1].tasks.withIndex()) {
            if (elapsed < task.seconds) return index to task
            elapsed -= task.seconds
        }
        return null
    }
    fun phaseSecondsRemaining(): Int {
        if (stage != 1) return remaining
        var elapsed = stageElapsedSeconds.coerceAtMost(stageSeconds(1) - 1)
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
            .put("sessionStartedAt", sessionStartedAt)
            .put("day", day).put("stage", stage).put("remaining", remaining)
            .put("deadlineAt", deadlineAt).put("activeSeconds", accumulatedActiveSeconds)
            .put("stageElapsedSeconds", stageElapsedSeconds)
            .put("cardioMachine", routine.stages[1].name).put("stageSeconds", times).put("phases", phases).toString()
    }
}

