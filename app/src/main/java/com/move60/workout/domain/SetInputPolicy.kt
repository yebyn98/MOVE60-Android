package com.move60.workout.domain

/**
 * The one definition of what a set's weight and repetitions may be. The routine plan, the live set input, the stored
 * record and the later correction all read these limits, so a value the plan allows (for example 150 reps) can never be
 * refused by the store afterwards.
 */
object SetLimits {
    const val MIN_REPS = 1
    /** Equal to the routine plan's target-repetitions bound ([RoutinePlanService.REPS_RANGE]). */
    const val MAX_REPS = 200
    const val MAX_KG = 500.0
}

enum class SetInputError {
    /** The weight is not a number between 0 and [SetLimits.MAX_KG]. */
    WEIGHT,
    /** The repetitions are not a whole number between [SetLimits.MIN_REPS] and [SetLimits.MAX_REPS]. */
    REPS,
    /** The values were fine but the record could not be stored as completed (nothing else was changed). */
    NOT_STORED
}

/** A set that passed [SetInputPolicy.validate], in the exact form that is stored. */
data class ValidSet(val kg: String, val reps: String)

object SetInputPolicy {
    private val KG = Regex("^\\d+(\\.\\d+)?$")
    private val REPS = Regex("^\\d+$")

    /** Null when [kg] and [reps] are acceptable. Plain decimal numbers only: no signs, exponents, commas or spaces inside. */
    fun validate(kg: String, reps: String): SetInputError? = check(kg, reps).let { if (it is Checked.Bad) it.error else null }

    /** The values to store (trimmed; repetitions without leading zeros), or null when [validate] would report an error. */
    fun normalize(kg: String, reps: String): ValidSet? = (check(kg, reps) as? Checked.Good)?.set

    private sealed interface Checked {
        data class Good(val set: ValidSet) : Checked
        data class Bad(val error: SetInputError) : Checked
    }

    private fun check(kgRaw: String, repsRaw: String): Checked {
        val kg = kgRaw.trim()
        val reps = repsRaw.trim()
        val weight = if (KG.matches(kg)) kg.toDoubleOrNull() else null
        if (weight == null || weight.isNaN() || weight.isInfinite() || weight !in 0.0..SetLimits.MAX_KG) return Checked.Bad(SetInputError.WEIGHT)
        val count = if (REPS.matches(reps)) reps.toIntOrNull() else null   // too many digits overflow to null
        if (count == null || count !in SetLimits.MIN_REPS..SetLimits.MAX_REPS) return Checked.Bad(SetInputError.REPS)
        return Checked.Good(ValidSet(kg, count.toString()))
    }
}
