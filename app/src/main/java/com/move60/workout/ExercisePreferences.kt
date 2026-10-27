package com.move60.workout

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/** Persists user-selected strength substitutions and last performed sets. */
class ExercisePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("move60_exercise_preferences_v1", Context.MODE_PRIVATE)
    fun choice(day: Int, slot: Int): String? = prefs.getString("choice_${day}_${slot}", null)
    fun setChoice(day: Int, slot: Int, id: String) {
        prefs.edit().putString("choice_${day}_${slot}", id).apply()
    }
    fun setLog(id: String, weight: Double, reps: Int, sets: Int) {
        val record = JSONObject().put("weight", weight).put("reps", reps).put("sets", sets).put("date", LocalDate.now().toString())
        prefs.edit().putString("log_${id}", record.toString()).apply()
    }
    fun log(id: String): String {
        val raw = prefs.getString("log_${id}", null) ?: return "아직 기록 없음"
        return try {
            val o = JSONObject(raw)
            "${o.optString("date")} · ${o.optDouble("weight")}kg × ${o.optInt("reps")}회 × ${o.optInt("sets")}세트"
        } catch (_: Exception) { "아직 기록 없음" }
    }
}
