package com.juzi.lianji.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juzi.lianji.MainUiState
import com.juzi.lianji.MainViewModel
import com.juzi.lianji.data.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.anim.folmeSpring
import java.time.YearMonth
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun CalendarScreen(state:MainUiState,padding:PaddingValues,listState:LazyListState,scrollBehavior:ScrollBehavior,onDay:(String)->Unit,onMonth:(YearMonth)->Unit){
    var monthValue by rememberSaveable{mutableStateOf(YearMonth.now().toString())};val month=YearMonth.parse(monthValue);val offset=month.atDay(1).dayOfWeek.value-1;val summaries=state.days.groupBy{it.localDate};val prefix=month.toString()
    val completed=state.sessions.filter{it.localDate.startsWith(prefix)&&it.status=="COMPLETED"};val trainingDays=completed.map{it.localDate}.distinct().size;val seconds=completed.sumOf{((it.endedAt?:it.startedAt)-it.startedAt).coerceAtLeast(0)}/1000
    LazyColumn(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),state=listState,contentPadding=PaddingValues(top=padding.calculateTopPadding()+12.dp,bottom=padding.calculateBottomPadding()+24.dp)){
        item{Card(modifier=Modifier.fillMaxWidth().cardPadding(),pressFeedbackType=PressFeedbackType.Sink,onClick={onMonth(month)}){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("本月概览",style=MiuixTheme.textStyles.title2);Text("${month.format(DateTimeFormatter.ofPattern("yyyy年 M月"))} · 点击查看详细分析",color=MiuixTheme.colorScheme.onSurfaceSecondary)};Icon(MiuixIcons.ChevronForward,"查看月度分析")};Row(Modifier.fillMaxWidth()){MonthStat(trainingDays.toString(),"训练日",Modifier.weight(1f));MonthStat(completed.size.toString(),"训练次数",Modifier.weight(1f));MonthStat(formatLongDuration(seconds),"总时长",Modifier.weight(1f))}}}}
        item{Card(Modifier.cardPadding()){Column(Modifier.padding(horizontal=10.dp,vertical=12.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){PreviousButton{monthValue=month.minusMonths(1).toString()};Text(month.format(DateTimeFormatter.ofPattern("yyyy年 M月")),style=MiuixTheme.textStyles.title2);NextButton{monthValue=month.plusMonths(1).toString()}}
            Row(Modifier.fillMaxWidth()){listOf("一","二","三","四","五","六","日").forEach{Text(it,Modifier.weight(1f),textAlign=TextAlign.Center,color=MiuixTheme.colorScheme.onSurfaceSecondary)}}
            repeat(6) { week ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val day = week * 7 + column - offset + 1
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day).toString()
                            val entries = summaries[date].orEmpty()
                            Surface(
                                onClick = { onDay(date) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(70.dp)
                                    .padding(horizontal = 1.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = Color.Transparent,
                                shadowElevation = 0.dp,
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 1.dp, vertical = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(day.toString())
                                    entries.take(2).forEach { entry ->
                                        val completedEntry = entry.status == "COMPLETED"
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 1.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (completedEntry) {
                                                StatusColors.Healthy.copy(alpha = .14f)
                                            } else {
                                                StatusColors.Warning.copy(alpha = .14f)
                                            },
                                            shadowElevation = 0.dp,
                                        ) {
                                            Text(
                                                entry.title,
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 1.dp, vertical = 1.dp),
                                                style = MiuixTheme.textStyles.footnote2.copy(
                                                    fontSize = 9.sp,
                                                    letterSpacing = (-0.35).sp,
                                                ),
                                                maxLines = 1,
                                                textAlign = TextAlign.Center,
                                                color = if (completedEntry) {
                                                    StatusColors.Healthy
                                                } else {
                                                    StatusColors.Warning
                                                },
                                            )
                                        }
                                    }
                                    if (entries.size > 2) {
                                        Text(
                                            "+${entries.size - 2}",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f).height(70.dp))
                        }
                    }
                }
            }
        }}}
    }
}

