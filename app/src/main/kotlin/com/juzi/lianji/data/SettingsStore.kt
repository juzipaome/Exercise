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
    val navigationBarStyle: String = "STANDARD",
    val navigationBarMode: String = "ICON_AND_TEXT",
    val floatingNavigationBarPosition: String = "CENTER",
    val pagerGestureOverride: Boolean = true,
    val progressiveBlur: Boolean = false,
    val largeScreenDialogs: Boolean = false,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic")
        val rest = intPreferencesKey("rest")
        val vibration = booleanPreferencesKey("vibration")
        val sound = booleanPreferencesKey("sound")
        val navigationBarStyle = stringPreferencesKey("navigation_bar_style")
        val navigationBarMode = stringPreferencesKey("navigation_bar_mode")
        val floatingNavigationBarPosition = stringPreferencesKey("floating_navigation_bar_position")
        val pagerGestureOverride = booleanPreferencesKey("pager_gesture_override")
        val progressiveBlur = booleanPreferencesKey("progressive_blur")
        val largeScreenDialogs = booleanPreferencesKey("large_screen_dialogs")
    }
    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.theme] ?: "SYSTEM",
            dynamicColor = p[Keys.dynamic] ?: true,
            defaultRestSeconds = p[Keys.rest] ?: 90,
            vibration = p[Keys.vibration] ?: true,
            sound = p[Keys.sound] ?: true,
            navigationBarStyle = p[Keys.navigationBarStyle] ?: "STANDARD",
            navigationBarMode = p[Keys.navigationBarMode] ?: "ICON_AND_TEXT",
            floatingNavigationBarPosition = p[Keys.floatingNavigationBarPosition] ?: "CENTER",
            pagerGestureOverride = p[Keys.pagerGestureOverride] ?: true,
            progressiveBlur = p[Keys.progressiveBlur] ?: false,
            largeScreenDialogs = p[Keys.largeScreenDialogs] ?: false,
        )
    }
    suspend fun setTheme(value: String) = context.dataStore.edit { it[Keys.theme] = value }
    suspend fun setDynamic(value: Boolean) = context.dataStore.edit { it[Keys.dynamic] = value }
    suspend fun setRest(value: Int) = context.dataStore.edit { it[Keys.rest] = value }
    suspend fun setVibration(value: Boolean) = context.dataStore.edit { it[Keys.vibration] = value }
    suspend fun setSound(value: Boolean) = context.dataStore.edit { it[Keys.sound] = value }
    suspend fun setNavigationBarStyle(value: String) = context.dataStore.edit { it[Keys.navigationBarStyle] = value }
    suspend fun setNavigationBarMode(value: String) = context.dataStore.edit { it[Keys.navigationBarMode] = value }
    suspend fun setFloatingNavigationBarPosition(value: String) = context.dataStore.edit { it[Keys.floatingNavigationBarPosition] = value }
    suspend fun setPagerGestureOverride(value: Boolean) = context.dataStore.edit { it[Keys.pagerGestureOverride] = value }
    suspend fun setProgressiveBlur(value: Boolean) = context.dataStore.edit { it[Keys.progressiveBlur] = value }
    suspend fun setLargeScreenDialogs(value: Boolean) = context.dataStore.edit { it[Keys.largeScreenDialogs] = value }
    suspend fun restore(value: AppSettings) = context.dataStore.edit {
        it[Keys.theme]=value.themeMode; it[Keys.dynamic]=value.dynamicColor
        it[Keys.rest]=value.defaultRestSeconds; it[Keys.vibration]=value.vibration; it[Keys.sound]=value.sound
        it[Keys.navigationBarStyle]=value.navigationBarStyle; it[Keys.navigationBarMode]=value.navigationBarMode
        it[Keys.floatingNavigationBarPosition]=value.floatingNavigationBarPosition
        it[Keys.pagerGestureOverride]=value.pagerGestureOverride
        it[Keys.progressiveBlur]=value.progressiveBlur
        it[Keys.largeScreenDialogs]=value.largeScreenDialogs
    }
}
