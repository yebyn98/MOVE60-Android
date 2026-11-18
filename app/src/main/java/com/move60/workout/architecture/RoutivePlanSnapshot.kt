package com.move60.workout.architecture

import com.move60.workout.domain.StageKind
import org.json.JSONArray
import org.json.JSONObject

/**
 * The plan of a ROUTIVE_4 session as it was when the session STARTED (docs/v61 D1): routine name, stage plan and the
 * exercises put into each stage. It is kept apart from measured values (P-21) and is stored once in
 * `sessions.planSnapshotJson`, so a routine that is edited or deleted later never changes what was actually planned.
 * Unknown planned times stay null; nothing is defaulted or generated.
 */
object RoutivePlanSnapshot {
    const val VERSION = 1

    data class Stage(val kind: StageKind, val plannedSeconds: Long?)
    data class Exercise(
        val stageIndex: Int, val id: String, val name: String, val plannedSeconds: Long?,
        val targetSets: Int?, val targetReps: Int?, val restSeconds: Long?
    )
    data class Parsed(val routineId: String, val routineName: String, val stages: List<Stage>, val exercises: List<Exercise>)

    fun toJson(routine: SavedRoutinePlan): String {
        val exercises = JSONArray()
        routine.stages.forEachIndexed { index, stage ->
            routine.exercises[stage.kind].orEmpty().forEach { e ->
                exercises.put(
                    JSONObject().put("stage", index).put("id", e.id).put("name", e.name)
                        .put("plannedSeconds", e.plannedSeconds ?: JSONObject.NULL)
                        .put("targetSets", e.targetSets ?: JSONObject.NULL)
                        .put("targetReps", e.targetReps ?: JSONObject.NULL)
                        .put("restSeconds", e.restSeconds ?: JSONObject.NULL)
                )
            }
        }
        return JSONObject()
            .put("v", VERSION).put("structure", "ROUTIVE_4")
            .put("routineId", routine.routineId).put("routineName", routine.name)
            .put("stages", JSONArray().also { a ->
                routine.stages.forEach { a.put(JSONObject().put("kind", it.kind.name).put("plannedSeconds", it.plannedSeconds ?: JSONObject.NULL)) }
            })
            .put("exercises", exercises)
            .toString()
    }

    /** Null for a missing, unreadable or unknown-version snapshot; the caller then simply has no plan to show. */
    fun parse(json: String?): Parsed? {
        if (json.isNullOrBlank()) return null
        return try {
            val o = JSONObject(json)
            if (o.optInt("v", 0) != VERSION) return null
            val stages = o.getJSONArray("stages").let { a ->
                (0 until a.length()).map { i ->
                    a.getJSONObject(i).let { s -> Stage(StageKind.valueOf(s.getString("kind")), if (s.isNull("plannedSeconds")) null else s.getLong("plannedSeconds")) }
                }
            }
            val exercises = o.getJSONArray("exercises").let { a ->
                (0 until a.length()).map { i ->
                    a.getJSONObject(i).let { e ->
                        Exercise(
                            e.getInt("stage"), e.getString("id"), e.getString("name"),
                            if (e.isNull("plannedSeconds")) null else e.getLong("plannedSeconds"),
                            if (e.isNull("targetSets")) null else e.getInt("targetSets"),
                            if (e.isNull("targetReps")) null else e.getInt("targetReps"),
                            if (e.isNull("restSeconds")) null else e.getLong("restSeconds")
                        )
                    }
                }
            }
            Parsed(o.getString("routineId"), o.getString("routineName"), stages, exercises)
        } catch (_: Exception) { null }
    }
}
