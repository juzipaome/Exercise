package com.juzi.lianji.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.juzi.lianji.MainViewModel
import com.juzi.lianji.LianJiApplication
import com.juzi.lianji.data.SessionSetRow
import com.juzi.lianji.data.TrackingMode
import com.juzi.lianji.data.activeDurationSeconds
import com.juzi.lianji.data.nextWorkoutSet
import com.juzi.lianji.data.movedItem
import com.juzi.lianji.data.orderedWorkoutGroups
import com.juzi.lianji.data.startedWorkoutGroupIndex
import kotlinx.coroutines.flow.first
import com.juzi.lianji.data.validWeight
import com.juzi.lianji.data.validReps
import kotlinx.coroutines.isActive
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
    val rows by remember(vm,sessionId){vm.repository.rows(sessionId)}.collectAsStateWithLifecycle(emptyList());val session by remember(vm,sessionId){vm.repository.session(sessionId)}.collectAsStateWithLifecycle(null);val state by vm.state.collectAsStateWithLifecycle();val context=LocalContext.current
    val now=rememberWorkoutClock(session?.status=="ACTIVE");var finishedAt by remember{mutableStateOf<Long?>(null)};var sheetKind by remember{mutableStateOf(WorkoutSheet.Exit)};var showSheet by remember{mutableStateOf(false)};var draggingExerciseId by remember{mutableStateOf<Long?>(null)};var pendingOrderIds by remember{mutableStateOf<List<Long>?>(null)};var reorderGroups by remember{mutableStateOf<List<List<SessionSetRow>>>(emptyList())};var showPlanUpdate by remember{mutableStateOf(false)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)(context.applicationContext as LianJiApplication).workoutNotifications.refresh()}
    LaunchedEffect(Unit){if(ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.POST_NOTIFICATIONS)}
    val totalSeconds=session?.let{((finishedAt?:it.endedAt?:it.startedAt)-it.startedAt)/1000}?:0
    val isFinished=finishedAt!=null||session?.status=="COMPLETED";val currentRest=rows.firstOrNull()?.restSeconds?:state.settings.defaultRestSeconds;val workoutListState=rememberLazyListState();var pendingScrollSetId by remember{mutableStateOf<Long?>(null)}
    val dragHaptics=LocalHapticFeedback.current
    val dragEdgePx=with(LocalDensity.current){48.dp.toPx()}
    val dragScrollPxPerSecond=with(LocalDensity.current){180.dp.toPx()}
    val dragSlop=LocalViewConfiguration.current.touchSlop
    var draggedIndex by remember{mutableStateOf<Int?>(null)}
    var dragStartOffset by remember{mutableIntStateOf(0)}
    var dragDistance by remember{mutableFloatStateOf(0f)}
    var dragPointerY by remember{mutableFloatStateOf(0f)}
    var dragMoved by remember{mutableStateOf(false)}
    val exerciseGroups=remember(rows){orderedWorkoutGroups(rows)}
    val plannedExerciseGroups=exerciseGroups
    val plannedOrderIds=plannedExerciseGroups.map{it.first().sessionExerciseId}
    val displayedExerciseGroups=if(draggingExerciseId!=null||pendingOrderIds!=null)reorderGroups else exerciseGroups
    val latestDisplayedExerciseGroups by rememberUpdatedState(displayedExerciseGroups)
    // Placement springs belong to manual reordering, never to automatic promotion.
    // Reattaching them when auto-scroll ends can animate stale item offsets.
    var animatePlacement by remember{mutableStateOf(false)}
    var deleteSet by remember{mutableStateOf<SessionSetRow?>(null)}
    var deleteExercise by remember{mutableStateOf<SessionSetRow?>(null)}
    fun requestDeleteSet(id:Long){
        val set=rows.firstOrNull{it.setId==id} ?: return
        if(rows.count{it.sessionExerciseId==set.sessionExerciseId}>1 && (set.startedAt!=null || set.completed))deleteSet=set
        else vm.deleteSet(id)
    }
    LaunchedEffect(plannedOrderIds,pendingOrderIds){if(pendingOrderIds==plannedOrderIds)pendingOrderIds=null}
    LaunchedEffect(rows,pendingScrollSetId,draggingExerciseId,pendingOrderIds){
        val setId=pendingScrollSetId ?: return@LaunchedEffect
        if(draggingExerciseId!=null||pendingOrderIds!=null)return@LaunchedEffect
        val target=startedWorkoutGroupIndex(rows,setId) ?: return@LaunchedEffect
        // Wait for the committed item provider/layout, not a guessed millisecond delay.
        snapshotFlow{workoutListState.layoutInfo}.first{layout->
            layout.totalItemsCount==exerciseGroups.size && layout.visibleItemsInfo.isNotEmpty() &&
                layout.visibleItemsInfo.all{item->exerciseGroups.getOrNull(item.index)?.first()?.sessionExerciseId?.let{"exercise-$it"}==item.key}
        }
        // Auto-reveal owns the motion for the whole list; other cards must not
        // keep a separate placement spring running after the scroll settles.
        workoutListState.animateScrollToItem(target)
        pendingScrollSetId=null
    }
    fun beginSet(setId:Long){animatePlacement=false;pendingScrollSetId=setId;vm.beginSet(setId)}
    fun openSheet(kind:WorkoutSheet){sheetKind=kind;showSheet=true}
    fun moveDraggedCard(){
        val index=draggedIndex ?: return
        val layout=workoutListState.layoutInfo
        val current=layout.visibleItemsInfo.firstOrNull{it.key=="exercise-$draggingExerciseId" && it.index==index} ?: return
        val center=dragStartOffset+dragDistance+current.size/2f
        val target=layout.visibleItemsInfo.firstOrNull{
            (it.index==index-1 && center<it.offset+it.size/2f) ||
                (it.index==index+1 && center>it.offset+it.size/2f)
        } ?: return
        // Keep the viewport at its current index/offset, not attached to a moving key.
        workoutListState.requestScrollToItem(workoutListState.firstVisibleItemIndex,workoutListState.firstVisibleItemScrollOffset)
        reorderGroups=movedItem(reorderGroups,index,target.index)
        draggedIndex=target.index
        dragMoved=true
        dragHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    LaunchedEffect(draggingExerciseId){
        if(draggingExerciseId==null)return@LaunchedEffect
        var previousFrame=withFrameNanos{it}
        while(isActive){
            val frame=withFrameNanos{it}
            val elapsedSeconds=((frame-previousFrame).coerceAtMost(50_000_000L))/1_000_000_000f
            previousFrame=frame
            // Pointer coordinates are relative to the viewport, not LazyColumn's content padding.
            val height=workoutListState.layoutInfo.viewportSize.height
            val direction=when{
                dragDistance < -dragSlop && dragPointerY < dragEdgePx -> -1f
                dragDistance > dragSlop && dragPointerY > height-dragEdgePx -> 1f
                else -> 0f
            }
            if(direction!=0f)workoutListState.scrollBy(direction*dragScrollPxPerSecond*elapsedSeconds)
            // A held pointer must keep swapping with newly revealed neighbors while edge-scrolling.
            moveDraggedCard()
        }
    }
    fun finishDrag(){if(dragMoved){val order=reorderGroups.map{it.first().sessionExerciseId};pendingOrderIds=order;vm.reorderExercises(sessionId,order)};draggingExerciseId=null;draggedIndex=null;dragDistance=0f;dragMoved=false}
    BackHandler{if(isFinished)onBack()else openSheet(WorkoutSheet.Exit)}
    Box(Modifier.fillMaxSize()){
    MiuixPageScaffold(title="训练中",titleProvider={if(isFinished)"训练完成" else "训练中 ${formatDuration(session?.let{(now.value-it.startedAt)/1000}?:0)}"},navigationIcon={IconButton(onClick={if(isFinished)onBack()else openSheet(WorkoutSheet.Exit)}){Icon(MiuixIcons.Back,"返回")}},actions={if(!isFinished){WorkoutMoreMenu(onAddExercise){openSheet(WorkoutSheet.Rest)};IconButton(onClick={val ended=System.currentTimeMillis();vm.finish(sessionId){hasChanges->finishedAt=ended;showPlanUpdate=hasChanges}}){Icon(MiuixIcons.Ok,"完成训练")}}},floatingToolbar={WorkoutRestToolbar(rows,now,::beginSet)}){pad->
        AnimatedContent(targetState=isFinished,modifier=Modifier.fillMaxSize(),transitionSpec={(fadeIn(folmeSpring(.9f,.35f))+slideInVertically(folmeSpring(.9f,.35f)){it/10}) togetherWith fadeOut(folmeSpring(.9f,.28f))},label="workout-finish") { finished ->
            if(finished) LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(top=pad.calculateTopPadding()+12.dp,bottom=24.dp)){item{CompletedWorkoutSummary(rows,state,totalSeconds,onBack)}}
            else BoxWithConstraints(Modifier.fillMaxSize().padding(top=pad.calculateTopPadding())){
                val positioningTail=maxHeight*.62f
                LazyColumn(Modifier.fillMaxSize().testTag("workout-exercises").pointerInput(sessionId){detectDragGesturesAfterLongPress(
                    onDragStart={point->
                        if(pendingOrderIds!=null)return@detectDragGesturesAfterLongPress
                        val layout=workoutListState.layoutInfo
                        val y=point.y+layout.viewportStartOffset
                        val hit=layout.visibleItemsInfo.firstOrNull{y>=it.offset && y<it.offset+it.size} ?: return@detectDragGesturesAfterLongPress
                        val groups=latestDisplayedExerciseGroups
                        val group=groups.getOrNull(hit.index) ?: return@detectDragGesturesAfterLongPress
                        pendingScrollSetId=null;animatePlacement=true;reorderGroups=groups
                        draggingExerciseId=group.first().sessionExerciseId;draggedIndex=hit.index
                        dragStartOffset=hit.offset;dragDistance=0f;dragPointerY=point.y;dragMoved=false
                        dragHaptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    },
                    onDrag={change,amount->change.consume();if(draggingExerciseId!=null){dragDistance+=amount.y;dragPointerY=change.position.y}},
                    onDragEnd={if(draggingExerciseId!=null){finishDrag();dragHaptics.performHapticFeedback(HapticFeedbackType.Confirm)}},
                    onDragCancel=::finishDrag,
                )},state=workoutListState,userScrollEnabled=draggingExerciseId==null,contentPadding=PaddingValues(top=12.dp,bottom=positioningTail)){
                    displayedExerciseGroups.forEachIndexed{index,sets->val first=sets.first();val exerciseId=first.sessionExerciseId;item(key="exercise-$exerciseId"){
                        val dragging=draggingExerciseId==exerciseId
                        ReorderableWorkoutCard(first.exerciseName,index,displayedExerciseGroups.size,dragging,{if(dragging){val offset=workoutListState.layoutInfo.visibleItemsInfo.firstOrNull{it.key=="exercise-$exerciseId"}?.offset?:dragStartOffset;dragStartOffset+dragDistance-offset}else 0f},if(dragging||pendingScrollSetId!=null||!animatePlacement)Modifier else Modifier.animateItem(fadeInSpec=null,placementSpec=folmeSpring(.88f,.32f),fadeOutSpec=null),onAccessibilityMove={from,to->animatePlacement=true;val reordered=movedItem(exerciseGroups,from,to);vm.reorderExercises(sessionId,reordered.map{it.first().sessionExerciseId})}) { dragging ->
                            if(first.trackingMode==TrackingMode.CARDIO)CardioWorkoutCard(sets,now,dragging,{onExerciseDetail(first.exerciseId)},{deleteExercise=first},::beginSet,vm::pauseSet,vm::completeCardio,vm::updateCardioValues,vm::updateCardioDraft,::requestDeleteSet)else ExerciseWorkoutCard(sets,now,dragging,{onExerciseDetail(first.exerciseId)},{deleteExercise=first},::beginSet,{vm.pauseSet(it)},{id,w,r->vm.completeSet(id,w,r)},vm::updateSetValues,::requestDeleteSet){val last=sets.last();vm.addSet(exerciseId,sets.size,last.weightKg,last.reps)}
                        }
                    }}
                }
            }
        }
    }
    WorkoutSheetHost(showSheet,sheetKind,currentRest,{showSheet=false},{showSheet=false;onBack()},{showSheet=false;vm.discard(sessionId);onBack()}){seconds->vm.setSessionRest(sessionId,seconds);showSheet=false}
    MiuixActionDialog(deleteSet!=null,"删除已有训练记录","将删除“${deleteSet?.exerciseName.orEmpty()}”第 ${(deleteSet?.setPosition?:0)+1} 组，包括已记录的时长和训练数据，无法撤销。","取消","确认删除",{deleteSet=null},{},{deleteSet?.let{vm.deleteSet(it.setId,confirmed=true)}})
    MiuixActionDialog(deleteExercise!=null,"删除训练动作","将从本次训练移除“${deleteExercise?.exerciseName.orEmpty()}”及其全部组和训练记录，无法撤销。原计划和其他训练历史不受影响。","取消","确认删除",{deleteExercise=null},{},{deleteExercise?.let{vm.deleteExercise(it.sessionExerciseId)}})
    MiuixActionDialog(showPlanUpdate,"保存本次计划调整","本次训练与原计划不同。你可以另存一份，或将本次调整同步到当前计划。","另存新计划","覆盖当前计划",{showPlanUpdate=false},{vm.saveSessionPlan(sessionId,false);showPlanUpdate=false},{vm.saveSessionPlan(sessionId,true);showPlanUpdate=false})
    }
}

