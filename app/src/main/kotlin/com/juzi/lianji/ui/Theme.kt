package com.juzi.lianji.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
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
    MiuixTheme(
        controller = remember(mode) { ThemeController(colorSchemeMode = mode) },
        content = content,
    )
}
