package com.move60.workout.architecture

import androidx.room.withTransaction

/**
 * Record correction (policy 6, P-22/P-23). The owner can correct the weight and repetitions of a COMPLETED set that
 * really exists in a stored session. The stored rows (`session_sets`, intervals, day slices, the session itself) are NEVER
 * changed: every correction is a new append-only row in `session_adjustments` holding the previous value, the new value and
 * the time, and the screen shows the latest value on top of the untouched original.
 *
 * Cardio console values (docs/v71) are corrected the same way: a new row whose field is [CorrectionField.cardio]; only the value,
 * never the time or the original input row. Reading them back is [CardioStageRecords]; writing them is [SessionCorrectionService.applyCardio].
 *
 * Not correctable here: sensor/raw active intervals, the session id, a set that was never recorded (nothing is created),
 * and values the app never stored (RPE) - there is nothing to correct for those.
 * Corrections cannot change active time or challenge credit, so closed challenge snapshots are untouched by construction.
 */
object CorrectionField {
    const val WEIGHT = "weightKg"
    const val REPS = "repetitions"

    fun set(exerciseId: String, setNumber: Int, attribute: String) = "set:$exerciseId:$setNumber:$attribute"

    /** Field of a corrected cardio console value: input [inputIndex], metric [metricKey] ([com.move60.workout.domain.CardioMetric.key]). */
    fun cardio(inputIndex: Int, metricKey: String) = "cardio:$inputIndex:$metricKey"

    data class ParsedCardio(val inputIndex: Int, val metric: com.move60.workout.domain.CardioMetric)

    /** Null for anything that is not a cardio field this code wrote (including a metric it does not know). */
    fun parseCardio(field: String): ParsedCardio? {
        if (!field.startsWith("cardio:")) return null
        val parts = field.removePrefix("cardio:").split(':')
        if (parts.size != 2) return null
        val index = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val metric = com.move60.workout.domain.CardioMetric.fromKey(parts[1]) ?: return null
        return ParsedCardio(index, metric)
    }

    data class Parsed(val exerciseId: String, val setNumber: Int, val attribute: String)

    /** Parsed from the right, so an exercise id may itself contain ':'. Null for anything this code did not write. */
    fun parse(field: String): Parsed? {
        if (!field.startsWith("set:")) return null
        val body = field.removePrefix("set:")
        val attrAt = body.lastIndexOf(':')
        if (attrAt <= 0) return null
        val attribute = body.substring(attrAt + 1)
        if (attribute != WEIGHT && attribute != REPS) return null
        val rest = body.substring(0, attrAt)
        val numAt = rest.lastIndexOf(':')
        if (numAt <= 0) return null
        val number = rest.substring(numAt + 1).toIntOrNull() ?: return null
        return Parsed(rest.substring(0, numAt), number, attribute)
    }
}

/** Values the user typed. null = leave that value as it is. */
data class SetCorrection(val exerciseId: String, val setNumber: Int, val weightKg: String? = null, val repetitions: String? = null)

sealed interface CorrectionResult {
    data class Saved(val adjustments: Int) : CorrectionResult
    data object SessionNotFound : CorrectionResult
    /** The set does not exist or was not completed. A correction never creates a set. */
    data class SetNotFound(val exerciseId: String, val setNumber: Int) : CorrectionResult
    data class InvalidValue(val exerciseId: String, val setNumber: Int, val attribute: String) : CorrectionResult
    /** Every given value already equals the current one, so nothing is written. */
    data object Unchanged : CorrectionResult
    data class Failed(val cause: Throwable) : CorrectionResult
}

/** The latest corrected value per set; null = never corrected (show the stored original). */
data class SetValues(val kg: String?, val reps: String?)

data class CorrectionEntry(
    val adjustmentId: Long, val createdAtEpochMs: Long, val exerciseId: String, val setNumber: Int,
    /** [CorrectionField.WEIGHT] or [CorrectionField.REPS]. */
    val attribute: String, val oldValue: String?, val newValue: String?, val reason: String?
)

/** One corrected cardio console value: the saved input [inputIndex], its [metric], the typed [value]. */
data class CardioValueEdit(val inputIndex: Int, val metric: com.move60.workout.domain.CardioMetric, val value: String)

