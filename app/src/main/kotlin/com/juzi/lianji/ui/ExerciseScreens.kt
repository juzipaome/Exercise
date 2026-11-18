package com.juzi.lianji.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.juzi.lianji.*
import com.juzi.lianji.data.TrackingMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassDialogDefaults
import top.yukonga.miuix.kmp.glass.GlassFloatingActionButton
import top.yukonga.miuix.kmp.glass.GlassFloatingActionButtonDefaults
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRowDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Edit
import top.yukonga.miuix.kmp.icon.glass.ExpandLess
import top.yukonga.miuix.kmp.icon.glass.FavoriteFill
import top.yukonga.miuix.kmp.icon.glass.FavoriteOutline
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.window.WindowDialog

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun ExerciseLibraryScreen(state:MainUiState,padding:PaddingValues,listState:LazyListState,scrollBehavior:ScrollBehavior,onDetail:(String)->Unit,onFavorite:(String)->Unit) {
    var query by rememberSaveable{mutableStateOf("")};var body by rememberSaveable{mutableStateOf("")};var equipment by rememberSaveable{mutableStateOf("")}
    val bodies=remember(state.exercises){state.exercises.map{it.bodyPart}.distinct().sortedBy(::bodyPartLabel)}
    val equipments=remember(state.exercises){state.exercises.map{it.equipment}.distinct().sortedBy(::equipmentLabel)}
    val filtered=remember(state.exercises,query,body,equipment){
        state.exercises.filter{(query.isBlank()||it.nameZh.contains(query,true)||it.nameEn.contains(query,true))&&(body.isBlank()||it.bodyPart==body)&&(equipment.isBlank()||it.equipment==equipment)}
    }
    val scope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current
    val showScrollTopFab by remember { derivedStateOf { listState.firstVisibleItemIndex > 6 } }
    val customMaterial = rememberOs4CustomMaterial()
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
        ) {
            item { ExerciseSearchBar(query,{query=it},Modifier.cardPadding().fillMaxWidth()) }
            item { Text("训练部位",style=MiuixTheme.textStyles.title3,modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp)) }
            item { LazyRow(contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){item{FilterButton("全部",body.isBlank()){body=""}};items(bodies){value->FilterButton(bodyPartLabel(value),body==value){body=value}}};Spacer(Modifier.height(8.dp)) }
            item { Text("器械",style=MiuixTheme.textStyles.title3,modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp)) }
            item { LazyRow(contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){item{FilterButton("全部",equipment.isBlank()){equipment=""}};items(equipments){value->FilterButton(equipmentLabel(value),equipment==value){equipment=value}}};Spacer(Modifier.height(12.dp)) }
            if(filtered.isEmpty())item{EmptyCard("没有匹配动作","换个关键词或筛选条件试试")}
            items(filtered,key={it.id}) { ex ->
                Card(modifier=Modifier.cardPadding(),pressFeedbackType=PressFeedbackType.Sink,onClick={onDetail(ex.id)}) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        ex.imagePath?.let{AsyncImage(model="file:///android_asset/$it",contentDescription=null,modifier=Modifier.size(72.dp).squircleClip(16.dp),contentScale=ContentScale.Crop)}
                        Column(Modifier.weight(1f)){
                            Text(ex.nameZh,style=MiuixTheme.textStyles.title3)
                            Text(ex.nameEn,maxLines=1,color=MiuixTheme.colorScheme.onSurfaceSecondary)
                            Text("${bodyPartLabel(ex.bodyPart)} · ${equipmentLabel(ex.equipment)}",maxLines=1,color=MiuixTheme.colorScheme.onSurfaceSecondary)
                        }
                        IconButton(onClick={onFavorite(ex.id)}){
                            Icon(
                                imageVector = if(ex.isFavorite) MiuixIcons.Glass.FavoriteFill else MiuixIcons.Glass.FavoriteOutline,
                                contentDescription = if(ex.isFavorite) "取消收藏" else "收藏",
                                modifier = Modifier.size(24.dp),
                                tint = if(ex.isFavorite) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = padding,
        )
        AnimatedVisibility(
            visible = showScrollTopFab,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            GlassFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                backdrop = LocalPageBackdrop.current,
                style = rememberOs4GlassStyle(),
                material = customMaterial ?: GlassFloatingActionButtonDefaults.material(),
                stroke = rememberOs4EdgeStroke(GlassFloatingActionButtonDefaults.stroke()),
            ) {
                Icon(
                    imageVector = MiuixIcons.Glass.ExpandLess,
                    contentDescription = "回到顶部",
                    modifier = Modifier.size(28.dp),
                    tint = if (customMaterial != null) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable private fun FilterButton(label:String,selected:Boolean,onClick:()->Unit){if(selected)Button(onClick=onClick){Text(label)}else TextButton(label,onClick=onClick)}

@Composable
fun ExerciseDetailScreen(vm:MainViewModel,id:String,onBack:()->Unit){
    val ex by remember(vm,id){vm.repository.exercise(id)}.collectAsStateWithLifecycle(null)
    val personalBest by remember(vm,id){vm.repository.personalBest(id)}.collectAsStateWithLifecycle(null)
    var showRename by remember{mutableStateOf(false)}
    val detailScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    MiuixPageScaffold(title="动作详情",navigationIcon={BackButton(onBack)},scrollBehavior=detailScrollBehavior){pad->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .os4ScrollModifiers(detailScrollBehavior),
            contentPadding = pad.withPageSpacing(),
        ){ex?.let{e->
            item{Card(Modifier.cardPadding()){Column{
                e.gifPath?.let{AsyncImage("file:///android_asset/$it",null,Modifier.fillMaxWidth().aspectRatio(1.8f),contentScale=ContentScale.Fit)}
                Column(Modifier.padding(18.dp)){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(e.nameZh,style=MiuixTheme.textStyles.title1);Text(e.nameEn,color=MiuixTheme.colorScheme.onSurfaceSecondary)};IconButton(onClick={showRename=true}){Icon(MiuixIcons.Glass.Edit,"修改中文名",Modifier.size(22.dp))}}
                    if(!e.isCustom&&e.datasetNameZh.isNotBlank()&&e.nameZh!=e.datasetNameZh)Text("数据集原名：${e.datasetNameZh}",color=MiuixTheme.colorScheme.onSurfaceSecondary)
                    Spacer(Modifier.height(10.dp));Text("${bodyPartLabel(e.bodyPart)} · ${equipmentLabel(e.equipment)} · ${e.target}")
                }
            }}}
            item{Card(Modifier.cardPadding()){Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                Surface(modifier=Modifier.size(52.dp).squircleSurface(MiuixTheme.colorScheme.primary.copy(alpha=0.12f),16.dp),color=Color.Transparent,shadowElevation=0.dp){Box(contentAlignment=Alignment.Center){Text("🏆",style=MiuixTheme.textStyles.title1)}}
                Column(Modifier.weight(1f)){Text("个人最佳",style=MiuixTheme.textStyles.title2);Text(personalBestLabel(personalBest).removePrefix("PB · "),color=MiuixTheme.colorScheme.primary)}
            }}}
            item{Card(Modifier.cardPadding()){Column(Modifier.padding(18.dp)){
                Text("动作说明",style=MiuixTheme.textStyles.title3);Text(e.instructionsZh.ifBlank{e.instructionsEn})
                Spacer(Modifier.height(18.dp));Text(e.attribution,color=MiuixTheme.colorScheme.onSurfaceSecondary)
            }}}
        }}
    }
    ex?.let{e->ExerciseRenameSheet(showRename,e.nameZh,e.datasetNameZh,!e.isCustom,{showRename=false},{name->vm.updateExerciseName(e.id,name){showRename=false}},{vm.restoreExerciseName(e.id){showRename=false}})}
}

