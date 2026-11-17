package com.move60.workout.architecture

import android.content.Context
import com.move60.workout.WorkoutHistory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 최초 운동 설정 (Figma FINAL/01 목적, FINAL/02 첫 운동 공간). docs/v65-first-run.md
 *
 * Nothing is chosen for the user: the purpose starts unselected and no space exists until the user creates one.
 * Existing users (v58 history, stored sessions or a registered space) are never forced through the setup.
 */
enum class WorkoutPurpose(val label: String, val supporting: String) {
    WEIGHT_MANAGEMENT("체중 관리", "내 페이스로 시작하는 꾸준한 변화"),
    STRENGTH("근력 향상", "매일 조금씩 강해지는 기록"),
    FITNESS("체력 향상", "오래 움직일 수 있는 나"),
    HABIT("운동 습관", "작은 움직임부터 차근차근");

    companion object { fun fromKey(key: String?): WorkoutPurpose? = entries.firstOrNull { it.name == key } }
}

/** Decides whether the first-run setup must be shown. Pure so it can be tested without a device. */
object FirstRunPolicy {
    fun needsSetup(done: Boolean, hasHistory: Boolean, hasSpace: Boolean, forced: Boolean = false): Boolean =
        forced || (!done && !hasHistory && !hasSpace)
}

@Singleton
class FirstRunStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var purpose: WorkoutPurpose?
        get() = WorkoutPurpose.fromKey(prefs.getString(KEY_PURPOSE, null))
        private set(value) { prefs.edit().apply { if (value == null) remove(KEY_PURPOSE) else putString(KEY_PURPOSE, value.name) }.apply() }

    val done: Boolean get() = prefs.getBoolean(KEY_DONE, false)

    fun savePurpose(purpose: WorkoutPurpose) { this.purpose = purpose }

    /** Committed synchronously: a restart right after the last step must not show the setup again. */
    fun markDone() { prefs.edit().putBoolean(KEY_DONE, true).commit() }

    companion object {
        const val FILE = "move60_first_run_v1"
        const val KEY_PURPOSE = "purpose"
        const val KEY_DONE = "done"
    }
}

/** Reads the existing-user signals. A failed read counts as "has data" so nobody is forced through the setup by a read error. */
class FirstRunGate(private val db: WorkoutDatabase, private val history: WorkoutHistory, private val store: FirstRunStore) {
    suspend fun needsSetup(forced: Boolean): Boolean {
        if (forced) return true
        if (store.done) return false
        val hasHistory = try { history.trainedDates().isNotEmpty() || db.routiveDao().sessionCount() > 0 } catch (_: Exception) { true }
        val hasSpace = try { db.spaceDao().spaces().isNotEmpty() } catch (_: Exception) { true }
        return FirstRunPolicy.needsSetup(store.done, hasHistory, hasSpace)
    }
}
