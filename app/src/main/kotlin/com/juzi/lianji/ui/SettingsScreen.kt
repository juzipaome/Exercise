package com.juzi.lianji.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

@Composable
fun SettingsScreen(state:MainUiState,padding:PaddingValues,listState:LazyListState,scrollBehavior:ScrollBehavior,vm:MainViewModel,onAbout:()->Unit){val scope=rememberCoroutineScope();var message by rememberSaveable{mutableStateOf("")};val themeModes=listOf("SYSTEM","LIGHT","DARK");val themeOptions=listOf("跟随系统","浅色主题","深色主题");val restOptions=listOf(30,60,90,120,180);val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->uri?.let{scope.launch{runCatching{vm.backupManager.exportTo(it)}.onSuccess{message="备份已导出"}.onFailure{message="导出失败：${it.message}"}}}};val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{scope.launch{runCatching{vm.backupManager.importFrom(it)}.onSuccess{message="恢复完成"}.onFailure{message="恢复失败，原数据未修改：${it.message}"}}}}
    LazyColumn(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),state=listState,contentPadding=PaddingValues(top=padding.calculateTopPadding()+12.dp,bottom=padding.calculateBottomPadding()+24.dp)){
        item{SmallTitle("外观")}
        item{Card(Modifier.cardPadding()){Column{OverlayDropdownPreference(items=themeOptions,selectedIndex=themeModes.indexOf(state.settings.themeMode).coerceAtLeast(0),title="主题模式",startAction={Icon(MiuixIcons.Theme,"主题模式")},onSelectedIndexChange={scope.launch{vm.settingsStore.setTheme(themeModes[it])}});HorizontalDivider(Modifier.padding(horizontal=16.dp));SwitchPreference(checked=state.settings.dynamicColor,onCheckedChange={scope.launch{vm.settingsStore.setDynamic(it)}},title="Monet 动态色",summary="跟随系统壁纸生成应用强调色")}}}
        item{SmallTitle("训练提醒")}
        item{Card(Modifier.cardPadding()){Column{OverlayDropdownPreference(items=restOptions.map(::formatRestLabel),selectedIndex=restOptions.indexOf(state.settings.defaultRestSeconds).coerceAtLeast(0),title="默认组间休息",startAction={Icon(MiuixIcons.Timer,"默认组间休息")},onSelectedIndexChange={scope.launch{vm.settingsStore.setRest(restOptions[it])}});HorizontalDivider(Modifier.padding(horizontal=16.dp));SwitchPreference(checked=state.settings.vibration,onCheckedChange={scope.launch{vm.settingsStore.setVibration(it)}},title="震动提醒",summary="休息结束时振动提示");HorizontalDivider(Modifier.padding(horizontal=16.dp));SwitchPreference(checked=state.settings.sound,onCheckedChange={scope.launch{vm.settingsStore.setSound(it)}},title="提示音",summary="休息结束时播放提示音")}}}
        item{SmallTitle("数据与应用")}
        item{Card(Modifier.cardPadding()){Column{ArrowPreference(title="导出备份",summary="将计划、日程与历史导出为 JSON",startAction={Icon(MiuixIcons.UploadCloud,"导出备份")},onClick={export.launch("练迹备份.json")});HorizontalDivider(Modifier.padding(horizontal=16.dp));ArrowPreference(title="恢复备份",summary="恢复前会校验文件，不覆盖损坏数据",startAction={Icon(MiuixIcons.Import,"恢复备份")},onClick={import.launch(arrayOf("application/json"))});HorizontalDivider(Modifier.padding(horizontal=16.dp));ArrowPreference(title="关于练迹",summary="动作来源、媒体授权与开源许可",startAction={Icon(MiuixIcons.Info,"关于练迹")},onClick=onAbout)}}}
        if(message.isNotBlank())item{Text(message,Modifier.padding(horizontal=20.dp,vertical=4.dp),color=MiuixTheme.colorScheme.primary)}
    }
}

@Composable fun AboutScreen(onBack:()->Unit){MiuixPageScaffold(title="关于",navigationIcon={BackButton(onBack)}){pad->LazyColumn(contentPadding=PaddingValues(top=pad.calculateTopPadding()+12.dp,bottom=24.dp)){item{Card(Modifier.cardPadding()){Column(Modifier.padding(20.dp)){Text("练迹",style=MiuixTheme.textStyles.title1);Text("0.1.0 · 私人离线健身记录",color=MiuixTheme.colorScheme.onSurfaceSecondary);Spacer(Modifier.height(18.dp));Text("动作数据");Text("hasaneyldrm/exercises-dataset，数据与说明采用 MIT License。");Spacer(Modifier.height(12.dp));Text("动作媒体");Text("© Gym visual — https://gymvisual.com/\n本应用中的 180×180 图片和 GIF 按个人授权使用。未经重新确认授权，不应公开分发媒体版本。",color=MiuixTheme.colorScheme.onSurfaceSecondary);Spacer(Modifier.height(12.dp));Text("界面");Text("MIUIX 0.9.4-rc01 · Apache-2.0")}}}}}}