@Composable
fun DayDetailScreen(vm:MainViewModel,date:String,onBack:()->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAddPast by remember{mutableStateOf(false)}
    var selectedPlanId by remember{mutableStateOf<Long?>(null)}
    var candidatePlanId by remember{mutableStateOf<Long?>(null)}
    var selectedPlanName by remember{mutableStateOf("")}
    var planExerciseIds by remember{mutableStateOf<List<String>>(emptyList())}
    var selectedExerciseIds by remember{mutableStateOf<Set<String>>(emptySet())}
    var startTime by remember{mutableStateOf("18:00")}
    var endTime by remember{mutableStateOf("19:00")}
    var cardioDistances by remember{mutableStateOf<Map<String,String>>(emptyMap())}
    val sessions=state.sessions.filter{it.localDate==date&&it.status!="DISCARDED"}
    val schedules=state.schedules.filter{it.scheduledDate==date}
    MiuixPageScaffold(title=date,navigationIcon={BackButton(onBack)},actions={if(!LocalDate.parse(date).isAfter(LocalDate.now()))IconButton(onClick={showAddPast=true}){Icon(MiuixIcons.Add,"补录过去训练")}}) { pad ->
        LazyColumn(contentPadding=PaddingValues(top=pad.calculateTopPadding()+12.dp,bottom=24.dp)) {
            if(sessions.isEmpty()&&schedules.isEmpty()) item{EmptyCard("这天没有训练","可以在计划中安排训练")}
            items(sessions,key={it.id}){SessionHistoryCard(vm,it)}
            items(schedules,key={"schedule-${it.id}"}){ScheduleHistoryCard(vm,it)}
        }
    }
    FloatingBottomSheet(showAddPast,"补录 $date 的训练",{showAddPast=false}){
        if(state.plans.isEmpty()) Text("请先在运动页创建训练计划。",color=MiuixTheme.colorScheme.onSurfaceSecondary)
        else if(selectedPlanId==null) {
            Text("先选择计划，下一步可取消本次没有完成的动作。",color=MiuixTheme.colorScheme.onSurfaceSecondary)
            state.plans.forEach { plan ->
                RadioButtonPreference(
                    title = plan.name,
                    summary = "${plan.exerciseCount} 个动作",
                    selected = candidatePlanId == plan.id,
                    radioButtonLocation = RadioButtonLocation.End,
                    onClick = { candidatePlanId = plan.id },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Button(onClick={showAddPast=false;candidatePlanId=null},modifier=Modifier.weight(1f)){Text("取消")}
                Button(
                    enabled=candidatePlanId!=null,
                    onClick={candidatePlanId?.let { planId -> val plan=state.plans.first{it.id==planId};vm.loadPlan(planId){_,ids->selectedPlanId=planId;selectedPlanName=plan.name;planExerciseIds=ids;selectedExerciseIds=ids.toSet();cardioDistances=emptyMap()} }},
                    colors=ButtonDefaults.buttonColorsPrimary(),
                    modifier=Modifier.weight(1f),
                ){Text("下一步")}
            }
        } else {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(selectedPlanName,style=MiuixTheme.textStyles.title3);Text("选择实际完成的动作",color=MiuixTheme.colorScheme.onSurfaceSecondary)};TextButton("重选计划",onClick={selectedPlanId=null})}
            planExerciseIds.forEach { id -> state.exercises.firstOrNull{it.id==id}?.let { exercise ->
                val checked=id in selectedExerciseIds
                val toggle={selectedExerciseIds=if(checked)selectedExerciseIds-id else selectedExerciseIds+id}
                CheckboxPreference(title=exercise.nameZh,summary=bodyPartLabel(exercise.bodyPart),checked=checked,onCheckedChange={toggle()},checkboxLocation=CheckboxLocation.End,modifier=Modifier.fillMaxWidth())
                if(checked&&exercise.trackingMode==TrackingMode.CARDIO)TextField(
                    value=cardioDistances[id].orEmpty(),
                    onValueChange={cardioDistances=cardioDistances+(id to it)},
                    label="距离（km，可选）",
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                    modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp),
                )
            } }
            HorizontalDivider()
            Text("训练时间",style=MiuixTheme.textStyles.title3)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){TextField(startTime,{startTime=it},label="开始 HH:mm",useLabelAsPlaceholder=true,modifier=Modifier.weight(1f));TextField(endTime,{endTime=it},label="结束 HH:mm",useLabelAsPlaceholder=true,modifier=Modifier.weight(1f))}
            val startMinute=parseTimeMinute(startTime);val endMinute=parseTimeMinute(endTime)
            val validTime=startMinute!=null&&endMinute!=null&&endMinute>startMinute
            val selectedCardioIds=selectedExerciseIds.filter{id->state.exercises.firstOrNull{it.id==id}?.trackingMode==TrackingMode.CARDIO}.toSet()
            val validDistances=selectedCardioIds.all{id->cardioDistances[id].orEmpty().let{it.isBlank()||(it.toDoubleOrNull()?.let{value->value>=0}==true)}}
            Text(if(validTime)"训练总时长：${formatLongDuration((endMinute-startMinute)*60L)}" else "请输入同一天内有效的开始与结束时间",color=if(validTime)MiuixTheme.colorScheme.onSurfaceSecondary else StatusColors.Warning)
            if(!validDistances)Text("距离应为不小于 0 的数字",color=StatusColors.Warning)
            Button(enabled=selectedExerciseIds.isNotEmpty()&&validTime&&validDistances,onClick={vm.addPastWorkout(selectedPlanId!!,planExerciseIds.filter{it in selectedExerciseIds},LocalDate.parse(date),startMinute!!,endMinute!!,selectedCardioIds.associateWith{id->cardioDistances[id]?.toDoubleOrNull()?:0.0});showAddPast=false;selectedPlanId=null},colors=ButtonDefaults.buttonColorsPrimary(),modifier=Modifier.fillMaxWidth()){Text("补录 ${selectedExerciseIds.size} 个动作")}
        }
    }
}

