package com.juzi.lianji.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.juzi.lianji.MainUiState
import com.juzi.lianji.MainViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

@Composable
fun SettingsScreen(state:MainUiState,padding:PaddingValues,listState:LazyListState,scrollBehavior:ScrollBehavior,vm:MainViewModel,snackbarHostState:SnackbarHostState,onAbout:()->Unit){val scope=rememberCoroutineScope();val themeModes=listOf("SYSTEM","LIGHT","DARK");val themeOptions=listOf("跟随系统","浅色主题","深色主题");val navigationStyles=listOf("STANDARD","FLOATING","LIQUID");val navigationStyleOptions=listOf("标准底栏","悬浮底栏","液态玻璃");val navigationModes=listOf("ICON_AND_TEXT","ICON_ONLY","SELECTED_LABEL");val navigationModeOptions=listOf("图标和文字","仅图标","仅选中项显示文字");val floatingPositions=listOf("CENTER","START","END");val floatingPositionOptions=listOf("居中","靠左","靠右");val restOptions=listOf(30,60,90,120,180);val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->uri?.let{scope.launch{runCatching{vm.backupManager.exportTo(it)}.onSuccess{snackbarHostState.showSnackbar("备份已导出")}.onFailure{snackbarHostState.showSnackbar("导出失败：${it.message}")}}}};val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{scope.launch{runCatching{vm.backupManager.importFrom(it)}.onSuccess{snackbarHostState.showSnackbar("恢复完成")}.onFailure{snackbarHostState.showSnackbar("恢复失败，原数据未修改：${it.message}")}}}}
    LazyColumn(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),state=listState,contentPadding=PaddingValues(top=padding.calculateTopPadding()+12.dp,bottom=padding.calculateBottomPadding()+24.dp)){
        item{SmallTitle("外观")}
        item{Card(Modifier.cardPadding()){Column{OverlayDropdownPreference(items=themeOptions,selectedIndex=themeModes.indexOf(state.settings.themeMode).coerceAtLeast(0),title="主题模式",startAction={Icon(MiuixIcons.Theme,null)},onSelectedIndexChange={scope.launch{vm.settingsStore.setTheme(themeModes[it])}});SwitchPreference(checked=state.settings.dynamicColor,onCheckedChange={scope.launch{vm.settingsStore.setDynamic(it)}},title="Monet 动态色",summary="跟随系统壁纸生成应用强调色")}}}
        item{SmallTitle("底部导航")}
        item{Card(Modifier.cardPadding()){Column{
            OverlayDropdownPreference(items=navigationStyleOptions,selectedIndex=navigationStyles.indexOf(state.settings.navigationBarStyle).coerceAtLeast(0),title="底栏样式",summary="标准、悬浮或官方示例的液态玻璃效果",onSelectedIndexChange={scope.launch{vm.settingsStore.setNavigationBarStyle(navigationStyles[it])}})
            AnimatedVisibility(state.settings.navigationBarStyle=="STANDARD"){OverlayDropdownPreference(items=navigationModeOptions,selectedIndex=navigationModes.indexOf(state.settings.navigationBarMode).coerceAtLeast(0),title="标签显示",onSelectedIndexChange={scope.launch{vm.settingsStore.setNavigationBarMode(navigationModes[it])}})}
            AnimatedVisibility(state.settings.navigationBarStyle=="FLOATING"){OverlayDropdownPreference(items=floatingPositionOptions,selectedIndex=floatingPositions.indexOf(state.settings.floatingNavigationBarPosition).coerceAtLeast(0),title="悬浮位置",onSelectedIndexChange={scope.launch{vm.settingsStore.setFloatingNavigationBarPosition(floatingPositions[it])}})}
        }}}
        item{SmallTitle("训练提醒")}
        item{Card(Modifier.cardPadding()){Column{OverlayDropdownPreference(items=restOptions.map(::formatRestLabel),selectedIndex=restOptions.indexOf(state.settings.defaultRestSeconds).coerceAtLeast(0),title="默认组间休息",startAction={Icon(MiuixIcons.Timer,null)},onSelectedIndexChange={scope.launch{vm.settingsStore.setRest(restOptions[it])}});SwitchPreference(checked=state.settings.vibration,onCheckedChange={scope.launch{vm.settingsStore.setVibration(it)}},title="震动提醒",summary="休息结束时振动提示");SwitchPreference(checked=state.settings.sound,onCheckedChange={scope.launch{vm.settingsStore.setSound(it)}},title="提示音",summary="休息结束时播放提示音")}}}
        item{SmallTitle("数据")}
        item{Card(Modifier.cardPadding()){Column{ArrowPreference(title="导出备份",summary="将计划、日程与历史导出为 JSON",startAction={Icon(MiuixIcons.UploadCloud,null)},onClick={export.launch("练迹备份.json")});ArrowPreference(title="恢复备份",summary="恢复前会校验文件，不覆盖损坏数据",startAction={Icon(MiuixIcons.Import,null)},onClick={import.launch(arrayOf("application/json"))})}}}
        item{SmallTitle("应用")}
        item{Card(Modifier.cardPadding()){ArrowPreference(title="关于练迹",summary="版本、数据来源、媒体授权与开源许可",startAction={Icon(MiuixIcons.Info,null)},onClick=onAbout)}}
    }
}
