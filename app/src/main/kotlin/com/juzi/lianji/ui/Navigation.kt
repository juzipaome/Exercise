package com.juzi.lianji.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.icon.glass.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

val LocalAnimatedBack = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
fun TopBarIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    GlassIconButton(
        onClick = onClick,
        modifier = modifier,
        style = rememberOs4GlassStyle(),
        material = rememberOs4CustomMaterial(),
        stroke = rememberOs4EdgeStroke(GlassTopAppBarDefaults.buttonStroke()),
        content = content,
    )
}

@Composable
fun BackButton(onClick: () -> Unit) {
    val action = LocalAnimatedBack.current ?: onClick
    val layoutDirection = LocalLayoutDirection.current
    TopBarIconButton(onClick = action) {
        Icon(
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer {
                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                },
            imageVector = MiuixIcons.Glass.ChevronBackward,
            contentDescription = "返回",
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun PreviousButton(onClick: () -> Unit) {
    val layoutDirection = LocalLayoutDirection.current
    GlassIconButton(
        onClick = onClick,
        size = 40.dp,
        style = rememberOs4GlassStyle(),
        material = rememberOs4CustomMaterial(),
        stroke = rememberOs4EdgeStroke(GlassTopAppBarDefaults.buttonStroke()),
    ) {
        Icon(
            imageVector = MiuixIcons.Glass.ChevronBackward,
            contentDescription = "上月",
            modifier = Modifier.size(24.dp).graphicsLayer {
                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
            },
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun NextButton(onClick: () -> Unit) {
    val layoutDirection = LocalLayoutDirection.current
    GlassIconButton(
        onClick = onClick,
        size = 40.dp,
        style = rememberOs4GlassStyle(),
        material = rememberOs4CustomMaterial(),
        stroke = rememberOs4EdgeStroke(GlassTopAppBarDefaults.buttonStroke()),
    ) {
        Icon(
            imageVector = MiuixIcons.Glass.ChevronForward,
            contentDescription = "下月",
            modifier = Modifier.size(24.dp).graphicsLayer {
                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
            },
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}