@Composable
private fun SessionHistoryCard(vm:MainViewModel,session:WorkoutSessionEntity) {
    val rows by vm.repository.rows(session.id).collectAsStateWithLifecycle(emptyList())
    var expanded by rememberSaveable(session.id){mutableStateOf(false)}
    var confirm by remember{mutableStateOf(false)}
    val groups=rows.filter{it.completed}.groupBy{it.sessionExerciseId}.values
    val seconds=((session.endedAt?:System.currentTimeMillis())-session.startedAt)/1000
    Card(Modifier.cardPadding()) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Surface(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth().padding(vertical=4.dp),shape=RoundedCornerShape(14.dp),color=Color.Transparent,shadowElevation=0.dp) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)){Text(session.planNameSnapshot,style=MiuixTheme.textStyles.title2);Text("${if(session.status=="COMPLETED")"已完成" else "进行中"} · ${groups.size} 个动作 · ${formatDuration(seconds)}",color=MiuixTheme.colorScheme.onSurfaceSecondary)}
                IconButton(onClick={expanded=!expanded}){Icon(if(expanded)MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,if(expanded)"收起详情" else "展开详情")}
                }
            }
            AnimatedVisibility(expanded,enter=expandVertically(animationSpec=folmeSpring(.9f,.32f))+fadeIn(folmeSpring(.9f,.28f)),exit=shrinkVertically(animationSpec=folmeSpring(.92f,.28f))+fadeOut(folmeSpring(.95f,.24f))) {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(groups.isEmpty()) Text("这次训练没有已完成的记录",color=MiuixTheme.colorScheme.onSurfaceSecondary) else groups.forEach{HistoryExercise(it,vm::updateSetValues,vm::updateCardioValues)}
                    Card{TextButton("删除训练记录",onClick={confirm=true},modifier=Modifier.fillMaxWidth())}
                }
            }
        }
    }
    DeleteConfirm(confirm,"删除训练记录","这条训练及其详细记录将被删除。",{confirm=false},{vm.deleteHistory(session.id);confirm=false})
}