@Composable
private fun WorkoutMoreMenu(onAddExercise:()->Unit,onAdjustRest:()->Unit){
    OverlayIconDropdownMenu(entry=DropdownEntry(listOf(DropdownItem("添加动作",onClick=onAddExercise),DropdownItem("调整休息时间",onClick=onAdjustRest)))){Icon(MiuixIcons.More,"更多训练功能")}
}

@Composable
private fun WorkoutCardMenu(name:String,onDetail:()->Unit,onDelete:()->Unit){
    OverlayIconDropdownMenu(entry=DropdownEntry(listOf(DropdownItem("查看动作详情",onClick=onDetail),DropdownItem("删除动作",onClick=onDelete)))){Icon(MiuixIcons.More,"$name 动作菜单")}
}

@Composable
private fun ReorderableWorkoutCard(
    exerciseName:String,
    index:Int,
    itemCount:Int,
    dragging:Boolean,
    dragOffset:()->Float,
    modifier:Modifier,
    onAccessibilityMove:(Int,Int)->Unit,
    content:@Composable (Boolean)->Unit,
){
    val scale by animateFloatAsState(if(dragging)1.025f else 1f,folmeSpring(.88f,.32f),label="workout-card-drag-scale")
    val accessibilityActions=buildList{
        if(index>0)add(CustomAccessibilityAction("上移") { onAccessibilityMove(index,index-1);true })
        if(index<itemCount-1)add(CustomAccessibilityAction("下移") { onAccessibilityMove(index,index+1);true })
    }
    Box(modifier.zIndex(if(dragging)1f else 0f).graphicsLayer{translationY=dragOffset();scaleX=scale;scaleY=scale}.semantics{contentDescription="长按拖动 $exerciseName 调整顺序";customActions=accessibilityActions}){
        content(dragging)
    }
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
private fun CardioWorkoutCard(records:List<SessionSetRow>,now:State<Long>,dragging:Boolean,onDetail:()->Unit,onDeleteExercise:()->Unit,onBegin:(Long)->Unit,onPause:(Long)->Unit,onComplete:(Long,Double)->Unit,onEdit:(Long,Int,Double)->Unit,onDraft:(Long,Double)->Unit,onDelete:(Long)->Unit){
    val first=records.first();val hasActivity=records.any{it.startedAt!=null||it.completed};var expanded by rememberSaveable(first.sessionExerciseId){mutableStateOf(true)};LaunchedEffect(hasActivity){if(hasActivity)expanded=true}
    Card(Modifier.cardPadding()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Surface(onClick={expanded=!expanded},enabled=!dragging,modifier=Modifier.fillMaxWidth().padding(vertical=2.dp).squircleClip(16.dp),color=Color.Transparent,shadowElevation=0.dp){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            val media=first.gifPath?:first.imagePath;if(media!=null)AsyncImage("file:///android_asset/$media",null,Modifier.size(72.dp).squircleClip(16.dp),contentScale=ContentScale.Crop)
            Column(Modifier.weight(1f)){Text(first.exerciseName,style=MiuixTheme.textStyles.title2);Text(if(hasActivity)"有氧计时 · ${records.count{it.completed}} 次已完成" else "有氧计时 · 未开始",color=MiuixTheme.colorScheme.onSurfaceSecondary)}
            if(dragging)Icon(MiuixIcons.Sort,"拖动排序")else{WorkoutCardMenu(first.exerciseName,onDetail,onDeleteExercise);Icon(if(expanded)MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,if(expanded)"收起" else "展开")}
        }}
        AnimatedVisibility(expanded,enter=expandVertically(folmeSpring(.9f,.32f))+fadeIn(folmeSpring(.9f,.28f)),exit=shrinkVertically(folmeSpring(.92f,.28f))+fadeOut(folmeSpring(.95f,.24f))){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){records.forEach{record->CardioRecordRow(record,now,{onBegin(record.setId)},{onPause(record.setId)},{distance->onComplete(record.setId,distance)},{duration,distance->onEdit(record.setId,duration,distance)},{onDelete(record.setId)},{distance->onDraft(record.setId,distance)})}}}
    }}
}

