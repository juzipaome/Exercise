package com.juzi.lianji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * OS4 page scaffold: captures underlying surface & page content for [GlassTopAppBar],
 * floating glass controls, and overlay layers without self-sampling.
 */
@Composable
fun MiuixPageScaffold(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingToolbar: @Composable () -> Unit = {},
    floatingToolbarPosition: ToolbarPosition = ToolbarPosition.BottomCenter,
    titleProvider: (() -> String)? = null,
    scrollBehavior: ScrollBehavior? = null,
    overlay: (@Composable BoxScope.(Backdrop?) -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val settings = LocalMiuixFeatureSettings.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    val shaderSupported = isRuntimeShaderSupported()
    val pageBackdrop = if (shaderSupported && overlay != null && LocalRootBackdrop.current == null) {
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }
    val contentBackdrop = if (shaderSupported) {
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }
    val surfaceBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
    }
    val parentTopLayer = LocalTopLayer.current
    val localTopLayer = remember { TopLayerState() }
    val resolvedTopLayer = parentTopLayer ?: localTopLayer

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalPageBackdrop provides contentBackdrop,
            LocalSurfaceBackdrop provides surfaceBackdrop,
            LocalTopLayer provides resolvedTopLayer,
        ) {
            Scaffold(
                modifier = if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier,
                topBar = {
                    GlassTopAppBar(
                        title = titleProvider?.invoke() ?: title,
                        expandable = false,
                        progressiveBlur = settings.progressiveBlur && contentBackdrop != null,
                        backdrop = contentBackdrop,
                        scrollBehavior = scrollBehavior,
                        style = rememberOs4GlassStyle(),
                        material = rememberOs4CustomMaterial() ?: GlassTopAppBarDefaults.buttonMaterial(),
                        stroke = rememberOs4EdgeStroke(GlassTopAppBarDefaults.buttonStroke()),
                        navigationIcon = navigationIcon,
                        actions = actions,
                    )
                },
                bottomBar = bottomBar,
                floatingToolbar = floatingToolbar,
                floatingToolbarPosition = floatingToolbarPosition,
            ) { padding ->
                Box(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .layerBackdrop(surfaceBackdrop)
                            .background(surfaceColor),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (contentBackdrop != null) Modifier.layerBackdrop(contentBackdrop) else Modifier),
                    ) {
                        content(padding)
                    }
                }
            }
            // Keep glass overlays outside the backdrop they sample.
            if (overlay != null) TopLayer { overlay(pageBackdrop) }
        }
        if (parentTopLayer == null) {
            TopLayerHost(localTopLayer)
        }
    }
}

@Composable
internal fun PaddingValues.withPageSpacing(top: Dp = 12.dp, bottom: Dp = 24.dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        end = calculateEndPadding(direction),
        top = calculateTopPadding() + top,
        bottom = calculateBottomPadding() + bottom,
    )
}

@Composable
fun ExerciseSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SearchBar(
        inputField = {
            InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { expanded = false },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                label = "搜索动作",
            )
        },
        onExpandedChange = { expanded = it },
        modifier = modifier,
        expanded = expanded,
        outsideEndAction = {
            TextButton("取消", onClick = { onQueryChange(""); expanded = false })
        },
    ) {}
}

/** Observe the MIUIX top bar without stealing clicks from navigation/actions. */
fun Modifier.topAppBarDoubleTap(onDoubleTap: () -> Unit): Modifier = pointerInput(onDoubleTap) {
    awaitPointerEventScope {
        while (true) {
            val firstDown=awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            var firstUpAt:Long?=null
            while (firstUpAt==null) {
                val event=awaitPointerEvent(PointerEventPass.Final)
                val change=event.changes.firstOrNull { it.id==firstDown.id } ?: break
                if (!change.pressed && change.previousPressed) firstUpAt=change.uptimeMillis
            }
            val firstUp=firstUpAt ?: continue
            var secondDownAt:Long?=null
            var secondPointerId=firstDown.id
            while (true) {
                val event=awaitPointerEvent(PointerEventPass.Final)
                val change=event.changes.firstOrNull() ?: continue
                if (change.pressed && !change.previousPressed) {
                    if (change.uptimeMillis-firstUp>viewConfiguration.doubleTapTimeoutMillis) break
                    secondDownAt=change.uptimeMillis
                    secondPointerId=change.id
                } else if (secondDownAt!=null && change.id==secondPointerId && !change.pressed && change.previousPressed) {
                    onDoubleTap()
                    break
                }
            }
        }
    }
}