sealed interface CardioCorrectionResult {
    data class Saved(val adjustments: Int) : CardioCorrectionResult
    data object SessionNotFound : CardioCorrectionResult
    /** That input/metric was never saved in this session. A correction never creates an input. */
    data class InputNotFound(val inputIndex: Int, val metricKey: String) : CardioCorrectionResult
    data class InvalidValue(val inputIndex: Int, val metricKey: String) : CardioCorrectionResult
    /** Every given value already equals the current one, so nothing is written. */
    data object Unchanged : CardioCorrectionResult
    data class Failed(val cause: Throwable) : CardioCorrectionResult
}

internal object CorrectionRules {
    private val CARDIO = Regex("^\\d+(\\.\\d+)?$")
    /** Upper bound for a console level/incline (proposal, PO to confirm): machines in the app use small whole numbers. */
    const val CARDIO_MAX = 100.0

    /** A plain non-negative number up to [CARDIO_MAX]; blank is not accepted (a correction never removes a recorded value). */
    fun cardio(raw: String): String? {
        val t = raw.trim()
        if (!CARDIO.matches(t)) return null
        val v = t.toDoubleOrNull() ?: return null
        return if (v in 0.0..CARDIO_MAX) t else null
    }

    private val WEIGHT = Regex("^\\d+(\\.\\d+)?$")
    private val REPS = Regex("^\\d+$")

    /** Same bounds as live input ([SetLimits.MAX_KG]). Blank is not accepted: a correction never removes a recorded value. */
    fun weight(raw: String): String? {
        val t = raw.trim()
        if (!WEIGHT.matches(t)) return null
        val v = t.toDoubleOrNull() ?: return null
        return if (v in 0.0..com.move60.workout.domain.SetLimits.MAX_KG) t else null
    }

    /** Same bounds as live input and plan ([SetLimits.MIN_REPS]..[SetLimits.MAX_REPS]). */
    fun reps(raw: String): String? {
        val t = raw.trim()
        if (!REPS.matches(t)) return null
        val v = t.toIntOrNull() ?: return null
        return if (v in com.move60.workout.domain.SetLimits.MIN_REPS..com.move60.workout.domain.SetLimits.MAX_REPS) t else null
    }
}

