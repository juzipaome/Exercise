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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
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
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassIconDropdownMenu
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Add
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.Edit
import top.yukonga.miuix.kmp.icon.glass.ExpandLess
import top.yukonga.miuix.kmp.icon.glass.ExpandMore
import top.yukonga.miuix.kmp.icon.glass.More
import top.yukonga.miuix.kmp.icon.glass.Ok
import top.yukonga.miuix.kmp.icon.glass.Pause
import top.yukonga.miuix.kmp.icon.glass.Play
import top.yukonga.miuix.kmp.icon.glass.Sort
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.anim.folmeSpring

private enum class WorkoutSheet { Exit, Rest }

@Composable
fun WorkoutScreen(vm:MainViewModel,sessionId:Long,onBack:()->Unit,onAddExercise:()->Unit,onExerciseDetail:(String)->Unit){
    val rows by remember(vm,sessionId){vm.repository.rows(sessionId)}.collectAsStateWithLifecycle(emptyList());val session by remember(vm,sessionId){vm.repository.session(sessionId)}.collectAsStateWithLifecycle(null);val state by vm.state.collectAsStateWithLifecycle();val context=LocalContext.current
    val now=rememberWorkoutClock(session?.status=="ACTIVE");var finishedAt by remember{mutableStateOf<Long?>(null)};var sheetKind by remember{mutableStateOf(WorkoutSheet.Exit)};var showSheet by remember{mutableStateOf(false)};var draggingExerciseId by remember{mutableStateOf<Long?>(null)};var pendingOrderIds by remember{mutableStateOf<List<Long>?>(null)};var reorderGroups by remember{mutableStateOf<List<List<SessionSetRow>>>(emptyList())};var showPlanUpdate by remember{mutableStateOf(false)}
    var showMoreMenu by remember{mutableStateOf(false)}
    val moreAnchor = rememberGlassPopupAnchor()
    val workoutScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)(context.applicationContext as LianJiApplication).workoutNotifications.refresh()}
    LaunchedEffect(Unit){if(ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.POST_NOTIFICATIONS)}
    val totalSeconds=session?.let{((finishedAt?:it.endedAt?:it.startedAt)-it.startedAt).coerceAtLeast(0)/1000}?:0
    val isFinished=finishedAt!=null||session?.status=="COMPLETED";val workoutListState=rememberLazyListState();var pendingScrollSetId by remember{mutableStateOf<Long?>(null)}
    var revealSetId by remember { mutableStateOf<Long?>(null) }
    val currentRest = session?.restSecondsOverride ?: rows.firstOrNull()?.restSeconds ?: state.settings.defaultRestSeconds
    val dragHaptics=LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val listSpacing = 12.dp
    val listSpacingPx = with(LocalDensity.current) { listSpacing.roundToPx() }
    val dragEdgePx=with(LocalDensity.current){36.dp.toPx()}
    val maxDragScrollPxPerSecond=with(LocalDensity.current){280.dp.toPx()}
    val dragSlop=LocalViewConfiguration.current.touchSlop
    var draggedIndex by remember{mutableStateOf<Int?>(null)}
    var dragStartOffset by remember{mutableIntStateOf(0)}
    var dragDistance by remember{mutableFloatStateOf(0f)}
    var dragPointerY by remember{mutableFloatStateOf(0f)}
    var topEdgeArmed by remember{mutableStateOf(false)}
    var bottomEdgeArmed by remember{mutableStateOf(false)}
    var draggedCardSize by remember{mutableIntStateOf(0)}
    var dragMoved by remember{mutableStateOf(false)}
    val exerciseGroups=remember(rows){orderedWorkoutGroups(rows)}
    val plannedExerciseGroups=exerciseGroups
    val plannedOrderIds=plannedExerciseGroups.map{it.first().sessionExerciseId}
    val latestExerciseGroups by rememberUpdatedState(exerciseGroups)
    val displayedExerciseGroupsState = remember {
        derivedStateOf {
            if (draggingExerciseId != null || pendingOrderIds != null) reorderGroups else latestExerciseGroups
        }
    }
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
        revealSetId = setId
        pendingScrollSetId=null
    }
    fun beginSet(setId:Long){animatePlacement=false;revealSetId=null;pendingScrollSetId=setId;vm.beginSet(setId)}
    fun openSheet(kind:WorkoutSheet){sheetKind=kind;showSheet=true}
    fun swapDraggedCard(from:Int,to:Int){
        val anchorIndex=workoutListState.firstVisibleItemIndex
        val anchorOffset=workoutListState.firstVisibleItemScrollOffset
        Snapshot.withMutableSnapshot {
            reorderGroups=movedItem(reorderGroups,from,to)
            draggedIndex=to
        }
        // Keep the viewport anchored to its top slot only when the swap involves that slot.
        if(from==anchorIndex || to==anchorIndex){
            val maxOffset = if (to == anchorIndex && draggedCardSize > 0) {
                (draggedCardSize - 1).coerceAtLeast(0)
            } else {
                anchorOffset
            }
            workoutListState.requestScrollToItem(anchorIndex,anchorOffset.coerceAtMost(maxOffset))
        }
        dragMoved=true
        dragHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    fun moveDraggedCard(){
        val dragId=draggingExerciseId ?: return
        val index=draggedIndex ?: return
        val groups=reorderGroups
        if(index !in groups.indices)return
        val layout=workoutListState.layoutInfo
        if(layout.totalItemsCount!=groups.size)return
        val current=layout.visibleItemsInfo.firstOrNull{it.index==index && it.key=="exercise-$dragId"} ?: return
        draggedCardSize=current.size
        val size=current.size
        if(size<=0)return
        val center=dragStartOffset+dragDistance+size/2f
        val target=layout.visibleItemsInfo.firstOrNull{candidate->
            val expectedId=groups.getOrNull(candidate.index)?.firstOrNull()?.sessionExerciseId
            expectedId!=null && candidate.key=="exercise-$expectedId" && (
                (candidate.index==index-1 && center<candidate.offset+candidate.size/2f) ||
                    (candidate.index==index+1 && center>candidate.offset+candidate.size/2f)
                )
        } ?: return
        swapDraggedCard(index,target.index)
    }
    LaunchedEffect(draggingExerciseId){
        if(draggingExerciseId==null)return@LaunchedEffect
        if(workoutListState.isScrollInProgress)workoutListState.scrollBy(0f)
        var previousFrame=withFrameNanos{it}
        while(isActive){
            val frame=withFrameNanos{it}
            val elapsedSeconds=((frame-previousFrame).coerceAtMost(50_000_000L))/1_000_000_000f
            previousFrame=frame
            val layout=workoutListState.layoutInfo
            // The list draws behind the top bar, but drag scrolling starts below it.
            val top = layout.beforeContentPadding - listSpacingPx
            val height=layout.viewportSize.height.toFloat()
            val velocityPxPerSec=when{
                topEdgeArmed && workoutListState.canScrollBackward && dragPointerY < top+dragEdgePx -> {
                    val depth=((top+dragEdgePx-dragPointerY)/dragEdgePx).coerceIn(0f,1f)
                    -maxDragScrollPxPerSecond*depth*depth
                }
                bottomEdgeArmed && workoutListState.canScrollForward && dragPointerY > height-dragEdgePx -> {
                    val depth=((dragPointerY-(height-dragEdgePx))/dragEdgePx).coerceIn(0f,1f)
                    maxDragScrollPxPerSecond*depth*depth
                }
                else -> 0f
            }
            val delta=velocityPxPerSec*elapsedSeconds
            if(delta!=0f){
                val index=draggedIndex
                val current=index?.let{idx->layout.visibleItemsInfo.firstOrNull{it.key=="exercise-$draggingExerciseId" && it.index==idx}}
                if(current!=null){
                    // Swap before the dragged card's slot can scroll out of visibleItemsInfo and get recycled.
                    if(delta>0f && index+1<reorderGroups.size && current.offset+current.size-delta <= layout.viewportStartOffset+top+4f){
                        swapDraggedCard(index,index+1)
                    } else if(delta<0f && index>0 && current.offset-delta >= layout.viewportEndOffset-4f){
                        swapDraggedCard(index,index-1)
                    }
                }
                workoutListState.scrollBy(delta)
            }
            // A held pointer must keep swapping with newly revealed neighbors while edge-scrolling.
            moveDraggedCard()
        }
    }
    fun finishDrag(){if(dragMoved){val order=reorderGroups.map{it.first().sessionExerciseId};pendingOrderIds=order;vm.reorderExercises(sessionId,order)};draggingExerciseId=null;draggedIndex=null;dragDistance=0f;topEdgeArmed=false;bottomEdgeArmed=false;draggedCardSize=0;dragMoved=false}
    BackHandler{if(isFinished)onBack()else openSheet(WorkoutSheet.Exit)}
    Box(Modifier.fillMaxSize()){
    val glassStyle = rememberOs4GlassStyle()
    val customMaterial = rememberOs4CustomMaterial()
    val popupVisuals = GlassPopupDefaults.visuals(
        style = glassStyle,
        material = customMaterial ?: GlassPopupDefaults.material(),
        stroke = rememberOs4EdgeStroke(GlassPopupDefaults.stroke()),
    )
    MiuixPageScaffold(
        title="训练中",
        titleProvider={if(isFinished)"训练完成" else "训练中 ${formatDuration(session?.let{(now.value-it.startedAt)/1000}?:0)}"},
        scrollBehavior=workoutScrollBehavior,
        navigationIcon={
            TopBarIconButton(onClick={if(isFinished)onBack()else openSheet(WorkoutSheet.Exit)}){
                Icon(
                    imageVector=MiuixIcons.Glass.ChevronBackward,
                    contentDescription="返回",
                    modifier=Modifier.size(28.dp),
                    tint=MiuixTheme.colorScheme.onSurface,
                )
            }
        },
        actions={
            if(!isFinished){
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    TopBarIconButton(
                        onClick={showMoreMenu=!showMoreMenu},
                        modifier=Modifier.glassPopupAnchor(moreAnchor,GlassTopAppBarDefaults.ButtonSize/2,floating=true),
                    ){
                        Icon(MiuixIcons.Glass.More,"更多训练功能",Modifier.size(28.dp).glassPopupAnchorContent(moreAnchor),tint=MiuixTheme.colorScheme.onSurface)
                    }
                    TopBarIconButton(
                        onClick={val ended=System.currentTimeMillis();vm.finish(sessionId){hasChanges->finishedAt=ended;showPlanUpdate=hasChanges}},
                    ){
                        Icon(MiuixIcons.Glass.Ok,"完成训练",Modifier.size(28.dp),tint=MiuixTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        topBarBottomContent={
            WorkoutRestToolbar(rows,now,isFinished,{workoutListState.canScrollBackward},::beginSet)
        },
        overlay={pageBackdrop->
            if(!isFinished){
                GlassIconDropdownMenu(
                    show=showMoreMenu,
                    onDismissRequest={showMoreMenu=false},
                    anchor=moreAnchor,
                    anchorContent={Icon(MiuixIcons.Glass.More,null,Modifier.size(28.dp),tint=MiuixTheme.colorScheme.onSurface)},
                    entries=listOf(DropdownEntry(listOf(DropdownItem("添加动作",onClick=onAddExercise),DropdownItem("调整休息时间",onClick={openSheet(WorkoutSheet.Rest)})))),
                    backdrop=LocalRootBackdrop.current ?: pageBackdrop,
                    visuals=popupVisuals,
                )
            }
        },
    ){pad->
        AnimatedContent(targetState=isFinished,modifier=Modifier.fillMaxSize(),transitionSpec={(fadeIn(folmeSpring(.9f,.35f))+slideInVertically(folmeSpring(.9f,.35f)){it/10}) togetherWith fadeOut(folmeSpring(.9f,.28f))},label="workout-finish") { finished ->
            if (finished) {
                LazyColumn(Modifier.fillMaxSize().os4ScrollModifiers(workoutScrollBehavior), contentPadding = pad.withPageSpacing()) {
                    item { CompletedWorkoutSummary(rows, state, totalSeconds, onBack) }
                }
            } else Box(
                Modifier.fillMaxSize()
                    .padding(
                        start = pad.calculateStartPadding(layoutDirection),
                        end = pad.calculateEndPadding(layoutDirection),
                    )
                    .consumeWindowInsets(pad)
                    .imePadding(),
            ) {
                val topPadding = pad.calculateTopPadding()
                LazyColumn(Modifier.fillMaxSize().testTag("workout-exercises").os4ScrollModifiers(workoutScrollBehavior).pointerInput(sessionId){detectDragGesturesAfterLongPress(
                    onDragStart={point->
                        if(pendingOrderIds!=null)return@detectDragGesturesAfterLongPress
                        val layout=workoutListState.layoutInfo
                        val top = layout.beforeContentPadding - listSpacingPx
                        if (point.y < top) return@detectDragGesturesAfterLongPress
                        val y=point.y+layout.viewportStartOffset
                        val hit=layout.visibleItemsInfo.firstOrNull{y>=it.offset && y<it.offset+it.size} ?: return@detectDragGesturesAfterLongPress
                        val groups=displayedExerciseGroupsState.value
                        val group=groups.getOrNull(hit.index) ?: return@detectDragGesturesAfterLongPress
                        pendingScrollSetId=null;animatePlacement=true;reorderGroups=groups
                        draggingExerciseId=group.first().sessionExerciseId;draggedIndex=hit.index
                        dragStartOffset=hit.offset;draggedCardSize=hit.size;dragDistance=0f
                        dragPointerY=point.y
                        // Only arm edge auto-scroll when the gesture starts clear of that edge (or later leaves it).
                        topEdgeArmed=point.y >= top+dragEdgePx+dragSlop
                        bottomEdgeArmed=point.y <= layout.viewportSize.height-dragEdgePx-dragSlop
                        dragMoved=false
                        dragHaptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    },
                    onDrag={change,amount->
                        change.consume()
                        if(draggingExerciseId!=null){
                            dragDistance+=amount.y
                            dragPointerY=change.position.y
                            val layout = workoutListState.layoutInfo
                            val height=layout.viewportSize.height
                            val top = layout.beforeContentPadding - listSpacingPx
                            if(!topEdgeArmed && dragPointerY >= top+dragEdgePx+dragSlop) topEdgeArmed=true
                            if(!bottomEdgeArmed && dragPointerY <= height-dragEdgePx-dragSlop) bottomEdgeArmed=true
                            moveDraggedCard()
                        }
                    },
                    onDragEnd={if(draggingExerciseId!=null){finishDrag();dragHaptics.performHapticFeedback(HapticFeedbackType.Confirm)}},
                    onDragCancel=::finishDrag,
                )},state=workoutListState,userScrollEnabled=draggingExerciseId==null,contentPadding=PaddingValues(top=topPadding+listSpacing,bottom=pad.calculateBottomPadding()+24.dp)){
                    val displayedExerciseGroups=displayedExerciseGroupsState.value
                    displayedExerciseGroups.forEachIndexed{index,sets->val first=sets.first();val exerciseId=first.sessionExerciseId;item(key="exercise-$exerciseId"){
                        val dragging=draggingExerciseId==exerciseId
                        ReorderableWorkoutCard(first.exerciseName,index,displayedExerciseGroups.size,dragging,{if(dragging){val offset=workoutListState.layoutInfo.visibleItemsInfo.firstOrNull{it.key=="exercise-$exerciseId"}?.offset?:dragStartOffset;dragStartOffset+dragDistance-offset}else 0f},if(dragging||pendingScrollSetId!=null||!animatePlacement)Modifier else Modifier.animateItem(fadeInSpec=null,placementSpec=folmeSpring(.88f,.32f),fadeOutSpec=null),onAccessibilityMove={from,to->animatePlacement=true;val reordered=movedItem(exerciseGroups,from,to);vm.reorderExercises(sessionId,reordered.map{it.first().sessionExerciseId})}) { dragging ->
                            val requestedSetId = pendingScrollSetId ?: revealSetId
                            val requestedSet = requestedSetId?.takeIf { id -> sets.any { it.setId == id } }
                            val onControlsRevealed = { if (revealSetId == requestedSet) revealSetId = null }
                            if(first.trackingMode==TrackingMode.CARDIO)CardioWorkoutCard(sets,now,dragging,requestedSet,revealSetId,onControlsRevealed,{onExerciseDetail(first.exerciseId)},{deleteExercise=first},::beginSet,vm::pauseSet,vm::completeCardio,vm::updateCardioValues,vm::updateCardioDraft,::requestDeleteSet)else ExerciseWorkoutCard(sets,now,dragging,requestedSet,revealSetId,onControlsRevealed,{onExerciseDetail(first.exerciseId)},{deleteExercise=first},::beginSet,{vm.pauseSet(it)},{id,w,r->vm.completeSet(id,w,r)},vm::updateSetValues,::requestDeleteSet){val last=sets.last();vm.addSet(exerciseId,sets.size,last.weightKg,last.reps)}
                        }
                    }}
                }
            }
        }
    }
    WorkoutSheetHost(showSheet,sheetKind,currentRest,{showSheet=false},{showSheet=false;onBack()},{showSheet=false;vm.discard(sessionId);onBack()}){seconds->vm.setSessionRest(sessionId,seconds);showSheet=false}
    MiuixActionDialog(deleteSet!=null,"删除已有训练记录","将删除“${deleteSet?.exerciseName.orEmpty()}”第 ${(deleteSet?.setPosition?:0)+1} 组，包括已记录的时长和训练数据，无法撤销。","取消","确认删除",{deleteSet=null},{},{deleteSet?.let{vm.deleteSet(it.setId,confirmed=true)}},destructivePrimary=true)
    MiuixActionDialog(deleteExercise!=null,"删除训练动作","将从本次训练移除“${deleteExercise?.exerciseName.orEmpty()}”及其全部组和训练记录，无法撤销。原计划和其他训练历史不受影响。","取消","确认删除",{deleteExercise=null},{},{deleteExercise?.let{vm.deleteExercise(it.sessionExerciseId)}},destructivePrimary=true)
    MiuixActionDialog(showPlanUpdate,"保存本次计划调整","本次训练与原计划不同。你可以另存一份，或将本次调整同步到当前计划。","另存新计划","覆盖当前计划",{showPlanUpdate=false},{vm.saveSessionPlan(sessionId,false);showPlanUpdate=false},{vm.saveSessionPlan(sessionId,true);showPlanUpdate=false})
    }
}

@Composable
private fun WorkoutCardMenu(name:String,onDetail:()->Unit,onDelete:()->Unit){
    var expanded by remember { mutableStateOf(false) }
    val anchor = rememberGlassPopupAnchor()
    val style = rememberOs4GlassStyle()
    val customMaterial = rememberOs4CustomMaterial()
    val visuals = GlassPopupDefaults.visuals(
        style = style,
        material = customMaterial ?: GlassPopupDefaults.material(),
        stroke = rememberOs4EdgeStroke(GlassPopupDefaults.stroke()),
    )
    val entries = remember(onDetail, onDelete) {
        listOf(DropdownEntry(listOf(DropdownItem("查看动作详情", onClick = onDetail), DropdownItem("删除动作", onClick = onDelete))))
    }
    GlassIconButton(
        onClick = { expanded = !expanded },
        size = 36.dp,
        inheritTopBarMaterial = false,
        backdrop = null,
        fill = Color.Transparent,
        stroke = null,
        shadow = null,
        modifier = Modifier.glassPopupAnchor(anchor, 18.dp, floating = false),
    ) {
        Icon(
            imageVector = MiuixIcons.Glass.More,
            contentDescription = "$name 动作菜单",
            modifier = Modifier.size(22.dp).glassPopupAnchorContent(anchor),
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
    TopLayer {
        GlassIconDropdownMenu(
            show = expanded,
            onDismissRequest = { expanded = false },
            anchor = anchor,
            anchorContent = {
                Icon(
                    imageVector = MiuixIcons.Glass.More,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            },
            entries = entries,
            backdrop = LocalRootBackdrop.current ?: LocalPageBackdrop.current,
            visuals = visuals,
        )
    }
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
private fun CardioWorkoutCard(records:List<SessionSetRow>,now:State<Long>,dragging:Boolean,requestedSet:Long?,revealSetId:Long?,onControlsRevealed:()->Unit,onDetail:()->Unit,onDeleteExercise:()->Unit,onBegin:(Long)->Unit,onPause:(Long)->Unit,onComplete:(Long,Double)->Unit,onEdit:(Long,Int,Double)->Unit,onDraft:(Long,Double)->Unit,onDelete:(Long)->Unit){
    val first=records.first();val hasActivity=records.any{it.startedAt!=null||it.completed};var expanded by rememberSaveable(first.sessionExerciseId){mutableStateOf(true)};LaunchedEffect(hasActivity,requestedSet){if(hasActivity||requestedSet!=null)expanded=true}
    Card(Modifier.cardPadding()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Surface(onClick={expanded=!expanded},enabled=!dragging,modifier=Modifier.fillMaxWidth().padding(vertical=2.dp).squircleClip(16.dp),color=Color.Transparent,shadowElevation=0.dp){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            val media=first.gifPath?:first.imagePath;if(media!=null)AsyncImage("file:///android_asset/$media",null,Modifier.size(72.dp).squircleClip(16.dp),contentScale=ContentScale.Crop)
            Column(Modifier.weight(1f)){Text(first.exerciseName,style=MiuixTheme.textStyles.title2);Text(if(hasActivity)"有氧计时 · ${records.count{it.completed}} 次已完成" else "有氧计时 · 未开始",color=MiuixTheme.colorScheme.onSurfaceSecondary)}
            if(dragging)Icon(MiuixIcons.Glass.Sort,"拖动排序",Modifier.size(22.dp))else{WorkoutCardMenu(first.exerciseName,onDetail,onDeleteExercise);Icon(if(expanded)MiuixIcons.Glass.ExpandLess else MiuixIcons.Glass.ExpandMore,if(expanded)"收起" else "展开",Modifier.size(22.dp))}
        }}
        AnimatedVisibility(expanded,enter=expandVertically(folmeSpring(.9f,.32f))+fadeIn(folmeSpring(.9f,.28f)),exit=shrinkVertically(folmeSpring(.92f,.28f))+fadeOut(folmeSpring(.95f,.24f))){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){records.forEach{record->CardioRecordRow(record,now,{onBegin(record.setId)},{onPause(record.setId)},{distance->onComplete(record.setId,distance)},{duration,distance->onEdit(record.setId,duration,distance)},{onDelete(record.setId)},{distance->onDraft(record.setId,distance)},revealControls=record.setId==revealSetId&&transition.currentState==EnterExitState.Visible,onControlsRevealed=onControlsRevealed)}}}
    }}
}

@Composable
private fun CardioRecordRow(record:SessionSetRow,now:State<Long>,onBegin:()->Unit,onPause:()->Unit,onComplete:(Double)->Unit,onEdit:(Int,Double)->Unit,onDelete:()->Unit,onDraft:(Double)->Unit={},allowDelete:Boolean=true,revealControls:Boolean=false,onControlsRevealed:()->Unit={}){
    var distance by rememberSaveable(record.setId){mutableStateOf(if(record.distanceKm>0)exactDecimal(record.distanceKm) else "")}
    var minutes by rememberSaveable(record.setId){mutableStateOf(if(record.durationSeconds>0)minutesForEdit(record.durationSeconds) else "")}
    var editing by rememberSaveable(record.setId){mutableStateOf(false)}
    val active=record.startedAt!=null&&!record.completed;val paused=record.pausedAt!=null;val elapsed=if(record.completed)record.durationSeconds.toLong()else activeDurationSeconds(record.startedAt,if(record.startedAt==null)0 else record.pausedAt?:now.value,record.pausedAt,record.pausedDurationMillis)
    LaunchedEffect(active,editing,record.distanceKm,record.durationSeconds){
        if(!active && !editing){
            distance=if(record.distanceKm>0)exactDecimal(record.distanceKm) else ""
            minutes=if(record.durationSeconds>0)minutesForEdit(record.durationSeconds) else ""
        }
    }
    when{
        active->Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(paused)"已暂停" else "进行中",style=MiuixTheme.textStyles.title3,color=MiuixTheme.colorScheme.primary);Text(formatDuration(elapsed),style=MiuixTheme.textStyles.title1)};IconButton(onClick=onDelete){Icon(MiuixIcons.Glass.Delete,"删除记录",Modifier.size(22.dp))}};TextField(distance,{distance=it;validWeight(it.ifBlank{"0"})?.let(onDraft)},label="距离（km，可选）",keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth());Row(Modifier.fillMaxWidth().revealWorkoutControls(revealControls,onControlsRevealed),horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick=if(paused)onBegin else onPause,modifier=Modifier.weight(1f)){Icon(if(paused)MiuixIcons.Glass.Play else MiuixIcons.Glass.Pause,if(paused)"继续" else "暂停",Modifier.size(20.dp));Text(if(paused)"继续" else "暂停")};Button(enabled=validWeight(distance.ifBlank{"0"})!=null,onClick={onComplete(validWeight(distance.ifBlank{"0"})!!)},colors=ButtonDefaults.buttonColorsPrimary(),modifier=Modifier.weight(1f)){Text("结束并保存")}}}
        editing->Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text("修改有氧记录",style=MiuixTheme.textStyles.title3);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){TextField(minutes,{minutes=it},label="时长（分钟）",keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.weight(1f));TextField(distance,{distance=it},label="距离（km）",keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.weight(1f))};Button(enabled=validWeight(minutes)!=null&&validWeight(distance.ifBlank{"0"})!=null,onClick={onEdit(minutesToSeconds(minutes),validWeight(distance.ifBlank{"0"})!!);editing=false},modifier=Modifier.fillMaxWidth()){Text("保存修改")}}
        record.completed->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(cardioSummary(record),style=MiuixTheme.textStyles.title3);Text("已完成",color=MiuixTheme.colorScheme.primary)};IconButton(onClick={minutes=minutesForEdit(record.durationSeconds);distance=exactDecimal(record.distanceKm);editing=true}){Icon(MiuixIcons.Glass.Edit,"编辑有氧记录",Modifier.size(22.dp))};if(allowDelete)IconButton(onClick=onDelete){Icon(MiuixIcons.Glass.Delete,"删除记录",Modifier.size(22.dp))}}
        else->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("准备开始",style=MiuixTheme.textStyles.title3);Text("自动记录运动时长，结束时可填写距离",color=MiuixTheme.colorScheme.onSurfaceSecondary)};IconButton(onClick=onBegin){Icon(MiuixIcons.Glass.Play,"开始有氧",Modifier.size(22.dp))};IconButton(onClick=onDelete){Icon(MiuixIcons.Glass.Delete,"删除记录",Modifier.size(22.dp))}}
    }
}

