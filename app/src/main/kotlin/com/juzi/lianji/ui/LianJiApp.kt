package com.juzi.lianji.ui

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juzi.lianji.MainViewModel
import com.juzi.lianji.MainUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.YearMonth
import kotlin.math.abs

@Serializable
sealed interface AppScreen : NavKey {
    @Serializable
    data object Main: AppScreen
    @Serializable
    data object NewPlan: AppScreen
    @Serializable
    data class EditPlan(val id:Long): AppScreen
    @Serializable
    data object NewExercise: AppScreen
    @Serializable
    data class ExerciseDetail(val id:String): AppScreen
    @Serializable
    data class Workout(val id:Long): AppScreen
    @Serializable
    data class AddWorkoutExercise(val id:Long): AppScreen
    @Serializable
    data class Day(val date:String): AppScreen
    @Serializable
    data class MonthAnalytics(val month:String): AppScreen
    @Serializable
    data object About: AppScreen
}

@Composable
fun LianJiApp(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val stack = rememberNavBackStack<AppScreen>(AppScreen.Main)
    var mainPage by rememberSaveable { mutableIntStateOf(0) }
    fun push(next: AppScreen) {
        stack.add(next)
    }
    fun animatedPop(){if(stack.size>1)stack.removeAt(stack.lastIndex)}
    val renderScreen: @Composable (AppScreen) -> Unit = { current -> when(val s=current) {
        AppScreen.Main -> MainTabs(vm, mainPage, { mainPage = it }, onNavigate=::push)
        AppScreen.NewPlan -> PlanEditorScreen(vm,onBack=::animatedPop,onSave={animatedPop()}) { id -> vm.start(id){animatedPop();push(AppScreen.Workout(it))} }
        is AppScreen.EditPlan -> PlanEditorScreen(vm,s.id,::animatedPop,{animatedPop()}) { id -> vm.start(id){animatedPop();push(AppScreen.Workout(it))} }
        AppScreen.NewExercise -> CustomExerciseScreen(vm,::animatedPop)
        is AppScreen.ExerciseDetail -> ExerciseDetailScreen(vm,s.id,::animatedPop)
        is AppScreen.Workout -> WorkoutScreen(vm,s.id,::animatedPop,onAddExercise={push(AppScreen.AddWorkoutExercise(s.id))})
        is AppScreen.AddWorkoutExercise -> WorkoutExercisePickerScreen(vm,s.id,::animatedPop)
        is AppScreen.Day -> DayDetailScreen(vm,s.date,::animatedPop)
        is AppScreen.MonthAnalytics -> MonthAnalyticsScreen(vm,YearMonth.parse(s.month),::animatedPop)
        AppScreen.About -> AboutScreen(::animatedPop)
    } }
    LianJiTheme(state.settings) {
        val navCornerRadius = rememberNavSystemCornerRadius()
        val navBackdropColor = MiuixTheme.colorScheme.surface
        val navEffects = remember(navCornerRadius, navBackdropColor) {
            NavDisplayEffects(
                cornerClipRadius = navCornerRadius,
                backdropColor = navBackdropColor,
            )
        }
        CompositionLocalProvider(LocalAnimatedBack provides ::animatedPop) {
            NavDisplay(
                backStack=stack,
                modifier=Modifier.fillMaxSize(),
                onBack=::animatedPop,
                effects=navEffects,
            ) {
                entry<AppScreen.Main> { renderScreen(it) }
                entry<AppScreen.NewPlan> { renderScreen(it) }
                entry<AppScreen.EditPlan> { renderScreen(it) }
                entry<AppScreen.NewExercise> { renderScreen(it) }
                entry<AppScreen.ExerciseDetail> { renderScreen(it) }
                entry<AppScreen.Workout> { renderScreen(it) }
                entry<AppScreen.AddWorkoutExercise> { renderScreen(it) }
                entry<AppScreen.Day> { renderScreen(it) }
                entry<AppScreen.MonthAnalytics> { renderScreen(it) }
                entry<AppScreen.About> { renderScreen(it) }
            }
        }
    }
}

