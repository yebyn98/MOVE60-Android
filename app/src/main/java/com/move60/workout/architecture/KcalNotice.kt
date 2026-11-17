package com.move60.workout.architecture

import android.content.Context

/**
 * v66 PO decision 1: kcal is hidden everywhere (stored records are never changed). People who already had records get one
 * non-blocking banner the first time they open the record screen; new users never see it.
 */
object KcalNoticePolicy {
    fun show(seen: Boolean, existingUser: Boolean): Boolean = !seen && existingUser

    const val TITLE = "칼로리 표시를 더 이상 제공하지 않아요"
    const val BODY = "이전 운동 기록은 그대로 보존돼요."
}

class KcalNoticeStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    val seen: Boolean get() = prefs.getBoolean(KEY_SEEN, false)
    /** Committed at once: "once" must hold even if the app is closed right after the banner appeared. */
    fun markSeen() { prefs.edit().putBoolean(KEY_SEEN, true).commit() }

    companion object { const val FILE = "move60_notices_v1"; const val KEY_SEEN = "kcal_hidden_seen" }
}