@Composable private fun ExerciseWorkoutCard(sets:List<SessionSetRow>,now:State<Long>,dragging:Boolean,requestedSet:Long?,revealSetId:Long?,onControlsRevealed:()->Unit,onDetail:()->Unit,onDeleteExercise:()->Unit,onBegin:(Long)->Unit,onPause:(Long)->Unit,onComplete:(Long,Double,Int)->Unit,onEdit:(Long,Double,Int)->Unit,onDelete:(Long)->Unit,onAdd:()->Unit){val first=sets.first();val hasActivity=sets.any{it.startedAt!=null||it.completed};var expanded by rememberSaveable(first.sessionExerciseId){mutableStateOf(hasActivity)};LaunchedEffect(hasActivity,requestedSet){if(hasActivity||requestedSet!=null)expanded=true};Card(Modifier.cardPadding()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Surface(onClick={expanded=!expanded},enabled=!dragging,modifier=Modifier.fillMaxWidth().padding(vertical=2.dp).squircleClip(16.dp),color=Color.Transparent,shadowElevation=0.dp){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){val media=first.gifPath?:first.imagePath;if(media!=null)AsyncImage("file:///android_asset/$media",null,Modifier.size(72.dp).squircleClip(16.dp),contentScale=ContentScale.Crop);Column(Modifier.weight(1f)){Text(first.exerciseName,style=MiuixTheme.textStyles.title2);Text(if(hasActivity)"${sets.count{it.completed}} / ${sets.size} 组已完成" else "未开始 · ${sets.size} 组",color=MiuixTheme.colorScheme.onSurfaceSecondary)};if(dragging)Icon(MiuixIcons.Glass.Sort,"拖动排序",Modifier.size(22.dp))else{WorkoutCardMenu(first.exerciseName,onDetail,onDeleteExercise);Icon(if(expanded)MiuixIcons.Glass.ExpandLess else MiuixIcons.Glass.ExpandMore,if(expanded)"收起" else "展开",Modifier.size(22.dp))}}}
    AnimatedVisibility(expanded,enter=expandVertically(folmeSpring(.9f,.32f))+fadeIn(folmeSpring(.9f,.28f)),exit=shrinkVertically(folmeSpring(.92f,.28f))+fadeOut(folmeSpring(.95f,.24f))){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){sets.forEach{set->CompactSetRow(set,now,{onBegin(set.setId)},{onPause(set.setId)},{w,r->onComplete(set.setId,w,r)},{w,r->onEdit(set.setId,w,r)},{onDelete(set.setId)},revealControls=set.setId==revealSetId&&transition.currentState==EnterExitState.Visible,onControlsRevealed=onControlsRevealed)};IconButton(onClick=onAdd,modifier=Modifier.align(Alignment.End)){Icon(MiuixIcons.Glass.Add,"添加一组",Modifier.size(22.dp))}}}
}}}

