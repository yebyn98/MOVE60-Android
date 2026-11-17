package com.move60.workout.architecture

import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/** The equipment basis for exercise candidates: the DEFAULT space only. "No space" and "could not read" are different states. */
sealed interface CandidateSpace {
    data object Loading : CandidateSpace
    data object NoSpace : CandidateSpace
    data object Failed : CandidateSpace
    data class Ready(val name: String, val kind: String, val equipment: Set<String>) : CandidateSpace
}

class DefaultSpaceEquipment(
    private val readSpaces: suspend () -> List<WorkoutSpaceEntity>,
    private val readEquipment: suspend (spaceId: String) -> List<SpaceEquipmentEntity>
) {
    @Inject constructor(db: WorkoutDatabase) : this({ db.spaceDao().spaces() }, { db.spaceDao().equipment(it) })

    suspend fun load(): CandidateSpace = try {
        val space = readSpaces().firstOrNull { it.isDefault }
        if (space == null) CandidateSpace.NoSpace
        else CandidateSpace.Ready(space.name, space.kind, readEquipment(space.spaceId).map { it.equipmentKey }.toSet())
    } catch (e: CancellationException) { throw e } catch (_: Exception) { CandidateSpace.Failed }
}
