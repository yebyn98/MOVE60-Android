package com.move60.workout.architecture

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class UserSettings(
    val soundEnabled: Boolean = true,
    val weightKg: Float = 63f,
    /** Today's condition (피곤/보통/좋음); null until the user picks one. */
    val condition: String? = null
)

interface PreferenceRepository {
    val settings: Flow<UserSettings>
    fun updateSound(enabled: Boolean)
    fun updateWeight(weight: Float)
    fun updateCondition(condition: String)
}

@Singleton
class LegacyPreferenceRepository @Inject constructor(
    @ApplicationContext context: Context
) : PreferenceRepository {
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    override val settings: Flow<UserSettings> = callbackFlow {
        fun snapshot() = UserSettings(
            soundEnabled = prefs.getBoolean("sound", true),
            weightKg = prefs.getFloat("calorie_weight_kg", 63f),
            condition = prefs.getString("condition", null)
        )
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(snapshot())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(snapshot())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    override fun updateSound(enabled: Boolean) {
        prefs.edit().putBoolean("sound", enabled).apply()
    }

    override fun updateWeight(weight: Float) {
        require(weight in 30f..300f) { "몸무게는 30~300kg 범위로 설정해 주세요" }
        prefs.edit().putFloat("calorie_weight_kg", weight).apply()
    }

    override fun updateCondition(condition: String) {
        prefs.edit().putString("condition", condition).apply()
    }
}