@Composable private fun CompactSetRow(set:SessionSetRow,now:State<Long>,onBegin:()->Unit,onPause:()->Unit,onComplete:(Double,Int)->Unit,onEdit:(Double,Int)->Unit,onDelete:()->Unit,allowDelete:Boolean=true,revealControls:Boolean=false,onControlsRevealed:()->Unit={}){var weight by rememberSaveable(set.setId){mutableStateOf(exactDecimal(set.weightKg))};var reps by rememberSaveable(set.setId){mutableStateOf(set.reps.toString())};var editing by rememberSaveable(set.setId){mutableStateOf(false)};val elapsed=if(set.completed)set.durationSeconds.toLong() else activeDurationSeconds(set.startedAt,if(set.startedAt==null)0 else set.pausedAt?:now.value,set.pausedAt,set.pausedDurationMillis);val active=set.startedAt!=null&&!set.completed;val paused=set.pausedAt!=null
    LaunchedEffect(active,editing,set.weightKg,set.reps){
        if(!active && !editing){
            weight=exactDecimal(set.weightKg)
            reps=set.reps.toString()
        }
    }
    if(active||editing)Column(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("第 ${set.setPosition+1} 组",style=MiuixTheme.textStyles.title3,modifier=Modifier.weight(1f));Text(if(active)"${if(paused)"已暂停" else "进行中"} · ${formatDuration(elapsed)}" else "修改记录",color=MiuixTheme.colorScheme.primary)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){TextField(weight,{weight=it;if(!editing)validWeight(it)?.let{w->validReps(reps)?.let{r->onEdit(w,r)}}},label="kg",keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.weight(1f));TextField(reps,{reps=it;if(!editing)validReps(it)?.let{r->validWeight(weight)?.let{w->onEdit(w,r)}}},label="次数",keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f))};Row(Modifier.fillMaxWidth().revealWorkoutControls(active&&revealControls,onControlsRevealed),horizontalArrangement=Arrangement.End){if(editing)IconButton(enabled=validWeight(weight)!=null&&validReps(reps)!=null,onClick={onEdit(validWeight(weight)!!,validReps(reps)!!);editing=false}){Icon(MiuixIcons.Glass.Ok,"保存修改",Modifier.size(22.dp))}else{IconButton(onClick=if(paused)onBegin else onPause){Icon(if(paused)MiuixIcons.Glass.Play else MiuixIcons.Glass.Pause,if(paused)"继续本组" else "暂停本组",Modifier.size(22.dp))};IconButton(enabled=validWeight(weight)!=null&&validReps(reps)!=null,onClick={onComplete(validWeight(weight)!!,validReps(reps)!!)}){Icon(MiuixIcons.Glass.Ok,"完成本组",Modifier.size(22.dp))}};if(allowDelete)IconButton(onClick=onDelete){Icon(MiuixIcons.Glass.Delete,"删除本组",Modifier.size(22.dp))}}}
    else Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){Text("第 ${set.setPosition+1} 组",style=MiuixTheme.textStyles.title3,modifier=Modifier.width(72.dp));Column(Modifier.weight(1f)){Text("${displayDecimal(set.weightKg)} kg × ${set.reps} 次");Text(if(set.completed)"已完成 · ${formatDuration(elapsed)}${if(set.restDurationSeconds>0)" · 休息 ${formatDuration(set.restDurationSeconds.toLong())}" else ""}" else "未开始",style=MiuixTheme.textStyles.footnote1,color=if(set.completed)MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary)};IconButton(onClick={weight=exactDecimal(set.weightKg);reps=set.reps.toString();editing=true}){Icon(MiuixIcons.Glass.Edit,if(set.completed)"编辑本组" else "训练前调整重量和次数",Modifier.size(22.dp))};if(!set.completed)IconButton(onClick=onBegin){Icon(MiuixIcons.Glass.Play,"开始本组",Modifier.size(22.dp))};if(allowDelete)IconButton(onClick=onDelete){Icon(MiuixIcons.Glass.Delete,"删除本组",Modifier.size(22.dp))}};HorizontalDivider()
}

