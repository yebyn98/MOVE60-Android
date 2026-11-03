package com.move60.workout

import android.content.Context
import org.json.JSONObject

/** The next unfinished set is derived from the phone's saved records. */
object TrainingProgress {
    data class Next(val task: WorkoutTask, val exercise: Int, val exercises: Int, val set: Int, val sets: Int) {
        val label get() = task.name + " · " + exercise + "/" + exercises + "종목 · " + set + "/" + sets + "세트"
    }
    fun count(task: WorkoutTask) = Regex("(\\d+)\\s*세트").find(task.detail)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1,8) ?: 3
    fun next(session: SessionStore): Next? {
        val tasks = session.routine.stages[2].tasks
        tasks.forEachIndexed { index, task ->
            for (set in 1..count(task)) if ("strength-"+session.day+"-"+task.id+"-"+set !in session.checked)
                return Next(task, index+1, tasks.size, set, count(task))
        }
        return null
    }
    fun restDuration(task: WorkoutTask) = Regex("휴식\\s*(\\d+)").find(task.detail)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(5,300) ?: 60
    fun complete(context: Context, session: SessionStore, id: String, set: Int): Boolean {
        if (!session.running || session.finished || session.stage != 2 || session.sessionStartedAt <= 0L) return false
        val next = next(session) ?: return false
        if (next.task.id != id || next.set != set || session.restSeconds() > 0) return false
        val records = SetRecordStore(context)
        val record = records.read(session.day, id, set, token=session.sessionStartedAt)
        if (!record.valid) return false
        records.write(session.day, id, set, record.copy(completed=true), token=session.sessionStartedAt)
        session.toggleChecked("strength-"+session.day+"-"+id+"-"+set, true)
        session.startRest(restDuration(next.task))
        return true
    }
    fun enrich(context: Context, state: JSONObject, session: SessionStore) {
        val next = next(session)
        state.put("controlVersion", session.controlVersion).put("sessionStartedAt",session.sessionStartedAt)
            .put("restDeadline",session.restDeadline).put("restPaused",session.restPaused).put("restId",session.restId)
            .put("exerciseId",next?.task?.id ?: "").put("setNumber",next?.set ?: 0)
            .put("setLabel",next?.label ?: "근력 세트 완료")
        val record = next?.let { SetRecordStore(context).read(session.day,it.task.id,it.set,token=session.sessionStartedAt) }
        state.put("canComplete", session.running && session.stage == 2 && next != null && record?.valid == true && session.restSeconds() == 0)
    }
}
