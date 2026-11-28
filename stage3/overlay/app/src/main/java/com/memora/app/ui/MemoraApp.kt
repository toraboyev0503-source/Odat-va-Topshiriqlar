package com.memora.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.memora.app.billing.EntitlementState
import com.memora.app.billing.SubscriptionPlan
import com.memora.app.data.local.NoteEntity
import com.memora.app.data.local.NoteType
import com.memora.app.data.local.ReminderEntity
import com.memora.app.data.local.TimeTracePhotoEntity
import com.memora.app.data.local.MemoryRecallEventEntity
import com.memora.app.data.model.NoteBlock
import com.memora.app.data.model.NoteBlocksCodec
import com.memora.app.data.repository.MemoraRepository
import com.memora.app.data.repository.TopilmaReviewSessionState
import com.memora.app.data.repository.TopilmalarRepository
import com.memora.app.export.ExportRequest
import com.memora.app.media.MediaStorage
import com.memora.app.performance.MemoraPerformance
import com.memora.app.notifications.AlarmScheduler
import com.memora.app.obsidian.ObsidianSyncManager
import com.memora.app.prefs.ThemeMode
import com.memora.app.prefs.UserPreferences
import com.memora.app.prefs.UserSettings
import com.memora.app.timetrace.TimeTraceStorage
import com.memora.app.ui.components.ExportDialog
import com.memora.app.ui.components.MemoraBottomBar
import com.memora.app.ui.components.NewNoteSheet
import com.memora.app.ui.components.RootTab
import com.memora.app.ui.screens.AboutScreen
import com.memora.app.ui.screens.CalendarHubScreen
import com.memora.app.ui.screens.DataBackupScreen
import com.memora.app.ui.screens.EditorScreen
import com.memora.app.ui.screens.HomeScreen
import com.memora.app.ui.screens.LanguageScreen
import com.memora.app.ui.screens.NotesHubScreen
import com.memora.app.ui.screens.ObsidianScreen
import com.memora.app.ui.screens.ReaderScreen
import com.memora.app.ui.screens.MemoryOccurrenceScreen
import com.memora.app.ui.screens.RemindersScreen
import com.memora.app.ui.screens.SecurityScreen
import com.memora.app.ui.screens.SettingsHubV2Screen
import com.memora.app.ui.screens.SubscriptionScreen
import com.memora.app.ui.screens.ThemeScreen
import com.memora.app.ui.screens.TimeTraceScreen
import com.memora.app.ui.screens.MemoryAlbumScreen
import com.memora.app.ui.screens.MemorySettingsScreen
import com.memora.app.ui.screens.TitlesScreen
import com.memora.app.ui.screens.TopilmalarScreen
import com.memora.app.ui.screens.TopilmaCommentsScreen
import com.memora.app.ui.screens.TopilmaIntegrationScreen
import com.memora.app.ui.theme.MemoraMotion
import java.time.LocalDate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val NOTES_HUB = "notes_hub/{filter}"
    const val CALENDAR_ROOT = "calendar_root"
    const val TOPILMALAR = "topilmalar/{tab}"
    const val TOPILMA_COMMENTS = "topilma_comments/{id}"
    const val SETTINGS_HUB = "settings_hub"
    const val EDITOR = "editor/{type}/{id}"
    const val READER = "reader/{id}"
    const val TREE_READER = "tree_reader/{eventId}"
    const val TITLES = "titles"
    const val REMINDERS = "reminders"
    const val LANGUAGE = "language"
    const val THEME = "theme"
    const val SECURITY = "security"
    const val OBSIDIAN = "obsidian"
    const val SUBSCRIPTION = "subscription"
    const val DATA_BACKUP = "data_backup"
    const val TIME_TRACE = "time_trace"
    const val ABOUT = "about"
    const val MEMORY_SETTINGS = "memory_settings"
    const val TOPILMA_INTEGRATION = "topilma_integration"

    fun notes(type: NoteType? = null) = "notes_hub/${type?.name ?: "ALL"}"
    fun editor(type: NoteType, id: String?) = "editor/${type.name}/${id ?: "new"}"
    fun reader(id: String) = "reader/$id"
    fun treeReader(id: String) = "tree_reader/"+android.net.Uri.encode(id)
    fun topilmalar(review: Boolean = false) = "topilmalar/${if (review) "REVIEW" else "SOURCES"}"
    fun topilmalarCreate() = "topilmalar/CREATE"
    fun topilmaComments(id: String) = "topilma_comments/$id"
}

