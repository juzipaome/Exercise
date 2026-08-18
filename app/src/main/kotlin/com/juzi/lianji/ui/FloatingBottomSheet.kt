package com.juzi.lianji.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Project-wide sheet entry point backed entirely by MIUIX 0.9.4-rc01.
 * MIUIX owns the scrim, enter/exit motion, drag handling and outside-tap dismissal.
 */
@Composable
fun FloatingBottomSheet(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    WindowBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
        allowDismiss = true,
        enableNestedScroll = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
fun MiuixActionDialog(
    show: Boolean,
    title: String,
    summary: String,
    secondaryText: String,
    primaryText: String,
    onDismissRequest: () -> Unit,
    onSecondary: () -> Unit,
    onPrimary: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
    ) {
        val dismissState = LocalDismissState.current
        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(
                text = secondaryText,
                onClick = { onSecondary(); dismissState?.invoke() },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = primaryText,
                onClick = { onPrimary(); dismissState?.invoke() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
