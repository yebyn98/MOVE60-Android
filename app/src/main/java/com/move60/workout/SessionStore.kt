package com.move60.workout

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import com.move60.workout.architecture.timerSnapshot

/** Stages only change on an explicit user action. Active time excludes pauses and may exceed 60 minutes. */
class SessionStore(private val context: Context, private val data: WorkoutData, private val dateNow: () -> java.time.LocalDate = { data.localToday() }) {
    private val preferences = context.getSharedPreferences("move60_native_sessions_v8", Context.MODE_PRIVATE)
    var recordDate: java.time.LocalDate = run {
        val today = dateNow()
        val stored = try { java.time.LocalDate.parse(preferences.getString("active_date", "")) } catch (_: Exception) { today }
        val active = preferences.getInt("active_day", today.dayOfWeek.value).coerceIn(1,5)
        val old = try { JSONObject(preferences.getString("daily_"+stored+"_"+active, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        if (stored <= today && old.optBoolean("running") && !old.optBoolean("finished")) stored else today
    }
        private set
    var day: Int = if (preferences.getString("active_date", "") == recordDate.toString())
        preferences.getInt("active_day", data.todayDay()).coerceIn(1,5) else recordDate.dayOfWeek.value.coerceIn(1,5)
        private set
    var bodyFocus: String? = null
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
    /** The user confirmed ending a partly performed session (saved as PARTIAL). Distinct from [finished]. */
    var endedEarly = false
        private set
    var sessionStartedAt: Long = 0L
        private set
    var accumulatedActiveSeconds: Int = 0
        private set
    var stageElapsedSeconds: Int = 0
        private set
    private var activeCheckpointAt: Long = 0L
    /** Real active stretches [startEpochSec, endEpochSec) recorded by [reconcile]; their sum equals [accumulatedActiveSeconds]
     *  for sessions recorded by this version. Older stored states have none (never reconstructed or invented). */
    private val intervals = ArrayList<LongArray>()
    fun activeIntervals(): List<Pair<Long, Long>> = intervals.map { it[0] to it[1] }
    private fun recordInterval(startMs: Long, seconds: Int) {
        if (seconds <= 0) return
        val start = Math.floorDiv(startMs, 1000L)
        val last = intervals.lastOrNull()
        if (last != null && last[1] == start) last[1] = start + seconds else intervals.add(longArrayOf(start, start + seconds))
    }
    /**
     * Stage structure this session started with. Missing in stored JSON = a session from before the 4-stage policy,
     * which must finish as LEGACY_5 (never converted, reset or dropped). The engine only runs LEGACY_5 so far;
     * nothing starts a ROUTIVE_4 session yet.
     */
    var structure: com.move60.workout.domain.StructureVersion = com.move60.workout.domain.StructureVersion.LEGACY_5
        private set
    var controlVersion: Long = 0L
        private set
    var restDeadline: Long = 0L
        private set
    var restPaused: Int = 0
        private set
    var restId: Long = 0L
        private set
    private var loadedRaw = ""
    val checked = mutableSetOf<String>()
    val completed = mutableSetOf<Int>()

    init { load(day) }
    private fun key() = "daily_"+recordDate+"_"+day
    /** P0-2: stage meaning comes from the session's structure, never from a hard-coded index. */
    fun kindOf(index: Int): com.move60.workout.domain.StageKind? = com.move60.workout.domain.WorkoutStructures.kindAt(structure, index)
    val isStrengthStage: Boolean get() = kindOf(stage) == com.move60.workout.domain.StageKind.STRENGTH
    val lastStageIndex: Int get() = com.move60.workout.domain.WorkoutStructures.lastIndex(structure)
    /**
     * The timing/routine engine still implements the legacy 5-stage flow only. A slot marked with another structure
     * (e.g. written by a newer build) is kept untouched and is not advanced by this engine until the 4-stage engine ships.
     */
    val engineSupported: Boolean get() = structure == com.move60.workout.domain.StructureVersion.LEGACY_5
    /**
     * P1-1: a finished / early-ended session lives only in this single day slot until its Room commit is confirmed.
     * Before the slot is replaced (new start, reset, day switch, date rollover) its real intervals and completed sets are
     * copied to the durable outbox, which the idempotent sync retries. Never blocks or throws into the timer flow.
     */
    private fun archiveUncommitted() {
        if (!(finished || endedEarly) || sessionStartedAt <= 0L) return
        try {
            com.move60.workout.architecture.PendingSessionOutbox(context)
                .enqueue(timerSnapshot(SetRecordStore(context)))
        } catch (_: Throwable) { /* the slot itself is still intact on failure */ }
    }
    fun taskKey(stage: Int, index: Int) = "$stage-$index"
    fun refreshRoutine() { reloadIfChanged(); controlVersion++; routine = data.routine(day, focus = bodyFocus); save() }
    fun setDay(next: Int) { reloadIfChanged(); if (next == day) return; archiveUncommitted(); save(); load(next.coerceIn(1, 5)); save() }

    /** Freeze the selected split once activity begins; a finished session can start a fresh plan. */
    fun selectBodyFocus(focus: String): Boolean {
        require(focus == "upper" || focus == "lower")
        reloadIfChanged()
        if (!finished && (running || sessionStartedAt > 0L || accumulatedActiveSeconds > 0)) return false
        if (bodyFocus == focus && !finished) return true
        archiveUncommitted()
        bodyFocus = focus
        routine = data.routine(day, focus = bodyFocus)
        resetFields()
        save()
        return true
    }

    private fun load(d: Int) {
        day = d
        bodyFocus = null
        routine = data.routine(day)
        checked.clear()
        completed.clear()
        var state = preferences.getString(key(), "")
        if (state.isNullOrEmpty()) {
            val legacy = preferences.getString("move60_"+data.weekKey()+"_"+day, null)
            val old = try { JSONObject(legacy ?: "{}") } catch (_: Exception) { JSONObject() }
            val started = old.optLong("sessionStartedAt")
            if (started > 0L && java.time.Instant.ofEpochMilli(started).atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate() == recordDate) {
                state = legacy
                preferences.edit().putString(key(),state).apply()
            }
        }
        loadedRaw = state ?: ""
        if (state.isNullOrEmpty()) {
            resetFields()
            return
        }
        try {
            val o = JSONObject(state)
            bodyFocus = o.optString("bodyFocus").takeIf { it == "upper" || it == "lower" }
            routine = data.routine(day, focus = bodyFocus)
            structure = com.move60.workout.domain.StructureVersion.fromStored(o.optString("structureVersion").takeIf { it.isNotEmpty() })
            controlVersion = o.optLong("controlVersion")
            restDeadline = o.optLong("restDeadline")
            restPaused = o.optInt("restPaused")
            restId = o.optLong("restId")
            stage = o.optInt("stage", 0).coerceIn(0, com.move60.workout.domain.WorkoutStructures.lastIndex(structure))
            accumulatedActiveSeconds = o.optInt("activeSeconds", o.optInt("elapsed", 0)).coerceAtLeast(0)
            stageElapsedSeconds = o.optInt("stageElapsedSeconds", stageSeconds() - o.optInt("remaining", stageSeconds())).coerceAtLeast(0)
            remaining = (stageSeconds() - stageElapsedSeconds).coerceAtLeast(0)
            deadlineAt = o.optLong("deadlineAt", 0L)
            running = o.optBoolean("running", false) && deadlineAt > 0L
            finished = o.optBoolean("finished", false)
            endedEarly = o.optBoolean("endedEarly", false)
            sessionStartedAt = o.optLong("sessionStartedAt", 0L)
            activeCheckpointAt = if (running) o.optLong("activeCheckpointAt", System.currentTimeMillis()) else 0L
            val tasks = o.optJSONArray("checked") ?: JSONArray()
            repeat(tasks.length()) { checked += tasks.optString(it) }
            intervals.clear()
            val stored = o.optJSONArray("intervals") ?: JSONArray()
            repeat(stored.length()) { i -> stored.optJSONArray(i)?.let { if (it.length() == 2) intervals.add(longArrayOf(it.optLong(0), it.optLong(1))) } }
            val stages = o.optJSONArray("completed") ?: JSONArray()
            repeat(stages.length()) { completed += stages.optInt(it) }
            reconcile()
        } catch (e: Exception) { resetFields() }
    }
    private fun resetFields() {
        structure = com.move60.workout.domain.StructureVersion.LEGACY_5
        clearRest()
        controlVersion++
        stage = 0
        remaining = stageSeconds()
        deadlineAt = 0
        running = false
        finished = false
        endedEarly = false
        sessionStartedAt = 0
        accumulatedActiveSeconds = 0
        intervals.clear()
        stageElapsedSeconds = 0
        activeCheckpointAt = 0L
        checked.clear()
        completed.clear()
    }
    private fun stageSeconds(s: Int = stage) = routine.stages[s].minutes * 60
    fun reloadIfChanged(): Boolean {
        if (recordDate != dateNow() && !running) {
            archiveUncommitted()
            recordDate = dateNow()
            load(recordDate.dayOfWeek.value.coerceIn(1,5))
            save()
            return true
        }
        val active = if (preferences.getString("active_date", "") == recordDate.toString())
            preferences.getInt("active_day", day).coerceIn(1, 5) else day
        val raw = preferences.getString("daily_"+recordDate+"_"+active, "") ?: ""
        if (active == day && raw == loadedRaw) return false
        load(active)
        return true
    }
    fun clearRest() { restDeadline = 0L; restPaused = 0; restId = 0L }
    fun restSeconds(now: Long = System.currentTimeMillis()): Int =
        if (restDeadline > 0L) ceil((restDeadline-now)/1000.0).toInt().coerceAtLeast(0) else restPaused
    fun startRest(seconds: Int) {
        reloadIfChanged()
        if (!running || !isStrengthStage || finished) return
        restId = System.currentTimeMillis()
        restDeadline = restId + seconds.coerceIn(5,300)*1000L
        restPaused = 0
        controlVersion++
        save()
    }
    fun adjustRest(delta: Int) {
        reloadIfChanged()
        if (restId == 0L) return
        val seconds = (restSeconds()+delta).coerceIn(0,300)
        if (seconds == 0) clearRest()
        else if (running) restDeadline = System.currentTimeMillis()+seconds*1000L
        else restPaused = seconds
        controlVersion++
        save()
    }
    fun skipRest() { reloadIfChanged(); clearRest(); controlVersion++; save() }
    fun save() {
        val checkedArray = JSONArray(); checked.sorted().forEach { checkedArray.put(it) }
        val completedArray = JSONArray(); completed.sorted().forEach { completedArray.put(it) }
        val intervalArray = JSONArray(); intervals.forEach { intervalArray.put(JSONArray().put(it[0]).put(it[1])) }
        val state = JSONObject().put("bodyFocus", bodyFocus ?: "").put("structure", structure.name).put("stage", stage).put("remaining", remaining)
            .put("deadlineAt", deadlineAt).put("running", running).put("finished", finished).put("endedEarly", endedEarly)
            .put("sessionStartedAt", sessionStartedAt).put("activeSeconds", accumulatedActiveSeconds)
            .put("stageElapsedSeconds", stageElapsedSeconds).put("activeCheckpointAt", activeCheckpointAt)
            .put("controlVersion", controlVersion).put("structureVersion", structure.name).put("restDeadline", restDeadline)
            .put("restPaused", restPaused).put("restId", restId)
            .put("checked", checkedArray)
            .put("completed", completedArray)
            .put("intervals", intervalArray)
        loadedRaw = state.toString()
        preferences.edit().putString("active_date",recordDate.toString()).putInt("active_day", day).putString(key(), loadedRaw).apply()
    }
    fun reconcile(now: Long = System.currentTimeMillis()): Boolean {
        if (!running || finished) return false
        if (activeCheckpointAt <= 0L) activeCheckpointAt = now
        val delta = ((now - activeCheckpointAt) / 1000L).coerceAtLeast(0L).toInt()
        if (delta <= 0) return false
        recordInterval(activeCheckpointAt, delta)
        accumulatedActiveSeconds = (accumulatedActiveSeconds + delta).coerceAtMost(Int.MAX_VALUE)
        stageElapsedSeconds = (stageElapsedSeconds + delta).coerceAtMost(Int.MAX_VALUE)
        activeCheckpointAt += delta * 1000L
        remaining = (stageSeconds() - stageElapsedSeconds).coerceAtLeast(0)
        deadlineAt = if (remaining > 0) now + remaining * 1000L else now
        save()
        return true
    }
    fun start() {
        reloadIfChanged()
        if (finished || endedEarly) { archiveUncommitted(); resetFields() }
        if (!engineSupported) return   // never run a session of a newer structure with this engine
        if (running) return
        controlVersion++
        running = true
        val now = System.currentTimeMillis()
        if (restPaused > 0) { restDeadline = now + restPaused*1000L; restPaused = 0 }
        deadlineAt = now + remaining * 1000L
        activeCheckpointAt = now
        if (sessionStartedAt == 0L) sessionStartedAt = now
        save()
    }
    fun pause() {
        reloadIfChanged()
        reconcile()
        if (running) {
            controlVersion++
            restPaused = restSeconds()
            restDeadline = 0L
            remaining = ceil((deadlineAt - System.currentTimeMillis()) / 1000.0).toInt().coerceIn(0, stageSeconds())
            deadlineAt = 0L
            running = false
            activeCheckpointAt = 0L
        }
        save()
    }
    fun resetStage() {
        pause()
        controlVersion++
        clearRest()
        remaining = stageSeconds()
        stageElapsedSeconds = 0
        finished = false
        completed.remove(stage)
        save()
    }
    fun jumpTo(target: Int) {
        reloadIfChanged()
        if (!engineSupported || target !in 0..lastStageIndex) return
        val wasRunning = running
        reconcile()
        clearRest()
        controlVersion++
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
        reloadIfChanged()
        if (!engineSupported) return
        if (stage >= lastStageIndex) {
            clearRest()
            controlVersion++
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
    /**
     * User-confirmed early end: stops the timer and keeps the real recorded intervals for saving as PARTIAL.
     * Returns false when nothing was performed or the session is already over. Pauses are not counted as active time.
     */
    fun endEarly(): Boolean {
        reloadIfChanged()
        if (finished || endedEarly) return false
        pause()
        if (sessionStartedAt == 0L && accumulatedActiveSeconds == 0) return false
        controlVersion++
        endedEarly = true
        save()
        return true
    }
    /** Reset (P-28 A): discards the current session's temporary data. Stored past records are never touched. */
    fun resetAll() { pause(); archiveUncommitted(); resetFields(); save() }
    fun toggleChecked(key: String, value: Boolean) { reloadIfChanged(); controlVersion++; if (value) checked.add(key) else checked.remove(key); save() }
    fun elapsed(): Int {
        if (running) reconcile()
        return accumulatedActiveSeconds
    }
    /** Cardio stages (1, 3) and the stretch stage (4) run as timed phases. */
    private fun timedStage() = phasesKeyForStage(stage) != null && routine.stages[stage].tasks.all { it.seconds > 0 }
    fun phase(): Pair<Int, WorkoutTask>? {
        if (!timedStage() || finished) return null
        var elapsed = stageElapsedSeconds.coerceAtMost(stageSeconds(stage) - 1)
        for ((index, task) in routine.stages[stage].tasks.withIndex()) {
            if (elapsed < task.seconds) return index to task
            elapsed -= task.seconds
        }
        return null
    }
    /** Seconds spent beyond the target of a timed stage; the stage keeps running, so the UI shows "+mm:ss". */
    fun overtimeSeconds(): Int =
        if (timedStage() && !finished) (stageElapsedSeconds - stageSeconds(stage)).coerceAtLeast(0) else 0
    /**
     * Manual move to another item of the current timed stage. Only the position inside the stage changes;
     * the accumulated active workout time is kept, and a running timer keeps running from the new item.
     */
    fun jumpToPhase(index: Int) {
        reloadIfChanged()
        reconcile()
        val tasks = routine.stages[stage].tasks
        if (!timedStage() || finished || index !in tasks.indices) return
        controlVersion++
        clearRest()
        stageElapsedSeconds = tasks.take(index).sumOf { it.seconds }
        remaining = (stageSeconds() - stageElapsedSeconds).coerceAtLeast(0)
        if (running) {
            val now = System.currentTimeMillis()
            activeCheckpointAt = now
            deadlineAt = now + remaining * 1000L
        }
        save()
    }
    fun phaseSecondsRemaining(): Int {
        if (!timedStage()) return remaining
        var elapsed = stageElapsedSeconds.coerceAtMost(stageSeconds(stage) - 1)
        for (task in routine.stages[stage].tasks) {
            if (elapsed < task.seconds) return task.seconds - elapsed
            elapsed -= task.seconds
        }
        return 0
    }
    fun snapshot(): String {
        val times = JSONArray()
        routine.stages.forEach { times.put(it.minutes * 60) }
        fun phasesOf(index: Int) = JSONArray().also { array ->
            routine.stages[index].tasks.forEach { t ->
                array.put(JSONObject().put("name", t.name).put("seconds", t.seconds)
                    .put("rpe", t.rpe).put("description", t.description).put("kind", t.kind))
            }
        }
        val phases0 = if (routine.stages[0].tasks.all { it.seconds > 0 }) phasesOf(0) else JSONArray()
        val phases = phasesOf(1)
        val phases3 = phasesOf(3)
        val phases4 = phasesOf(4)
        return JSONObject().put("bodyFocus", bodyFocus ?: "").put("workoutDate",recordDate.toString()).put("running", running).put("finished", finished)
            .put("sessionStartedAt", sessionStartedAt)
            .put("controlVersion", controlVersion).put("restDeadline", restDeadline)
            .put("restPaused", restPaused).put("restId", restId)
            .put("day", day).put("structure", structure.name).put("stage", stage).put("remaining", remaining)
            .put("deadlineAt", deadlineAt).put("activeSeconds", accumulatedActiveSeconds)
            .put("activeCheckpointAt", activeCheckpointAt)
            .put("stageElapsedSeconds", stageElapsedSeconds)
            .put("cardioMachine", routine.stages[1].tasks.firstOrNull()?.kind ?: routine.stages[1].name).put("stageSeconds", times).put("phases0", phases0).put("phases", phases).put("phases3", phases3).put("phases4", phases4).toString()
    }
}