@Composable
private fun MainTabs(vm:MainViewModel,selectedPage:Int,onSelectedPage:(Int)->Unit,onNavigate:(AppScreen)->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pager=rememberPagerState(initialPage=selectedPage,pageCount={4})
    val mainPagerState=rememberMainPagerState(pager)
    LaunchedEffect(pager.currentPage){mainPagerState.syncPage();onSelectedPage(pager.currentPage)}
    // Each page receives only the fields it renders. Loading the exercise library and
    // history after startup must not invalidate and recompose the already visible home.
    val homeState=remember(state.plans,state.active,state.settings){MainUiState(isReady=true,plans=state.plans,active=state.active,settings=state.settings)}
    val calendarState=remember(state.schedules,state.sessions,state.days){MainUiState(isReady=true,schedules=state.schedules,sessions=state.sessions,days=state.days)}
    val exerciseState=remember(state.exercises){MainUiState(isReady=true,exercises=state.exercises)}
    val settingsState=remember(state.settings){MainUiState(isReady=true,settings=state.settings)}
    val labels=listOf("运动","日历","动作库","设置")
    val icons=listOf(MiuixIcons.Home,MiuixIcons.Months,MiuixIcons.All,MiuixIcons.Settings)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide=maxWidth>=600.dp
        val surfaceColor=MiuixTheme.colorScheme.surface
        val backdrop=rememberLayerBackdrop { drawRect(surfaceColor);drawContent() }
        val navigationBarBlurColors=BlurDefaults.blurColors(
            blendColors=listOf(BlendColorEntry(surfaceColor.copy(alpha=.8f))),
        )
        Row(Modifier.fillMaxSize()) {
            if(wide) NavigationRail {
                labels.forEachIndexed { i,label -> NavigationRailItem(selected=mainPagerState.selectedPage==i,onClick={mainPagerState.animateToPage(i)},icon=icons[i],label=label) }
            }
            Scaffold(
                modifier=Modifier.weight(1f),
                bottomBar={ if(!wide) Box(
                    Modifier
                        .textureBlur(backdrop=backdrop,shape=RectangleShape,blurRadius=25f,colors=navigationBarBlurColors)
                        .clickable(interactionSource=remember{MutableInteractionSource()},indication=null,onClick={}),
                ) { NavigationBar(color=Color.Transparent) { labels.forEachIndexed { i,label -> NavigationBarItem(selected=mainPagerState.selectedPage==i,onClick={mainPagerState.animateToPage(i)},icon=icons[i],label=label) } } } },
            ) { padding ->
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    HorizontalPager(state=pager,modifier=Modifier.fillMaxSize(),verticalAlignment=androidx.compose.ui.Alignment.Top) { page ->
                        when(page) {
                            0 -> WorkoutHomeScreen(homeState, padding, onNew={onNavigate(AppScreen.NewPlan)},onEdit={onNavigate(AppScreen.EditPlan(it))}, onStart={vm.start(it){id->onNavigate(AppScreen.Workout(id))}}, onContinue={homeState.active?.let{onNavigate(AppScreen.Workout(it.id))}}, onDuplicate=vm::duplicatePlan,onDelete=vm::deletePlan)
                            1 -> CalendarScreen(calendarState,padding,onDay={onNavigate(AppScreen.Day(it))},onMonth={onNavigate(AppScreen.MonthAnalytics(it.toString()))})
                            2 -> ExerciseLibraryScreen(exerciseState,padding,{onNavigate(AppScreen.ExerciseDetail(it))},{onNavigate(AppScreen.NewExercise)},vm::toggleFavorite)
                            else -> SettingsScreen(settingsState,padding,vm,onAbout={onNavigate(AppScreen.About)})
                        }
                    }
                }
            }
        }
    }
}

/** Kept in sync with the MainPagerState shipped in the MIUIX 0.9.4-rc01 example app. */
@Stable
private class MainPagerState(
    val pagerState:PagerState,
    private val coroutineScope:CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set
    var isNavigating by mutableStateOf(false)
        private set
    private var navJob:Job?=null

    fun animateToPage(targetIndex:Int) {
        if(targetIndex==selectedPage)return
        navJob?.cancel()
        selectedPage=targetIndex
        isNavigating=true
        navJob=coroutineScope.launch {
            val myJob=coroutineContext.job
            try {
                pagerState.scroll(MutatePriority.UserInput) {
                    val distance=abs(targetIndex-pagerState.currentPage).coerceAtLeast(2)
                    val duration=100*distance+100
                    val layoutInfo=pagerState.layoutInfo
                    val pageSize=layoutInfo.pageSize+layoutInfo.pageSpacing
                    val currentDistanceInPages=targetIndex-pagerState.currentPage-pagerState.currentPageOffsetFraction
                    val scrollPixels=currentDistanceInPages*pageSize
                    var previousValue=0f
                    animate(
                        initialValue=0f,
                        targetValue=scrollPixels,
                        animationSpec=tween(durationMillis=duration,easing=EaseInOut),
                    ){currentValue,_->
                        previousValue+=scrollBy(currentValue-previousValue)
                    }
                }
                if(pagerState.currentPage!=targetIndex)pagerState.scrollToPage(targetIndex)
            } finally {
                if(navJob==myJob) {
                    isNavigating=false
                    if(pagerState.currentPage!=targetIndex)selectedPage=pagerState.currentPage
                }
            }
        }
    }

    fun syncPage(){if(!isNavigating&&selectedPage!=pagerState.currentPage)selectedPage=pagerState.currentPage}
}

@Composable
private fun rememberMainPagerState(
    pagerState:PagerState,
    coroutineScope:CoroutineScope=rememberCoroutineScope(),
)=remember(pagerState,coroutineScope){MainPagerState(pagerState,coroutineScope)}