@Composable
private fun CardioRecordRow(record:SessionSetRow,now:State<Long>,onBegin:()->Unit,onPause:()->Unit,onComplete:(Double)->Unit,onEdit:(Int,Double)->Unit,onDelete:()->Unit,onDraft:(Double)->Unit={},allowDelete:Boolean=true){
    var distance by rememberSaveable(record.setId){mutableStateOf(if(record.distanceKm>0)exactDecimal(record.distanceKm) else "")}
    var minutes by rememberSaveable(record.setId){mutableStateOf(if(record.durationSeconds>0)minutesForEdit(record.durationSeconds) else "")}
    var editing by remember(record.setId){mutableStateOf(false)}
    val active=record.startedAt!=null&&!record.completed;val paused=record.pausedAt!=null;val elapsed=if(record.completed)record.durationSeconds.toLong()else activeDurationSeconds(record.startedAt,if(record.startedAt==null)0 else record.pausedAt?:now.value,record.pausedAt,record.pausedDurationMillis)
    when{
        active->Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(paused)"已暂停" else "进行中",style=MiuixTheme.textStyles.title3,color=MiuixTheme.colorScheme.primary);Text(formatDuration(elapsed),style=MiuixTheme.textStyles.title1)};IconButton(onClick=onDelete){Icon(MiuixIcons.Delete,"删除记录")}};TextField(distance,{distance=it;validWeight(it.ifBlank{"0"})?.let(onDraft)},label="距离（km，可选）",keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth());Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick=if(paused)onBegin else onPause,modifier=Modifier.weight(1f)){Icon(if(paused)MiuixIcons.Play else MiuixIcons.Pause,if(paused)"继续" else "暂停");Text(if(paused)"继续" else "暂停")};Button(enabled=validWeight(distance.ifBlank{"0"})!=null,onClick={onComplete(validWeight(distance.ifBlank{"0"})!!)},colors=ButtonDefaults.buttonColorsPrimary(),modifier=Modifier.weight(1f)){Text("结束并保存")}}}
        editing->Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text("修改有氧记录",style=MiuixTheme.textStyles.title3);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){TextField(minutes,{minutes=it},label="时长（分钟）",keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.weight(1f));TextField(distance,{distance=it},label="距离（km）",keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.weight(1f))};Button(enabled=validWeight(minutes)!=null&&validWeight(distance.ifBlank{"0"})!=null,onClick={onEdit(minutesToSeconds(minutes),validWeight(distance.ifBlank{"0"})!!);editing=false},modifier=Modifier.fillMaxWidth()){Text("保存修改")}}
        record.completed->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(cardioSummary(record),style=MiuixTheme.textStyles.title3);Text("已完成",color=MiuixTheme.colorScheme.primary)};IconButton(onClick={minutes=minutesForEdit(record.durationSeconds);distance=exactDecimal(record.distanceKm);editing=true}){Icon(MiuixIcons.Edit,"编辑有氧记录")};if(allowDelete)IconButton(onClick=onDelete){Icon(MiuixIcons.Delete,"删除记录")}}
        else->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("准备开始",style=MiuixTheme.textStyles.title3);Text("自动记录运动时长，结束时可填写距离",color=MiuixTheme.colorScheme.onSurfaceSecondary)};IconButton(onClick=onBegin){Icon(MiuixIcons.Play,"开始有氧")};IconButton(onClick=onDelete){Icon(MiuixIcons.Delete,"删除记录")}}
    }
}

