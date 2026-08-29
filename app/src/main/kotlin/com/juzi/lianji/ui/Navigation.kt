package com.juzi.lianji.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

val LocalAnimatedBack = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable fun BackButton(onClick:()->Unit)=IconButton(onClick=LocalAnimatedBack.current?:onClick){Icon(MiuixIcons.Back,"返回")}
@Composable fun PreviousButton(onClick:()->Unit)=IconButton(onClick=onClick){Icon(MiuixIcons.ChevronBackward,"上月")}
@Composable fun NextButton(onClick:()->Unit)=IconButton(onClick=onClick){Icon(MiuixIcons.ChevronForward,"下月")}
