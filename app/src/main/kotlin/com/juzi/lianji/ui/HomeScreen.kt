package com.juzi.lianji.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.juzi.lianji.MainUiState
import com.juzi.lianji.data.PlanSummary
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.glass.GlassFloatingToolbar
import top.yukonga.miuix.kmp.glass.GlassFloatingToolbarDefaults
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassIconDropdownMenu
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassTabRow
import top.yukonga.miuix.kmp.glass.GlassTabRowDefaults
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.More
import top.yukonga.miuix.kmp.icon.glass.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun WorkoutHomeScreen(
    state: MainUiState,
    padding: PaddingValues,
    listState: LazyListState,
    scrollBehavior: ScrollBehavior,
    onEdit: (Long) -> Unit,
    onStart: (Long) -> Unit,
    onContinue: () -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onSchedule: (Long, LocalDate) -> Unit = { _, _ -> },
) {
    var schedulingPlan by remember { mutableStateOf<PlanSummary?>(null) }
    val scheduleDateState = rememberTextFieldState()
    val layoutDirection = LocalLayoutDirection.current
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
        state.active?.let { active ->
            item {
                HeroCard("训练进行中", active.planNameSnapshot, "继续记录", onContinue)
            }
        }
        item { SmallTitle("我的计划") }
        if (state.plans.isEmpty()) item { EmptyCard("还没有训练计划", "创建计划后，可随时开始或安排到日历") }
        items(state.plans, key = { it.id }) { plan ->
            Card(Modifier.cardPadding()) {
                Column(Modifier.padding(18.dp)) {
                    Text(plan.name, style = MiuixTheme.textStyles.title2)
                    Text(
                        "${plan.exerciseCount} 个动作${if (plan.note.isNotBlank()) " · ${plan.note}" else ""}",
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { onStart(plan.id) },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(MiuixIcons.Glass.Play, "开始训练", Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("开始训练")
                        }
                        PlanMoreMenu(
                            onEdit = { onEdit(plan.id) },
                            onSchedule = {
                                scheduleDateState.setTextAndPlaceCursorAtEnd(LocalDate.now().plusDays(1).toString())
                                schedulingPlan = plan
                            },
                            onDuplicate = { onDuplicate(plan.id) },
                            onDelete = { onDelete(plan.id) },
                        )
                    }
                }
            }
        }
    }
    FloatingBottomSheet(
        show = schedulingPlan != null,
        title = "安排「${schedulingPlan?.name.orEmpty()}」",
        onDismissRequest = { schedulingPlan = null },
    ) {
        val scheduleDateInput = scheduleDateState.text.toString()
        val today = LocalDate.now()
        val quickDates = listOf("今天" to today, "明天" to today.plusDays(1), "后天" to today.plusDays(2))
        val quickIndex = quickDates.indexOfFirst { it.second.toString() == scheduleDateInput.trim() }
        GlassTabRow(
            tabs = quickDates.map { it.first },
            selectedIndex = quickIndex.coerceAtLeast(0),
            onSelect = { scheduleDateState.setTextAndPlaceCursorAtEnd(quickDates[it].second.toString()) },
            backdrop = LocalRootBackdrop.current ?: LocalSurfaceBackdrop.current,
            modifier = Modifier.fillMaxWidth(),
            style = rememberOs4GlassStyle(),
            material = rememberOs4CustomMaterial() ?: GlassTabRowDefaults.material(),
            stroke = rememberOs4EdgeStroke(GlassTabRowDefaults.stroke()),
            colors = if (quickIndex >= 0) GlassTabRowDefaults.primaryColors() else GlassTabRowDefaults.neutralColors(),
        )
        SheetTextField(
            state = scheduleDateState,
            label = "训练日期 YYYY-MM-DD",
            useLabelAsPlaceholder = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val parsedDate = runCatching { LocalDate.parse(scheduleDateInput.trim()) }.getOrNull()
        if (parsedDate == null) {
            Text("请输入有效的日期（例如 2026-11-19）", color = MiuixTheme.colorScheme.error)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { schedulingPlan = null }, modifier = Modifier.weight(1f)) {
                Text("取消")
            }
            Button(
                enabled = parsedDate != null,
                onClick = {
                    val planId = schedulingPlan?.id
                    if (planId != null && parsedDate != null) {
                        onSchedule(planId, parsedDate)
                        schedulingPlan = null
                    }
                },
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.weight(1f),
            ) {
                Text("确认安排")
            }
        }
    }
}