@Composable private fun ExerciseWorkoutCard(sets:List<SessionSetRow>,now:State<Long>,dragging:Boolean,onDetail:()->Unit,onDeleteExercise:()->Unit,onBegin:(Long)->Unit,onPause:(Long)->Unit,onComplete:(Long,Double,Int)->Unit,onEdit:(Long,Double,Int)->Unit,onDelete:(Long)->Unit,onAdd:()->Unit){val first=sets.first();val hasActivity=sets.any{it.startedAt!=null||it.completed};var expanded by rememberSaveable(first.sessionExerciseId){mutableStateOf(hasActivity)};LaunchedEffect(hasActivity){if(hasActivity)expanded=true};Card(Modifier.cardPadding()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Surface(onClick={expanded=!expanded},enabled=!dragging,modifier=Modifier.fillMaxWidth().padding(vertical=2.dp).squircleClip(16.dp),color=Color.Transparent,shadowElevation=0.dp){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){val media=first.gifPath?:first.imagePath;if(media!=null)AsyncImage("file:///android_asset/$media",null,Modifier.size(72.dp).squircleClip(16.dp),contentScale=ContentScale.Crop);Column(Modifier.weight(1f)){Text(first.exerciseName,style=MiuixTheme.textStyles.title2);Text(if(hasActivity)"${sets.count{it.completed}} / ${sets.size} 组已完成" else "未开始 · ${sets.size} 组",color=MiuixTheme.colorScheme.onSurfaceSecondary)};if(dragging)Icon(MiuixIcons.Sort,"拖动排序")else{WorkoutCardMenu(first.exerciseName,onDetail,onDeleteExercise);Icon(if(expanded)MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,if(expanded)"收起" else "展开")}}}
    AnimatedVisibility(expanded,enter=expandVertically(folmeSpring(.9f,.32f))+fadeIn(folmeSpring(.9f,.28f)),exit=shrinkVertically(folmeSpring(.92f,.28f))+fadeOut(folmeSpring(.95f,.24f))){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){sets.forEach{set->CompactSetRow(set,now,{onBegin(set.setId)},{onPause(set.setId)},{w,r->onComplete(set.setId,w,r)},{w,r->onEdit(set.setId,w,r)},{onDelete(set.setId)})};IconButton(onClick=onAdd,modifier=Modifier.align(Alignment.End)){Icon(MiuixIcons.Add,"添加一组")}}}
}}}

