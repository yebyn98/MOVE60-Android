package com.move60.workout.domain

/**
 * Pure progress model of a ROUTIVE_4 session (준비운동 → 근력 → 유산소 → 마무리). No Android, no clock: the caller feeds
 * active seconds, so it is deterministic and unit-testable.
 *
 * Confirmed policy encoded here:
 * - Stage planned time comes from the routine plan; null stays null (never 0 or a default) and has no remaining time.
 * - Strength is driven by set completion; its planned time never force-ends it.
 * - Other stages become "ready" when the planned time is reached, or never (manual) when it is unknown.
 * - Nothing advances by itself: moving on, skipping and going back are explicit user actions.
 * - Cardio is ONE stage; its internal segments never create stages.
 * - Elapsed time of a stage is real active time and is preserved when the user goes back or skips.
 * - Every stage ends as PERFORMED ("다음"/"운동 완료"), SKIPPED (explicit "건너뛰기"/"마무리 생략") or UNFINISHED (never decided).
 *   Going back never clears SKIPPED; only performing the stage ("다음") does. The session is [finished] when the user
 *   walked through the last stage and at least one stage was performed (explicit skips are the user's decision, never an
 *   automatic PARTIAL); it is [endedEarly] when the user ended with undecided stages left (saved as PARTIAL), or when
 *   nothing at all was performed. The final record (COMPLETED/PARTIAL/NOT_PERFORMED) is derived from this by the commit rules.
 */
data class FourStageProgress(
    val plan: List<StageTimePlan>,
    val stageIndex: Int = 0,
    val elapsedSeconds: List<Long> = List(plan.size) { 0L },
    val skipped: Set<Int> = emptySet(),
    val performed: Set<Int> = emptySet(),
    val finished: Boolean = false,
    val endedEarly: Boolean = false
) {
    init {
        require(plan.map { it.kind } == WorkoutStructures.stages(StructureVersion.ROUTIVE_4).map { it.kind }) {
            "a ROUTIVE_4 plan has exactly the stages warm-up, strength, cardio, cool-down in that order"
        }
        require(elapsedSeconds.size == plan.size && elapsedSeconds.all { it >= 0L }) { "elapsed must match the plan and be non-negative" }
        require(stageIndex in plan.indices) { "stage index out of range" }
    }

    val kind: StageKind get() = plan[stageIndex].kind
    val isLast: Boolean get() = stageIndex == plan.lastIndex
    val isOver: Boolean get() = finished || endedEarly
    val totalElapsedSeconds: Long get() = elapsedSeconds.sum()

    fun plannedSeconds(index: Int = stageIndex): Long? = plan[index].plannedSeconds

    /** Seconds left in the stage plan; null when the plan time is unknown (shown as "미정", never 0). Strength has none that ends it. */
    fun remainingSeconds(index: Int = stageIndex): Long? =
        plannedSeconds(index)?.let { (it - elapsedSeconds[index]).coerceAtLeast(0L) }

    /** Seconds beyond the plan; 0 while inside it or when the plan is unknown. */
    fun overtimeSeconds(index: Int = stageIndex): Long =
        plannedSeconds(index)?.let { (elapsedSeconds[index] - it).coerceAtLeast(0L) } ?: 0L

    /** Why the user may move on. Never an automatic transition. */
    fun readiness(allStrengthSetsDone: Boolean): StageReadiness = when {
        isOver -> StageReadiness.OVER
        kind == StageKind.STRENGTH -> if (allStrengthSetsDone) StageReadiness.SETS_DONE else StageReadiness.IN_PROGRESS
        else -> {
            val planned = plannedSeconds()
            when {
                planned == null -> StageReadiness.MANUAL
                elapsedSeconds[stageIndex] >= planned -> StageReadiness.PLAN_REACHED
                else -> StageReadiness.IN_PROGRESS
            }
        }
    }

    /** Adds real active seconds to the current stage. Ignored once the session is over or for non-positive input. */
    fun tick(seconds: Long): FourStageProgress {
        if (isOver || seconds <= 0L) return this
        return copy(elapsedSeconds = elapsedSeconds.toMutableList().also { it[stageIndex] += seconds })
    }

    /** Explicit "다음" (or "운동 완료" on the last stage): the stage counts as performed and any earlier skip mark is replaced. */
    fun advance(): FourStageProgress = when {
        isOver -> this
        isLast -> copy(performed = performed + stageIndex, skipped = skipped - stageIndex, finished = true)
        else -> copy(performed = performed + stageIndex, skipped = skipped - stageIndex, stageIndex = stageIndex + 1)
    }

    /**
     * User skips the current stage: recorded as skipped, real elapsed time is kept. Skipping the last stage ("마무리 생략")
     * finishes the session by the user's own decision; only when nothing was performed at all it is an early end.
     */
    fun skip(): FourStageProgress = when {
        isOver -> this
        isLast -> copy(skipped = skipped + stageIndex, performed = performed - stageIndex).let {
            if (it.performed.isEmpty()) it.copy(endedEarly = true) else it.copy(finished = true)
        }
        else -> copy(skipped = skipped + stageIndex, performed = performed - stageIndex, stageIndex = stageIndex + 1)
    }

    /** Back to the previous stage; its elapsed time and its skipped mark are kept (only performing it clears the mark). */
    fun back(): FourStageProgress = if (isOver || stageIndex == 0) this else copy(stageIndex = stageIndex - 1)

    fun statusOf(index: Int): StageStatus = when {
        index in skipped -> StageStatus.SKIPPED
        index in performed -> StageStatus.PERFORMED
        else -> StageStatus.UNFINISHED
    }

    /** Confirmed early end (saved as PARTIAL by the existing commit rules, never as complete). */
    fun endEarly(): FourStageProgress = if (isOver) this else copy(endedEarly = true)
}

enum class StageStatus { PERFORMED, SKIPPED, UNFINISHED }

enum class StageReadiness { IN_PROGRESS, SETS_DONE, PLAN_REACHED, MANUAL, OVER }

/** Builds the 4-stage plan from saved routine stage times; rejects anything that is not exactly the ROUTIVE_4 shape. */
object FourStagePlans {
    fun from(stages: List<StageTimePlan>): FourStageProgress? =
        if (stages.map { it.kind } == WorkoutStructures.stages(StructureVersion.ROUTIVE_4).map { it.kind }) FourStageProgress(stages) else null
}
