package com.move60.workout.domain

/** Confirmed rule: show the watch heart rate only when valid (30..240 bpm) and received <= 10 s ago. */
object HeartRateFreshness {
    const val MIN_BPM = 30
    const val MAX_BPM = 240
    const val MAX_AGE_MS = 10_000L
    const val WAITING_LABEL = "수신 대기"

    fun isValid(bpm: Int): Boolean = bpm in MIN_BPM..MAX_BPM
    fun isFresh(bpm: Int, ageMs: Long): Boolean = isValid(bpm) && ageMs in 0L..MAX_AGE_MS

    /** Text for the phone UI; the heart value never gates timer, stage progress or records. */
    fun display(bpm: Int, ageMs: Long, unit: String = "bpm"): String =
        if (isFresh(bpm, ageMs)) "$bpm $unit" else WAITING_LABEL
}