@Composable
fun MemoraApp(
    repository: MemoraRepository,
    memoryTreeRepository: com.memora.app.data.repository.MemoryTreeRepository,
    topilmalarRepository: TopilmalarRepository,
    obsidianSync: ObsidianSyncManager,
    preferences: UserPreferences,
    settings: UserSettings,
    entitlement: EntitlementState,
    subscriptionPlans: List<SubscriptionPlan>,
    pendingEditorType: String?,
    onPendingEditorConsumed: () -> Unit,
    pendingOpenTimeTrace: Boolean,
    pendingOpenAlbumCamera: Boolean,
    onPendingAlbumCameraConsumed: () -> Unit,
    pendingMemoryNote: String?,
    onPendingMemoryNoteConsumed: () -> Unit,
    onPendingTimeTraceConsumed: () -> Unit,
    pendingOpenTopilmalarReview: Boolean,
    onPendingTopilmalarReviewConsumed: () -> Unit,
    onExport: (ExportRequest) -> Unit,
    onImport: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestExactTiming: () -> Unit,
    onPurchasePlan: (String) -> Unit,
    onRefreshSubscription: () -> Unit,
    onManageSubscription: () -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val mediaStorage = remember(context) { MediaStorage(context.applicationContext) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showNewNoteSheet by remember { mutableStateOf(false) }
    var treeArrival by remember { mutableStateOf<com.memora.app.ui.components.TreeArrival?>(null) }
    var selectedTreeSlot by remember { mutableStateOf(0) }
    var selectedTreeKind by remember { mutableStateOf("SHORT") }
    val backOne: () -> Unit = { navController.popBackStack() }

    fun openSingle(route: String) { navController.navigate(route) { launchSingleTop = true } }
    fun openRoot(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun openEditor(type: NoteType) {
        showNewNoteSheet = false
        if (entitlement.canWrite) navController.navigate(Routes.editor(type, null))
        else openSingle(Routes.SUBSCRIPTION)
    }

    suspend fun deleteNoteAndMedia(note: NoteEntity) {
        repository.deleteNotes(listOf(note.id))
        NoteBlocksCodec.decode(note.blocksJson, note.plainText).forEach { block ->
            when (block) {
                is NoteBlock.Image -> mediaStorage.delete(block.relativePath)
                is NoteBlock.Audio -> mediaStorage.delete(block.relativePath)
                is NoteBlock.Text -> Unit
            }
        }
    }

    // Screen-specific collectors live inside their destinations; the root observes routes only.
    // No full album/title/reminder list invalidates the entire navigation tree.
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val visibleBackStackEntries by navController.visibleEntries.collectAsStateWithLifecycle()
    val currentRoute = currentBackStackEntry?.destination?.route
    LaunchedEffect(currentRoute) {
        // Static route names only: note titles and IDs never enter diagnostics.
        val section = when (currentRoute) {
            Routes.HOME -> "home"
            Routes.NOTES_HUB -> "notes"
            Routes.CALENDAR_ROOT -> "calendar"
            Routes.SETTINGS_HUB -> "settings"
            Routes.EDITOR -> "editor"
            Routes.READER -> "reader"
            Routes.TIME_TRACE -> "album"
            else -> "detail"
        }
        MemoraPerformance.screen(section)
    }
    val currentTopilmalarMode = currentBackStackEntry?.arguments?.getString("tab")
    val rootTab = when (currentRoute) {
        Routes.HOME -> RootTab.HOME
        Routes.NOTES_HUB -> RootTab.NOTES
        Routes.CALENDAR_ROOT -> RootTab.CALENDAR
        Routes.SETTINGS_HUB -> RootTab.SETTINGS
        else -> null
    }

    // Back always closes the most local layer first. Route popping is disabled while an app-level
    // sheet/dialog is open so one Back press can never jump past it to Home.
    BackHandler(enabled = showNewNoteSheet || showExportDialog) {
        when {
            showNewNoteSheet -> showNewNoteSheet = false
            showExportDialog -> showExportDialog = false
        }
    }
    BackHandler(enabled = currentRoute != Routes.HOME && currentRoute != Routes.TREE_READER && !showNewNoteSheet && !showExportDialog) {
        navController.popBackStack()
    }

    LaunchedEffect(pendingEditorType, entitlement.canWrite) {
        val raw = pendingEditorType ?: return@LaunchedEffect
        val type = runCatching { NoteType.valueOf(raw) }.getOrNull() ?: return@LaunchedEffect
        val destination = if (entitlement.canWrite) Routes.editor(type, null) else Routes.SUBSCRIPTION
        navController.navigate(destination) { launchSingleTop = true }
        onPendingEditorConsumed()
    }

    LaunchedEffect(pendingMemoryNote) {
        val id = pendingMemoryNote ?: return@LaunchedEffect
        if(id.startsWith("tree-event:")) {
            val occurrence=id.removePrefix("tree-event:")
            if(memoryTreeRepository.getEvent(occurrence)!=null) {
                navController.navigate(Routes.treeReader(occurrence)) {
                    popUpTo(Routes.HOME) { inclusive=false }
                    launchSingleTop=true
                }
            }
        } else {
            val occurrence=memoryTreeRepository.currentPendingForNote(id)
            if(occurrence!=null) {
                navController.navigate(Routes.treeReader(occurrence.id)) {
                    popUpTo(Routes.HOME) { inclusive=false }
                    launchSingleTop=true
                }
            } else navController.navigate(Routes.reader(id)) { launchSingleTop = true }
        }
        onPendingMemoryNoteConsumed()
    }

    LaunchedEffect(pendingOpenAlbumCamera, entitlement.canWrite) {
        if (!pendingOpenAlbumCamera) return@LaunchedEffect
        if (entitlement.canWrite) navController.navigate(Routes.TIME_TRACE) { launchSingleTop = true }
        else navController.navigate(Routes.SUBSCRIPTION) { launchSingleTop = true }
        // Camera opens when the album composable sees the pending input.
    }

    LaunchedEffect(pendingOpenTimeTrace, entitlement.canWrite) {
        if (!pendingOpenTimeTrace) return@LaunchedEffect
        navController.navigate(if (entitlement.canWrite) Routes.TIME_TRACE else Routes.SUBSCRIPTION) { launchSingleTop = true }
        onPendingTimeTraceConsumed()
    }

    LaunchedEffect(pendingOpenTopilmalarReview) {
        if (!pendingOpenTopilmalarReview) return@LaunchedEffect
        navController.navigate(Routes.HOME) { launchSingleTop = true }
        onPendingTopilmalarReviewConsumed()
    }

    // Bottom chrome is only shown after detail transitions have finished. In particular,
    // it must not reappear over a reader/editor that is still animating off screen.
    val detailStillVisible = visibleBackStackEntries.any { !it.isMemoraPrimaryDestination() }

    val deviceDensity = LocalDensity.current
    val compactDensity = Density(
        density = deviceDensity.density * 0.80f * settings.uiScale,
        fontScale = deviceDensity.fontScale
    )

    CompositionLocalProvider(
        LocalDensity provides compactDensity,
        LocalLayoutDirection provides if (settings.language.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        // Keep the NavHost at a stable, full-size viewport for the entire navigation.
        // Each root destination reserves its own 82 dp for the bottom bar; detail screens do not.
        // A changing NavHost height during transitions was shifting two screen contents at once.
        val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val showBottomNavigation = QuietNavigationPolicy.showBottomBar(
            rootSelected = rootTab != null,
            keyboardVisible = keyboardVisible,
            detailStillVisible = detailStillVisible
        )
        val direction = if (settings.language.rtl) -1 else 1
        val travelPx = with(LocalDensity.current) { QuietNavigationPolicy.DETAIL_TRAVEL_DP.dp.roundToPx() } * direction
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.fillMaxSize(),
                    // No crossfade: the target is fully opaque and covers the previous page.
                    // Main tabs are instant. Detail pages enter from 12 dp away in 170 ms.
                    enterTransition = {
                        if (initialState.isMemoraPrimaryDestination() && targetState.isMemoraPrimaryDestination()) {
                            EnterTransition.None
                        } else {
                            slideInHorizontally(
                                animationSpec = tween(QuietNavigationPolicy.DETAIL_DURATION_MS, easing = MemoraMotion.EmphasizedDecelerate),
                                initialOffsetX = { travelPx }
                            )
                        }
                    },
                    // The previous page remains stationary under the incoming opaque page.
                    exitTransition = { ExitTransition.None },
                    // During Back, the previous page stays still beneath the exiting page.
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = {
                        if (initialState.isMemoraPrimaryDestination() && targetState.isMemoraPrimaryDestination()) {
                            ExitTransition.None
                        } else {
                            slideOutHorizontally(
                                animationSpec = tween(QuietNavigationPolicy.DETAIL_DURATION_MS, easing = MemoraMotion.EmphasizedDecelerate),
                                targetOffsetX = { travelPx }
                            )
                        }
                    }
                ) {
                    composable(Routes.HOME) {
                        MemoraNavigationPage(isRootTab = true, reserveBottomSpace = !keyboardVisible) {
                            val allShort by remember(repository) { repository.observeCount(NoteType.SHORT).distinctUntilChanged() }
                                .collectAsStateWithLifecycle(0)
                            val allLong by remember(repository) { repository.observeCount(NoteType.LONG).distinctUntilChanged() }
                                .collectAsStateWithLifecycle(0)
                            val albumPhotos by remember(repository) { repository.observeMemoryAlbum() }
                                .collectAsStateWithLifecycle(emptyList())
                            val treeCounts by remember(memoryTreeRepository) {
                                memoryTreeRepository.observeVisualCounts().distinctUntilChanged()
                            }.collectAsStateWithLifecycle(initialValue = null)
                            val treeLeaves by remember(memoryTreeRepository) {
                                memoryTreeRepository.observeVisualLeaves().distinctUntilChanged()
                            }.collectAsStateWithLifecycle(initialValue = null)
                            val todayKey = remember { TimeTraceStorage.todayKey() }
                            HomeScreen(
                                language = settings.language,
                                shortCount = allShort,
                                longCount = allLong,
                                isDark = isDark,
                                uiScale = settings.uiScale,
                                onToggleDark = { scope.launch { preferences.setThemeMode(if (isDark) ThemeMode.LIGHT else ThemeMode.DARK) } },
                                onShort = { openRoot(Routes.notes(NoteType.SHORT)) },
                                onLong = { openRoot(Routes.notes(NoteType.LONG)) },
                                timeTraceTodaySaved = albumPhotos.any { com.memora.app.timetrace.TimeTraceStorage.dateKey(it.capturedAt) == todayKey },
                                timeTraceStreak = albumPhotos.size,
                                onTimeTrace = {
                                    if (entitlement.canWrite) openSingle(Routes.TIME_TRACE) else openSingle(Routes.SUBSCRIPTION)
                                },
                                treeCounts = treeCounts,
                                treeLeaves = treeLeaves,
                                onTreeLeaf = { leaf ->
                                    selectedTreeSlot=treeLeaves.orEmpty().indexOfFirst { it.id==leaf.id }.coerceIn(0,4)
                                    selectedTreeKind=leaf.kind
                                    openSingle(Routes.treeReader(leaf.id))
                                },
                                treeArrival=treeArrival,
                                onTreeArrivalFinished={ id -> if(treeArrival?.eventId==id) treeArrival=null }
                            )
                        }
                    }

                    composable(
                        Routes.NOTES_HUB,
                        arguments = listOf(navArgument("filter") { type = NavType.StringType })
                    ) { entry ->
                        MemoraNavigationPage(isRootTab = true, reserveBottomSpace = !keyboardVisible) {
                            val filter = entry.arguments?.getString("filter")
                            val initialType = runCatching { filter?.takeUnless { it == "ALL" }?.let(NoteType::valueOf) }.getOrNull()
                            NotesHubScreen(
                                repository = repository,
                                language = settings.language,
                                initialType = initialType,
                                canEdit = entitlement.canWrite,
                                onOpen = { navController.navigate(Routes.reader(it)) },
                                onEdit = { note ->
                                    if (entitlement.canWrite) {
                                        navController.navigate(Routes.editor(NoteType.valueOf(note.type), note.id))
                                    } else openSingle(Routes.SUBSCRIPTION)
                                },
                                onDelete = { note ->
                                    if (entitlement.canWrite) scope.launch {
                                        // List items contain a deliberately truncated preview. Load the
                                        // original before deleting, so all media references are cleaned.
                                        repository.getNote(note.id)?.let { deleteNoteAndMedia(it) }
                                    } else openSingle(Routes.SUBSCRIPTION)
                                }
                            )
                        }
                    }

                    composable(Routes.CALENDAR_ROOT) {
                        MemoraNavigationPage(isRootTab = true, reserveBottomSpace = !keyboardVisible) {
                            val calendarSummaries by remember(repository) {
                                repository.observeNoteSummaries()
                            }.collectAsStateWithLifecycle(emptyList())
                            CalendarHubScreen(
                                notes = calendarSummaries,
                                language = settings.language,
                                onOpenNote = { navController.navigate(Routes.reader(it)) }
                            )
                        }
                    }

                    composable(Routes.SETTINGS_HUB) {
                        MemoraNavigationPage(isRootTab = true, reserveBottomSpace = !keyboardVisible) {
                            SettingsHubV2Screen(
                                settings = settings,
                                onReminders = { openSingle(Routes.MEMORY_SETTINGS) },
                                onTitles = { openSingle(Routes.TITLES) },
                                onCalendar = { openSingle(Routes.CALENDAR_ROOT) },
                                onTheme = { openSingle(Routes.THEME) },
                                onLanguage = { openSingle(Routes.LANGUAGE) },
                                onData = { openSingle(Routes.DATA_BACKUP) },
                                onSecurity = { openSingle(Routes.SECURITY) },
                                onObsidian = { openSingle(Routes.OBSIDIAN) },
                                onTopilmaIntegration = { openSingle(Routes.TOPILMA_INTEGRATION) },
                                onSubscription = { openSingle(Routes.SUBSCRIPTION) },
                                onAbout = { openSingle(Routes.ABOUT) }
                            )
                        }
                    }

                    composable(Routes.OBSIDIAN) {
                        MemoraNavigationPage(isRootTab = false, reserveBottomSpace = !keyboardVisible) {
                            ObsidianScreen(
                                settings = settings,
                                manager = obsidianSync,
                                onBack = backOne
                            )
                        }
                    }

                    composable(Routes.TOPILMA_INTEGRATION) {
                        MemoraNavigationPage(isRootTab = false, reserveBottomSpace = !keyboardVisible) {
                            TopilmaIntegrationScreen(onBack = backOne)
                        }
                    }

                    composable(Routes.ABOUT) {
                        MemoraNavigationPage(isRootTab = false, reserveBottomSpace = !keyboardVisible) {
                            AboutScreen(language = settings.language, onBack = backOne)
                        }
                    }

                    composable(Routes.TIME_TRACE) {
                        MemoraNavigationPage(isRootTab = false, reserveBottomSpace = !keyboardVisible) {
                            val albumPhotos by remember(repository) { repository.observeMemoryAlbum() }
                                .collectAsStateWithLifecycle(emptyList())
                            MemoryAlbumScreen(
                                photos = albumPhotos,
                                language = settings.language,
                                repository = repository,
                                canEdit = entitlement.canWrite,
                                initialCamera = pendingOpenAlbumCamera,
                                onBack = {
                                    onPendingAlbumCameraConsumed()
                                    backOne()
                                }
                            )
                            if (pendingOpenAlbumCamera) {
                                LaunchedEffect(Unit) { onPendingAlbumCameraConsumed() }
                            }
                        }
                    }

                    composable(Routes.MEMORY_SETTINGS) {
                        MemoraNavigationPage(isRootTab = false, reserveBottomSpace = !keyboardVisible) {
                            MemorySettingsScreen(settings, preferences, backOne,
                                onRequestNotifications, onRequestExactTiming)
                        }
                    }

                    composable(Routes.DATA_BACKUP) {
                        MemoraNavigationPage(isRootTab = false, reserveBottomSpace = !keyboardVisible) {
                            DataBackupScreen(
                                language = settings.language,
                                onBack = backOne,
                                onExport = { showExportDialog = true },
                                onImport = {
                                    if (entitlement.canWrite) onImport() else openSingle(Routes.SUBSCRIPTION)
                                },
                                onTopilmalarHtml = { includeComments ->
                                    onExport(
                                        ExportRequest(
                                            format = com.memora.app.export.ExportFormat.MULTIMEDIA,
                                            period = com.memora.app.export.ExportPeriod.ALL,
                                            target = com.memora.app.export.ExportTarget.TOPILMALAR,
                                            includeComments = includeComments
                                        )
                                    )
                                },
                                onTopilmalarWord = { includeComments ->
                                    onExport(
                                        ExportRequest(
                                            format = com.memora.app.export.ExportFormat.WORD,
                                            period = com.memora.app.export.ExportPeriod.ALL,
                                            target = com.memora.app.export.ExportTarget.TOPILMALAR,
                                            includeComments = includeComments
                                        )
                                    )
                                }
                            )
                        }
                    }

                    composable(
                        route = Routes.EDITOR,
                        arguments = listOf(
                            navArgument("type") { type = NavType.StringType },
                            navArgument("id") { type = NavType.StringType }
                        )
                    ) { entry ->
                        MemoraNavigationPage(isRootTab = false, reserveBottomSpace = false, fullCanvas = true) {
                            if (!entitlement.canWrite) {
                                LaunchedEffect(Unit) {
                                    navController.popBackStack()
                                    navController.navigate(Routes.SUBSCRIPTION) { launchSingleTop = true }
                                }
                                return@MemoraNavigationPage
                            }
                            val titles by remember(repository) { repository.observeTitles() }
                                .collectAsStateWithLifecycle(emptyList())
                            val type = NoteType.valueOf(entry.arguments?.getString("type") ?: NoteType.SHORT.name)
                            val idArg = entry.arguments?.getString("id")
                            val compact = LocalDensity.current
                            CompositionLocalProvider(LocalDensity provides Density(compact.density * 1.125f, compact.fontScale)) {
                                EditorScreen(
                                    noteId = idArg?.takeUnless { it == "new" },
                                    type = type,
                                    repository = repository,
                                    savedTitles = titles,
                                    language = settings.language,
                                    writingFont = settings.writingFont,
                                    uiScale = settings.uiScale,
                                    onBack = backOne
                                )
                            }
                        }
                    }

                    composable(Routes.TREE_READER, arguments = listOf(navArgument("eventId") { type=NavType.StringType })) { entry ->
                        MemoraNavigationPage(isRootTab=false,reserveBottomSpace=false,fullCanvas=true) {
                            MemoryOccurrenceScreen(
                                eventId=entry.arguments?.getString("eventId").orEmpty(),
                                tree=memoryTreeRepository, repository=repository,
                                language=settings.language, writingFont=settings.writingFont,
                                onBack=backOne,
                                onCompleted={ event ->
                                    treeArrival=com.memora.app.ui.components.TreeArrival(
                                        event.id,if(event.sourceKey.startsWith("PHOTO:")) "PHOTO" else selectedTreeKind,
                                        selectedTreeSlot)
                                }
                            )
                        }
                    }

                    composable(Routes.READER, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                        MemoraNavigationPage(isRootTab = false, reserveBottomSpace = false, fullCanvas = true) {
                            val id = entry.arguments?.getString("id").orEmpty()
                            val note by repository.observeNote(id).collectAsStateWithLifecycle(initialValue = null)
                            ReaderScreen(
                                note = note,
                                repository = repository,
                                language = settings.language,
                                writingFont = settings.writingFont,
                                canEdit = entitlement.canWrite,
                                onBack = backOne,
                                onEdit = { note?.let { navController.navigate(Routes.editor(NoteType.valueOf(it.type), it.id)) } },
                                onDelete = {
                                    note?.let { current ->
                                        scope.launch {
                                            deleteNoteAndMedia(current)
                                            navController.popBackStack()
                                        }
                                    }
                                },
                                onWriteRequired = { openSingle(Routes.SUBSCRIPTION) }

                            )
                        }
                    }

                    composable(Routes.TITLES) {
                        MemoraNavigationPage(isRootTab = false, reserveBottomSpace = !keyboardVisible) {
                            val titles by remember(repository) { repository.observeTitles() }
                                .collectAsStateWithLifecycle(emptyList())
                            TitlesScreen(
                                titles = titles,
                                language = settings.language,
                                onBack = backOne,
                                onAdd = { value -> scope.launch { repository.addTitle(value) } },
                                onDelete = { title -> scope.launch { repository.deleteTitle(title) } },
                                onEdit = { title, value, _ -> scope.launch { repository.renameTitle(title, value, false) } },
                                onPin = { title, pinned -> scope.launch { repository.setTitlePinned(title, pinned) } }
                            )
                        }
                    }

                    composable(Routes.REMINDERS) {
                        MemoraNavigationPage(isRootTab = false, reserveBottomSpace = !keyboardVisible) {
                            val reminders by remember(repository) { repository.observeReminders() }
                                .collectAsStateWithLifecycle(emptyList())
                            val local = LocalContext.current
                            RemindersScreen(
                                reminders = reminders,
                                language = settings.language,
                                exactTimingAvailable = AlarmScheduler(local).canScheduleExact(),
                                onBack = backOne,
                                onRequestNotifications = onRequestNotifications,
                                onRequestExactTiming = onRequestExactTiming,
                                onAdd = { hour, minute ->
                                    scope.launch {
                                        val id = repository.addReminder(hour, minute)
                                        if (id != -1L) AlarmScheduler(local).scheduleReminder(ReminderEntity(id = id, hour = hour, minute = minute, enabled = true))
                                    }
                                },
                                onToggle = { reminder, enabled ->
                                    scope.launch {
                                        val changed = reminder.copy(enabled = enabled)
                                        repository.updateReminder(changed)
                                        if (enabled) AlarmScheduler(local).scheduleReminder(changed) else AlarmScheduler(local).cancelReminder(changed.id)
                                    }
                                },
                                onDelete = { reminder -> scope.launch { AlarmScheduler(local).cancelReminder(reminder.id); repository.deleteReminder(reminder) } },
                                monthlySummaryEnabled = settings.monthlySummaryEnabled,
                                yearlySummaryEnabled = settings.yearlySummaryEnabled,
                                onMonthlySummary = { scope.launch { preferences.setMonthlySummaryEnabled(it) } },
                                onYearlySummary = { scope.launch { preferences.setYearlySummaryEnabled(it) } }
                            )
                        }
                    }

                    composable(Routes.LANGUAGE) {
                        MemoraNavigationPage(isRootTab = false, reserveBottomSpace = !keyboardVisible) {
                            LanguageScreen(current = settings.language, onBack = backOne, onSelect = { scope.launch { preferences.setLanguage(it) } })
                        }
                    }
                    composable(Routes.THEME) {
                        MemoraNavigationPage(isRootTab = false, reserveBottomSpace = !keyboardVisible) {
                            ThemeScreen(
                                settings = settings,
                                onBack = backOne,
                                onPalette = { scope.launch { preferences.setPalette(it) } },
                                onThemeMode = { scope.launch { preferences.setThemeMode(it) } },
                                onUiFont = { scope.launch { preferences.setUiFont(it) } },
                                onWritingFont = { scope.launch { preferences.setWritingFont(it) } },
                                onUiScale = { scope.launch { preferences.setUiScale(it) } }
                            )
                        }
                    }
                    composable(Routes.SUBSCRIPTION) {
                        MemoraNavigationPage(isRootTab = false, reserveBottomSpace = !keyboardVisible) {
                            SubscriptionScreen(
                                language = settings.language,
                                entitlement = entitlement,
                                plans = subscriptionPlans,
                                onBack = backOne,
                                onSubscribe = onPurchasePlan,
                                onRestore = onRefreshSubscription,
                                onManage = onManageSubscription
                            )
                        }
                    }
                    composable(Routes.SECURITY) {
                        MemoraNavigationPage(isRootTab = false, reserveBottomSpace = !keyboardVisible) {
                            SecurityScreen(
                                settings = settings,
                                onBack = backOne,
                                onEnabled = { scope.launch { preferences.setLockEnabled(it) } },
                                onInterval = { scope.launch { preferences.setLockInterval(it) } }
                            )
                        }
                    }
                }

                if (showBottomNavigation) {
                    val inherited = LocalDensity.current
                    val safe = settings.uiScale.coerceIn(.8f, 1.5f)
                    val chrome = safe.coerceAtMost(1.2f)
                    CompositionLocalProvider(LocalDensity provides Density(inherited.density * (chrome / safe), inherited.fontScale)) {
                        MemoraBottomBar(
                            language = settings.language,
                            selected = rootTab ?: RootTab.HOME,
                            onHome = { openRoot(Routes.HOME) },
                            onNotes = { openRoot(Routes.notes()) },
                            onAdd = { showNewNoteSheet = true },
                            onCalendar = { openRoot(Routes.CALENDAR_ROOT) },
                            onSettings = { openRoot(Routes.SETTINGS_HUB) },
                            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
                        )
                    }
                }
            }
        }

        if (showExportDialog) {
            ExportDialog(language = settings.language, onDismiss = { showExportDialog = false }, onExport = { showExportDialog = false; onExport(it) })
        }
        if (showNewNoteSheet) {
            NewNoteSheet(
                language = settings.language,
                onDismiss = { showNewNoteSheet = false },
                onShort = { openEditor(NoteType.SHORT) },
                onAlbum = {
                    showNewNoteSheet = false
                    if (entitlement.canWrite) navController.navigate(Routes.TIME_TRACE)
                    else openSingle(Routes.SUBSCRIPTION)
                },
                onLong = { openEditor(NoteType.LONG) }
            )
        }
    }
}


/**
 * Opaque, stable-size transition target. Every destination gets its own themed canvas.
 * Root tabs reserve space inside their canvas, never by resizing the NavHost mid-transition.
 */
@Composable
private fun MemoraNavigationPage(
    isRootTab: Boolean,
    reserveBottomSpace: Boolean,
    fullCanvas: Boolean = false,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .then(if (fullCanvas) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
                .then(if (isRootTab && reserveBottomSpace) Modifier.padding(bottom = 82.dp) else Modifier)
        ) {
            content()
        }
    }
}

private fun NavBackStackEntry.isMemoraPrimaryDestination(): Boolean =
    QuietNavigationPolicy.isPrimaryDestination(destination.route, arguments?.getString("tab"))

private fun traceStreak(photos: List<TimeTracePhotoEntity>): Int {
    if (photos.isEmpty()) return 0
    val days = photos.mapNotNull { runCatching { TimeTraceStorage.parseDate(it.dateKey) }.getOrNull() }.toSet()
    var cursor = LocalDate.now()
    if (cursor !in days) cursor = cursor.minusDays(1)
    var count = 0
    while (cursor in days) { count++; cursor = cursor.minusDays(1) }
    return count
}
