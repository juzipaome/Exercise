package com.juzi.lianji

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.juzi.lianji.data.*
import com.juzi.lianji.ui.LianJiApp
import com.juzi.lianji.ui.LianJiTheme
import com.juzi.lianji.ui.WorkoutScreen
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class UiRegressionTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    private lateinit var db: LianJiDatabase
    private lateinit var vm: MainViewModel

    @Before fun open() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, LianJiDatabase::class.java).build()
        val settingsDir = File(instrumentation.targetContext.cacheDir, "ui-regression")
        val testContext = object : ContextWrapper(instrumentation.context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = settingsDir
        }
        val app = LianJiApplication().apply {
            repository = LianJiRepository(db)
            settingsStore = SettingsStore(testContext)
            backupManager = BackupManager(testContext, db, settingsStore)
        }
        compose.runOnUiThread { vm = MainViewModel(app) }
    }

    @After fun close() {
        if (::vm.isInitialized) compose.runOnUiThread { vm.viewModelScope.cancel() }
        if (::db.isInitialized) db.close()
    }

    private fun awaitText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun workout(names: List<String>, sets: Int = 1): Long = runBlocking {
        val exercises=names.map { name ->
            ExerciseEntity(id=name,nameEn=name,nameZh=name,bodyPart="waist",equipment="body weight",target="",muscleGroup="",secondaryMuscles="",instructionsZh="",instructionsEn="",imagePath=null,gifPath=null,attribution="test")
        }
        db.exerciseDao().upsertAll(exercises)
        val plan=vm.repository.save(WorkoutPlanEntity(name="UI regression"),exercises.mapIndexed { i,e ->
            PlanExerciseEntity(planId=0,exerciseId=e.id,position=i,defaultSets=sets)
        })
        vm.repository.start(plan)
    }

    private fun card(name: String)=compose.onNodeWithContentDescription("长按拖动 $name 调整顺序")

    @Test fun active_workout_cards_scroll_behind_the_glass_top_bar() {
        val sessionId = workout((0..15).map { "Blur$it" })
        compose.setContent { LianJiApp(vm, requestedWorkoutId = sessionId) }
        awaitText("Blur0")
        val list = compose.onNodeWithTag("workout-exercises")
        val backBottom = compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(card("Blur0").fetchSemanticsNode().boundsInRoot.top > backBottom)
        val distance = with(compose.density) { 40.dp.toPx() }
        list.performTouchInput { swipe(center, center - Offset(0f, distance), durationMillis = 600) }
        assertTrue(card("Blur0").fetchSemanticsNode().boundsInRoot.top < backBottom)
    }

    @Test fun dragging_visible_first_card_keeps_viewport_and_expanded_height() {
        val sessionId=workout((0..15).map { "Move$it" })
        compose.setContent { LianJiTheme(AppSettings(dynamicColor=false)) { WorkoutScreen(vm,sessionId,{},{},{}) } }
        awaitText("Move0")
        val list=compose.onNodeWithTag("workout-exercises")
        list.performScrollToIndex(5)
        compose.onNodeWithText("Move5").performClick()
        compose.waitForIdle()
        val before=card("Move5").fetchSemanticsNode().boundsInRoot
        val neighbor=card("Move6").fetchSemanticsNode().boundsInRoot
        val origin=list.fetchSemanticsNode().boundsInRoot.topLeft
        // Grab the header of the viewport's first card, not the plan's first card.
        val start=Offset(before.left+30f,before.top+30f)-origin
        compose.mainClock.autoAdvance=false
        list.performTouchInput { down(start); advanceEventTime(600); moveTo(start) }
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        val held=card("Move5").fetchSemanticsNode().boundsInRoot
        // Drag feedback scales the card by 1.025, but must not collapse its content.
        assertTrue("Long press collapsed the card: before=$before held=$held",held.height>=before.height*.98f)
        assertEquals("Long press scrolled the list",neighbor.top,card("Move6").fetchSemanticsNode().boundsInRoot.top,1f)
        val smallMove=40f
        list.performTouchInput { moveTo(start+Offset(0f,smallMove),100) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals("A downward move at the top edge scrolled in the opposite direction",neighbor.top,card("Move6").fetchSemanticsNode().boundsInRoot.top,2f)
        val delta=(before.height+neighbor.height)/2f+40f
        list.performTouchInput { moveTo(start+Offset(0f,delta),100) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals("First visible key dragged the viewport with it",held.top+delta,card("Move5").fetchSemanticsNode().boundsInRoot.top,2f)
        assertEquals("Neighbor did not fill the old slot",before.top,card("Move6").fetchSemanticsNode().boundsInRoot.top,2f)
        list.performTouchInput { up() }
        compose.mainClock.autoAdvance=true
        compose.waitUntil(10_000) { runBlocking { vm.repository.rows(sessionId).first()[5].exerciseId=="Move6" } }
    }

    @Test fun held_pointer_scrolls_and_reorders_at_both_viewport_edges() {
        val names=(0..19).map { "Edge$it" }
        val sessionId=workout(names)
        compose.setContent { LianJiTheme(AppSettings(dynamicColor=false)) { WorkoutScreen(vm,sessionId,{},{},{}) } }
        awaitText("Edge0")
        val list=compose.onNodeWithTag("workout-exercises")
        val visibleTop = card("Edge0").fetchSemanticsNode().boundsInRoot.top -
            list.fetchSemanticsNode().boundsInRoot.top - with(compose.density) { 12.dp.toPx() }
        fun dragToEdge(name: String, downwards: Boolean) {
            val bounds=list.fetchSemanticsNode().boundsInRoot
            val start=card(name).fetchSemanticsNode().boundsInRoot.center-bounds.topLeft
            val end=Offset(start.x,if(downwards)bounds.height-8f else visibleTop+8f)
            compose.mainClock.autoAdvance=false
            list.performTouchInput { down(start); advanceEventTime(600); moveTo(start) }
            compose.mainClock.advanceTimeBy(100)
            list.performTouchInput { moveTo(end,100) }
            // No further move events: edge scrolling must keep reordering while held.
            compose.mainClock.advanceTimeBy(2_400)
            compose.waitForIdle()
            card(name).assertIsDisplayed()
            list.performTouchInput { up() }
            compose.mainClock.autoAdvance=true
            compose.waitForIdle()
        }
        list.performScrollToIndex(7)
        dragToEdge("Edge9",false)
        compose.waitUntil(10_000) { runBlocking { vm.repository.rows(sessionId).first().indexOfFirst{it.exerciseId=="Edge9"}<7 } }
        list.performScrollToIndex(7)
        val target=runBlocking { vm.repository.rows(sessionId).first()[8].exerciseId }
        dragToEdge(target,true)
        compose.waitUntil(10_000) { runBlocking { vm.repository.rows(sessionId).first().indexOfFirst{it.exerciseId==target}>10 } }
    }

    @Test fun long_pressing_topmost_card_with_upward_jitter_does_not_rapidly_scroll_neighbors() {
        val sessionId=workout((0..15).map { "TopHold$it" })
        compose.setContent { LianJiTheme(AppSettings(dynamicColor=false)) { WorkoutScreen(vm,sessionId,{},{},{}) } }
        awaitText("TopHold0")
        val list=compose.onNodeWithTag("workout-exercises")
        val visibleTop = card("TopHold0").fetchSemanticsNode().boundsInRoot.top -
            list.fetchSemanticsNode().boundsInRoot.top - with(compose.density) { 12.dp.toPx() }
        list.performScrollToIndex(5)
        compose.waitForIdle()
        val before=card("TopHold5").fetchSemanticsNode().boundsInRoot
        val neighbor=card("TopHold6").fetchSemanticsNode().boundsInRoot
        val origin=list.fetchSemanticsNode().boundsInRoot.topLeft
        val start=Offset(before.left+30f,before.top+18f)-origin
        compose.mainClock.autoAdvance=false
        list.performTouchInput { down(start); advanceEventTime(600); moveTo(start) }
        compose.mainClock.advanceTimeBy(200)
        list.performTouchInput { moveTo(Offset(start.x,(start.y-20f).coerceAtLeast(2f)),100) }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        assertEquals("Long-pressing topmost card with upward jitter caused rapid neighbor scroll",neighbor.top,card("TopHold6").fetchSemanticsNode().boundsInRoot.top,2f)
        val clearY=visibleTop+with(compose.density) { 64.dp.toPx() }
        list.performTouchInput { moveTo(Offset(start.x,clearY),100) }
        compose.mainClock.advanceTimeBy(100)
        list.performTouchInput { moveTo(Offset(start.x,visibleTop+6f),100) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        list.performTouchInput { up() }
        compose.mainClock.autoAdvance=true
        compose.waitUntil(10_000) { runBlocking { vm.repository.rows(sessionId).first().indexOfFirst{it.exerciseId=="TopHold5"}<5 } }
    }

    @Test fun dragging_top_card_downward_in_upper_half_never_scrolls_to_bottom() {
        val sessionId=workout((0..15).map { "UpperDrag$it" })
        compose.setContent { LianJiTheme(AppSettings(dynamicColor=false)) { WorkoutScreen(vm,sessionId,{},{},{}) } }
        awaitText("UpperDrag0")
        val list=compose.onNodeWithTag("workout-exercises")
        val origin=list.fetchSemanticsNode().boundsInRoot.topLeft
        val viewportHeight=list.fetchSemanticsNode().boundsInRoot.height
        val visibleTop = card("UpperDrag0").fetchSemanticsNode().boundsInRoot.top -
            origin.y - with(compose.density) { 12.dp.toPx() }
        val upperLimit=visibleTop+(viewportHeight-visibleTop)*0.45f

        // 1. Fast downward drag of UpperDrag0 within the upper half of the viewport
        val before0=card("UpperDrag0").fetchSemanticsNode().boundsInRoot
        val neighbor1=card("UpperDrag1").fetchSemanticsNode().boundsInRoot
        val start0=Offset(before0.left+30f,before0.top+24f)-origin
        val targetDelta0=(before0.height*1.2f).coerceAtMost(upperLimit-start0.y)
        compose.mainClock.autoAdvance=false
        list.performTouchInput { down(start0); advanceEventTime(600); moveTo(start0) }
        compose.mainClock.advanceTimeBy(100)
        list.performTouchInput { moveTo(start0+Offset(0f,targetDelta0),50) }
        compose.mainClock.advanceTimeBy(1_200)
        compose.waitForIdle()
        assertEquals("Viewport jumped when dragging top card fast",before0.top,card("UpperDrag1").fetchSemanticsNode().boundsInRoot.top,2f)
        assertTrue("List scrolled all the way to the bottom!",compose.onAllNodesWithText("UpperDrag15").fetchSemanticsNodes().isEmpty())
        list.performTouchInput { up() }
        compose.mainClock.autoAdvance=true
        compose.waitUntil(10_000) { runBlocking { vm.repository.rows(sessionId).first()[1].exerciseId=="UpperDrag0" } }

        // 2. Slow incremental downward drag of the new top card (UpperDrag1) in the upper half
        val before1=card("UpperDrag1").fetchSemanticsNode().boundsInRoot
        val start1=Offset(before1.left+30f,before1.top+24f)-origin
        val slowTotal=(neighbor1.height+before1.height*0.3f).coerceAtMost(upperLimit-start1.y)
        compose.mainClock.autoAdvance=false
        list.performTouchInput { down(start1); advanceEventTime(600); moveTo(start1) }
        compose.mainClock.advanceTimeBy(100)
        val steps=25
        repeat(steps) { step ->
            val y=start1.y+slowTotal*(step+1)/steps
            list.performTouchInput { moveTo(Offset(start1.x,y),32) }
            compose.mainClock.advanceTimeBy(32)
        }
        compose.mainClock.advanceTimeBy(1_200)
        compose.waitForIdle()
        assertEquals("Viewport jumped during slow downward drag",before1.top,card("UpperDrag0").fetchSemanticsNode().boundsInRoot.top,2f)
        assertTrue("List scrolled to bottom during slow drag!",compose.onAllNodesWithText("UpperDrag15").fetchSemanticsNodes().isEmpty())
        list.performTouchInput { up() }
        compose.mainClock.autoAdvance=true
        compose.waitUntil(10_000) {
            val idx=runBlocking { vm.repository.rows(sessionId).first().indexOfFirst { it.exerciseId=="UpperDrag1" } }
            idx in 1..2
        }
    }

    @Test fun workout_top_menu_opens_rest_settings_and_add_exercise() {
        val sessionId = workout(listOf("MenuExercise"))
        compose.setContent { LianJiApp(vm, requestedWorkoutId = sessionId) }
        awaitText("MenuExercise")
        val more = compose.onNodeWithContentDescription("更多训练功能")

        more.performTouchInput { click() }
        compose.onNodeWithText("调整休息时间").assertIsDisplayed().performTouchInput { click() }
        awaitText("本次组间休息")
        compose.onNodeWithText("取消").performClick()

        more.performTouchInput { click() }
        compose.onNodeWithText("添加动作").assertIsDisplayed().performTouchInput { click() }
        awaitText("训练中添加动作")
    }

    @Test fun appearance_settings_open_on_a_separate_page_and_save_theme_selection() {
        val originalMode = runBlocking { vm.settingsStore.settings.first().themeMode }
        lateinit var activity: Activity
        try {
            runBlocking { vm.settingsStore.setTheme("LIGHT") }
            compose.setContent {
                val host = checkNotNull(LocalActivity.current)
                SideEffect { activity = host }
                LianJiApp(vm)
            }
            fun assertLightSystemBars(expected: Boolean) = compose.runOnIdle {
                val window = activity.window
                val bars = WindowCompat.getInsetsController(window, window.decorView)
                assertEquals(expected, bars.isAppearanceLightStatusBars)
                assertEquals(expected, bars.isAppearanceLightNavigationBars)
            }
            assertLightSystemBars(true)
            compose.onNodeWithText("设置").performClick()
            awaitText("外观")
            compose.onNodeWithText("深浅模式").assertDoesNotExist()
            compose.onNodeWithText("外观").performClick()
            awaitText("深浅模式")
            compose.onNodeWithText("深浅模式").performClick()
            compose.onNodeWithText("深色主题").assertIsDisplayed().performTouchInput { click() }
            compose.waitUntil(10_000) { runBlocking { vm.settingsStore.settings.first().themeMode == "DARK" } }
            assertLightSystemBars(false)
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithText("默认组间休息").assertIsDisplayed()
        } finally {
            runBlocking { vm.settingsStore.setTheme(originalMode) }
        }
    }

    @Test fun main_create_actions_move_between_the_bottom_and_top_bars_without_duplicates() {
        val original = runBlocking { vm.settingsStore.settings.first().navigationBarAction }
        try {
            runBlocking { vm.settingsStore.setNavigationBarAction(true) }
            compose.setContent { LianJiApp(vm) }
            val createPlan = compose.onNodeWithContentDescription("创建计划")
            val bottomPlanY = createPlan.fetchSemanticsNode().boundsInRoot.center.y
            createPlan.performClick()
            awaitText("计划名称")
            compose.onNodeWithContentDescription("返回").performClick()

            compose.onNodeWithText("动作库").performClick()
            val createExercise = compose.onNodeWithContentDescription("自定义动作")
            val bottomExerciseY = createExercise.fetchSemanticsNode().boundsInRoot.center.y
            createExercise.performClick()
            awaitText("动作名称")
            compose.onNodeWithContentDescription("返回").performClick()

            runBlocking { vm.settingsStore.setNavigationBarAction(false) }
            compose.waitUntil(10_000) { !vm.state.value.settings.navigationBarAction }
            assertTrue(createExercise.fetchSemanticsNode().boundsInRoot.center.y < bottomExerciseY)
            compose.onNodeWithText("运动").performClick()
            assertTrue(createPlan.fetchSemanticsNode().boundsInRoot.center.y < bottomPlanY)
        } finally {
            runBlocking { vm.settingsStore.setNavigationBarAction(original) }
        }
    }

    @Test fun deleting_trained_set_confirms_and_last_set_requires_explicit_card_deletion() {
        val sessionId=workout(listOf("DeleteA","DeleteB"),sets=2)
        val trained=runBlocking {
            val row=vm.repository.rows(sessionId).first().first()
            vm.repository.beginSet(row.setId)
            vm.repository.completeSet(row.setId,25.0,8)
            db.sessionDao().getSet(row.setId)!!
        }
        compose.setContent { LianJiApp(vm,requestedWorkoutId=sessionId) }
        awaitText("DeleteA")
        compose.onAllNodesWithContentDescription("删除本组")[0].performClick()
        awaitText("删除已有训练记录")
        assertEquals(trained,runBlocking { db.sessionDao().getSet(trained.id) })
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        assertEquals(trained,runBlocking { db.sessionDao().getSet(trained.id) })
        compose.onAllNodesWithContentDescription("删除本组")[0].performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil(10_000) { runBlocking { db.sessionDao().getSet(trained.id)==null } }
        compose.onNodeWithContentDescription("删除本组").performClick()
        awaitText("操作未完成")
        assertEquals(1,runBlocking { db.sessionDao().getSetsForExercise(trained.sessionExerciseId).size })
        compose.onNodeWithText("知道了").performClick()
        compose.onNodeWithContentDescription("DeleteA 动作菜单").performClick()
        compose.onNodeWithText("删除动作").performClick()
        awaitText("删除训练动作")
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        assertEquals(1,runBlocking { db.sessionDao().getSetsForExercise(trained.sessionExerciseId).size })
        compose.onNodeWithContentDescription("DeleteA 动作菜单").performClick()
        compose.onNodeWithText("删除动作").performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil(10_000) { runBlocking { db.sessionDao().getSetsForExercise(trained.sessionExerciseId).isEmpty() } }
        assertEquals(listOf("DeleteB"),runBlocking { db.sessionDao().getSessionExercises(sessionId).map{it.exerciseId} })
        assertEquals(2,runBlocking { db.planDao().getAllItems().size })
    }

    @Test fun failed_save_keeps_draft_and_retry_returns_only_after_persistence() {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_save BEFORE INSERT ON exercises BEGIN SELECT RAISE(ABORT, 'test write failure'); END")
        compose.setContent { LianJiApp(vm) }
        compose.onNodeWithText("动作库").performClick()
        compose.onNodeWithContentDescription("自定义动作").performClick()
        compose.onNode(hasSetTextAction() and hasText("动作名称")).performTextInput("UI-save-regression")
        compose.onNodeWithText("保存动作").assertIsEnabled().performScrollTo().performClick()
        awaitText("操作未完成")
        compose.onNodeWithText("知道了").performClick()
        compose.onNodeWithText("UI-save-regression").assertExists()
        compose.onNodeWithText("保存动作").assertIsEnabled()
        assertTrue(runBlocking { db.exerciseDao().customForBackup().isEmpty() })

        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_save")
        compose.onNodeWithText("保存动作").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("保存动作").fetchSemanticsNodes().isEmpty() }
        assertEquals("UI-save-regression", runBlocking { db.exerciseDao().customForBackup().single().nameZh })
        compose.onNodeWithText("UI-save-regression").assertIsDisplayed()
    }

    @Test fun calendar_switches_month_and_opens_only_the_selected_days_records() {
        val month = YearMonth.now()
        val previous = month.minusMonths(1)
        val dateLabel = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
        val dayTitle = DateTimeFormatter.ofPattern("yyyy年M月d日")
        val hasSchedule = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已安排 1 项")
        val thisMonthFirst = hasContentDescription(month.atDay(1).format(dateLabel), substring = true)
        val previousMonthFirst = hasContentDescription(previous.atDay(1).format(dateLabel))
        runBlocking {
            db.scheduleDao().insert(ScheduledWorkoutEntity(planId=null,planName="本月测试安排",scheduledDate=month.atDay(1).toString()))
            db.scheduleDao().insert(ScheduledWorkoutEntity(planId=null,planName="上月测试安排",scheduledDate=previous.atDay(1).toString()))
        }
        compose.setContent { LianJiApp(vm) }
        compose.onNodeWithText("日历").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(thisMonthFirst and hasSchedule).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithContentDescription("上月").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(previousMonthFirst and hasSchedule).fetchSemanticsNodes().size == 1 }
        compose.onNode(thisMonthFirst).assertDoesNotExist()
        compose.onNodeWithText("回到本月").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(thisMonthFirst and hasSchedule).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithContentDescription("上月").performClick()
        compose.onNode(previousMonthFirst).performClick()
        awaitText(previous.atDay(1).format(dayTitle))
        compose.onNode(hasText("上月测试安排") and hasAnySibling(hasText("训练已安排"))).assertIsDisplayed()
        compose.onNodeWithText("本月测试安排").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun starting_lower_card_promotes_it_without_late_neighbor_motion() {
        val sessionId = runBlocking {
            val exercises = listOf("Alpha", "Beta", "Gamma").map { name ->
                ExerciseEntity(id=name,nameEn=name,nameZh=name,bodyPart="waist",equipment="body weight",target="",muscleGroup="",secondaryMuscles="",instructionsZh="",instructionsEn="",imagePath=null,gifPath=null,attribution="test")
            }
            db.exerciseDao().upsertAll(exercises)
            val plan = vm.repository.save(WorkoutPlanEntity(name="UI reorder"),exercises.mapIndexed { i,e ->
                PlanExerciseEntity(planId=0,exerciseId=e.id,position=i,defaultSets=1)
            })
            vm.repository.start(plan)
        }
        compose.setContent { LianJiTheme(AppSettings(dynamicColor=false)) { WorkoutScreen(vm,sessionId,{},{},{}) } }
        awaitText("Gamma")
        compose.onNodeWithText("Gamma").performClick()
        compose.onNodeWithContentDescription("开始本组").performScrollTo()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("开始本组").performClick()
        compose.waitUntil(10_000) { runBlocking { vm.repository.rows(sessionId).first().first().exerciseId == "Gamma" } }

        fun bounds(name: String): Rect = compose.onNodeWithContentDescription("长按拖动 $name 调整顺序").fetchSemanticsNode().boundsInRoot
        var promotedFrames = 0
        // Inspect intermediate frames, not just the final idle state (which hides a late spring).
        repeat(60) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val gamma = bounds("Gamma"); val alpha = bounds("Alpha"); val beta = bounds("Beta")
            if (gamma.top < alpha.top) {
                promotedFrames++
                assertTrue("Alpha overlapped the promoted card at frame $it: Gamma=$gamma Alpha=$alpha Beta=$beta", gamma.bottom <= alpha.top + 1f)
                assertTrue("Beta overlapped Alpha at frame $it: Gamma=$gamma Alpha=$alpha Beta=$beta", alpha.bottom <= beta.top + 1f)
            }
        }
        assertTrue("The lower card was never revealed at the top", promotedFrames > 0)
        val settled = listOf(bounds("Gamma"),bounds("Alpha"),bounds("Beta"))
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        listOf(bounds("Gamma"),bounds("Alpha"),bounds("Beta")).zip(settled).forEach { (after,before) ->
            assertEquals("A card moved again after settling",before.top,after.top,1f)
        }
        compose.mainClock.autoAdvance = true
        // Manual ordering still works after automatic promotion disables placement springs.
        compose.onNodeWithContentDescription("长按拖动 Beta 调整顺序")
            .performCustomAccessibilityActionWithLabel("上移")
        compose.waitUntil(10_000) {
            runBlocking { vm.repository.rows(sessionId).first().map { it.exerciseId } == listOf("Gamma", "Beta", "Alpha") }
        }
        compose.waitForIdle()
        assertTrue(bounds("Gamma").bottom <= bounds("Beta").top + 1f)
        assertTrue(bounds("Beta").bottom <= bounds("Alpha").top + 1f)
    }
}
