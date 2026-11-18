package com.juzi.lianji.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.juzi.lianji.MainViewModel
import com.juzi.lianji.data.ExerciseEntity
import com.juzi.lianji.data.isCardio
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Info

@Composable
fun PlanEditorScreen(vm:MainViewModel,planId:Long?=null,onBack:()->Unit,onSave:(Long)->Unit,onSaveAndStart:(Long)->Unit,onDetail:(String)->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val editorScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    var saving by remember { mutableStateOf(false) }
    var name by rememberSaveable{mutableStateOf("")};var query by rememberSaveable{mutableStateOf("")};var body by rememberSaveable{mutableStateOf("")};var favoriteOnly by rememberSaveable{mutableStateOf(false)};var selected by rememberSaveable{mutableStateOf(listOf<String>())}
    var loaded by rememberSaveable(planId){mutableStateOf(planId==null)}
    LaunchedEffect(planId,loaded){if(planId!=null&&!loaded)vm.loadPlan(planId){plan,ids->name=plan.name;selected=ids;loaded=true}}
    val bodies=remember(state.exercises){state.exercises.map{it.bodyPart}.distinct().sortedBy(::bodyPartLabel)}
    val latestExercises by rememberUpdatedState(state.exercises)
    val visibleState = remember {
        derivedStateOf {
            val rank = selected.withIndex().associate { (i, id) -> id to i }
            latestExercises.filter {
                (query.isBlank() || it.nameZh.contains(query, true) || it.nameEn.contains(query, true)) &&
                    (body.isBlank() || it.bodyPart == body) &&
                    (!favoriteOnly || it.isFavorite)
            }.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
        }
    }
    val trimmedName = name.trim()
    val canSave = trimmedName.isNotEmpty() && selected.isNotEmpty()
    fun submit(onDone: (Long) -> Unit) {
        if (saving || !canSave || !loaded) return
        saving = true
        val job = if (planId == null) {
            vm.createPlan(trimmedName, selected, onDone)
        } else {
            vm.savePlan(planId, trimmedName, selected, onDone)
        }
        scope.launch {
            try {
                job.join()
            } finally {
                saving = false
            }
        }
    }
    MiuixPageScaffold(
        title=if(planId==null)"创建计划" else "查看与编辑计划",
        navigationIcon={BackButton(onBack)},
        scrollBehavior=editorScrollBehavior,
        bottomBar = {
            val safeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            Card(Modifier.windowInsetsPadding(safeInsets).padding(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = canSave && loaded && !saving, onClick = { submit(onSave) }, modifier = Modifier.weight(1f)) {
                        Text("保存")
                    }
                    Button(enabled = canSave && loaded && !saving, onClick = { submit(onSaveAndStart) }, modifier = Modifier.weight(1f)) {
                        Text("保存并开始")
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .os4ScrollModifiers(editorScrollBehavior),
            state=listState,
            contentPadding = pad.withPageSpacing(bottom = 12.dp),
        ) {
            item{TextField(name,{name=it},label="计划名称",useLabelAsPlaceholder=true,modifier=Modifier.cardPadding().fillMaxWidth())}
            item{ExerciseSearchBar(query,{query=it},Modifier.cardPadding().fillMaxWidth())}
            item{LazyRow(contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){item{PlanFilter("已收藏",favoriteOnly){favoriteOnly=!favoriteOnly}};item{PlanFilter("全部",body.isBlank()){body=""}};items(bodies){part->PlanFilter(bodyPartLabel(part),body==part){body=part}}};Spacer(Modifier.height(8.dp))}
            val visible = visibleState.value
            item{SmallTitle("已选 ${selected.size} 个动作 · 当前 ${visible.size} 个结果")}
            items(visible,key={it.id}){ex->
                val checked=ex.id in selected
                ExerciseChoice(ex,checked,{onDetail(ex.id)}){
                    val anchorIndex=listState.firstVisibleItemIndex
                    val anchorOffset=listState.firstVisibleItemScrollOffset
                    Snapshot.withMutableSnapshot {
                        selected=if(checked)selected-ex.id else selected+ex.id
                    }
                    if(anchorIndex>0||anchorOffset>0)listState.requestScrollToItem(anchorIndex,anchorOffset)
                }
            }
        }
    }
}

@Composable
fun WorkoutExercisePickerScreen(vm:MainViewModel,sessionId:Long,onBack:()->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    val pickerScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    var saving by remember{mutableStateOf(false)}
    val rows by remember(vm,sessionId){vm.repository.rows(sessionId)}.collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var favoriteOnly by remember { mutableStateOf(false) }
    val existing=rows.mapTo(mutableSetOf()){it.exerciseId}
    val bodies=remember(state.exercises){state.exercises.map{it.bodyPart}.distinct().sortedBy(::bodyPartLabel)}
    val visible=state.exercises.filter{(query.isBlank()||it.nameZh.contains(query,true)||it.nameEn.contains(query,true))&&(body.isBlank()||it.bodyPart==body)&&(!favoriteOnly||it.isFavorite)}
    MiuixPageScaffold(title="训练中添加动作",navigationIcon={BackButton(onBack)},scrollBehavior=pickerScrollBehavior){pad->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .consumeWindowInsets(pad)
                .imePadding()
                .os4ScrollModifiers(pickerScrollBehavior),
            contentPadding = pad.withPageSpacing(),
        ){
            item{ExerciseSearchBar(query,{query=it},Modifier.cardPadding().fillMaxWidth())}
            item{LazyRow(contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){item{PlanFilter("已收藏",favoriteOnly){favoriteOnly=!favoriteOnly}};item{PlanFilter("全部",body.isBlank()){body=""}};items(bodies){part->PlanFilter(bodyPartLabel(part),body==part){body=part}}};Spacer(Modifier.height(8.dp))}
            item{SmallTitle("当前 ${visible.size} 个结果 · 力量动作 3 组，有氧动作计时 1 次")}
            items(visible,key={it.id}){ex->
                val added=ex.id in existing
                Card(Modifier.cardPadding()){
                    Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        val media=ex.imagePath?:ex.gifPath
                        if(media!=null)AsyncImage("file:///android_asset/$media",null,Modifier.size(64.dp).squircleClip(14.dp),contentScale=ContentScale.Crop) else Spacer(Modifier.size(64.dp))
                        Column(Modifier.weight(1f)){Text(ex.nameZh,style=MiuixTheme.textStyles.title3);Text(bodyPartLabel(ex.bodyPart),color=MiuixTheme.colorScheme.onSurfaceSecondary)}
                        Button(enabled=!added&&!saving,onClick={
                            saving=true
                            val job=vm.addExercise(sessionId,ex.id,onDone=onBack)
                            scope.launch { try { job.join() } finally { saving=false } }
                        }){Text(if(added)"已添加" else "添加")}
                    }
                }
            }
        }
    }
}