@Composable
private fun PlanMoreMenu(
    onEdit: () -> Unit,
    onSchedule: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val anchor = rememberGlassPopupAnchor()
    val style = rememberOs4GlassStyle()
    val customMaterial = rememberOs4CustomMaterial()
    val buttonStroke = rememberOs4EdgeStroke(GlassTopAppBarDefaults.buttonStroke())
    val popupVisuals = GlassPopupDefaults.visuals(
        style = style,
        material = customMaterial ?: GlassPopupDefaults.material(),
        stroke = rememberOs4EdgeStroke(GlassPopupDefaults.stroke()),
    )
    val entries = remember(onEdit, onSchedule, onDuplicate, onDelete) {
        listOf(
            DropdownEntry(
                listOf(
                    DropdownItem("编辑计划", onClick = onEdit),
                    DropdownItem("安排到日历", onClick = onSchedule),
                    DropdownItem("复制计划", onClick = onDuplicate),
                    DropdownItem("删除计划", onClick = onDelete),
                ),
            ),
        )
    }
    GlassIconButton(
        onClick = { expanded = !expanded },
        size = 42.dp,
        backdrop = LocalPageBackdrop.current,
        style = style,
        material = customMaterial,
        stroke = buttonStroke,
        modifier = Modifier.glassPopupAnchor(anchor, 21.dp, floating = true),
    ) {
        Icon(
            imageVector = MiuixIcons.Glass.More,
            contentDescription = "更多计划操作",
            modifier = Modifier.size(24.dp).glassPopupAnchorContent(anchor),
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
                    modifier = Modifier.size(24.dp),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            },
            entries = entries,
            backdrop = LocalRootBackdrop.current ?: LocalPageBackdrop.current,
            visuals = popupVisuals,
        )
    }
}

@Composable fun PageHeading(title:String,subtitle:String) { Column(Modifier.padding(horizontal=18.dp,vertical=10.dp)) { Text(title,style=MiuixTheme.textStyles.title1); Text(subtitle,color=MiuixTheme.colorScheme.onSurfaceSecondary) } }

@Composable
fun HeroCard(title: String, subtitle: String, button: String, onClick: () -> Unit, floating: Boolean = false) {
    val content: @Composable () -> Unit = {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(20.dp)) {
            if (maxWidth < 340.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text(title, style = MiuixTheme.textStyles.title2)
                        Text(subtitle, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    }
                    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(button) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MiuixTheme.textStyles.title2)
                        Text(subtitle, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = onClick) { Text(button) }
                }
            }
        }
    }
    if (floating) {
        GlassFloatingToolbar(
            modifier = Modifier.fillMaxWidth(),
            outSidePadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
            backdrop = LocalPageBackdrop.current,
            style = rememberOs4GlassStyle(),
            material = rememberOs4CustomMaterial() ?: GlassFloatingToolbarDefaults.material(),
            stroke = rememberOs4EdgeStroke(GlassFloatingToolbarDefaults.stroke()),
        ) {
            content()
        }
    } else {
        Card(Modifier.cardPadding()) { content() }
    }
}

@Composable fun EmptyCard(title:String,subtitle:String) { Card(Modifier.cardPadding()) { Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally) { Text(title,style=MiuixTheme.textStyles.title3); Text(subtitle,color=MiuixTheme.colorScheme.onSurfaceSecondary) } } }
fun Modifier.cardPadding()=this.padding(horizontal=12.dp).padding(bottom=12.dp)
