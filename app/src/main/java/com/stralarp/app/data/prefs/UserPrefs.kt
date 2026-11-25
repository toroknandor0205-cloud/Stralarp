package com.stralarp.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("stralarp_prefs")

data class SavedSettings(
    val snapToRoad: Boolean = true,
    val targetPaceMinPerKm: Double = 5.5,
    val paceVariabilityPercent: Float = 8f,
    val samplingIntervalSeconds: Int = 2,
    val injectGpsJitter: Boolean = true,
    val activityType: String = "running",
    val injectHeartRate: Boolean = false,
    val darkTheme: Boolean = true
)

class UserPrefs(private val context: Context) {

    private object Keys {
        val SNAP = booleanPreferencesKey("snap")
        val PACE = doublePreferencesKey("pace")
        val VAR = floatPreferencesKey("variability")
        val SAMPLE = intPreferencesKey("sample_sec")
        val JITTER = booleanPreferencesKey("jitter")
        val ACTIVITY = stringPreferencesKey("activity")
        val HR = booleanPreferencesKey("hr")
        val DARK = booleanPreferencesKey("dark_theme")
    }

    val settings: Flow<SavedSettings> = context.dataStore.data.map { p ->
        SavedSettings(
            snapToRoad = p[Keys.SNAP] ?: true,
            targetPaceMinPerKm = p[Keys.PACE] ?: 5.5,
            paceVariabilityPercent = p[Keys.VAR] ?: 8f,
            samplingIntervalSeconds = p[Keys.SAMPLE] ?: 2,
            injectGpsJitter = p[Keys.JITTER] ?: true,
            activityType = p[Keys.ACTIVITY] ?: "running",
            injectHeartRate = p[Keys.HR] ?: false,
            darkTheme = p[Keys.DARK] ?: true
        )
    }

    suspend fun save(s: SavedSettings) {
        context.dataStore.edit { p ->
            p[Keys.SNAP] = s.snapToRoad
            p[Keys.PACE] = s.targetPaceMinPerKm
            p[Keys.VAR] = s.paceVariabilityPercent
            p[Keys.SAMPLE] = s.samplingIntervalSeconds
            p[Keys.JITTER] = s.injectGpsJitter
            p[Keys.ACTIVITY] = s.activityType
            p[Keys.HR] = s.injectHeartRate
            p[Keys.DARK] = s.darkTheme
        }
    }
}
