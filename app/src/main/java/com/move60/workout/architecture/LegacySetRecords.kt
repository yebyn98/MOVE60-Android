package com.move60.workout.architecture

import com.move60.workout.SetRecordStore
import java.time.LocalDate

/**
 * Policy 5 (A): completed sets that v58 saved in `SetRecordStore` stay viewable in the ROUTIVE v2 record detail.
 *
 * This is a READ-ONLY view. It writes nothing: no session, no active time, no challenge credit and no copy of the
 * old values are created, and the SharedPreferences originals are never changed. Because nothing is copied,
 * repeating or retrying it cannot duplicate a set and a failure leaves the originals as they were.
 *
 * Linking rule: a legacy workout is linked to a stored session only when a session with the exact id
 * `timer-<date>-d<day>-<token>` exists (the id [TimerSessionSync.sessionId] derives from the same date, day and
 * start token). Anything else stays unlinked; no session is created and no time is guessed.
 */
enum class SetOrigin { STORED, LEGACY }

data class ShownSet(
    val exerciseId: String, val setNumber: Int, val kg: String, val reps: String,
    val origin: SetOrigin,
    /** A legacy value that differs from the stored session's value for the same exercise and set. Both are kept. */
    val differsFromStored: Boolean = false
)

data class LegacySetGroup(val day: Int, val token: Long, val sessionId: String?, val sets: List<ShownSet>) {
    val linked: Boolean get() = sessionId != null
}

data class LegacySetDay(val date: LocalDate, val groups: List<LegacySetGroup>) {
    val linked: List<LegacySetGroup> get() = groups.filter { it.linked }
    val unlinked: List<LegacySetGroup> get() = groups.filter { !it.linked }
    val isEmpty: Boolean get() = groups.isEmpty()
}

class LegacySetReader(
    private val db: WorkoutDatabase,
    private val source: () -> List<SetRecordStore.StoredSet>
) {
    suspend fun forDate(date: LocalDate): LegacySetDay {
        val dao = db.routiveDao()
        val groups = source().filter { it.date == date }
            .groupBy { it.day to it.token }
            .toSortedMap(compareBy<Pair<Int, Long>>({ it.second }, { it.first }))
            .map { (key, legacy) ->
                val (day, token) = key
                val id = "timer-$date-d$day-$token"
                val stored = if (dao.session(id) != null) dao.sets(id).filter { it.state == SetState.COMPLETED.name } else null
                if (stored == null) {
                    LegacySetGroup(day, token, null, legacy.map { ShownSet(it.exerciseId, it.setNumber, it.kg, it.reps, SetOrigin.LEGACY) })
                } else {
                    val byKey = stored.associateBy { it.exerciseId to it.setNumber }
                    val shown = stored.map { ShownSet(it.exerciseId, it.setNumber, it.weightKg, it.repetitions, SetOrigin.STORED) } +
                        legacy.mapNotNull { l ->
                            val s = byKey[l.exerciseId to l.setNumber]
                            when {
                                s == null -> ShownSet(l.exerciseId, l.setNumber, l.kg, l.reps, SetOrigin.LEGACY)
                                s.weightKg == l.kg && s.repetitions == l.reps -> null   // identical duplicate: shown once, as stored
                                else -> ShownSet(l.exerciseId, l.setNumber, l.kg, l.reps, SetOrigin.LEGACY, differsFromStored = true)
                            }
                        }
                    LegacySetGroup(day, token, id, shown.sortedWith(compareBy({ it.exerciseId }, { it.setNumber }, { it.origin })))
                }
            }
        return LegacySetDay(date, groups)
    }
}