@Composable private fun CompactSetRow(set:SessionSetRow,now:State<Long>,onBegin:()->Unit,onPause:()->Unit,onComplete:(Double,Int)->Unit,onEdit:(Double,Int)->Unit,onDelete:()->Unit,allowDelete:Boolean=true){var weight by rememberSaveable(set.setId){mutableStateOf(set.weightKg.toString())};var reps by rememberSaveable(set.setId){mutableStateOf(set.reps.toString())};var editing by remember(set.setId){mutableStateOf(false)};val elapsed=if(set.completed)set.durationSeconds.toLong() else activeDurationSeconds(set.startedAt,if(set.startedAt==null)0 else set.pausedAt?:now.value,set.pausedAt,set.pausedDurationMillis);val active=set.startedAt!=null&&!set.completed;val paused=set.pausedAt!=null
    if(active||editing)Column(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("第 ${set.setPosition+1} 组",style=MiuixTheme.textStyles.title3,modifier=Modifier.weight(1f));Text(if(active)"${if(paused)"已暂停" else "进行中"} · ${formatDuration(elapsed)}" else "修改记录",color=MiuixTheme.colorScheme.primary)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){TextField(weight,{weight=it;if(!editing)validWeight(it)?.let{w->validReps(reps)?.let{r->onEdit(w,r)}}},label="kg",keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.weight(1f));TextField(reps,{reps=it;if(!editing)validReps(it)?.let{r->validWeight(weight)?.let{w->onEdit(w,r)}}},label="次数",keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f))};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){if(editing)IconButton(enabled=validWeight(weight)!=null&&validReps(reps)!=null,onClick={onEdit(validWeight(weight)!!,validReps(reps)!!);editing=false}){Icon(MiuixIcons.Ok,"保存修改")}else{IconButton(onClick=if(paused)onBegin else onPause){Icon(if(paused)MiuixIcons.Play else MiuixIcons.Pause,if(paused)"继续本组" else "暂停本组")};IconButton(enabled=validWeight(weight)!=null&&validReps(reps)!=null,onClick={onComplete(validWeight(weight)!!,validReps(reps)!!)}){Icon(MiuixIcons.Ok,"完成本组")}};if(allowDelete)IconButton(onClick=onDelete){Icon(MiuixIcons.Delete,"删除本组")}}}
    else Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){Text("第 ${set.setPosition+1} 组",style=MiuixTheme.textStyles.title3,modifier=Modifier.width(72.dp));Column(Modifier.weight(1f)){Text("${set.weightKg} kg × ${set.reps} 次");Text(if(set.completed)"已完成 · ${formatDuration(elapsed)}${if(set.restDurationSeconds>0)" · 休息 ${formatDuration(set.restDurationSeconds.toLong())}" else ""}" else "未开始",style=MiuixTheme.textStyles.footnote1,color=if(set.completed)MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary)};IconButton(onClick={weight=set.weightKg.toString();reps=set.reps.toString();editing=true}){Icon(MiuixIcons.Edit,if(set.completed)"编辑本组" else "训练前调整重量和次数")};if(!set.completed)IconButton(onClick=onBegin){Icon(MiuixIcons.Play,"开始本组")};if(allowDelete)IconButton(onClick=onDelete){Icon(MiuixIcons.Delete,"删除本组")}};HorizontalDivider()
}

