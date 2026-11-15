package com.move60.workout.architecture

import androidx.room.withTransaction
import com.move60.workout.domain.PlanValidationIssue
import com.move60.workout.domain.PlannedExercise
import com.move60.workout.domain.RoutineDurationPolicy
import com.move60.workout.domain.StageKind
import com.move60.workout.domain.StageTimePlan
import com.move60.workout.domain.StructureVersion
import com.move60.workout.domain.WorkoutStructures

data class SavedRoutinePlan(val routineId: String, val name: String, val targetSeconds: Long?, val stages: List<StageTimePlan>)

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

    suspend fun save(routineId: String, name: String, targetSeconds: Long?, planned: Map<StageKind, Long?>): RoutinePlanResult {
        val trimmed = name.trim()
        if (routineId.isBlank()) return RoutinePlanResult.Rejected("routineId is blank")
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME) return RoutinePlanResult.Rejected("name must be 1..$MAX_NAME chars")
        val stages = WorkoutStructures.stages(StructureVersion.ROUTIVE_4)
        // E-02 B: unknown times do not block saving, but known times must pass the shared duration policy.
        val evaluation = RoutineDurationPolicy.evaluate(
            stages.map { PlannedExercise(it.kind.name, planned[it.kind]) }, targetSeconds)
        if (!evaluation.canConfirm) return RoutinePlanResult.Rejected("invalid plan time", evaluation.issue)
        return try {
            db.withTransaction {
                if (dao.routinePlan(routineId) != null) return@withTransaction RoutinePlanResult.Rejected("routine already exists")
                val now = clock()
                dao.insertRoutinePlan(RoutinePlanEntity(routineId, trimmed, StructureVersion.ROUTIVE_4.name, targetSeconds, now, now))
                dao.insertRouteStages(stages.mapIndexed { i, s -> RoutineStagePlanEntity(routineId, i, s.kind.name, planned[s.kind]) })
                RoutinePlanResult.Saved(routineId)
            }
        } catch (t: Throwable) { RoutinePlanResult.Failed(t) }
    }

    suspend fun load(routineId: String): SavedRoutinePlan? {
        val plan = dao.routinePlan(routineId) ?: return null
        val stages = dao.routineStages(routineId).map { StageTimePlan(StageKind.valueOf(it.kind), it.plannedSeconds) }
        return SavedRoutinePlan(plan.routineId, plan.name, plan.targetSeconds, stages)
    }

    companion object { const val MAX_NAME = 30 }
}