@Composable
private fun ScheduleHistoryCard(vm:MainViewModel,schedule:ScheduledWorkoutEntity) {
    var confirm by remember{mutableStateOf(false)}
    Card(Modifier.cardPadding()){Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(schedule.planName,style=MiuixTheme.textStyles.title2);Text("训练已安排",color=StatusColors.Warning)};TextButton("删除安排",onClick={confirm=true})}}
    DeleteConfirm(confirm,"删除训练安排","只删除这一天的安排，不会删除计划模板。",{confirm=false},{vm.deleteSchedule(schedule);confirm=false})
}

@Composable
private fun DeleteConfirm(show:Boolean,title:String,message:String,onCancel:()->Unit,onConfirm:()->Unit) {
    MiuixActionDialog(show,title,message,"取消","删除",onCancel,onCancel,onConfirm)
}

@Composable
fun MonthAnalyticsScreen(vm:MainViewModel,month:YearMonth,onBack:()->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val statsFlow=remember(month){vm.repository.monthlyExerciseStats("$month%")}
    val exerciseStats by statsFlow.collectAsStateWithLifecycle(emptyList())
    val sessions=state.sessions.filter{it.status=="COMPLETED"&&it.localDate.startsWith(month.toString())}
    val totalSeconds=sessions.sumOf{((it.endedAt?:it.startedAt)-it.startedAt).coerceAtLeast(0)}/1000
    val averageSeconds=if(sessions.isEmpty())0 else totalSeconds/sessions.size
    val totalSets=exerciseStats.filter{it.trackingMode!=TrackingMode.CARDIO}.sumOf{it.completedSets}
    val totalCardio=exerciseStats.filter{it.trackingMode==TrackingMode.CARDIO}.sumOf{it.completedSets}
    val totalVolume=exerciseStats.sumOf{it.volume}
    val bodyStats=exerciseStats.groupBy{it.bodyPart}.map{(part,items)->Triple(part,items.sumOf{it.completedSets},items.all{it.trackingMode==TrackingMode.CARDIO})}.sortedByDescending{it.second}
    val durationBuckets=listOf(
        "30分钟内" to sessions.count{sessionMinutes(it)<30},
        "30–60分钟" to sessions.count{sessionMinutes(it) in 30..<60},
        "60–90分钟" to sessions.count{sessionMinutes(it) in 60..<90},
        "90分钟以上" to sessions.count{sessionMinutes(it)>=90},
    )
    val timeBuckets=listOf(
        "上午" to sessions.count{sessionHour(it)<12},
        "下午" to sessions.count{sessionHour(it) in 12..<18},
        "晚上" to sessions.count{sessionHour(it)>=18},
    )
    MiuixPageScaffold(title=month.format(DateTimeFormatter.ofPattern("yyyy年 M月分析")),navigationIcon={BackButton(onBack)}){pad->
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(top=pad.calculateTopPadding()+12.dp,bottom=24.dp)){
            item{Card(Modifier.cardPadding()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Text("训练总览",style=MiuixTheme.textStyles.title2);Row(Modifier.fillMaxWidth()){MonthStat(sessions.size.toString(),"训练次数",Modifier.weight(1f));MonthStat(formatLongDuration(totalSeconds),"总时长",Modifier.weight(1f));MonthStat(formatLongDuration(averageSeconds),"平均时长",Modifier.weight(1f))};Row(Modifier.fillMaxWidth()){MonthStat(totalSets.toString(),"力量组数",Modifier.weight(1f));MonthStat(totalCardio.toString(),"有氧次数",Modifier.weight(1f));MonthStat(formatVolume(totalVolume),"训练容量",Modifier.weight(1f))};Row(Modifier.fillMaxWidth()){MonthStat(sessions.map{it.localDate}.distinct().size.toString(),"训练日",Modifier.weight(1f));Spacer(Modifier.weight(2f))}}}}
            item{AnalysisCard("训练时长分布",if(sessions.isEmpty())"本月还没有完成的训练" else "按每次训练的总时长统计",durationBuckets)}
            item{AnalysisCard("训练时段",if(sessions.isEmpty())"暂无时段数据" else "按开始训练的时间统计",timeBuckets)}
            item{
                val maxBody=bodyStats.maxOfOrNull{it.second}?:0
                Card(Modifier.cardPadding()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("部位分布",style=MiuixTheme.textStyles.title2);Text("力量按组、有氧按次数统计，更直观看出本月训练侧重",color=MiuixTheme.colorScheme.onSurfaceSecondary);if(bodyStats.isEmpty())Text("暂无动作数据",color=MiuixTheme.colorScheme.onSurfaceSecondary)else bodyStats.forEach{(part,count,cardio)->AnalysisBar(bodyPartLabel(part),count,if(maxBody==0)0f else count.toFloat()/maxBody,"$count ${if(cardio)"次" else "组"}")}}}
            }
            item{
                Card(Modifier.cardPadding()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("高频动作",style=MiuixTheme.textStyles.title2);if(exerciseStats.isEmpty())Text("暂无动作数据",color=MiuixTheme.colorScheme.onSurfaceSecondary)else exerciseStats.take(5).forEachIndexed{index,item->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${index+1}",Modifier.width(28.dp),color=MiuixTheme.colorScheme.primary);Column(Modifier.weight(1f)){Text(item.exerciseName,style=MiuixTheme.textStyles.title3);Text(bodyPartLabel(item.bodyPart),color=MiuixTheme.colorScheme.onSurfaceSecondary)};Text("${item.completedSets} ${if(item.trackingMode==TrackingMode.CARDIO)"次" else "组"}",color=MiuixTheme.colorScheme.onSurfaceSecondary)}}}}
            }
        }
    }
}