@Composable
fun EditableCompletedCardioRecord(record:SessionSetRow,onEdit:(Int,Double)->Unit){
    CardioRecordRow(record,remember{mutableLongStateOf(0L)},onBegin={},onPause={},onComplete={},onEdit=onEdit,onDelete={},allowDelete=false)
}

@Composable
fun EditableCompletedStrengthRecord(record:SessionSetRow,onEdit:(Double,Int)->Unit){
    CompactSetRow(record,remember{mutableLongStateOf(0L)},onBegin={},onPause={},onComplete={_,_->},onEdit=onEdit,onDelete={},allowDelete=false)
}

@Composable private fun WorkoutSheetHost(show:Boolean,kind:WorkoutSheet,currentRest:Int,onDismiss:()->Unit,onContinue:()->Unit,onDiscard:()->Unit,onSelectRest:(Int)->Unit){
    var selectedRest by remember(currentRest,show){mutableIntStateOf(currentRest)}
    if(kind==WorkoutSheet.Exit)MiuixActionDialog(show,"退出本次训练","训练记录正在自动保存。你可以稍后继续，也可以放弃并删除本次记录。","放弃训练","后台继续",onDismiss,onDiscard,onContinue,destructiveSecondary=true)
    else FloatingBottomSheet(show,"本次组间休息",onDismiss){Column{listOf(30,60,90,120,180).forEach{seconds->RadioButtonPreference(title=formatRestLabel(seconds),selected=selectedRest==seconds,onClick={selectedRest=seconds},radioButtonLocation=RadioButtonLocation.End,modifier=Modifier.fillMaxWidth())}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick=onDismiss,modifier=Modifier.weight(1f)){Text("取消")};Button(onClick={onSelectRest(selectedRest)},colors=ButtonDefaults.buttonColorsPrimary(),modifier=Modifier.weight(1f)){Text("确定")}}}
}

