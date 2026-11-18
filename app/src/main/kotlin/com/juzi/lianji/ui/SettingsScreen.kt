package com.juzi.lianji.ui

import android.app.AlarmManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.juzi.lianji.MainUiState
import com.juzi.lianji.MainViewModel
import com.juzi.lianji.data.AppSettings
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Import
import top.yukonga.miuix.kmp.icon.glass.Info
import top.yukonga.miuix.kmp.icon.glass.Theme
import top.yukonga.miuix.kmp.icon.glass.Timer
import top.yukonga.miuix.kmp.icon.glass.UploadCloud
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

private val PaletteOptions = listOf(
    ThemePaletteStyle.TonalSpot to "色调斑点",
    ThemePaletteStyle.Neutral to "柔和中性",
    ThemePaletteStyle.Vibrant to "鲜艳撞色",
    ThemePaletteStyle.Expressive to "富有表现力",
    ThemePaletteStyle.Rainbow to "彩虹",
    ThemePaletteStyle.FruitSalad to "水果沙拉",
    ThemePaletteStyle.Monochrome to "单色极简",
    ThemePaletteStyle.Fidelity to "高保真",
    ThemePaletteStyle.Content to "内容优先",
    ThemePaletteStyle.Cmf to "CMF 专属",
)

@Composable
fun SettingsScreen(
    state: MainUiState,
    padding: PaddingValues,
    listState: LazyListState,
    scrollBehavior: ScrollBehavior,
    vm: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onAppearance: () -> Unit,
    onAbout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val restOptions = listOf(30, 60, 90, 120, 180)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            scope.launch {
                runCatching { vm.backupManager.exportTo(it) }
                    .onSuccess { snackbarHostState.showSnackbar("备份已导出") }
                    .onFailure { snackbarHostState.showSnackbar("导出失败：${it.message}") }
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                runCatching { vm.backupManager.importFrom(it) }
                    .onSuccess { message -> snackbarHostState.showSnackbar(message) }
                    .onFailure { snackbarHostState.showSnackbar("恢复失败：${it.message}") }
            }
        }
    }
    val context = LocalContext.current
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    var exactAlarms by remember { mutableStateOf(alarmManager.canScheduleExactAlarms()) }
    val alarmPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        exactAlarms = alarmManager.canScheduleExactAlarms()
        (context.applicationContext as com.juzi.lianji.LianJiApplication).restReminder.refresh()
    }
    SettingsList(padding, listState, scrollBehavior) {
        item {
            Card(Modifier.cardPadding()) {
                ArrowPreference(
                    title = "外观",
                    summary = "主题、玻璃效果、手势与动效",
                    startAction = { Icon(MiuixIcons.Glass.Theme, null, Modifier.size(22.dp)) },
                    onClick = onAppearance,
                )
            }
        }
        item { SmallTitle("训练提醒") }
        item {
            Card(Modifier.cardPadding()) {
                Column {
                    Os4DropdownPreference(
                        items = restOptions.map(::formatRestLabel),
                        selectedIndex = restOptions.indexOf(state.settings.defaultRestSeconds).coerceAtLeast(0),
                        title = "默认组间休息",
                        startAction = { Icon(MiuixIcons.Glass.Timer, null, Modifier.size(22.dp)) },
                        onSelectedIndexChange = { scope.launch { vm.settingsStore.setRest(restOptions[it]) } },
                    )
                    SwitchPreference(
                        checked = state.settings.vibration,
                        onCheckedChange = { scope.launch { vm.settingsStore.setVibration(it) } },
                        title = "震动提醒",
                        summary = "休息结束时振动提示",
                    )
                    SwitchPreference(
                        checked = state.settings.sound,
                        onCheckedChange = { scope.launch { vm.settingsStore.setSound(it) } },
                        title = "提示音",
                        summary = "休息结束时播放提示音",
                    )
                }
            }
        }
        item {
            Card(Modifier.cardPadding()) {
                ArrowPreference(
                    title = "准时休息提醒",
                    summary = if (exactAlarms) "已允许；可在离开应用后按时提醒" else "未允许精确闹钟，后台提醒可能延迟；点击设置",
                    onClick = {
                        alarmPermission.launch(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                        )
                    },
                )
            }
        }
        item { SmallTitle("数据") }
        item {
            Card(Modifier.cardPadding()) {
                Column {
                    ArrowPreference(
                        title = "导出备份",
                        summary = "导出计划、日程、历史、收藏和设置",
                        startAction = { Icon(MiuixIcons.Glass.UploadCloud, null, Modifier.size(22.dp)) },
                        onClick = { export.launch("练迹备份.json") },
                    )
                    ArrowPreference(
                        title = "恢复备份",
                        summary = "合并导入，同一备份不重复添加；未完成训练保留为中断历史",
                        startAction = { Icon(MiuixIcons.Glass.Import, null, Modifier.size(22.dp)) },
                        onClick = { import.launch(arrayOf("application/json")) },
                    )
                }
            }
        }
        item { SmallTitle("应用") }
        item {
            Card(Modifier.cardPadding()) {
                ArrowPreference(
                    title = "关于练迹",
                    summary = "版本、数据来源、媒体授权与开源许可",
                    startAction = { Icon(MiuixIcons.Glass.Info, null, Modifier.size(22.dp)) },
                    onClick = onAbout,
                )
            }
        }
    }
}

