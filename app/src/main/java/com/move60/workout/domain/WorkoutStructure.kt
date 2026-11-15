package com.move60.workout.domain

/**
 * ROUTIVE workout structure policy (ChatGPT-confirmed, 4-stage engine transition). Pure Kotlin.
 *
 * - LEGACY_5: sessions that started before the update (준비운동 → 유산소① → 근력 → 유산소② → 마무리).
 *   They finish in the old structure and are never converted or reset.
 * - ROUTIVE_4: new sessions (준비운동 → 근력 → 유산소 → 마무리). Cardio is ONE top-level stage; its internal
 *   phases (warm-up/steady/cool-down, interval work/recovery) are never counted as stages.
 */
enum class StructureVersion { LEGACY_5, ROUTIVE_4;
    companion object {
        /** A stored session without a marker predates this policy, so it is a legacy 5-stage session. */
        fun fromStored(value: String?): StructureVersion =
            entries.firstOrNull { it.name == value } ?: LEGACY_5
    }
}

enum class StageKind { WARM_UP, CARDIO, STRENGTH, COOL_DOWN }

/** How a stage ends. Strength is driven by completed sets; a reached plan time never force-ends it. */
enum class TransitionMode { SET_COMPLETION, PLANNED_TIME_OR_MANUAL, MANUAL }

data class StageDefinition(val kind: StageKind, val name: String)

object WorkoutStructures {
    private val legacy = listOf(
        StageDefinition(StageKind.WARM_UP, "준비운동"), StageDefinition(StageKind.CARDIO, "유산소 ①"),
        StageDefinition(StageKind.STRENGTH, "근력"), StageDefinition(StageKind.CARDIO, "유산소 ②"),
        StageDefinition(StageKind.COOL_DOWN, "마무리")
    )
    private val routive = listOf(
        StageDefinition(StageKind.WARM_UP, "준비운동"), StageDefinition(StageKind.STRENGTH, "근력"),
        StageDefinition(StageKind.CARDIO, "유산소"), StageDefinition(StageKind.COOL_DOWN, "마무리")
    )

    fun stages(version: StructureVersion): List<StageDefinition> = when (version) {
        StructureVersion.LEGACY_5 -> legacy
        StructureVersion.ROUTIVE_4 -> routive
    }

    fun stageCount(version: StructureVersion): Int = stages(version).size

    /** "n / total 단계" label shared by phone, watch, notification and widget. */
    fun label(version: StructureVersion, stageIndex: Int): String {
        val total = stageCount(version)
        return "${stageIndex.coerceIn(0, total - 1) + 1} / $total 단계"
    }

    fun transitionMode(kind: StageKind, plannedSeconds: Long?): TransitionMode = when {
        kind == StageKind.STRENGTH -> TransitionMode.SET_COMPLETION
        plannedSeconds != null && plannedSeconds > 0L -> TransitionMode.PLANNED_TIME_OR_MANUAL
        else -> TransitionMode.MANUAL        // unconfirmed (null) time: the user moves on; never 0 or a made-up default
    }
}

/** Planned time of a stage. Null stays null: it is never converted to 0 minutes or a default (E-02 B). */
data class StageTimePlan(val kind: StageKind, val plannedSeconds: Long?) {
    init { require(plannedSeconds == null || plannedSeconds > 0L) { "planned time must be positive or null" } }
}