class SessionCorrectionService(private val db: WorkoutDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao get() = db.routiveDao()

    /** Latest corrected value per (exercise, set), replayed in the order the corrections were saved. */
    suspend fun overlay(sessionId: String): Map<Pair<String, Int>, SetValues> {
        val out = HashMap<Pair<String, Int>, SetValues>()
        for (a in dao.adjustments(sessionId)) {
            val f = CorrectionField.parse(a.field) ?: continue
            val key = f.exerciseId to f.setNumber
            val current = out[key] ?: SetValues(null, null)
            out[key] = if (f.attribute == CorrectionField.WEIGHT) current.copy(kg = a.newValue) else current.copy(reps = a.newValue)
        }
        return out
    }

    /** Oldest first. Each row keeps the value before the change, so the original is always the first `oldValue` of a chain. */
    suspend fun history(sessionId: String): List<CorrectionEntry> = dao.adjustments(sessionId).mapNotNull { a ->
        CorrectionField.parse(a.field)?.let {
            CorrectionEntry(a.adjustmentId, a.createdAtEpochMs, it.exerciseId, it.setNumber, it.attribute, a.oldValue, a.newValue, a.reason)
        }
    }

    /**
     * Saves all given corrections or none. Every value is validated first; one invalid value, a missing set or a duplicate
     * set in the request writes nothing. Only values that differ from the current one are recorded.
     */
    suspend fun apply(sessionId: String, edits: List<SetCorrection>, reason: String? = null): CorrectionResult = try {
        db.withTransaction {
            if (dao.session(sessionId) == null) return@withTransaction CorrectionResult.SessionNotFound
            val stored = dao.sets(sessionId).filter { it.state == SetState.COMPLETED.name }.associateBy { it.exerciseId to it.setNumber }
            val current = overlay(sessionId)
            val now = clock()
            val seen = HashSet<Pair<String, Int>>()
            val pending = ArrayList<SessionAdjustmentEntity>()
            for (e in edits) {
                val key = e.exerciseId to e.setNumber
                val row = stored[key] ?: return@withTransaction CorrectionResult.SetNotFound(e.exerciseId, e.setNumber)
                if (!seen.add(key)) return@withTransaction CorrectionResult.InvalidValue(e.exerciseId, e.setNumber, "duplicate")
                val shown = current[key]
                if (e.weightKg != null) {
                    val v = CorrectionRules.weight(e.weightKg) ?: return@withTransaction CorrectionResult.InvalidValue(e.exerciseId, e.setNumber, CorrectionField.WEIGHT)
                    val old = shown?.kg ?: row.weightKg
                    if (v != old) pending += SessionAdjustmentEntity(
                        sessionId = sessionId, createdAtEpochMs = now, field = CorrectionField.set(e.exerciseId, e.setNumber, CorrectionField.WEIGHT),
                        oldValue = old, newValue = v, reason = reason
                    )
                }
                if (e.repetitions != null) {
                    val v = CorrectionRules.reps(e.repetitions) ?: return@withTransaction CorrectionResult.InvalidValue(e.exerciseId, e.setNumber, CorrectionField.REPS)
                    val old = shown?.reps ?: row.repetitions
                    if (v != old) pending += SessionAdjustmentEntity(
                        sessionId = sessionId, createdAtEpochMs = now, field = CorrectionField.set(e.exerciseId, e.setNumber, CorrectionField.REPS),
                        oldValue = old, newValue = v, reason = reason
                    )
                }
            }
            if (pending.isEmpty()) return@withTransaction CorrectionResult.Unchanged
            pending.forEach { dao.appendAdjustment(it) }
            CorrectionResult.Saved(pending.size)
        }
    } catch (error: Throwable) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        CorrectionResult.Failed(error)
    }

    /**
     * Saves corrected cardio values or nothing (docs/v71 step 5). Only values of inputs that were saved in this session can be
     * corrected, only the value (never the time), and the original `session_cardio_inputs` rows are never touched: each change is a
     * new `session_adjustments` row with the previous and the new value. Equal values write nothing.
     */
    suspend fun applyCardio(sessionId: String, edits: List<CardioValueEdit>, reason: String? = null): CardioCorrectionResult = try {
        db.withTransaction {
            if (dao.session(sessionId) == null) return@withTransaction CardioCorrectionResult.SessionNotFound
            val stored = dao.cardioInputs(sessionId).associateBy { it.inputIndex to it.metricKey }
            // Replay earlier cardio corrections in saved order (adjustmentId is strictly increasing): the last one is what is shown now.
            val shown = HashMap<Pair<Int, String>, String>()
            for (a in dao.adjustments(sessionId)) {
                val f = CorrectionField.parseCardio(a.field) ?: continue
                a.newValue?.let { shown[f.inputIndex to f.metric.key] = it }
            }
            val now = clock()
            val seen = HashSet<Pair<Int, String>>()
            val pending = ArrayList<SessionAdjustmentEntity>()
            for (e in edits) {
                val key = e.inputIndex to e.metric.key
                val row = stored[key] ?: return@withTransaction CardioCorrectionResult.InputNotFound(e.inputIndex, e.metric.key)
                if (!seen.add(key)) return@withTransaction CardioCorrectionResult.InvalidValue(e.inputIndex, e.metric.key)
                val v = CorrectionRules.cardio(e.value) ?: return@withTransaction CardioCorrectionResult.InvalidValue(e.inputIndex, e.metric.key)
                val old = shown[key] ?: row.valueText
                if (v.toDouble() != old.trim().replace(',', '.').toDoubleOrNull()) pending += SessionAdjustmentEntity(
                    sessionId = sessionId, createdAtEpochMs = now, field = CorrectionField.cardio(e.inputIndex, e.metric.key),
                    oldValue = old, newValue = v, reason = reason
                )
            }
            if (pending.isEmpty()) return@withTransaction CardioCorrectionResult.Unchanged
            pending.forEach { dao.appendAdjustment(it) }
            CardioCorrectionResult.Saved(pending.size)
        }
    } catch (error: Throwable) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        CardioCorrectionResult.Failed(error)
    }
}
