package com.juzi.lianji.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val Context.dataStore by preferencesDataStore("settings")

@Serializable
data class AppSettings(
    val themeMode: String = "SYSTEM",
    val dynamicColor: Boolean = true,
    val defaultRestSeconds: Int = 90,
    val vibration: Boolean = true,
    val sound: Boolean = true,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic")
        val rest = intPreferencesKey("rest")
        val vibration = booleanPreferencesKey("vibration")
        val sound = booleanPreferencesKey("sound")
    }
    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(p[Keys.theme] ?: "SYSTEM", p[Keys.dynamic] ?: true, p[Keys.rest] ?: 90, p[Keys.vibration] ?: true, p[Keys.sound] ?: true)
    }
    suspend fun setTheme(value: String) = context.dataStore.edit { it[Keys.theme] = value }
    suspend fun setDynamic(value: Boolean) = context.dataStore.edit { it[Keys.dynamic] = value }
    suspend fun setRest(value: Int) = context.dataStore.edit { it[Keys.rest] = value }
    suspend fun setVibration(value: Boolean) = context.dataStore.edit { it[Keys.vibration] = value }
    suspend fun setSound(value: Boolean) = context.dataStore.edit { it[Keys.sound] = value }
}
