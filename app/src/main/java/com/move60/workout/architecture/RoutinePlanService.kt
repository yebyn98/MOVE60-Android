package com.move60.workout.architecture

import androidx.room.withTransaction
import com.move60.workout.domain.PlanValidationIssue
import com.move60.workout.domain.PlannedExercise
import com.move60.workout.domain.RoutineDurationPolicy
import com.move60.workout.domain.StageKind
import com.move60.workout.domain.StageTimePlan
import com.move60.workout.domain.StructureVersion
import com.move60.workout.domain.WorkoutStructures

/** An exercise chosen for a stage. `plannedSeconds` null = unknown (never 0). */
data class RoutineExerciseInput(val id: String, val name: String, val plannedSeconds: Long? = null)

data class SavedRoutinePlan(
    val routineId: String, val name: String, val targetSeconds: Long?, val stages: List<StageTimePlan>,
    val exercises: Map<StageKind, List<RoutineExerciseInput>> = emptyMap()
)

sealed interface RoutinePlanResult {
    data class Saved(val routineId: String) : RoutinePlanResult
    data class Rejected(val reason: String, val issue: PlanValidationIssue? = null) : RoutinePlanResult
    data class Failed(val cause: Throwable) : RoutinePlanResult
}

/**
 * Persists routine plans for the 4-stage structure. Stage order is fixed by the confirmed structure; times are
 * the user's plan (null = unconfirmed, never 0). Saving never touches sessions or history.
 */
class RoutinePlanService(private val db: WorkoutDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao get() = db.routiveDao()

    /**
     * Stage times are NOT entered: each stage's plan is derived from its exercises' planned times (Figma 41 final policy).
     * A stage is known only when it has exercises and every one of them has a time; otherwise it stays null (never 0).
     */
    suspend fun save(
        routineId: String, name: String, targetSeconds: Long?,
        exercises: Map<StageKind, List<RoutineExerciseInput>>
    ): RoutinePlanResult {
        val trimmed = name.trim()
        if (routineId.isBlank()) return RoutinePlanResult.Rejected("routineId is blank")
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME) return RoutinePlanResult.Rejected("name must be 1..$MAX_NAME chars")
        // E-02 B: at least one exercise is required to confirm a routine; unknown times are allowed.
        if (exercises.values.sumOf { it.size } < 1) return RoutinePlanResult.Rejected("at least one exercise is required")
        if (exercises.values.flatten().any { it.id.isBlank() || it.name.isBlank() || (it.plannedSeconds != null && it.plannedSeconds <= 0L) })
            return RoutinePlanResult.Rejected("invalid exercise")
        val stages = WorkoutStructures.stages(StructureVersion.ROUTIVE_4)
        // E-02 B: unknown times do not block saving, but the known sum must pass the shared duration policy.
        // Every exercise is one entry (unique key), so the same exercise in two stages is never merged or double counted.
        val entries = stages.flatMapIndexed { si, st ->
            exercises[st.kind].orEmpty().mapIndexed { oi, e -> PlannedExercise("$si:$oi:${e.id}", e.plannedSeconds) }
        }
        val evaluation = RoutineDurationPolicy.evaluate(entries, targetSeconds)
        if (!evaluation.canConfirm) return RoutinePlanResult.Rejected("invalid plan time", evaluation.issue)
        return try {
            db.withTransaction {
                if (dao.routinePlan(routineId) != null) return@withTransaction RoutinePlanResult.Rejected("routine already exists")
                val now = clock()
                dao.insertRoutinePlan(RoutinePlanEntity(routineId, trimmed, StructureVersion.ROUTIVE_4.name, targetSeconds, now, now))
                dao.insertRouteStages(stages.mapIndexed { i, s -> RoutineStagePlanEntity(routineId, i, s.kind.name, derivedStageSeconds(exercises[s.kind].orEmpty())) })
                dao.insertRoutineExercises(stages.flatMapIndexed { si, st ->
                    exercises[st.kind].orEmpty().mapIndexed { oi, e -> RoutineExerciseEntity(routineId, si, oi, e.id, e.name, e.plannedSeconds) }
                })
                RoutinePlanResult.Saved(routineId)
            }
        } catch (t: Throwable) { RoutinePlanResult.Failed(t) }
    }

    private fun derivedStageSeconds(items: List<RoutineExerciseInput>): Long? =
        if (items.isEmpty() || items.any { it.plannedSeconds == null }) null else items.sumOf { it.plannedSeconds!! }

    suspend fun load(routineId: String): SavedRoutinePlan? {
        val plan = dao.routinePlan(routineId) ?: return null
        val stages = dao.routineStages(routineId).map { StageTimePlan(StageKind.valueOf(it.kind), it.plannedSeconds) }
        val order = com.move60.workout.domain.WorkoutStructures.stages(StructureVersion.ROUTIVE_4)
        val ex = dao.routineExercises(routineId).groupBy({ order[it.stageIndex.coerceIn(order.indices)].kind }) { RoutineExerciseInput(it.exerciseId, it.name, it.plannedSeconds) }
        return SavedRoutinePlan(plan.routineId, plan.name, plan.targetSeconds, stages, ex)
    }

    /** All saved routines, newest first. A row that cannot be read is skipped, never deleted. */
    suspend fun list(): List<SavedRoutinePlan> = dao.routinePlans().mapNotNull { runCatching { load(it.routineId) }.getOrNull() }

    companion object { const val MAX_NAME = 30 }
}
