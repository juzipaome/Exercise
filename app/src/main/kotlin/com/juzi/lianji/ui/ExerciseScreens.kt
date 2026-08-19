package com.juzi.lianji.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.juzi.lianji.*
import com.juzi.lianji.data.TrackingMode
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Edit

@Composable
fun ExerciseLibraryScreen(state:MainUiState,padding:PaddingValues,listState:LazyListState,scrollBehavior:ScrollBehavior,onDetail:(String)->Unit,onFavorite:(String)->Unit) {
    var query by rememberSaveable{mutableStateOf("")};var body by rememberSaveable{mutableStateOf("")};var equipment by rememberSaveable{mutableStateOf("")}
    val bodies=remember(state.exercises){state.exercises.map{it.bodyPart}.distinct().sortedBy(::bodyPartLabel)}
    val equipments=remember(state.exercises){state.exercises.map{it.equipment}.distinct().sortedBy(::equipmentLabel)}
    val filtered=remember(state.exercises,query,body,equipment){
        state.exercises.filter{(query.isBlank()||it.nameZh.contains(query,true)||it.nameEn.contains(query,true))&&(body.isBlank()||it.bodyPart==body)&&(equipment.isBlank()||it.equipment==equipment)}
    }
    LazyColumn(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),state=listState,contentPadding=PaddingValues(top=padding.calculateTopPadding()+12.dp,bottom=padding.calculateBottomPadding()+24.dp)) {
        item { TextField(query,{query=it},label="搜索动作",useLabelAsPlaceholder=true,modifier=Modifier.cardPadding().fillMaxWidth()) }
        item { Text("训练部位",style=MiuixTheme.textStyles.title3,modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp)) }
        item { LazyRow(contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){item{FilterButton("全部",body.isBlank()){body=""}};items(bodies){value->FilterButton(bodyPartLabel(value),body==value){body=value}}};Spacer(Modifier.height(8.dp)) }
        item { Text("器械",style=MiuixTheme.textStyles.title3,modifier=Modifier.padding(horizontal=12.dp,vertical=4.dp)) }
        item { LazyRow(contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){item{FilterButton("全部",equipment.isBlank()){equipment=""}};items(equipments){value->FilterButton(equipmentLabel(value),equipment==value){equipment=value}}};Spacer(Modifier.height(12.dp)) }
        if(filtered.isEmpty())item{EmptyCard("没有匹配动作","换个关键词或筛选条件试试")}
        items(filtered,key={it.id}) { ex -> Card(modifier=Modifier.cardPadding(),pressFeedbackType=PressFeedbackType.Sink,onClick={onDetail(ex.id)}) { Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { ex.imagePath?.let{AsyncImage(model="file:///android_asset/$it",contentDescription=null,modifier=Modifier.size(72.dp).squircleClip(16.dp),contentScale=ContentScale.Crop)};Column(Modifier.weight(1f)){Text(ex.nameZh,style=MiuixTheme.textStyles.title3);Text(ex.nameEn,maxLines=1,color=MiuixTheme.colorScheme.onSurfaceSecondary);Text("${bodyPartLabel(ex.bodyPart)} · ${equipmentLabel(ex.equipment)}",maxLines=1,color=MiuixTheme.colorScheme.onSurfaceSecondary)};IconButton(onClick={onFavorite(ex.id)}){Icon(if(ex.isFavorite)MiuixIcons.FavoritesFill else MiuixIcons.Favorites,if(ex.isFavorite)"取消收藏" else "收藏")}} } }
    }
}

@Composable private fun FilterButton(label:String,selected:Boolean,onClick:()->Unit){if(selected)Button(onClick=onClick){Text(label)}else TextButton(label,onClick=onClick)}

