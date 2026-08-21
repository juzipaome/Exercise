package com.juzi.lianji.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info

@Composable
fun PlanEditorScreen(vm:MainViewModel,planId:Long?=null,onBack:()->Unit,onSave:(Long)->Unit,onSaveAndStart:(Long)->Unit,onDetail:(String)->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var name by rememberSaveable{mutableStateOf("")};var query by rememberSaveable{mutableStateOf("")};var body by rememberSaveable{mutableStateOf("")};var favoriteOnly by rememberSaveable{mutableStateOf(false)};var selected by rememberSaveable{mutableStateOf(listOf<String>())}
    var loaded by rememberSaveable(planId){mutableStateOf(planId==null)}
    LaunchedEffect(planId,loaded){if(planId!=null&&!loaded)vm.loadPlan(planId){plan,ids->name=plan.name;selected=ids;loaded=true}}
    val bodies=remember(state.exercises){state.exercises.map{it.bodyPart}.distinct().sortedBy(::bodyPartLabel)}
    val visible=state.exercises.filter{(query.isBlank()||it.nameZh.contains(query,true)||it.nameEn.contains(query,true))&&(body.isBlank()||it.bodyPart==body)&&(!favoriteOnly||it.isFavorite)}.sortedBy{if(it.id in selected)0 else 1}
    val canSave=name.isNotBlank()&&selected.isNotEmpty()
    MiuixPageScaffold(
        title=if(planId==null)"创建计划" else "查看与编辑计划",
        navigationIcon={BackButton(onBack)},
        bottomBar={Card(Modifier.navigationBarsPadding().padding(12.dp)){Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(enabled=canSave&&loaded,onClick={if(planId==null)vm.createPlan(name,selected,onSave)else vm.savePlan(planId,name,selected,onSave)},modifier=Modifier.weight(1f)){Text("保存")};Button(enabled=canSave&&loaded,onClick={if(planId==null)vm.createPlan(name,selected,onSaveAndStart)else vm.savePlan(planId,name,selected,onSaveAndStart)},modifier=Modifier.weight(1f)){Text("保存并开始")}}}},
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(top=pad.calculateTopPadding()+12.dp,bottom=pad.calculateBottomPadding()+12.dp)) {
            item{TextField(name,{name=it},label="计划名称",useLabelAsPlaceholder=true,modifier=Modifier.cardPadding().fillMaxWidth())}
            item{TextField(query,{query=it},label="搜索动作",useLabelAsPlaceholder=true,modifier=Modifier.cardPadding().fillMaxWidth())}
            item{LazyRow(contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){item{PlanFilter("已收藏",favoriteOnly){favoriteOnly=!favoriteOnly}};item{PlanFilter("全部",body.isBlank()){body=""}};items(bodies){part->PlanFilter(bodyPartLabel(part),body==part){body=part}}};Spacer(Modifier.height(8.dp))}
            item{SmallTitle("已选 ${selected.size} 个动作 · 当前 ${visible.size} 个结果")}
            items(visible,key={it.id}){ex->val checked=ex.id in selected;ExerciseChoice(ex,checked,{onDetail(ex.id)}){selected=if(checked)selected-ex.id else selected+ex.id}}
        }
    }
}

@Composable
fun WorkoutExercisePickerScreen(vm:MainViewModel,sessionId:Long,onBack:()->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val rows by vm.repository.rows(sessionId).collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var favoriteOnly by remember { mutableStateOf(false) }
    val existing=rows.mapTo(mutableSetOf()){it.exerciseId}
    val bodies=remember(state.exercises){state.exercises.map{it.bodyPart}.distinct().sortedBy(::bodyPartLabel)}
    val visible=state.exercises.filter{(query.isBlank()||it.nameZh.contains(query,true)||it.nameEn.contains(query,true))&&(body.isBlank()||it.bodyPart==body)&&(!favoriteOnly||it.isFavorite)}
    MiuixPageScaffold(title="训练中添加动作",navigationIcon={BackButton(onBack)}){pad->
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(top=pad.calculateTopPadding()+12.dp,bottom=24.dp)){
            item{TextField(query,{query=it},label="搜索动作",useLabelAsPlaceholder=true,modifier=Modifier.cardPadding().fillMaxWidth())}
            item{LazyRow(contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){item{PlanFilter("已收藏",favoriteOnly){favoriteOnly=!favoriteOnly}};item{PlanFilter("全部",body.isBlank()){body=""}};items(bodies){part->PlanFilter(bodyPartLabel(part),body==part){body=part}}};Spacer(Modifier.height(8.dp))}
            item{SmallTitle("当前 ${visible.size} 个结果 · 力量动作 3 组，有氧动作计时 1 次")}
            items(visible,key={it.id}){ex->
                val added=ex.id in existing
                Card(Modifier.cardPadding()){
                    Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        val media=ex.imagePath?:ex.gifPath
                        if(media!=null)AsyncImage("file:///android_asset/$media",null,Modifier.size(64.dp).squircleClip(14.dp),contentScale=ContentScale.Crop) else Spacer(Modifier.size(64.dp))
                        Column(Modifier.weight(1f)){Text(ex.nameZh,style=MiuixTheme.textStyles.title3);Text(bodyPartLabel(ex.bodyPart),color=MiuixTheme.colorScheme.onSurfaceSecondary)}
                        Button(enabled=!added,onClick={vm.addExercise(sessionId,ex.id);onBack()}){Text(if(added)"已添加" else "添加")}
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
            IconButton(onClick=onDetail){Icon(MiuixIcons.Info,"查看动作详情")}
            Checkbox(state=if(checked)ToggleableState.On else ToggleableState.Off,onClick=rowToggle)
        }
    }
}
