package com.move60.workout.architecture

import android.content.Context
import com.move60.workout.SessionStore
import com.move60.workout.WorkoutData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

sealed interface LaunchResult {
    data class Started(val routineId: String) : LaunchResult
    /** A ROUTIVE_4 session is already in progress; nothing was changed. */
    data class AlreadyActive(val routineName: String) : LaunchResult
    /** A legacy 5-stage session is in progress; nothing was changed. */
    data object LegacyActive : LaunchResult
    data object NotFound : LaunchResult
    data object InvalidPlan : LaunchResult
    data object Failed : LaunchResult
}

/** Starts a ROUTIVE_4 session from a saved routine, refusing whenever another workout is in progress. */
class RoutiveSessionLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plans: RoutinePlanService
) {
    fun activeSession(): RoutiveSession? = RoutiveSessionStore(context).active()?.takeIf { !it.over }

    suspend fun start(routineId: String): LaunchResult {
        val store = RoutiveSessionStore(context)
        store.active()?.takeIf { !it.over }?.let { return LaunchResult.AlreadyActive(it.routineName) }
        if (SessionStore(context, WorkoutData(context)).hasActiveSession) return LaunchResult.LegacyActive
        val plan = try { plans.list().firstOrNull { it.routineId == routineId } } catch (_: Exception) { return LaunchResult.Failed }
            ?: return LaunchResult.NotFound
        return when (store.start(plan)) {
            is RoutiveStart.Started -> LaunchResult.Started(routineId)
            RoutiveStart.AlreadyActive -> LaunchResult.AlreadyActive(plan.name)
            RoutiveStart.InvalidPlan -> LaunchResult.InvalidPlan
        }
    }
}