@Composable private fun AnalysisCard(title:String,summary:String,values:List<Pair<String,Int>>) {
    val max=values.maxOfOrNull{it.second}?:0
    Card(Modifier.cardPadding()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(title,style=MiuixTheme.textStyles.title2);Text(summary,color=MiuixTheme.colorScheme.onSurfaceSecondary);values.forEach{(label,value)->AnalysisBar(label,value,if(max==0)0f else value.toFloat()/max,"$value 次")}}}
}

@Composable private fun AnalysisBar(label:String,value:Int,progress:Float,trailing:String) {
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)){Row(Modifier.fillMaxWidth()){Text(label,Modifier.weight(1f));Text(trailing,color=MiuixTheme.colorScheme.onSurfaceSecondary)};LinearProgressIndicator(progress=progress)}
}

private fun sessionMinutes(session:WorkoutSessionEntity)=(((session.endedAt?:session.startedAt)-session.startedAt).coerceAtLeast(0)/60_000).toInt()
private fun sessionHour(session:WorkoutSessionEntity)=Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).hour
private fun formatVolume(value:Double)=if(value>=1000)"%.1f 吨".format(value/1000) else "%.0f kg".format(value)

@Composable private fun HistoryExercise(records:List<SessionSetRow>,onStrengthEdit:(Long,Double,Int)->Unit,onCardioEdit:(Long,Int,Double)->Unit){val cardio=records.first().trackingMode==TrackingMode.CARDIO;Card{Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(records.first().exerciseName,style=MiuixTheme.textStyles.title3);records.forEach{record->if(cardio)EditableCompletedCardioRecord(record){duration,distance->onCardioEdit(record.setId,duration,distance)}else EditableCompletedStrengthRecord(record){weight,reps->onStrengthEdit(record.setId,weight,reps)}}}}}
private fun formatLongDuration(seconds:Long)=if(seconds>=3600)"${seconds/3600}小时${seconds/60%60}分" else "${seconds/60}分钟"
private fun parseTimeMinute(value:String):Int? { val match=Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(value.trim())?:return null;val hour=match.groupValues[1].toIntOrNull()?:return null;val minute=match.groupValues[2].toIntOrNull()?:return null;return if(hour in 0..23&&minute in 0..59)hour*60+minute else null }
@Composable private fun MonthStat(value:String,label:String,modifier:Modifier=Modifier){Column(modifier,horizontalAlignment=Alignment.CenterHorizontally){Text(value,style=MiuixTheme.textStyles.title3);Text(label,style=MiuixTheme.textStyles.footnote1,color=MiuixTheme.colorScheme.onSurfaceSecondary)}}
