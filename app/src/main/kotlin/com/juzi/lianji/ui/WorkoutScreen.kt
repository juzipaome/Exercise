package com.juzi.lianji.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.juzi.lianji.MainViewModel
import com.juzi.lianji.data.SessionSetRow
import com.juzi.lianji.data.TrackingMode
import com.juzi.lianji.data.activeDurationSeconds
import com.juzi.lianji.data.nextWorkoutSet
import com.juzi.lianji.data.orderedWorkoutGroups
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.anim.folmeSpring

private enum class WorkoutSheet { Exit, Rest }

@Composable
fun WorkoutScreen(vm:MainViewModel,sessionId:Long,onBack:()->Unit,onAddExercise:()->Unit,onExerciseDetail:(String)->Unit){
    val rows by vm.repository.rows(sessionId).collectAsStateWithLifecycle(emptyList());val session by vm.repository.session(sessionId).collectAsStateWithLifecycle(null);val state by vm.state.collectAsStateWithLifecycle();val context=LocalContext.current
    var now by remember{mutableLongStateOf(System.currentTimeMillis())};var finishedAt by remember{mutableStateOf<Long?>(null)};var notifiedRestId by remember{mutableStateOf<Long?>(null)};var sheetKind by remember{mutableStateOf(WorkoutSheet.Exit)};var showSheet by remember{mutableStateOf(false)};var showPlanUpdate by remember{mutableStateOf(false)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    LaunchedEffect(Unit){if(ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.POST_NOTIFICATIONS);while(true){now=System.currentTimeMillis();delay(1_000)}}
    val openRest=rows.lastOrNull{it.restStartedAt!=null&&it.restEndedAt==null};val restRemaining=openRest?.let{(it.restSeconds-((now-it.restStartedAt!!)/1000).toInt()).coerceAtLeast(0)}?:0
    LaunchedEffect(openRest?.setId,restRemaining){if(openRest!=null&&restRemaining==0&&notifiedRestId!=openRest.setId){notifiedRestId=openRest.setId;notifyRest(context,state.settings.vibration,state.settings.sound)}}
    val totalSeconds=session?.let{((finishedAt?:it.endedAt?:now)-it.startedAt)/1000}?:0
    val isFinished=finishedAt!=null||session?.status=="COMPLETED";val currentRest=rows.firstOrNull()?.restSeconds?:state.settings.defaultRestSeconds;val workoutListState=rememberLazyListState();val scope=rememberCoroutineScope()
    val exerciseGroups=orderedWorkoutGroups(rows)
    fun openSheet(kind:WorkoutSheet){sheetKind=kind;showSheet=true}
    BackHandler{if(isFinished)onBack()else openSheet(WorkoutSheet.Exit)}
    Box(Modifier.fillMaxSize()){
    MiuixPageScaffold(title=if(isFinished)"训练完成" else "训练中 ${formatDuration(totalSeconds)}",navigationIcon={IconButton(onClick={if(isFinished)onBack()else openSheet(WorkoutSheet.Exit)}){Icon(MiuixIcons.Back,"返回")}},actions={if(!isFinished){WorkoutMoreMenu(onAddExercise,{openSheet(WorkoutSheet.Rest)});IconButton(onClick={val ended=System.currentTimeMillis();vm.finish(sessionId){hasChanges->finishedAt=ended;showPlanUpdate=hasChanges}}){Icon(MiuixIcons.Ok,"完成训练")}}}){pad->
        AnimatedContent(targetState=isFinished,modifier=Modifier.fillMaxSize(),transitionSpec={(fadeIn(folmeSpring(.9f,.35f))+slideInVertically(folmeSpring(.9f,.35f)){it/10}) togetherWith fadeOut(folmeSpring(.9f,.28f))},label="workout-finish") { finished ->
            if(finished) LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(top=pad.calculateTopPadding()+12.dp,bottom=24.dp)){item{CompletedWorkoutSummary(rows,state,totalSeconds,onBack)}}
            else BoxWithConstraints(Modifier.fillMaxSize().padding(top=pad.calculateTopPadding())){
                val positioningTail=maxHeight*.62f
                LazyColumn(Modifier.fillMaxSize(),state=workoutListState,contentPadding=PaddingValues(top=if(openRest!=null)164.dp else 12.dp,bottom=positioningTail)){
                    exerciseGroups.forEach{sets->val first=sets.first();val exerciseId=first.sessionExerciseId;item(key="exercise-$exerciseId"){if(first.trackingMode==TrackingMode.CARDIO)CardioWorkoutCard(sets,now,{onExerciseDetail(first.exerciseId)},vm::beginSet,vm::pauseSet,vm::completeCardio,vm::updateCardioValues,vm::deleteSet)else ExerciseWorkoutCard(sets,now,{onExerciseDetail(first.exerciseId)},{vm.beginSet(it)},{vm.pauseSet(it)},{id,w,r->vm.completeSet(id,w,r)},vm::updateSetValues,vm::deleteSet){val last=sets.last();vm.addSet(exerciseId,sets.size,last.weightKg,last.reps)}}}
                }
                AnimatedVisibility(openRest!=null,modifier=Modifier.align(Alignment.TopCenter),enter=slideInVertically(folmeSpring(.88f,.35f)){-it}+fadeIn(folmeSpring(.9f,.3f)),exit=slideOutVertically(folmeSpring(.92f,.3f)){-it}+fadeOut(folmeSpring(.95f,.25f))){
                    openRest?.let{rest->HeroCard(if(restRemaining>0)"组间休息 ${formatDuration(restRemaining.toLong())}" else "休息结束","上一组 ${formatDuration(rest.durationSeconds.toLong())} · 已休息 ${formatDuration(((now-rest.restStartedAt!!)/1000).coerceAtLeast(0))}","开始下一项"){
                        nextWorkoutSet(rows,rest.setId)?.let{next->vm.beginSet(next.setId);scope.launch{delay(100);val reordered=orderedWorkoutGroups(rows);val target=reordered.indexOfFirst{it.first().sessionExerciseId==next.sessionExerciseId}.coerceAtLeast(0);workoutListState.animateScrollToItem(target)}}
                    }}
                }
            }
        }
    }
    WorkoutSheetHost(showSheet,sheetKind,currentRest,{showSheet=false},{showSheet=false;onBack()},{showSheet=false;vm.discard(sessionId);onBack()}){seconds->vm.setSessionRest(sessionId,seconds);showSheet=false}
    MiuixActionDialog(showPlanUpdate,"保存本次计划调整","本次训练与原计划不同。你可以另存一份，或将本次调整同步到当前计划。","另存新计划","覆盖当前计划",{showPlanUpdate=false},{vm.saveSessionPlan(sessionId,false);showPlanUpdate=false},{vm.saveSessionPlan(sessionId,true);showPlanUpdate=false})
    }
}

