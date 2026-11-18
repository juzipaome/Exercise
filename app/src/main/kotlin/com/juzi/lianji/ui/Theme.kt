package com.juzi.lianji.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.juzi.lianji.data.AppSettings
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassStyle
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import top.yukonga.miuix.kmp.theme.*
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

object StatusColors {
    val Healthy = Color(0xFF268A55)
    val Warning = Color(0xFFD7901D)
}

val Os4KeyColors: List<Pair<String, Color>> = listOf(
    "拂晓蓝" to Color(0xFF3482FF),
    "翠竹绿" to Color(0xFF36D167),
    "星云紫" to Color(0xFF7C4DFF),
    "琥珀黄" to Color(0xFFFFB21D),
    "赤霞橙" to Color(0xFFFF5722),
    "樱花粉" to Color(0xFFE91E63),
    "碧海青" to Color(0xFF00BCD4),
)

fun os4KeyColor(index: Int): Color? = if (index <= 0) null else Os4KeyColors.getOrNull(index - 1)?.second

@Composable
fun LianJiTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val mode = when {
        settings.dynamicColor && settings.themeMode == "LIGHT" -> ColorSchemeMode.MonetLight
        settings.dynamicColor && settings.themeMode == "DARK" -> ColorSchemeMode.MonetDark
        settings.dynamicColor -> ColorSchemeMode.MonetSystem
        settings.themeMode == "LIGHT" -> ColorSchemeMode.Light
        settings.themeMode == "DARK" -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }
    val keyColor = os4KeyColor(settings.keyColorIndex)
    val palette = ThemePaletteStyle.entries.firstOrNull { it.name == settings.paletteStyle } ?: ThemePaletteStyle.TonalSpot
    val spec = ThemeColorSpec.entries.firstOrNull { it.name == settings.colorSpec } ?: ThemeColorSpec.Spec2021
    val controller = remember(mode, keyColor, palette, spec) {
        if (keyColor != null && mode in setOf(ColorSchemeMode.MonetSystem, ColorSchemeMode.MonetLight, ColorSchemeMode.MonetDark)) {
            ThemeController(
                colorSchemeMode = mode,
                keyColor = keyColor,
                paletteStyle = palette,
                colorSpec = spec,
            )
        } else {
            ThemeController(colorSchemeMode = mode)
        }
    }
    CompositionLocalProvider(LocalSquircleEnabled provides settings.enableSquircle) {
        MiuixTheme(
            controller = controller,
            content = content,
        )
    }
}

@Composable
fun isOs4DarkTheme(): Boolean = MiuixTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun rememberOs4GlassStyle(): GlassStyle = GlassDefaults.style(isOs4DarkTheme())

@Composable
fun rememberOs4CustomMaterial(): GlassMaterial? {
    val isDark = isOs4DarkTheme()
    return when (LocalMiuixFeatureSettings.current.glassMaterialPreset) {
        "PURED_THIN" -> GlassMaterials.puredThinGlass(isDark)
        "POPUP_VIEW" -> GlassMaterials.popupViewGlass(isDark)
        else -> null
    }
}

@Composable
fun rememberOs4EdgeStroke(defaultStroke: EdgeStroke?): EdgeStroke? {
    val isDark = isOs4DarkTheme()
    return when (LocalMiuixFeatureSettings.current.glassEdgeStroke) {
        "SMALL" -> if (isDark) GlassEdgeStrokes.SmallDark else GlassEdgeStrokes.SmallLight
        "MIDDLE" -> if (isDark) GlassEdgeStrokes.MiddleDark else GlassEdgeStrokes.MiddleLight
        "BIG" -> if (isDark) GlassEdgeStrokes.BigDark else GlassEdgeStrokes.BigLight
        "NONE" -> null
        else -> defaultStroke
    }
}

@Composable
fun Modifier.os4ScrollModifiers(scrollBehavior: ScrollBehavior? = null): Modifier {
    val settings = LocalMiuixFeatureSettings.current
    return this
        .then(if (settings.scrollEndHaptic) Modifier.scrollEndHaptic() else Modifier)
        .overScrollVertical(
            onOffsetChanged = scrollBehavior?.let { behavior -> { behavior.state.overscrollOffset = it } },
        )
        .then(if (scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier)
}