@Composable
fun EditableCompletedCardioRecord(record:SessionSetRow,onEdit:(Int,Double)->Unit){
    CardioRecordRow(record,remember{mutableStateOf(0L)},onBegin={},onPause={},onComplete={},onEdit=onEdit,onDelete={},allowDelete=false)
}

@Composable
fun EditableCompletedStrengthRecord(record:SessionSetRow,onEdit:(Double,Int)->Unit){
    CompactSetRow(record,remember{mutableStateOf(0L)},onBegin={},onPause={},onComplete={_,_->},onEdit=onEdit,onDelete={},allowDelete=false)
}

@Composable private fun WorkoutSheetHost(show:Boolean,kind:WorkoutSheet,currentRest:Int,onDismiss:()->Unit,onContinue:()->Unit,onDiscard:()->Unit,onSelectRest:(Int)->Unit){
    var selectedRest by remember(currentRest,show){mutableIntStateOf(currentRest)}
    if(kind==WorkoutSheet.Exit)MiuixActionDialog(show,"退出本次训练","训练记录正在自动保存。你可以稍后继续，也可以放弃并删除本次记录。","放弃训练","后台继续",onDismiss,onDiscard,onContinue)
    else FloatingBottomSheet(show,"本次组间休息",onDismiss){Column{listOf(30,60,90,120,180).forEach{seconds->RadioButtonPreference(title=formatRestLabel(seconds),selected=selectedRest==seconds,onClick={selectedRest=seconds},radioButtonLocation=RadioButtonLocation.End,modifier=Modifier.fillMaxWidth())}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick=onDismiss,modifier=Modifier.weight(1f)){Text("取消")};Button(onClick={onSelectRest(selectedRest)},colors=ButtonDefaults.buttonColorsPrimary(),modifier=Modifier.weight(1f)){Text("确定")}}}
}

