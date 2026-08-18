package com.juzi.lianji.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.juzi.lianji.data.AppSettings
import top.yukonga.miuix.kmp.theme.*

object StatusColors {
    val Healthy = Color(0xFF268A55)
    val Warning = Color(0xFFD7901D)
}

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
    val typography = remember {
        val defaults = defaultTextStyles()
        defaults.copy(
            title1 = defaults.title1.copy(fontSize = 28.sp),
            title2 = defaults.title2.copy(fontSize = 21.sp),
            title3 = defaults.title3.copy(fontSize = 18.sp),
            title4 = defaults.title4.copy(fontSize = 17.sp),
        )
    }
    MiuixTheme(
        controller = remember(mode) { ThemeController(colorSchemeMode = mode) },
        textStyles = typography,
        content = content,
    )
}