@Composable
private fun WorkoutMoreMenu(onAddExercise:()->Unit,onAdjustRest:()->Unit){
    OverlayIconDropdownMenu(entry=DropdownEntry(listOf(DropdownItem("添加动作",onClick=onAddExercise),DropdownItem("调整休息时间",onClick=onAdjustRest)))){Icon(MiuixIcons.More,"更多训练功能")}
}

@Composable private fun CompletedWorkoutSummary(rows:List<SessionSetRow>,state:com.juzi.lianji.MainUiState,totalSeconds:Long,onBack:()->Unit){
    val completed=rows.filter{it.completed};val exercises=completed.groupBy{it.sessionExerciseId}.values
    val strengthSets=completed.count{it.trackingMode!=TrackingMode.CARDIO};val cardioActivities=completed.count{it.trackingMode==TrackingMode.CARDIO}
    val bodyParts=completed.mapNotNull{row->state.exercises.firstOrNull{it.id==row.exerciseId}?.bodyPart}.distinct().map(::bodyPartLabel)
    Card(Modifier.cardPadding()){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Column{Text("本次训练已完成",style=MiuixTheme.textStyles.title2);Text(buildString{append("总用时 ${formatDuration(totalSeconds)} · ${exercises.size} 个动作");if(strengthSets>0)append(" · $strengthSets 组");if(cardioActivities>0)append(" · 有氧 $cardioActivities 次")},color=MiuixTheme.colorScheme.onSurfaceSecondary)}
        if(bodyParts.isNotEmpty()){Text("训练部位",style=MiuixTheme.textStyles.title3);Text(bodyParts.joinToString(" · "),color=MiuixTheme.colorScheme.onSurfaceSecondary)}
        if(exercises.isEmpty())Text("这次训练没有已完成的记录",color=MiuixTheme.colorScheme.onSurfaceSecondary) else exercises.forEach{records->val cardio=records.first().trackingMode==TrackingMode.CARDIO;Column{Text(records.first().exerciseName,style=MiuixTheme.textStyles.title3);Text(if(cardio)records.joinToString(" · "){cardioSummary(it)} else "${records.size} 组 · ${records.sumOf{it.reps}} 次",color=MiuixTheme.colorScheme.onSurfaceSecondary)}}
        Button(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("返回首页")}
    }}
}