fun formatRestLabel(seconds:Int)=if(seconds<60)"${seconds} 秒" else if(seconds%60==0)"${seconds/60} 分钟" else "${seconds/60} 分 ${seconds%60} 秒"

fun formatDuration(seconds:Long):String{
    val s=seconds.coerceAtLeast(0)
    return if(s>=3600)"%d:%02d:%02d".format(s/3600,s/60%60,s%60) else "%02d:%02d".format(s/60,s%60)
}
fun minutesForEdit(seconds:Int)=displayDecimal(seconds/60.0)
fun minutesToSeconds(minutes:String)=((validWeight(minutes)?:0.0)*60).coerceAtMost(Int.MAX_VALUE.toDouble()).roundToInt()
fun cardioSummary(record:SessionSetRow):String=buildString{append(formatDuration(record.durationSeconds.toLong()));if(record.distanceKm>0)append(" · ${displayDecimal(record.distanceKm)} km");if(record.distanceKm>0&&record.durationSeconds>0){val pace=record.durationSeconds/60.0/record.distanceKm;append(" · ${pace.toInt()}:${((pace%1)*60).toInt().toString().padStart(2,'0')} /km")}}

@Composable
private fun Modifier.revealWorkoutControls(reveal: Boolean, onRevealed: () -> Unit): Modifier {
    if (!reveal) return this
    val requester = remember { BringIntoViewRequester() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val bottomClearance = WindowInsets.navigationBars.getBottom(density) + with(density) { 12.dp.toPx() }
    val currentOnRevealed by rememberUpdatedState(onRevealed)
    LaunchedEffect(size, bottomClearance) {
        if (size.height > 0) {
            requester.bringIntoView(Rect(0f, 0f, size.width.toFloat(), size.height + bottomClearance))
            currentOnRevealed()
        }
    }
    return bringIntoViewRequester(requester).onGloballyPositioned { size = it.size }
}

@Composable
private fun WorkoutRestToolbar(
    rows: List<SessionSetRow>,
    now: State<Long>,
    finished: Boolean,
    isFloating: () -> Boolean,
    onBegin: (Long) -> Unit,
) {
    val rest = if (finished) null else rows.lastOrNull { it.restStartedAt != null && it.restEndedAt == null }
    AnimatedVisibility(rest!=null,enter=fadeIn(folmeSpring(.9f,.28f))+expandVertically(folmeSpring(.9f,.32f)),exit=fadeOut(folmeSpring(.95f,.24f))+shrinkVertically(folmeSpring(.92f,.28f))){rest?.let{
        val elapsed=((now.value-it.restStartedAt!!)/1000).coerceAtLeast(0)
        val remaining=(it.restSeconds-elapsed).coerceAtLeast(0)
        HeroCard(if(remaining>0)"组间休息 ${formatDuration(remaining)}" else "休息结束",
            "上一组 ${formatDuration(it.durationSeconds.toLong())} · 已休息 ${formatDuration(elapsed)}",
            "开始下一项",{nextWorkoutSet(rows,it.setId)?.let{next->onBegin(next.setId)}},floating=isFloating())
    }}
}
