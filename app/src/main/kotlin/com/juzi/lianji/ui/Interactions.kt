package com.juzi.lianji.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

internal object NoHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

/** Uses the same toggle haptic as MIUIX Checkbox when its surrounding row is tapped. */
@Composable
fun hapticRowClick(checked: Boolean, onClick: () -> Unit): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics, checked, onClick) {
        {
            haptics.performHapticFeedback(if(checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
            onClick()
        }
    }
}