@Composable
private fun CardioWorkoutCard(records:List<SessionSetRow>,now:Long,onDetail:()->Unit,onBegin:(Long)->Unit,onPause:(Long)->Unit,onComplete:(Long,Double)->Unit,onEdit:(Long,Int,Double)->Unit,onDelete:(Long)->Unit){
    val first=records.first();val hasActivity=records.any{it.startedAt!=null||it.completed}
    Card(Modifier.cardPadding()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            val media=first.gifPath?:first.imagePath;if(media!=null)AsyncImage("file:///android_asset/$media",null,Modifier.size(72.dp).squircleClip(16.dp),contentScale=ContentScale.Crop)
            Column(Modifier.weight(1f)){Text(first.exerciseName,style=MiuixTheme.textStyles.title2);Text(if(hasActivity)"有氧计时 · ${records.count{it.completed}} 次已完成" else "有氧计时 · 未开始",color=MiuixTheme.colorScheme.onSurfaceSecondary)}
            IconButton(onClick=onDetail){Icon(MiuixIcons.Info,"查看动作详情")}
        }
        records.forEach{record->CardioRecordRow(record,now,{onBegin(record.setId)},{onPause(record.setId)},{distance->onComplete(record.setId,distance)},{duration,distance->onEdit(record.setId,duration,distance)},{onDelete(record.setId)})}
    }}
}

@Composable
private fun CardioRecordRow(record:SessionSetRow,now:Long,onBegin:()->Unit,onPause:()->Unit,onComplete:(Double)->Unit,onEdit:(Int,Double)->Unit,onDelete:()->Unit){
    var distance by remember(record.setId,record.distanceKm){mutableStateOf(if(record.distanceKm>0)exactDecimal(record.distanceKm) else "")}
    var minutes by remember(record.setId,record.durationSeconds){mutableStateOf(if(record.durationSeconds>0)minutesForEdit(record.durationSeconds) else "")}
    var editing by remember(record.setId){mutableStateOf(false)}
    val active=record.startedAt!=null&&!record.completed;val paused=record.pausedAt!=null;val elapsed=if(record.completed)record.durationSeconds.toLong()else activeDurationSeconds(record.startedAt,now,record.pausedAt,record.pausedDurationMillis)
    when{
        active->Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(paused)"已暂停" else "进行中",style=MiuixTheme.textStyles.title3,color=MiuixTheme.colorScheme.primary);Text(formatDuration(elapsed),style=MiuixTheme.textStyles.title1)};IconButton(onClick=onDelete){Icon(MiuixIcons.Delete,"删除记录")}};TextField(distance,{distance=it},label="距离（km，可选）",keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth());Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick=if(paused)onBegin else onPause,modifier=Modifier.weight(1f)){Icon(if(paused)MiuixIcons.Play else MiuixIcons.Pause,if(paused)"继续" else "暂停");Text(if(paused)"继续" else "暂停")};Button(onClick={onComplete(distance.toDoubleOrNull()?:0.0)},colors=ButtonDefaults.buttonColorsPrimary(),modifier=Modifier.weight(1f)){Text("结束并保存")}}}
        editing->Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text("修改有氧记录",style=MiuixTheme.textStyles.title3);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){TextField(minutes,{minutes=it},label="时长（分钟）",keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.weight(1f));TextField(distance,{distance=it},label="距离（km）",keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.weight(1f))};Button(onClick={onEdit(minutesToSeconds(minutes),distance.toDoubleOrNull()?:0.0);editing=false},modifier=Modifier.fillMaxWidth()){Text("保存修改")}}
        record.completed->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(cardioSummary(record),style=MiuixTheme.textStyles.title3);Text("已完成",color=MiuixTheme.colorScheme.primary)};IconButton(onClick={editing=true}){Icon(MiuixIcons.Edit,"编辑有氧记录")}}
        else->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("准备开始",style=MiuixTheme.textStyles.title3);Text("自动记录运动时长，结束时可填写距离",color=MiuixTheme.colorScheme.onSurfaceSecondary)};IconButton(onClick=onBegin){Icon(MiuixIcons.Play,"开始有氧")};IconButton(onClick=onDelete){Icon(MiuixIcons.Delete,"删除记录")}}
    }
}

