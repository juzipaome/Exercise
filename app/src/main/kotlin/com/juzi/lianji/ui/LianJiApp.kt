package com.juzi.lianji.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.juzi.lianji.MainUiState
import com.juzi.lianji.MainViewModel
import com.juzi.lianji.data.AppSettings
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassDialogDefaults
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassNavigationRail
import top.yukonga.miuix.kmp.glass.GlassNavigationRailDefaults
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.rememberGlassNavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Add
import top.yukonga.miuix.kmp.icon.glass.All
import top.yukonga.miuix.kmp.icon.glass.Edit
import top.yukonga.miuix.kmp.icon.glass.HomeOutline
import top.yukonga.miuix.kmp.icon.glass.Months
import top.yukonga.miuix.kmp.icon.glass.Play
import top.yukonga.miuix.kmp.icon.glass.SettingsOutline
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride
import top.yukonga.miuix.kmp.utils.springAnimateToPage

val LocalMiuixFeatureSettings = staticCompositionLocalOf { AppSettings() }
val LocalRootBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalHasRootScaffold = staticCompositionLocalOf { false }
val LocalPageBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalSurfaceBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

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
    data object Appearance: AppScreen
    @Serializable
    data object About: AppScreen
}

@Composable
fun LianJiApp(vm: MainViewModel, requestedWorkoutId: Long? = null, onWorkoutOpened: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val stack = rememberNavBackStack<AppScreen>(AppScreen.Main)
    var mainPage by rememberSaveable { mutableIntStateOf(0) }
    fun push(next: AppScreen) {
        if (stack.lastOrNull() != next) stack.add(next)
    }
    fun animatedPop(){if(stack.size>1)stack.removeAt(stack.lastIndex)}
    LaunchedEffect(requestedWorkoutId) {
        requestedWorkoutId?.let { id ->
            val target = AppScreen.Workout(id)
            val existing = stack.indexOf(target)
            if (existing < 0) stack.add(target) else while (stack.lastIndex > existing) stack.removeAt(stack.lastIndex)
            onWorkoutOpened()
        }
    }
    val isMainTop = stack.size == 1 && stack.lastOrNull() == AppScreen.Main
    val renderScreen: @Composable (AppScreen) -> Unit = { current -> when(val s=current) {
        AppScreen.Main -> MainTabs(vm, mainPage, { mainPage = it }, isMainTop = isMainTop, onNavigate = ::push)
        AppScreen.NewPlan -> PlanEditorScreen(vm,onBack=::animatedPop,onSave={animatedPop()},onSaveAndStart={id->vm.start(id){animatedPop();push(AppScreen.Workout(it))}},onDetail={push(AppScreen.ExerciseDetail(it))})
        is AppScreen.EditPlan -> PlanEditorScreen(vm,s.id,::animatedPop,{animatedPop()},{id->vm.start(id){animatedPop();push(AppScreen.Workout(it))}},{push(AppScreen.ExerciseDetail(it))})
        AppScreen.NewExercise -> CustomExerciseScreen(vm,::animatedPop)
        is AppScreen.ExerciseDetail -> ExerciseDetailScreen(vm,s.id,::animatedPop)
        is AppScreen.Workout -> WorkoutScreen(vm,s.id,::animatedPop,onAddExercise={push(AppScreen.AddWorkoutExercise(s.id))},onExerciseDetail={push(AppScreen.ExerciseDetail(it))})
        is AppScreen.AddWorkoutExercise -> WorkoutExercisePickerScreen(vm,s.id,::animatedPop)
        is AppScreen.Day -> DayDetailScreen(vm,s.date,::animatedPop,onStartWorkout={planId->vm.start(planId){push(AppScreen.Workout(it))}})
        is AppScreen.MonthAnalytics -> MonthAnalyticsScreen(vm,YearMonth.parse(s.month),::animatedPop)
        AppScreen.Appearance -> AppearanceScreen(state.settings, vm, ::animatedPop)
        AppScreen.About -> AboutScreen(::animatedPop)
    } }
    LianJiTheme(state.settings) {
        val window = LocalActivity.current?.window
        val lightSystemBars = !isOs4DarkTheme()
        SideEffect {
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = lightSystemBars
                controller.isAppearanceLightNavigationBars = lightSystemBars
            }
        }
        val navCornerRadius = rememberNavSystemCornerRadius()
        val navBackdropColor = MiuixTheme.colorScheme.surface
        val navEffects = remember(
            state.settings.navCornerClip,
            state.settings.navDim,
            navCornerRadius,
            navBackdropColor,
        ) {
            NavDisplayEffects(
                enableCornerClip = state.settings.navCornerClip,
                cornerClipRadius = navCornerRadius,
                dimAmount = if (state.settings.navDim) 0.5f else 0f,
                backdropColor = navBackdropColor,
            )
        }
        val swipeBackDirection = when {
            !state.settings.enableSwipeBack -> NavSwipeDirection.None
            LocalLayoutDirection.current == LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
            else -> NavSwipeDirection.LeftToRight
        }
        val topLayer = remember { TopLayerState() }
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
        ) {
            val rootBackdrop = if (isRuntimeShaderSupported()) {
                rememberLayerBackdrop {
                    drawRect(navBackdropColor)
                    drawContent()
                }
            } else {
                null
            }
            CompositionLocalProvider(
                LocalMiuixFeatureSettings provides state.settings,
                LocalRootBackdrop provides rootBackdrop,
                LocalHasRootScaffold provides true,
                LocalTopLayer provides topLayer,
                LocalAnimatedBack provides ::animatedPop,
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (rootBackdrop != null) Modifier.layerBackdrop(rootBackdrop) else Modifier),
                    ) {
                        NavDisplay(
                            backStack = stack,
                            modifier = Modifier.fillMaxSize(),
                            onBack = ::animatedPop,
                            effects = navEffects,
                        ) {
                            entry<AppScreen.Main>(swipeDismiss = NavSwipeDirection.None) { renderScreen(it) }
                            entry<AppScreen.NewPlan>(swipeDismiss = swipeBackDirection) { renderScreen(it) }
                            entry<AppScreen.EditPlan>(swipeDismiss = swipeBackDirection) { renderScreen(it) }
                            entry<AppScreen.NewExercise>(swipeDismiss = swipeBackDirection) { renderScreen(it) }
                            entry<AppScreen.ExerciseDetail>(swipeDismiss = swipeBackDirection) { renderScreen(it) }
                            entry<AppScreen.Workout>(swipeDismiss = NavSwipeDirection.None) { renderScreen(it) }
                            entry<AppScreen.AddWorkoutExercise>(swipeDismiss = swipeBackDirection) { renderScreen(it) }
                            entry<AppScreen.Day>(swipeDismiss = swipeBackDirection) { renderScreen(it) }
                            entry<AppScreen.MonthAnalytics>(swipeDismiss = swipeBackDirection) { renderScreen(it) }
                            entry<AppScreen.Appearance>(swipeDismiss = swipeBackDirection) { renderScreen(it) }
                            entry<AppScreen.About>(swipeDismiss = swipeBackDirection) { renderScreen(it) }
                        }
                    }
                    TopLayerHost(topLayer)
                }
                val operationError by vm.operationError.collectAsStateWithLifecycle()
                GlassDialog(
                    show = operationError != null,
                    backdrop = rootBackdrop,
                    title = "操作未完成",
                    summary = operationError,
                    onDismissRequest = vm::dismissOperationError,
                    largeScreen = state.settings.largeScreenDialogs,
                    material = rememberOs4CustomMaterial() ?: GlassDialogDefaults.material(),
                    stroke = rememberOs4EdgeStroke(GlassDialogDefaults.stroke()),
                ) {
                    val dismiss = LocalDismissState.current
                    GlassDialogButton(
                        onClick = { (dismiss ?: vm::dismissOperationError).invoke() },
                        modifier = Modifier.fillMaxWidth(),
                        role = GlassDialogButtonRole.Primary,
                    ) {
                        Text("知道了", style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTabs(
    vm: MainViewModel,
    selectedPage: Int,
    onSelectedPage: (Int) -> Unit,
    isMainTop: Boolean,
    onNavigate: (AppScreen) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = selectedPage, pageCount = { 4 })
    val mainPagerState = rememberMainPagerState(pager)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listStates = List(4) { rememberLazyListState() }
    val scrollBehaviors = listOf(
        MiuixScrollBehavior(rememberTopAppBarState()),
        MiuixScrollBehavior(rememberTopAppBarState()),
        MiuixScrollBehavior(rememberTopAppBarState()),
        MiuixScrollBehavior(rememberTopAppBarState()),
    )
    LaunchedEffect(pager.currentPage) {
        mainPagerState.syncPage()
        onSelectedPage(pager.currentPage)
    }
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isMainTop && mainPagerState.selectedPage != 0,
        onBackCompleted = { mainPagerState.animateToPage(0) },
    )
    // Each page receives only the fields it renders. Loading the exercise library and
    // history after startup must not invalidate and recompose the already visible home.
    val homeState = remember(state.plans, state.active, state.settings) {
        MainUiState(isReady = true, plans = state.plans, active = state.active, settings = state.settings)
    }
    val exerciseState = remember(state.exercises) { MainUiState(isReady = true, exercises = state.exercises) }
    val settingsState = remember(state.settings) { MainUiState(isReady = true, settings = state.settings) }
    val labels = listOf("运动", "日历", "动作库", "设置")
    val subtitles = listOf("今天也为自己积累一点进步", "按月回看你的训练轨迹", "离线动作库，随时可查", "让练迹更符合你的使用习惯")
    val icons = listOf(
        MiuixIcons.Glass.HomeOutline,
        MiuixIcons.Glass.Months,
        MiuixIcons.Glass.All,
        MiuixIcons.Glass.SettingsOutline,
    )
    val hasActiveWorkout = state.active != null
    val glassStyle = rememberOs4GlassStyle()
    val customMaterial = rememberOs4CustomMaterial()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val expandRail = maxWidth >= 840.dp
        val railState = rememberGlassNavigationRailState(initialExpanded = expandRail)
        LaunchedEffect(expandRail) { railState.expanded = expandRail }
        val showBottomShortcut = !wide && state.settings.navigationBarAction
        val showRailShortcuts = wide && railState.expanded
        LaunchedEffect(wide, railState.expanded) {
            mainPagerState.snapTransitions = wide && railState.expanded
        }
        val surfaceColor = MiuixTheme.colorScheme.surface
        val shaderSupported = isRuntimeShaderSupported()
        val railBackdrop = if (wide && shaderSupported) {
            rememberLayerBackdrop { drawRect(surfaceColor); drawContent() }
        } else {
            null
        }
        val backdrop = rememberLayerBackdrop { drawRect(surfaceColor); drawContent() }
        val railStartInset = remember(railState) {
            object : WindowInsets {
                private fun startPx(density: Density) = with(density) {
                    ((GlassNavigationRailDefaults.Margin + GlassNavigationRailDefaults.ExpandedWidth) * railState.progress).roundToPx()
                }
                override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int =
                    if (layoutDirection == LayoutDirection.Ltr) startPx(density) else 0
                override fun getTop(density: Density): Int = 0
                override fun getRight(density: Density, layoutDirection: LayoutDirection): Int =
                    if (layoutDirection == LayoutDirection.Rtl) startPx(density) else 0
                override fun getBottom(density: Density): Int = 0
            }
        }
        val navItems = remember(hasActiveWorkout) {
            labels.mapIndexed { index, label ->
                GlassNavigationItem(
                    icon = icons[index],
                    label = label,
                    badge = if (index == 0 && hasActiveWorkout) ({ Badge() }) else null,
                )
            }
        }
        val secondaryRailItems = remember {
            listOf(
                GlassNavigationItem(icon = MiuixIcons.Glass.Add, label = "创建计划"),
                GlassNavigationItem(icon = MiuixIcons.Glass.Edit, label = "新建动作"),
            )
        }
        val topBarButtonMaterial = customMaterial ?: GlassTopAppBarDefaults.buttonMaterial()
        val topBarButtonStroke = rememberOs4EdgeStroke(GlassTopAppBarDefaults.buttonStroke())
        val navBarMaterial = customMaterial ?: GlassNavigationBarDefaults.material()
        val navBarStroke = rememberOs4EdgeStroke(GlassNavigationBarDefaults.stroke())
        val railMaterial = customMaterial ?: GlassNavigationRailDefaults.material()
        val railStroke = rememberOs4EdgeStroke(GlassNavigationRailDefaults.stroke())

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (railBackdrop != null) Modifier.layerBackdrop(railBackdrop) else Modifier),
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        val activePage = mainPagerState.selectedPage
                        val activeListState = listStates[activePage]
                        val activeScrollBehavior = scrollBehaviors[activePage]
                        val scrollToTop = {
                            coroutineScope.launch {
                                activeListState.animateScrollToItem(0)
                                activeScrollBehavior.state.heightOffset = 0f
                                activeScrollBehavior.state.contentOffset = 0f
                            }
                        }
                        Box(Modifier.topAppBarDoubleTap { scrollToTop() }) {
                            GlassTopAppBar(
                                title = labels[activePage],
                                subtitle = subtitles[activePage],
                                expandable = !wide,
                                progressiveBlur = state.settings.progressiveBlur && shaderSupported,
                                backdrop = backdrop,
                                scrollBehavior = activeScrollBehavior,
                                style = glassStyle,
                                material = topBarButtonMaterial,
                                stroke = topBarButtonStroke,
                                contentModifier = if (wide) Modifier.windowInsetsPadding(railStartInset) else Modifier,
                                titleAlpha = if (wide) ({ railState.progress }) else ({ 1f }),
                                actions = {
                                    if (activePage == 0 && !showRailShortcuts && (!showBottomShortcut || hasActiveWorkout)) {
                                        TopBarIconButton(onClick = { onNavigate(AppScreen.NewPlan) }) {
                                            Icon(
                                                imageVector = MiuixIcons.Glass.Add,
                                                contentDescription = "创建计划",
                                                modifier = Modifier.size(28.dp),
                                                tint = MiuixTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                    if (activePage == 2 && !showRailShortcuts && !showBottomShortcut) {
                                        TopBarIconButton(onClick = { onNavigate(AppScreen.NewExercise) }) {
                                            Icon(
                                                imageVector = MiuixIcons.Glass.Add,
                                                contentDescription = "自定义动作",
                                                modifier = Modifier.size(28.dp),
                                                tint = MiuixTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    },
                    bottomBar = {
                        if (!wide) {
                            val page = mainPagerState.selectedPage
                            val trailingAction: (@Composable () -> Unit)? =
                                if (showBottomShortcut && page in 0..2) {
                                    {
                                        when (page) {
                                            0 -> if (homeState.active != null) {
                                                GlassIconButton(
                                                    onClick = { onNavigate(AppScreen.Workout(homeState.active.id)) },
                                                    backdrop = backdrop,
                                                    size = GlassNavigationBarDefaults.Height,
                                                    style = glassStyle,
                                                    material = topBarButtonMaterial,
                                                    stroke = topBarButtonStroke,
                                                    shadow = GlassShadows.Float,
                                                ) {
                                                    Icon(
                                                        imageVector = MiuixIcons.Glass.Play,
                                                        contentDescription = "继续当前训练",
                                                        modifier = Modifier.size(26.dp),
                                                        tint = MiuixTheme.colorScheme.onSurface,
                                                    )
                                                }
                                            } else {
                                                GlassIconButton(
                                                    onClick = { onNavigate(AppScreen.NewPlan) },
                                                    backdrop = backdrop,
                                                    size = GlassNavigationBarDefaults.Height,
                                                    style = glassStyle,
                                                    material = topBarButtonMaterial,
                                                    stroke = topBarButtonStroke,
                                                    shadow = GlassShadows.Float,
                                                ) {
                                                    Icon(
                                                        imageVector = MiuixIcons.Glass.Add,
                                                        contentDescription = "创建计划",
                                                        modifier = Modifier.size(28.dp),
                                                        tint = MiuixTheme.colorScheme.onSurface,
                                                    )
                                                }
                                            }

                                            1 -> GlassIconButton(
                                                onClick = { onNavigate(AppScreen.Day(LocalDate.now().toString())) },
                                                backdrop = backdrop,
                                                size = GlassNavigationBarDefaults.Height,
                                                style = glassStyle,
                                                material = topBarButtonMaterial,
                                                stroke = topBarButtonStroke,
                                                shadow = GlassShadows.Float,
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.Glass.Months,
                                                    contentDescription = "查看今日记录",
                                                    modifier = Modifier.size(26.dp),
                                                    tint = MiuixTheme.colorScheme.onSurface,
                                                )
                                            }

                                            else -> GlassIconButton(
                                                onClick = { onNavigate(AppScreen.NewExercise) },
                                                backdrop = backdrop,
                                                size = GlassNavigationBarDefaults.Height,
                                                style = glassStyle,
                                                material = topBarButtonMaterial,
                                                stroke = topBarButtonStroke,
                                                shadow = GlassShadows.Float,
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.Glass.Add,
                                                    contentDescription = "自定义动作",
                                                    modifier = Modifier.size(28.dp),
                                                    tint = MiuixTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    null
                                }
                            GlassNavigationBar(
                                items = navItems,
                                selectedIndex = page,
                                onSelect = { mainPagerState.animateToPage(it) },
                                backdrop = backdrop,
                                style = glassStyle,
                                material = navBarMaterial,
                                stroke = navBarStroke,
                                trailingAction = trailingAction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                            )
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { padding ->
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                        val flingBehavior = PagerDefaults.flingBehavior(state = pager, snapAnimationSpec = PagerNavigationSpringSpec)
                        HorizontalPager(
                            state = pager,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (wide) Modifier.windowInsetsPadding(railStartInset) else Modifier)
                                .pagerGestureOverride(
                                    pagerState = pager,
                                    flingBehavior = flingBehavior,
                                    enabled = state.settings.pagerGestureOverride,
                                ),
                            userScrollEnabled = !state.settings.pagerGestureOverride,
                            flingBehavior = flingBehavior,
                            pageNestedScrollConnection = if (state.settings.pagerGestureOverride) {
                                PagerGestureNestedScrollConnection
                            } else {
                                PagerDefaults.pageNestedScrollConnection(pager, Orientation.Horizontal)
                            },
                            verticalAlignment = Alignment.Top,
                        ) { page ->
                            when (page) {
                                0 -> WorkoutHomeScreen(
                                    homeState,
                                    padding,
                                    listStates[0],
                                    scrollBehaviors[0],
                                    onEdit = { onNavigate(AppScreen.EditPlan(it)) },
                                    onStart = { vm.start(it) { id -> onNavigate(AppScreen.Workout(id)) } },
                                    onContinue = { homeState.active?.let { onNavigate(AppScreen.Workout(it.id)) } },
                                    onDuplicate = vm::duplicatePlan,
                                    onDelete = vm::deletePlan,
                                    onSchedule = { id, date -> vm.scheduleWorkout(id, date) },
                                )
                                1 -> CalendarScreen(
                                    vm,
                                    padding,
                                    listStates[1],
                                    scrollBehaviors[1],
                                    onDay = { onNavigate(AppScreen.Day(it)) },
                                    onMonth = { onNavigate(AppScreen.MonthAnalytics(it.toString())) },
                                )
                                2 -> ExerciseLibraryScreen(
                                    exerciseState,
                                    padding,
                                    listStates[2],
                                    scrollBehaviors[2],
                                    { onNavigate(AppScreen.ExerciseDetail(it)) },
                                    vm::toggleFavorite,
                                )
                                else -> SettingsScreen(
                                    settingsState,
                                    padding,
                                    listStates[3],
                                    scrollBehaviors[3],
                                    vm,
                                    snackbarHostState,
                                    onAppearance = { onNavigate(AppScreen.Appearance) },
                                    onAbout = { onNavigate(AppScreen.About) },
                                )
                            }
                        }
                    }
                }
            }
            if (wide) {
                GlassNavigationRail(
                    items = navItems,
                    secondaryItems = secondaryRailItems,
                    selectedIndex = mainPagerState.selectedPage,
                    onSelect = { index ->
                        when (index) {
                            in navItems.indices -> mainPagerState.animateToPage(index)
                            navItems.size -> onNavigate(AppScreen.NewPlan)
                            navItems.size + 1 -> onNavigate(AppScreen.NewExercise)
                        }
                    },
                    backdrop = railBackdrop,
                    state = railState,
                    style = glassStyle,
                    material = railMaterial,
                    stroke = railStroke,
                    expandContentDescription = "展开导航栏",
                    collapseContentDescription = "收起导航栏",
                )
            }
        }
    }
}

/** Kept in sync with the MainPagerState shipped in the MIUIX 0.9.5-beta01 example app. */
@Stable
private class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set
    var isNavigating by mutableStateOf(false)
        private set
    var snapTransitions by mutableStateOf(false)
    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return
        navJob?.cancel()
        selectedPage = targetIndex
        isNavigating = true
        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                if (snapTransitions) {
                    pagerState.scrollToPage(targetIndex)
                } else {
                    pagerState.springAnimateToPage(targetIndex)
                }
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) selectedPage = pagerState.currentPage
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) selectedPage = pagerState.currentPage
    }
}

@Composable
private fun rememberMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
) = remember(pagerState, coroutineScope) { MainPagerState(pagerState, coroutineScope) }
