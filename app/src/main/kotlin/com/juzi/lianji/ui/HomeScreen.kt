package com.juzi.lianji.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.juzi.lianji.MainUiState
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu

@Composable
fun WorkoutHomeScreen(state:MainUiState,padding:PaddingValues,listState:LazyListState,scrollBehavior:ScrollBehavior,onEdit:(Long)->Unit,onStart:(Long)->Unit,onContinue:()->Unit,onDuplicate:(Long)->Unit,onDelete:(Long)->Unit) {
    LazyColumn(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),state=listState,contentPadding=PaddingValues(top=padding.calculateTopPadding()+12.dp,bottom=padding.calculateBottomPadding()+24.dp)) {
        state.active?.let { active -> item {
            HeroCard("训练进行中",active.planNameSnapshot,"继续记录",onContinue)
        }}
        item { SmallTitle("我的计划") }
        if(state.plans.isEmpty()) item { EmptyCard("还没有训练计划","创建计划后，可随时开始或安排到日历") }
        items(state.plans,key={it.id}) { plan ->
            Card(Modifier.cardPadding()) {
                Column(Modifier.padding(18.dp)) {
                    Text(plan.name,style=MiuixTheme.textStyles.title2)
                    Text("${plan.exerciseCount} 个动作${if(plan.note.isNotBlank()){" · ${plan.note}"}else ""}",color=MiuixTheme.colorScheme.onSurfaceSecondary)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Button(onClick={onStart(plan.id)},colors=ButtonDefaults.buttonColorsPrimary(),modifier=Modifier.weight(1f)){Icon(MiuixIcons.Play,"开始训练");Spacer(Modifier.width(8.dp));Text("开始训练")}
                        PlanMoreMenu(onEdit={onEdit(plan.id)},onDuplicate={onDuplicate(plan.id)},onDelete={onDelete(plan.id)})
                    }
                }
            }
        }
    }
}

@Composable private fun PlanMoreMenu(onEdit:()->Unit,onDuplicate:()->Unit,onDelete:()->Unit){
    OverlayIconDropdownMenu(entry=DropdownEntry(listOf(DropdownItem("编辑计划",onClick=onEdit),DropdownItem("复制计划",onClick=onDuplicate),DropdownItem("删除计划",onClick=onDelete)))){Icon(MiuixIcons.More,"更多计划操作")}
}

@Composable fun PageHeading(title:String,subtitle:String) { Column(Modifier.padding(horizontal=18.dp,vertical=10.dp)) { Text(title,style=MiuixTheme.textStyles.title1); Text(subtitle,color=MiuixTheme.colorScheme.onSurfaceSecondary) } }
@Composable fun HeroCard(title:String,subtitle:String,button:String,onClick:()->Unit) { Card(Modifier.cardPadding()) { BoxWithConstraints(Modifier.fillMaxWidth().padding(20.dp)) { if(maxWidth<340.dp) Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Column{Text(title,style=MiuixTheme.textStyles.title2);Text(subtitle,color=MiuixTheme.colorScheme.onSurfaceSecondary)};Button(onClick=onClick,modifier=Modifier.fillMaxWidth()){Text(button)}} else Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,style=MiuixTheme.textStyles.title2);Text(subtitle,color=MiuixTheme.colorScheme.onSurfaceSecondary)};Spacer(Modifier.width(12.dp));Button(onClick=onClick){Text(button)}} } } }
@Composable fun EmptyCard(title:String,subtitle:String) { Card(Modifier.cardPadding()) { Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally) { Text(title,style=MiuixTheme.textStyles.title3); Text(subtitle,color=MiuixTheme.colorScheme.onSurfaceSecondary) } } }
fun Modifier.cardPadding()=this.padding(horizontal=12.dp).padding(bottom=12.dp)