@Composable
private fun ExerciseRenameSheet(show:Boolean,currentName:String,datasetName:String,canRestore:Boolean,onDismiss:()->Unit,onSave:(String)->Job,onRestore:()->Job){
    var name by remember(show,currentName){mutableStateOf(currentName)}
    var saving by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun save(action:()->Job) {
        if(saving)return
        saving=true
        val job=action()
        scope.launch { try { job.join() } finally { saving=false } }
    }
    val settings = LocalMiuixFeatureSettings.current
    if (LocalHasRootScaffold.current) {
        GlassDialog(
            show = show,
            backdrop = LocalRootBackdrop.current,
            title = "修改动作中文名",
            summary = "已有训练历史仍保留当时的名称。",
            onDismissRequest = onDismiss,
            largeScreen = settings.largeScreenDialogs,
            material = rememberOs4CustomMaterial() ?: GlassDialogDefaults.material(),
            stroke = rememberOs4EdgeStroke(GlassDialogDefaults.stroke()),
        ) {
            val dismissState = LocalDismissState.current
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(name, { name = it }, label = "中文显示名", modifier = Modifier.fillMaxWidth())
                if (canRestore && datasetName.isNotBlank() && currentName != datasetName) {
                    GlassDialogButton(
                        onClick = { save(onRestore) },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("恢复数据集原名", style = MiuixTheme.textStyles.button)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassDialogButton(
                        onClick = { (dismissState ?: onDismiss).invoke() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("取消", style = MiuixTheme.textStyles.button)
                    }
                    GlassDialogButton(
                        onClick = { save { onSave(name.trim()) } },
                        enabled = !saving && name.trim().isNotEmpty(),
                        role = GlassDialogButtonRole.Primary,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (saving) "正在保存…" else "保存", style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }
    } else {
        WindowDialog(show = show, title = "修改动作中文名", summary = "已有训练历史仍保留当时的名称。", onDismissRequest = onDismiss, largeScreen = settings.largeScreenDialogs) {
            val dismissState = LocalDismissState.current
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(name, { name = it }, label = "中文显示名", modifier = Modifier.fillMaxWidth())
                if (canRestore && datasetName.isNotBlank() && currentName != datasetName) TextButton("恢复数据集原名", enabled = !saving, onClick = { save(onRestore) }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    TextButton("取消", onClick = { (dismissState ?: onDismiss).invoke() }, modifier = Modifier.weight(1f))
                    TextButton(if (saving) "正在保存…" else "保存", enabled = !saving && name.trim().isNotEmpty(), onClick = { save { onSave(name.trim()) } }, colors = ButtonDefaults.textButtonColorsPrimary(), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun CustomExerciseScreen(vm:MainViewModel,onBack:()->Unit){
    var name by rememberSaveable{mutableStateOf("")};var body by rememberSaveable{mutableStateOf("")};var equipment by rememberSaveable{mutableStateOf("")};var note by rememberSaveable{mutableStateOf("")};var trackingMode by rememberSaveable{mutableStateOf(TrackingMode.STRENGTH)}
    val scope=rememberCoroutineScope()
    var saving by remember{mutableStateOf(false)}
    val surfaceColor = MiuixTheme.colorScheme.surface
    MiuixPageScaffold(title="自定义动作",navigationIcon={BackButton(onBack)}){pad->
        Column(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            TextField(name,{name=it},label="动作名称")
            Text("记录方式",style=MiuixTheme.textStyles.title3)
            GlassSegmentedTabRow(
                tabs = listOf("组数与重量", "有氧计时"),
                selectedIndex = if (trackingMode == TrackingMode.CARDIO) 1 else 0,
                onSelect = { trackingMode = if (it == 1) TrackingMode.CARDIO else TrackingMode.STRENGTH },
                backdrop = LocalSurfaceBackdrop.current ?: rememberLayerBackdrop { drawRect(surfaceColor) },
                modifier = Modifier.fillMaxWidth(),
                style = rememberOs4GlassStyle(),
                material = rememberOs4CustomMaterial() ?: GlassSegmentedTabRowDefaults.material(),
                stroke = rememberOs4EdgeStroke(GlassSegmentedTabRowDefaults.stroke()),
            )
            Text(if(trackingMode==TrackingMode.CARDIO)"训练时记录时长，可选填距离，不设置组数和重量。" else "训练时按组记录重量、次数和组间休息。",color=MiuixTheme.colorScheme.onSurfaceSecondary)
            if(trackingMode==TrackingMode.STRENGTH)TextField(body,{body=it},label="训练部位")
            TextField(equipment,{equipment=it},label="器械")
            TextField(note,{note=it},label="动作说明")
            Button(enabled=name.isNotBlank()&&!saving,onClick={
                saving=true
                val job=vm.saveCustom(name.trim(),body,equipment,note,trackingMode,onDone=onBack)
                scope.launch { try { job.join() } finally { saving=false } }
            },modifier=Modifier.fillMaxWidth()){Text(if(saving)"正在保存…" else "保存动作")}
        }
    }
}