@Composable private fun ExerciseWorkoutCard(sets:List<SessionSetRow>,now:Long,onDetail:()->Unit,onBegin:(Long)->Unit,onPause:(Long)->Unit,onComplete:(Long,Double,Int)->Unit,onEdit:(Long,Double,Int)->Unit,onDelete:(Long)->Unit,onAdd:()->Unit){val first=sets.first();val hasActivity=sets.any{it.startedAt!=null||it.completed};var expanded by rememberSaveable(first.sessionExerciseId){mutableStateOf(hasActivity)};LaunchedEffect(hasActivity){if(hasActivity)expanded=true};Card(Modifier.cardPadding()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Surface(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth().padding(vertical=2.dp),shape=RoundedCornerShape(16.dp),color=Color.Transparent,shadowElevation=0.dp){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){val media=first.gifPath?:first.imagePath;if(media!=null)AsyncImage("file:///android_asset/$media",null,Modifier.size(72.dp).squircleClip(16.dp),contentScale=ContentScale.Crop);Column(Modifier.weight(1f)){Text(first.exerciseName,style=MiuixTheme.textStyles.title2);Text(if(hasActivity)"${sets.count{it.completed}} / ${sets.size} 组已完成" else "未开始 · ${sets.size} 组",color=MiuixTheme.colorScheme.onSurfaceSecondary)};IconButton(onClick=onDetail){Icon(MiuixIcons.Info,"查看动作详情")};Icon(if(expanded)MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,if(expanded)"收起" else "展开")}}
    AnimatedVisibility(expanded,enter=expandVertically(folmeSpring(.9f,.32f))+fadeIn(folmeSpring(.9f,.28f)),exit=shrinkVertically(folmeSpring(.92f,.28f))+fadeOut(folmeSpring(.95f,.24f))){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){sets.forEach{set->CompactSetRow(set,now,{onBegin(set.setId)},{onPause(set.setId)},{w,r->onComplete(set.setId,w,r)},{w,r->onEdit(set.setId,w,r)},{onDelete(set.setId)})};IconButton(onClick=onAdd,modifier=Modifier.align(Alignment.End)){Icon(MiuixIcons.Add,"添加一组")}}}
}}}

@Composable private fun CompactSetRow(set:SessionSetRow,now:Long,onBegin:()->Unit,onPause:()->Unit,onComplete:(Double,Int)->Unit,onEdit:(Double,Int)->Unit,onDelete:()->Unit){var weight by remember(set.setId,set.weightKg){mutableStateOf(set.weightKg.toString())};var reps by remember(set.setId,set.reps){mutableStateOf(set.reps.toString())};var editing by remember(set.setId){mutableStateOf(false)};val elapsed=if(set.completed)set.durationSeconds.toLong() else activeDurationSeconds(set.startedAt,now,set.pausedAt,set.pausedDurationMillis);val active=set.startedAt!=null&&!set.completed;val paused=set.pausedAt!=null
    if(active||editing)Column(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("第 ${set.setPosition+1} 组",style=MiuixTheme.textStyles.title3,modifier=Modifier.weight(1f));Text(if(active)"${if(paused)"已暂停" else "进行中"} · ${formatDuration(elapsed)}" else "修改记录",color=MiuixTheme.colorScheme.primary)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){TextField(weight,{weight=it},label="kg",keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.weight(1f));TextField(reps,{reps=it},label="次数",keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f))};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){if(editing)IconButton(onClick={onEdit(weight.toDoubleOrNull()?:0.0,reps.toIntOrNull()?:0);editing=false}){Icon(MiuixIcons.Ok,"保存修改")}else{IconButton(onClick=if(paused)onBegin else onPause){Icon(if(paused)MiuixIcons.Play else MiuixIcons.Pause,if(paused)"继续本组" else "暂停本组")};IconButton(onClick={onComplete(weight.toDoubleOrNull()?:0.0,reps.toIntOrNull()?:0)}){Icon(MiuixIcons.Ok,"完成本组")}};if(!set.completed)IconButton(onClick=onDelete){Icon(MiuixIcons.Delete,"删除本组")}}}
    else Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){Text("第 ${set.setPosition+1} 组",style=MiuixTheme.textStyles.title3,modifier=Modifier.width(72.dp));Column(Modifier.weight(1f)){Text("${set.weightKg} kg × ${set.reps} 次");Text(if(set.completed)"已完成 · ${formatDuration(elapsed)}${if(set.restDurationSeconds>0)" · 休息 ${formatDuration(set.restDurationSeconds.toLong())}" else ""}" else "未开始",style=MiuixTheme.textStyles.footnote1,color=if(set.completed)MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary)};if(set.completed)IconButton(onClick={editing=true}){Icon(MiuixIcons.Edit,"编辑本组")}else{IconButton(onClick=onBegin){Icon(MiuixIcons.Play,"开始本组")};IconButton(onClick=onDelete){Icon(MiuixIcons.Delete,"删除本组")}}};HorizontalDivider()
}

