// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package com.juzi.lianji.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Full-window layer drawn above page backdrops and navigation chrome so OS4 glass popups sample
 * underlying content without sampling themselves or getting clipped.
 */
@Stable
class TopLayerState {
    internal val entries = mutableStateListOf<TopLayerEntry>()
}

@Stable
internal class TopLayerEntry {
    var content: @Composable BoxScope.() -> Unit by mutableStateOf({})
}

val LocalTopLayer = staticCompositionLocalOf<TopLayerState?> { null }

/** Renders [content] in the nearest [TopLayerHost], or inline when none is installed. */
@Composable
fun TopLayer(content: @Composable BoxScope.() -> Unit) {
    val layer = LocalTopLayer.current
    if (layer == null) {
        Box(modifier = Modifier.fillMaxSize(), content = content)
        return
    }
    val entry = remember { TopLayerEntry() }
    SideEffect { entry.content = content }
    DisposableEffect(layer, entry) {
        layer.entries += entry
        onDispose { layer.entries -= entry }
    }
}

/** Hosts overlays lifted via [TopLayer] outside sampled backdrop layers. */
@Composable
fun TopLayerHost(
    state: TopLayerState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        state.entries.forEach { entry ->
            key(entry) { entry.content(this) }
        }
    }
}
