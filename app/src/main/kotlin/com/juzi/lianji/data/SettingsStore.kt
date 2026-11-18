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
    val keyColorIndex: Int = 0,
    val paletteStyle: String = "TonalSpot",
    val colorSpec: String = "Spec2021",
    val defaultRestSeconds: Int = 90,
    val vibration: Boolean = true,
    val sound: Boolean = true,
    val glassMaterialPreset: String = "DEFAULT",
    val glassEdgeStroke: String = "DEFAULT",
    val progressiveBlur: Boolean = true,
    val navigationBarAction: Boolean = true,
    val enableSquircle: Boolean = true,
    val largeScreenDialogs: Boolean = false,
    val pagerGestureOverride: Boolean = true,
    val scrollEndHaptic: Boolean = true,
    val enableSwipeBack: Boolean = true,
    val navCornerClip: Boolean = true,
    val navDim: Boolean = true,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic")
        val keyColorIndex = intPreferencesKey("key_color_index")
        val paletteStyle = stringPreferencesKey("palette_style")
        val colorSpec = stringPreferencesKey("color_spec")
        val rest = intPreferencesKey("rest")
        val vibration = booleanPreferencesKey("vibration")
        val sound = booleanPreferencesKey("sound")
        val glassMaterialPreset = stringPreferencesKey("glass_material_preset")
        val glassEdgeStroke = stringPreferencesKey("glass_edge_stroke")
        val progressiveBlur = booleanPreferencesKey("progressive_blur")
        val navigationBarAction = booleanPreferencesKey("navigation_bar_action")
        val enableSquircle = booleanPreferencesKey("enable_squircle")
        val largeScreenDialogs = booleanPreferencesKey("large_screen_dialogs")
        val pagerGestureOverride = booleanPreferencesKey("pager_gesture_override")
        val scrollEndHaptic = booleanPreferencesKey("scroll_end_haptic")
        val enableSwipeBack = booleanPreferencesKey("enable_swipe_back")
        val navCornerClip = booleanPreferencesKey("nav_corner_clip")
        val navDim = booleanPreferencesKey("nav_dim")
    }
    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.theme] ?: "SYSTEM",
            dynamicColor = p[Keys.dynamic] ?: true,
            keyColorIndex = (p[Keys.keyColorIndex] ?: 0).coerceIn(0, 7),
            paletteStyle = p[Keys.paletteStyle] ?: "TonalSpot",
            colorSpec = p[Keys.colorSpec] ?: "Spec2021",
            defaultRestSeconds = p[Keys.rest] ?: 90,
            vibration = p[Keys.vibration] ?: true,
            sound = p[Keys.sound] ?: true,
            glassMaterialPreset = p[Keys.glassMaterialPreset] ?: "DEFAULT",
            glassEdgeStroke = p[Keys.glassEdgeStroke] ?: "DEFAULT",
            progressiveBlur = p[Keys.progressiveBlur] ?: true,
            navigationBarAction = p[Keys.navigationBarAction] ?: true,
            enableSquircle = p[Keys.enableSquircle] ?: true,
            largeScreenDialogs = p[Keys.largeScreenDialogs] ?: false,
            pagerGestureOverride = p[Keys.pagerGestureOverride] ?: true,
            scrollEndHaptic = p[Keys.scrollEndHaptic] ?: true,
            enableSwipeBack = p[Keys.enableSwipeBack] ?: true,
            navCornerClip = p[Keys.navCornerClip] ?: true,
            navDim = p[Keys.navDim] ?: true,
        )
    }
    suspend fun setTheme(value: String) = context.dataStore.edit { it[Keys.theme] = value }
    suspend fun setDynamic(value: Boolean) = context.dataStore.edit { it[Keys.dynamic] = value }
    suspend fun setKeyColorIndex(value: Int) = context.dataStore.edit { it[Keys.keyColorIndex] = value.coerceIn(0, 7) }
    suspend fun setPaletteStyle(value: String) = context.dataStore.edit { it[Keys.paletteStyle] = value }
    suspend fun setColorSpec(value: String) = context.dataStore.edit { it[Keys.colorSpec] = value }
    suspend fun setRest(value: Int) = context.dataStore.edit { it[Keys.rest] = value }
    suspend fun setVibration(value: Boolean) = context.dataStore.edit { it[Keys.vibration] = value }
    suspend fun setSound(value: Boolean) = context.dataStore.edit { it[Keys.sound] = value }
    suspend fun setGlassMaterialPreset(value: String) = context.dataStore.edit { it[Keys.glassMaterialPreset] = value }
    suspend fun setGlassEdgeStroke(value: String) = context.dataStore.edit { it[Keys.glassEdgeStroke] = value }
    suspend fun setProgressiveBlur(value: Boolean) = context.dataStore.edit { it[Keys.progressiveBlur] = value }
    suspend fun setNavigationBarAction(value: Boolean) = context.dataStore.edit { it[Keys.navigationBarAction] = value }
    suspend fun setEnableSquircle(value: Boolean) = context.dataStore.edit { it[Keys.enableSquircle] = value }
    suspend fun setLargeScreenDialogs(value: Boolean) = context.dataStore.edit { it[Keys.largeScreenDialogs] = value }
    suspend fun setPagerGestureOverride(value: Boolean) = context.dataStore.edit { it[Keys.pagerGestureOverride] = value }
    suspend fun setScrollEndHaptic(value: Boolean) = context.dataStore.edit { it[Keys.scrollEndHaptic] = value }
    suspend fun setEnableSwipeBack(value: Boolean) = context.dataStore.edit { it[Keys.enableSwipeBack] = value }
    suspend fun setNavCornerClip(value: Boolean) = context.dataStore.edit { it[Keys.navCornerClip] = value }
    suspend fun setNavDim(value: Boolean) = context.dataStore.edit { it[Keys.navDim] = value }
    suspend fun restore(value: AppSettings) = context.dataStore.edit {
        it[Keys.theme] = value.themeMode
        it[Keys.dynamic] = value.dynamicColor
        it[Keys.keyColorIndex] = value.keyColorIndex.coerceIn(0, 7)
        it[Keys.paletteStyle] = value.paletteStyle
        it[Keys.colorSpec] = value.colorSpec
        it[Keys.rest] = value.defaultRestSeconds
        it[Keys.vibration] = value.vibration
        it[Keys.sound] = value.sound
        it[Keys.glassMaterialPreset] = value.glassMaterialPreset
        it[Keys.glassEdgeStroke] = value.glassEdgeStroke
        it[Keys.progressiveBlur] = value.progressiveBlur
        it[Keys.navigationBarAction] = value.navigationBarAction
        it[Keys.enableSquircle] = value.enableSquircle
        it[Keys.largeScreenDialogs] = value.largeScreenDialogs
        it[Keys.pagerGestureOverride] = value.pagerGestureOverride
        it[Keys.scrollEndHaptic] = value.scrollEndHaptic
        it[Keys.enableSwipeBack] = value.enableSwipeBack
        it[Keys.navCornerClip] = value.navCornerClip
        it[Keys.navDim] = value.navDim
    }
}
