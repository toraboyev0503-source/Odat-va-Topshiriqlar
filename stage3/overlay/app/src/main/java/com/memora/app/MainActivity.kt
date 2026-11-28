package com.memora.app

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.memora.app.export.ExportFormat
import com.memora.app.export.ExportManager
import com.memora.app.export.ExportRequest
import com.memora.app.export.ImportPreview
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.notifications.AlarmScheduler
import com.memora.app.prefs.LockInterval
import com.memora.app.prefs.UserSettings
import com.memora.app.ui.MemoraApp
import com.memora.app.ui.components.ImportPreviewDialog
import com.memora.app.ui.components.MemoraFeedbackHost
import com.memora.app.ui.components.MemoraFeedbackKind
import com.memora.app.ui.components.MemoraFeedbackMessage
import com.memora.app.ui.screens.BrandLaunchScreen
import com.memora.app.ui.screens.LockedScreen
import com.memora.app.ui.theme.MemoraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.memora.app.billing.SubscriptionCatalog
import com.memora.app.performance.MemoraPerformance

class MainActivity : FragmentActivity() {

    private lateinit var app: MemoraApplication
    private lateinit var exportManager: ExportManager
    private lateinit var folderLauncher: ActivityResultLauncher<Uri?>
    private lateinit var backupLauncher: ActivityResultLauncher<String>
    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    private var pendingExportRequest: ExportRequest? = null
    private val pendingEditorType = mutableStateOf<String?>(null)
    private val pendingOpenTimeTrace = mutableStateOf(false)
    private val pendingOpenAlbumCamera = mutableStateOf(false)
    private val pendingMemoryNote = mutableStateOf<String?>(null)
    private val pendingOpenTopilmalarReview = mutableStateOf(false)
    private val pendingImportUri = mutableStateOf<Uri?>(null)
    private val pendingImportPreview = mutableStateOf<ImportPreview?>(null)
    private val feedback = mutableStateOf<MemoraFeedbackMessage?>(null)
    private val unlocked = mutableStateOf(false)
    private var brandSplashFinished = false
    private var backgroundAt: Long? = null
    private var authenticating = false
    private val lockRuntimePrefs by lazy {
        getSharedPreferences("memora_lock_runtime", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { provider -> provider.remove() }
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        MemoraPerformance.attach(window)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        unlocked.value = sessionUnlocked
        backgroundAt = sessionBackgroundAt

        app = application as MemoraApplication
        exportManager = ExportManager(this, contentResolver, app.repository, app.topilmalarRepository, app.preferences)
        registerLaunchers()
        handleIntent(intent)

        setContent {
            val settings by app.preferences.settings.collectAsStateWithLifecycle(initialValue = UserSettings())
            val entitlement by app.subscriptions.entitlement.collectAsStateWithLifecycle()
            val subscriptionPlans by app.subscriptions.plans.collectAsStateWithLifecycle()
            MemoraTheme(settings) { palette, _ ->
                var showBrandSplash by remember { mutableStateOf(true) }
                var brandSystemBarsActive by remember { mutableStateOf(true) }
                val view = LocalView.current
                val useDarkSystemIcons = brandSystemBarsActive || palette.background.luminance() > 0.5f

                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = useDarkSystemIcons
                        isAppearanceLightNavigationBars = useDarkSystemIcons
                    }
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = if (brandSystemBarsActive) {
                        ContextCompat.getColor(this@MainActivity, R.color.memora_launch_bottom_bg)
                    } else {
                        android.graphics.Color.TRANSPARENT
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                }

                LaunchedEffect(Unit) {
                    // Give each brand beat enough time to register: ivory pause -> pages ->
                    // bookmark -> wordmark -> tagline -> breathing hold -> Home backdrop.
                    delay(2450)
                    showBrandSplash = false
                    // Let the gentler final cross-fade complete before biometric/device-credential
                    // UI can appear on top of the launch experience.
                    delay(340)
                    brandSystemBarsActive = false
                    brandSplashFinished = true
                    authenticateIfNeeded(force = false)
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Render the real first screen underneath the branded overlay. The final fade
                    // therefore reveals Home naturally instead of swapping to a new frame.
                    if (!settings.lockEnabled || unlocked.value) {
                        MemoraApp(
                            repository = app.repository,
                            memoryTreeRepository = app.memoryTreeRepository,
                            topilmalarRepository = app.topilmalarRepository,
                            obsidianSync = app.obsidianSync,
                            preferences = app.preferences,
                            settings = settings,
                            entitlement = entitlement,
                            subscriptionPlans = subscriptionPlans,
                            pendingEditorType = if (brandSystemBarsActive) null else pendingEditorType.value,
                            onPendingEditorConsumed = { pendingEditorType.value = null },
                            pendingOpenTimeTrace = if (brandSystemBarsActive) false else pendingOpenTimeTrace.value,
                            pendingOpenAlbumCamera = if (brandSystemBarsActive) false else pendingOpenAlbumCamera.value,
                            onPendingAlbumCameraConsumed = { pendingOpenAlbumCamera.value = false },
                            pendingMemoryNote = if (brandSystemBarsActive) null else pendingMemoryNote.value,
                            onPendingMemoryNoteConsumed = { pendingMemoryNote.value = null },
                            onPendingTimeTraceConsumed = { pendingOpenTimeTrace.value = false },
                            pendingOpenTopilmalarReview = if (brandSystemBarsActive) false else pendingOpenTopilmalarReview.value,
                            onPendingTopilmalarReviewConsumed = { pendingOpenTopilmalarReview.value = false },
                            onExport = ::beginExport,
                            onImport = { importLauncher.launch(arrayOf("*/*")) },
                            onRequestNotifications = ::requestNotificationPermission,
                            onRequestExactTiming = ::requestExactAlarmAccess,
                            onPurchasePlan = { app.subscriptions.launchPurchase(this@MainActivity, it) },
                            onRefreshSubscription = app.subscriptions::refresh,
                            onManageSubscription = ::openSubscriptionManagement
                        )
                    } else {
                        LockedScreen(
                            language = settings.language,
                            onUnlock = { authenticateIfNeeded(force = true) }
                        )
                    }

                    AnimatedVisibility(
                        visible = showBrandSplash,
                        exit = fadeOut(animationSpec = tween(durationMillis = 340)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        BrandLaunchScreen(language = settings.language)
                    }

                    pendingImportPreview.value?.let { preview ->
                        if (!showBrandSplash) {
                            ImportPreviewDialog(
                                preview = preview,
                                language = settings.language,
                                onConfirm = {
                                    val uri = pendingImportUri.value
                                    pendingImportPreview.value = null
                                    if (uri != null) performImport(uri)
                                },
                                onDismiss = {
                                    pendingImportPreview.value = null
                                    pendingImportUri.value = null
                                }
                            )
                        }
                    }
                    if (!showBrandSplash) {
                        MemoraFeedbackHost(
                            message = feedback.value,
                            onDismiss = { feedback.value = null },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 20.dp, vertical = 24.dp)
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        MemoraPerformance.setForeground(true)
        if (brandSplashFinished) {
            authenticateIfNeeded(force = false)
        }
        if (::app.isInitialized) {
            app.subscriptions.onAppForeground()
            lifecycleScope.launch(Dispatchers.IO) {
                val scheduler = AlarmScheduler(this@MainActivity)
                app.repository.enabledReminders().forEach(scheduler::scheduleReminder)
            }
        }
    }

    override fun onStop() {
        MemoraPerformance.setForeground(false)
        MemoraPerformance.snapshot(applicationContext, "activity_stop")
        if (!isChangingConfigurations && !authenticating) {
            val now = System.currentTimeMillis()
            backgroundAt = now
            sessionBackgroundAt = now
            sessionUnlocked = unlocked.value
            lockRuntimePrefs.edit().putLong(KEY_LAST_BACKGROUND_AT, now).commit()
        }
        super.onStop()
    }

    override fun onDestroy() {
        MemoraPerformance.detach()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun registerLaunchers() {
        folderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) finishFolderExport(uri)
        }

        backupLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri != null) finishExport(uri)
        }

        importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            pendingImportUri.value = uri
            lifecycleScope.launch {
                val language = app.preferences.settings.first().language
                feedback.value = MemoraFeedbackMessage(
                    text = if (language == com.memora.app.prefs.AppLanguage.UZ) "Zaxira tekshirilmoqda…" else "Checking backup…",
                    kind = MemoraFeedbackKind.LOADING
                )
            }
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { exportManager.previewBackup(uri) }
                    .onSuccess { preview ->
                        withContext(Dispatchers.Main) { pendingImportPreview.value = preview; feedback.value = null }
                    }
                    .onFailure {
                        val language = app.preferences.settings.first().language
                        withContext(Dispatchers.Main) {
                            pendingImportUri.value = null
                            feedback.value = MemoraFeedbackMessage(text = L.t(language, S.IMPORT_FAILED), kind = MemoraFeedbackKind.ERROR)
                        }
                    }
            }
        }

        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* The reminder screen stays usable even if permission is denied. */ }
    }

    private fun performImport(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val language = app.preferences.settings.first().language
            withContext(Dispatchers.Main) {
                feedback.value = MemoraFeedbackMessage(
                    text = if (language == com.memora.app.prefs.AppLanguage.UZ) "Zaxira tiklanmoqda…" else "Restoring backup…",
                    kind = MemoraFeedbackKind.LOADING
                )
            }
            runCatching { exportManager.importBackup(uri) }
                .onSuccess { result ->
                    val scheduler = AlarmScheduler(this@MainActivity)
                    app.repository.enabledReminders().forEach(scheduler::scheduleReminder)
                    withContext(Dispatchers.Main) {
                        pendingImportUri.value = null
                        feedback.value = MemoraFeedbackMessage(
                            text = L.format(language, S.IMPORT_SUMMARY, result.imported, result.duplicatesSkipped),
                            kind = MemoraFeedbackKind.SUCCESS
                        )
                    }
                }
                .onFailure {
                    withContext(Dispatchers.Main) {
                        pendingImportUri.value = null
                        feedback.value = MemoraFeedbackMessage(text = L.t(language, S.IMPORT_FAILED), kind = MemoraFeedbackKind.ERROR)
                    }
                }
        }
    }

    private fun beginExport(request: ExportRequest) {
        pendingExportRequest = request
        if (request.format == ExportFormat.BACKUP) {
            backupLauncher.launch(ExportManager.suggestedFileName(request))
        } else {
            folderLauncher.launch(null)
        }
    }

    private fun finishFolderExport(treeUri: Uri) {
        val request = pendingExportRequest ?: return
        pendingExportRequest = null
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val language = app.preferences.settings.first().language
                withContext(Dispatchers.Main) {
                    feedback.value = MemoraFeedbackMessage(
                        text = if (language == com.memora.app.prefs.AppLanguage.UZ) "Eksport tayyorlanmoqda…" else "Preparing export…",
                        kind = MemoraFeedbackKind.LOADING
                    )
                }
                exportManager.writeFolder(request, treeUri, language)
                withContext(Dispatchers.Main) {
                    feedback.value = MemoraFeedbackMessage(text = L.t(language, S.SAVED), kind = MemoraFeedbackKind.SUCCESS)
                }
            } catch (t: Throwable) {
                val language = app.preferences.settings.first().language
                withContext(Dispatchers.Main) {
                    feedback.value = MemoraFeedbackMessage(
                        text = L.t(language, S.EXPORT_FAILED),
                        kind = MemoraFeedbackKind.ERROR
                    )
                }
            }
        }
    }

    private fun finishExport(uri: android.net.Uri) {
        val request = pendingExportRequest ?: return
        pendingExportRequest = null
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val language = app.preferences.settings.first().language
                withContext(Dispatchers.Main) {
                    feedback.value = MemoraFeedbackMessage(
                        text = if (language == com.memora.app.prefs.AppLanguage.UZ) "Zaxira tayyorlanmoqda…" else "Preparing backup…",
                        kind = MemoraFeedbackKind.LOADING
                    )
                }
                exportManager.write(request, uri, language)
                withContext(Dispatchers.Main) {
                    feedback.value = MemoraFeedbackMessage(text = L.t(language, S.SAVED), kind = MemoraFeedbackKind.SUCCESS)
                }
            } catch (t: Throwable) {
                val language = app.preferences.settings.first().language
                withContext(Dispatchers.Main) {
                    feedback.value = MemoraFeedbackMessage(
                        text = L.t(language, S.EXPORT_FAILED),
                        kind = MemoraFeedbackKind.ERROR
                    )
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }


    private fun requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val scheduler = AlarmScheduler(this)
        if (scheduler.canScheduleExact()) return
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName")
                )
            )
        }.onFailure {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openSubscriptionManagement() {
        val uri = Uri.parse(
            "https://play.google.com/store/account/subscriptions" +
                "?sku=${SubscriptionCatalog.PRODUCT_ID}&package=${BuildConfig.APPLICATION_ID.removeSuffix(".debug")}" 
        )
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    private fun handleIntent(intent: Intent?) {
        pendingEditorType.value = intent?.getStringExtra(EXTRA_OPEN_EDITOR)
        pendingMemoryNote.value = intent?.getStringExtra(EXTRA_OPEN_NOTE)
        intent?.removeExtra(EXTRA_OPEN_NOTE)
        if (intent?.getStringExtra(EXTRA_OPEN_ALBUM_CAMERA) == "true") {
            pendingOpenAlbumCamera.value = true
            intent.removeExtra(EXTRA_OPEN_ALBUM_CAMERA)
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_TIME_TRACE, false) == true) {
            pendingOpenTimeTrace.value = true
            intent.removeExtra(EXTRA_OPEN_TIME_TRACE)
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_TOPILMALAR_REVIEW, false) == true) {
            pendingOpenTopilmalarReview.value = true
            intent.removeExtra(EXTRA_OPEN_TOPILMALAR_REVIEW)
        }
        val notificationId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, -1) ?: -1
        if (notificationId != -1) {
            NotificationManagerCompat.from(this).cancel(notificationId)
            intent?.removeExtra(EXTRA_NOTIFICATION_ID)
        }
    }

    private fun authenticateIfNeeded(force: Boolean) {
        if (authenticating) return
        lifecycleScope.launch {
            val settings = app.preferences.settings.first()
            if (!settings.lockEnabled) {
                unlocked.value = true
                sessionUnlocked = true
                return@launch
            }
            if (!force && !intervalElapsed(settings)) {
                unlocked.value = true
                sessionUnlocked = true
                return@launch
            }

            val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val canAuthenticate = BiometricManager.from(this@MainActivity)
                .canAuthenticate(authenticators)

            if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
                val keyguard = getSystemService(KeyguardManager::class.java)
                if (keyguard?.isDeviceSecure != true) {
                    unlocked.value = true
                    sessionUnlocked = true
                    feedback.value = MemoraFeedbackMessage(
                        text = L.t(settings.language, S.NO_DEVICE_SECURITY),
                        kind = MemoraFeedbackKind.INFO
                    )
                    return@launch
                }
            }

            authenticating = true
            unlocked.value = false
            sessionUnlocked = false
            val prompt = BiometricPrompt(
                this@MainActivity,
                ContextCompat.getMainExecutor(this@MainActivity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        authenticating = false
                        unlocked.value = true
                        sessionUnlocked = true
                        backgroundAt = null
                        sessionBackgroundAt = null
                        lockRuntimePrefs.edit().remove(KEY_LAST_BACKGROUND_AT).apply()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        authenticating = false
                        unlocked.value = false
                        sessionUnlocked = false
                    }

                    override fun onAuthenticationFailed() {
                        unlocked.value = false
                        sessionUnlocked = false
                    }
                }
            )
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(L.t(settings.language, S.AUTH_TITLE))
                .setSubtitle(L.t(settings.language, S.AUTH_SUBTITLE))
                .setAllowedAuthenticators(authenticators)
                .build()
            prompt.authenticate(promptInfo)
        }
    }

    private fun intervalElapsed(settings: UserSettings): Boolean {
        val background = backgroundAt
            ?: sessionBackgroundAt
            ?: lockRuntimePrefs.getLong(KEY_LAST_BACKGROUND_AT, 0L).takeIf { it > 0L }
            ?: return !unlocked.value
        val elapsed = (System.currentTimeMillis() - background).coerceAtLeast(0L)
        val threshold = when (settings.lockInterval) {
            LockInterval.IMMEDIATELY -> 0L
            LockInterval.ONE_MINUTE -> 60_000L
            LockInterval.FIVE_MINUTES -> 5 * 60_000L
            LockInterval.FIFTEEN_MINUTES -> 15 * 60_000L
            LockInterval.THIRTY_MINUTES -> 30 * 60_000L
        }
        return elapsed >= threshold
    }

    companion object {
        private const val KEY_LAST_BACKGROUND_AT = "last_background_at"
        private var sessionUnlocked: Boolean = false
        private var sessionBackgroundAt: Long? = null
        const val EXTRA_OPEN_EDITOR = "open_editor_type"
        const val EXTRA_OPEN_NOTE = "open_memory_note_id"
        const val EXTRA_OPEN_ALBUM_CAMERA = "open_album_camera"
        const val EXTRA_OPEN_TIME_TRACE = "open_time_trace"
        const val EXTRA_OPEN_TOPILMALAR_REVIEW = "open_topilmalar_review"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