@Composable private fun PlanFilter(label:String,selected:Boolean,onClick:()->Unit){if(selected)Button(onClick=onClick){Text(label)}else TextButton(label,onClick=onClick)}

@Composable private fun ExerciseChoice(ex:ExerciseEntity,checked:Boolean,onDetail:()->Unit,onToggle:()->Unit){
    val rowToggle=hapticRowClick(checked,onToggle)
    Card(modifier=Modifier.cardPadding(),pressFeedbackType=PressFeedbackType.Sink,onClick=rowToggle){
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            val media=ex.imagePath?:ex.gifPath
            if(media!=null)AsyncImage("file:///android_asset/$media",null,Modifier.size(76.dp).squircleClip(16.dp),contentScale=ContentScale.Crop)
            else Spacer(Modifier.size(76.dp))
            Column(Modifier.weight(1f)){Text(ex.nameZh,style=MiuixTheme.textStyles.title3);Text(ex.nameEn,maxLines=1,color=MiuixTheme.colorScheme.onSurfaceSecondary);Text("${bodyPartLabel(ex.bodyPart)} · ${equipmentLabel(ex.equipment)} · ${if(ex.isCardio)"计时" else "组数/重量"}",color=MiuixTheme.colorScheme.onSurfaceSecondary)}
            IconButton(onClick=onDetail){Icon(MiuixIcons.Glass.Info,"查看动作详情",Modifier.size(22.dp))}
            Checkbox(state=if(checked)ToggleableState.On else ToggleableState.Off,onClick=onToggle)
        }
    }
}