fun formatRestLabel(seconds:Int)=if(seconds<60)"${seconds} 秒" else if(seconds%60==0)"${seconds/60} 分钟" else "${seconds/60} 分 ${seconds%60} 秒"

fun formatDuration(seconds:Long):String=if(seconds>=3600)"%d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60)else"%02d:%02d".format(seconds/60,seconds%60)
fun minutesForEdit(seconds:Int)=displayDecimal(seconds/60.0)
fun minutesToSeconds(minutes:String)=((validWeight(minutes)?:0.0)*60).coerceAtMost(Int.MAX_VALUE.toDouble()).roundToInt()
fun cardioSummary(record:SessionSetRow):String=buildString{append(formatDuration(record.durationSeconds.toLong()));if(record.distanceKm>0)append(" · ${displayDecimal(record.distanceKm)} km");if(record.distanceKm>0&&record.durationSeconds>0){val pace=record.durationSeconds/60.0/record.distanceKm;append(" · ${pace.toInt()}:${((pace%1)*60).toInt().toString().padStart(2,'0')} /km")}}

@Composable
private fun WorkoutRestToolbar(rows:List<SessionSetRow>,now:State<Long>,onBegin:(Long)->Unit){
    val rest=rows.lastOrNull{it.restStartedAt!=null&&it.restEndedAt==null}
    AnimatedVisibility(rest!=null,enter=fadeIn(folmeSpring(.9f,.28f))+expandVertically(folmeSpring(.9f,.32f)),exit=fadeOut(folmeSpring(.95f,.24f))+shrinkVertically(folmeSpring(.92f,.28f))){rest?.let{
        val elapsed=((now.value-it.restStartedAt!!)/1000).coerceAtLeast(0)
        val remaining=(it.restSeconds-elapsed).coerceAtLeast(0)
        HeroCard(if(remaining>0)"组间休息 ${formatDuration(remaining)}" else "休息结束",
            "上一组 ${formatDuration(it.durationSeconds.toLong())} · 已休息 ${formatDuration(elapsed)}",
            "开始下一项",{nextWorkoutSet(rows,it.setId)?.let{next->onBegin(next.setId)}},floating=true)
    }}
}
