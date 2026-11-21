package com.juzi.lianji.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassBottomSheetDefaults
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassDialogDefaults
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Project-wide sheet entry point backed by OS4 [GlassBottomSheet] inside a root Scaffold host,
 * falling back to [WindowBottomSheet] for standalone screen test harnesses.
 */
@Composable
fun FloatingBottomSheet(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val useGlass = LocalHasRootScaffold.current
    val sheetBody: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .then(if (useGlass) Modifier.navigationBarsPadding() else Modifier)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
    if (useGlass) {
        GlassBottomSheet(
            show = show,
            backdrop = LocalRootBackdrop.current,
            title = title,
            onDismissRequest = onDismissRequest,
            allowDismiss = true,
            enableNestedScroll = true,
            material = rememberOs4CustomMaterial() ?: GlassBottomSheetDefaults.material(),
            stroke = rememberOs4EdgeStroke(GlassBottomSheetDefaults.stroke()),
        ) {
            sheetBody()
        }
    } else {
        WindowBottomSheet(
            show = show,
            title = title,
            onDismissRequest = onDismissRequest,
            allowDismiss = true,
            enableNestedScroll = true,
        ) {
            sheetBody()
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
    destructivePrimary: Boolean = false,
    destructiveSecondary: Boolean = false,
) {
    val settings = LocalMiuixFeatureSettings.current
    if (LocalHasRootScaffold.current) {
        GlassDialog(
            show = show,
            backdrop = LocalRootBackdrop.current,
            title = title,
            summary = summary,
            onDismissRequest = onDismissRequest,
            largeScreen = settings.largeScreenDialogs,
            material = rememberOs4CustomMaterial() ?: GlassDialogDefaults.material(),
            stroke = rememberOs4EdgeStroke(GlassDialogDefaults.stroke()),
        ) {
            val dismissState = LocalDismissState.current
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassDialogButton(
                    onClick = { onSecondary(); (dismissState ?: onDismissRequest).invoke() },
                    modifier = Modifier.weight(1f),
                    role = if (destructiveSecondary) GlassDialogButtonRole.Destructive else GlassDialogButtonRole.Normal,
                ) {
                    Text(secondaryText, style = MiuixTheme.textStyles.button)
                }
                GlassDialogButton(
                    onClick = { onPrimary(); (dismissState ?: onDismissRequest).invoke() },
                    modifier = Modifier.weight(1f),
                    role = if (destructivePrimary) GlassDialogButtonRole.Destructive else GlassDialogButtonRole.Primary,
                ) {
                    Text(primaryText, style = MiuixTheme.textStyles.button)
                }
            }
        }
    } else {
        WindowDialog(
            show = show,
            title = title,
            summary = summary,
            onDismissRequest = onDismissRequest,
            largeScreen = settings.largeScreenDialogs,
        ) {
            val dismissState = LocalDismissState.current
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = secondaryText,
                    onClick = { onSecondary(); (dismissState ?: onDismissRequest).invoke() },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = primaryText,
                    onClick = { onPrimary(); (dismissState ?: onDismissRequest).invoke() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

/**
 * Text field tailored for [FloatingBottomSheet], using OS4 [GlassTextField] over frosted
 * glass surfaces and falling back to [TextField] in standalone test harnesses.
 */
@Composable
fun SheetTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    useLabelAsPlaceholder: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    if (LocalHasRootScaffold.current) {
        GlassTextField(
            state = state,
            modifier = modifier,
            label = label,
            useLabelAsPlaceholder = useLabelAsPlaceholder,
            keyboardOptions = keyboardOptions,
        )
    } else {
        TextField(
            state = state,
            modifier = modifier,
            label = label,
            useLabelAsPlaceholder = useLabelAsPlaceholder,
            keyboardOptions = keyboardOptions,
        )
    }
}