@Composable
fun ExerciseDetailScreen(vm:MainViewModel,id:String,onBack:()->Unit){
    val ex by vm.repository.exercise(id).collectAsStateWithLifecycle(null);val state by vm.state.collectAsStateWithLifecycle();var showRename by remember{mutableStateOf(false)}
    MiuixPageScaffold(title="动作详情",navigationIcon={BackButton(onBack)}){pad->
        LazyColumn(contentPadding=PaddingValues(top=pad.calculateTopPadding()+12.dp,bottom=24.dp)){ex?.let{e->
            item{Card(Modifier.cardPadding()){Column{
                e.gifPath?.let{AsyncImage("file:///android_asset/$it",null,Modifier.fillMaxWidth().aspectRatio(1.8f),contentScale=ContentScale.Fit)}
                Column(Modifier.padding(18.dp)){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(e.nameZh,style=MiuixTheme.textStyles.title1);Text(e.nameEn,color=MiuixTheme.colorScheme.onSurfaceSecondary)};IconButton(onClick={showRename=true}){Icon(MiuixIcons.Edit,"修改中文名")}}
                    if(!e.isCustom&&e.datasetNameZh.isNotBlank()&&e.nameZh!=e.datasetNameZh)Text("数据集原名：${e.datasetNameZh}",color=MiuixTheme.colorScheme.onSurfaceSecondary)
                    Spacer(Modifier.height(10.dp));Text("${bodyPartLabel(e.bodyPart)} · ${equipmentLabel(e.equipment)} · ${e.target}")
                }
            }}}
            item{Card(Modifier.cardPadding()){Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                Surface(modifier=Modifier.size(52.dp),shape=RoundedCornerShape(16.dp),color=MiuixTheme.colorScheme.primary.copy(alpha=0.12f),shadowElevation=0.dp){Box(contentAlignment=Alignment.Center){Text("🏆",style=MiuixTheme.textStyles.title1)}}
                Column(Modifier.weight(1f)){Text("个人最佳",style=MiuixTheme.textStyles.title2);Text(personalBestLabel(state.personalBests[e.id]).removePrefix("PB · "),color=MiuixTheme.colorScheme.primary)}
            }}}
            item{Card(Modifier.cardPadding()){Column(Modifier.padding(18.dp)){
                Text("动作说明",style=MiuixTheme.textStyles.title3);Text(e.instructionsZh.ifBlank{e.instructionsEn})
                Spacer(Modifier.height(18.dp));Text(e.attribution,color=MiuixTheme.colorScheme.onSurfaceSecondary)
            }}}
        }}
    }
    ex?.let{e->ExerciseRenameSheet(showRename,e.nameZh,e.datasetNameZh,!e.isCustom,{showRename=false},{name->vm.updateExerciseName(e.id,name);showRename=false},{vm.restoreExerciseName(e.id);showRename=false})}
}

@Composable
private fun ExerciseRenameSheet(show:Boolean,currentName:String,datasetName:String,canRestore:Boolean,onDismiss:()->Unit,onSave:(String)->Unit,onRestore:()->Unit){
    var name by remember(show,currentName){mutableStateOf(currentName)}
    WindowDialog(show=show,title="修改动作中文名",summary="已有训练历史仍保留当时的名称。",onDismissRequest=onDismiss){
        val dismissState=LocalDismissState.current
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            TextField(name,{name=it},label="中文显示名",modifier=Modifier.fillMaxWidth())
            if(canRestore&&datasetName.isNotBlank()&&currentName!=datasetName)TextButton("恢复数据集原名",onClick=onRestore,modifier=Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)){TextButton("取消",onClick={dismissState?.invoke()},modifier=Modifier.weight(1f));TextButton("保存",enabled=name.trim().isNotEmpty(),onClick={onSave(name.trim());dismissState?.invoke()},colors=ButtonDefaults.textButtonColorsPrimary(),modifier=Modifier.weight(1f))}
        }
    }
}

@Composable
fun CustomExerciseScreen(vm:MainViewModel,onBack:()->Unit){
    var name by remember{mutableStateOf("")};var body by remember{mutableStateOf("")};var equipment by remember{mutableStateOf("")};var note by remember{mutableStateOf("")};var trackingMode by remember{mutableStateOf(TrackingMode.STRENGTH)}
    MiuixPageScaffold(title="自定义动作",navigationIcon={BackButton(onBack)}){pad->
        Column(Modifier.padding(top=pad.calculateTopPadding()+12.dp).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            TextField(name,{name=it},label="动作名称")
            Text("记录方式",style=MiuixTheme.textStyles.title3)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                if(trackingMode==TrackingMode.STRENGTH)Button(onClick={trackingMode=TrackingMode.STRENGTH},modifier=Modifier.weight(1f)){Text("组数与重量")}else TextButton("组数与重量",onClick={trackingMode=TrackingMode.STRENGTH},modifier=Modifier.weight(1f))
                if(trackingMode==TrackingMode.CARDIO)Button(onClick={trackingMode=TrackingMode.CARDIO},modifier=Modifier.weight(1f)){Text("有氧计时")}else TextButton("有氧计时",onClick={trackingMode=TrackingMode.CARDIO},modifier=Modifier.weight(1f))
            }
            Text(if(trackingMode==TrackingMode.CARDIO)"训练时记录时长，可选填距离，不设置组数和重量。" else "训练时按组记录重量、次数和组间休息。",color=MiuixTheme.colorScheme.onSurfaceSecondary)
            if(trackingMode==TrackingMode.STRENGTH)TextField(body,{body=it},label="训练部位")
            TextField(equipment,{equipment=it},label="器械")
            TextField(note,{note=it},label="动作说明")
            Button(enabled=name.isNotBlank(),onClick={vm.saveCustom(name,body,equipment,note,trackingMode);onBack()},modifier=Modifier.fillMaxWidth()){Text("保存动作")}
        }
    }
}
