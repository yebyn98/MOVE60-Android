package com.move60.workout

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** Dated per-set drafts and performed values; legacy aggregate logs remain untouched. */
class SetRecordStore(context: Context) {
    private val prefs = context.getSharedPreferences("move60_set_records_v1", Context.MODE_PRIVATE)
    data class Record(val kg: String = "", val reps: String = "", val rpe: String = "5", val completed: Boolean = false) {
        val valid: Boolean get() = kg.toDoubleOrNull()?.let { it in 0.0..500.0 } == true &&
            reps.toIntOrNull()?.let { it in 1..100 } == true && rpe.toIntOrNull()?.let { it in 1..10 } == true
        val volume: Double get() = if (completed && valid) kg.toDouble() * reps.toInt() else 0.0
    }
    fun today() = LocalDate.now(ZoneId.of("Asia/Seoul"))
    private fun key(date: LocalDate, day: Int, id: String, set: Int, token: Long) = "set:$date:$day:$token:$id:$set"
    fun read(day: Int, id: String, set: Int, date: LocalDate = today(), token: Long = 0L): Record =
        decode(prefs.getString(key(date, day, id, set, token), null) ?: prefs.getString(key(date, day, id, set, 0L), null)).let { if (token > 0L && !prefs.contains(key(date, day, id, set, token))) it.copy(completed = false) else it }
    fun write(day: Int, id: String, set: Int, record: Record, date: LocalDate = today(), token: Long = 0L) {
        val json = JSONObject().put("kg", record.kg).put("reps", record.reps).put("rpe", record.rpe)
            .put("completed", record.completed && record.valid)
        val edit = prefs.edit().putString(key(date, day, id, set, token), json.toString())
        if (token > 0L) edit.putString(key(date, day, id, set, 0L), JSONObject(json.toString()).put("completed", false).toString())
        edit.apply()
    }
    fun completed(date: LocalDate): List<Record> = prefs.all.filterKeys { it.startsWith("set:$date:") && it.split(':').getOrNull(3) != "0" }
        .values.map { decode(it as? String) }.filter { it.completed && it.valid }
    fun beginSession(day: Int, token: Long) {
        if (token <= 0L) return
        val prefix = "set:${today()}:$day:0:"
        val edit = prefs.edit()
        prefs.all.filterKeys { it.startsWith(prefix) }.forEach { (key, value) ->
            val target = key.replace(prefix, "set:${today()}:$day:$token:")
            if (!prefs.contains(target)) edit.putString(target, value as? String)
        }
        edit.apply()
    }
    private fun decode(value: String?): Record = try {
        val o = JSONObject(value ?: "{}")
        Record(o.optString("kg"), o.optString("reps"), o.optString("rpe", "5"), o.optBoolean("completed"))
    } catch (_: Exception) { Record() }
}