@Composable
fun EditableCompletedCardioRecord(record:SessionSetRow,onEdit:(Int,Double)->Unit){
    CardioRecordRow(record,0L,onBegin={},onPause={},onComplete={},onEdit=onEdit,onDelete={})
}

@Composable
fun EditableCompletedStrengthRecord(record:SessionSetRow,onEdit:(Double,Int)->Unit){
    CompactSetRow(record,0L,onBegin={},onPause={},onComplete={_,_->},onEdit=onEdit,onDelete={})
}

@Composable private fun WorkoutSheetHost(show:Boolean,kind:WorkoutSheet,currentRest:Int,onDismiss:()->Unit,onContinue:()->Unit,onDiscard:()->Unit,onSelectRest:(Int)->Unit){
    var selectedRest by remember(currentRest,show){mutableIntStateOf(currentRest)}
    if(kind==WorkoutSheet.Exit)MiuixActionDialog(show,"退出本次训练","训练记录正在自动保存。你可以稍后继续，也可以放弃并删除本次记录。","放弃训练","后台继续",onDismiss,onDiscard,onContinue)
    else FloatingBottomSheet(show,"本次组间休息",onDismiss){Column{listOf(30,60,90,120,180).forEach{seconds->RadioButtonPreference(title=formatRestLabel(seconds),selected=selectedRest==seconds,onClick={selectedRest=seconds},radioButtonLocation=RadioButtonLocation.End,modifier=Modifier.fillMaxWidth())}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick=onDismiss,modifier=Modifier.weight(1f)){Text("取消")};Button(onClick={onSelectRest(selectedRest)},colors=ButtonDefaults.buttonColorsPrimary(),modifier=Modifier.weight(1f)){Text("确定")}}}
}

fun formatRestLabel(seconds:Int)=if(seconds<60)"${seconds} 秒" else if(seconds%60==0)"${seconds/60} 分钟" else "${seconds/60} 分 ${seconds%60} 秒"

fun formatDuration(seconds:Long):String=if(seconds>=3600)"%d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60)else"%02d:%02d".format(seconds/60,seconds%60)
fun minutesForEdit(seconds:Int)=displayDecimal(seconds/60.0)
fun minutesToSeconds(minutes:String)=((minutes.toDoubleOrNull()?:0.0)*60).roundToInt()
fun cardioSummary(record:SessionSetRow):String=buildString{append(formatDuration(record.durationSeconds.toLong()));if(record.distanceKm>0)append(" · ${displayDecimal(record.distanceKm)} km");if(record.distanceKm>0&&record.durationSeconds>0){val pace=record.durationSeconds/60.0/record.distanceKm;append(" · ${pace.toInt()}:${((pace%1)*60).toInt().toString().padStart(2,'0')} /km")}}
private fun notifyRest(context:Context,vibration:Boolean,sound:Boolean){if(vibration)context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(350,VibrationEffect.DEFAULT_AMPLITUDE));if(ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)context.getSystemService(NotificationManager::class.java).notify(90,NotificationCompat.Builder(context,"rest_timer").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("休息结束").setContentText("准备开始下一组").setPriority(NotificationCompat.PRIORITY_HIGH).setSilent(!sound).build())}