@Composable
fun AppearanceScreen(settings: AppSettings, vm: MainViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val themeModes = listOf("SYSTEM", "LIGHT", "DARK")
    val themeOptions = listOf("跟随系统", "浅色主题", "深色主题")
    val keyColorOptions = listOf("跟随系统壁纸") + Os4KeyColors.map { it.first }
    val materialPresets = listOf("DEFAULT", "PURED_THIN", "POPUP_VIEW")
    val materialPresetOptions = listOf("组件默认", "纯净薄透玻璃", "弹窗柔雾玻璃")
    val edgeStrokePresets = listOf("DEFAULT", "SMALL", "MIDDLE", "BIG", "NONE")
    val edgeStrokeOptions = listOf("默认自适应", "细腻", "适中", "明显", "关闭")

    MiuixPageScaffold(
        title = "外观",
        navigationIcon = { BackButton(onBack) },
        scrollBehavior = scrollBehavior,
    ) { padding ->
        SettingsList(padding, listState, scrollBehavior) {
            item { SmallTitle("主题与色彩") }
            item {
                Card(Modifier.cardPadding()) {
                    Column {
                        Os4DropdownPreference(
                            items = themeOptions,
                            selectedIndex = themeModes.indexOf(settings.themeMode).coerceAtLeast(0),
                            title = "深浅模式",
                            onSelectedIndexChange = { scope.launch { vm.settingsStore.setTheme(themeModes[it]) } },
                        )
                        SwitchPreference(
                            checked = settings.dynamicColor,
                            onCheckedChange = { scope.launch { vm.settingsStore.setDynamic(it) } },
                            title = "Monet 动态取色",
                            summary = "生成统一的系统级动态色调",
                        )
                        AnimatedVisibility(settings.dynamicColor) {
                            Column {
                                Os4DropdownPreference(
                                    items = keyColorOptions,
                                    selectedIndex = settings.keyColorIndex.coerceIn(0, keyColorOptions.lastIndex),
                                    title = "取色来源",
                                    onSelectedIndexChange = { scope.launch { vm.settingsStore.setKeyColorIndex(it) } },
                                )
                                AnimatedVisibility(settings.keyColorIndex > 0) {
                                    Column {
                                        val selectedPaletteIndex = PaletteOptions.indexOfFirst { it.first.name == settings.paletteStyle }.coerceAtLeast(0)
                                        val selectedPalette = PaletteOptions[selectedPaletteIndex].first
                                        Os4DropdownPreference(
                                            items = PaletteOptions.map { it.second },
                                            selectedIndex = selectedPaletteIndex,
                                            title = "调色板风格",
                                            onSelectedIndexChange = { scope.launch { vm.settingsStore.setPaletteStyle(PaletteOptions[it].first.name) } },
                                        )
                                        val isCmf = selectedPalette == ThemePaletteStyle.Cmf
                                        val isSpec2025Supported = selectedPalette in setOf(
                                            ThemePaletteStyle.TonalSpot,
                                            ThemePaletteStyle.Neutral,
                                            ThemePaletteStyle.Vibrant,
                                            ThemePaletteStyle.Expressive,
                                        )
                                        val (specItems, specIndex, specEnabled) = when {
                                            isCmf -> Triple(listOf("Spec2026"), 0, false)
                                            !isSpec2025Supported -> Triple(listOf("Spec2021"), 0, false)
                                            else -> Triple(
                                                listOf("Spec2021", "Spec2025"),
                                                if (settings.colorSpec == ThemeColorSpec.Spec2025.name) 1 else 0,
                                                true,
                                            )
                                        }
                                        Os4DropdownPreference(
                                            items = specItems,
                                            selectedIndex = specIndex,
                                            title = "色彩规范",
                                            enabled = specEnabled,
                                            onSelectedIndexChange = {
                                                val chosen = if (it == 1) ThemeColorSpec.Spec2025.name else ThemeColorSpec.Spec2021.name
                                                scope.launch { vm.settingsStore.setColorSpec(chosen) }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { SmallTitle("玻璃效果") }
            item {
                Card(Modifier.cardPadding()) {
                    Column {
                        Os4DropdownPreference(
                            items = materialPresetOptions,
                            selectedIndex = materialPresets.indexOf(settings.glassMaterialPreset).coerceAtLeast(0),
                            title = "玻璃材质预设",
                            summary = "切换顶栏、底栏与弹层的玻璃融合厚度",
                            onSelectedIndexChange = { scope.launch { vm.settingsStore.setGlassMaterialPreset(materialPresets[it]) } },
                        )
                        Os4DropdownPreference(
                            items = edgeStrokeOptions,
                            selectedIndex = edgeStrokePresets.indexOf(settings.glassEdgeStroke).coerceAtLeast(0),
                            title = "边缘高光描边",
                            summary = "调整 OS4 玻璃组件轮廓高光强度",
                            onSelectedIndexChange = { scope.launch { vm.settingsStore.setGlassEdgeStroke(edgeStrokePresets[it]) } },
                        )
                        SwitchPreference(
                            checked = settings.progressiveBlur,
                            onCheckedChange = { scope.launch { vm.settingsStore.setProgressiveBlur(it) } },
                            title = "顶栏渐进模糊",
                            summary = "顶栏背景随内容滚动渐进过渡模糊强度",
                        )
                        SwitchPreference(
                            checked = settings.navigationBarAction,
                            onCheckedChange = { scope.launch { vm.settingsStore.setNavigationBarAction(it) } },
                            title = "底栏快捷按钮",
                            summary = "在底栏旁显示当前页面的玻璃快捷动作胶囊",
                        )
                        SwitchPreference(
                            checked = settings.enableSquircle,
                            onCheckedChange = { scope.launch { vm.settingsStore.setEnableSquircle(it) } },
                            title = "平滑超椭圆圆角",
                            summary = "为卡片与容器启用高阶曲率连续圆角",
                        )
                        SwitchPreference(
                            checked = settings.largeScreenDialogs,
                            onCheckedChange = { scope.launch { vm.settingsStore.setLargeScreenDialogs(it) } },
                            title = "大屏居中弹窗",
                            summary = "在宽屏设备上使用居中缩放式玻璃对话框",
                        )
                    }
                }
            }
            item { SmallTitle("手势与动效") }
            item {
                Card(Modifier.cardPadding()) {
                    Column {
                        SwitchPreference(
                            checked = settings.pagerGestureOverride,
                            onCheckedChange = { scope.launch { vm.settingsStore.setPagerGestureOverride(it) } },
                            title = "增强跨轴分页手势",
                            summary = "列表滚动或回弹时仍可横向切换主页面",
                        )
                        SwitchPreference(
                            checked = settings.scrollEndHaptic,
                            onCheckedChange = { scope.launch { vm.settingsStore.setScrollEndHaptic(it) } },
                            title = "列表边界震动反馈",
                            summary = "滚动触达顶部或底部边界时提供轻触震动",
                        )
                        SwitchPreference(
                            checked = settings.enableSwipeBack,
                            onCheckedChange = { scope.launch { vm.settingsStore.setEnableSwipeBack(it) } },
                            title = "二级页面侧滑返回",
                            summary = "支持在计划编辑、日历详情等页面跟手侧滑返回",
                        )
                        SwitchPreference(
                            checked = settings.navCornerClip,
                            onCheckedChange = { scope.launch { vm.settingsStore.setNavCornerClip(it) } },
                            title = "转场屏幕圆角裁切",
                            summary = "页面推入与退场时按屏幕圆角平滑裁切",
                        )
                        SwitchPreference(
                            checked = settings.navDim,
                            onCheckedChange = { scope.launch { vm.settingsStore.setNavDim(it) } },
                            title = "转场背景压暗",
                            summary = "页面切换时为下层页面叠加景深遮罩",
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
private fun SettingsList(
    padding: PaddingValues,
    listState: LazyListState,
    scrollBehavior: ScrollBehavior,
    content: LazyListScope.() -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .os4ScrollModifiers(scrollBehavior),
            state = listState,
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                end = padding.calculateEndPadding(layoutDirection),
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            content = content,
        )
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = padding,
        )
    }
}

@Composable
private fun Os4DropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    startAction: @Composable (() -> Unit)? = null,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val visuals = GlassPopupDefaults.visuals(
        style = rememberOs4GlassStyle(),
        material = rememberOs4CustomMaterial() ?: GlassPopupDefaults.material(),
        stroke = rememberOs4EdgeStroke(GlassPopupDefaults.stroke()),
    )
    GlassDropdownPreference(
        items = items,
        selectedIndex = selectedIndex,
        title = title,
        backdrop = LocalRootBackdrop.current ?: LocalPageBackdrop.current,
        enabled = enabled,
        summary = summary,
        startAction = startAction,
        visuals = visuals,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}
