package com.memora.app.ui.screens

import com.memora.app.ui.components.MemoraDialog as AlertDialog
import android.app.DatePickerDialog
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.app.TimePickerDialog
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import com.memora.app.data.local.TimeTracePhotoEntity
import com.memora.app.data.repository.MemoraRepository
import com.memora.app.prefs.AppLanguage
import com.memora.app.prefs.TimeTraceSpeed
import com.memora.app.prefs.UserPreferences
import com.memora.app.prefs.UserSettings
import com.memora.app.notifications.TimeTraceScheduler
import com.memora.app.timetrace.TimeTraceStorage
import com.memora.app.timetrace.TimeTraceVideoExporter
import com.memora.app.ui.components.MemoraTopBar
import com.memora.app.ui.components.MemoraPermissionDialog
import com.memora.app.ui.components.MemoraConfirmationDialog
import com.memora.app.ui.components.MemoraEmptyState
import com.memora.app.ui.components.MemoraFeedbackHost
import com.memora.app.ui.components.MemoraFeedbackKind
import com.memora.app.ui.components.MemoraFeedbackMessage
import com.memora.app.ui.theme.MemoraDesign
import com.memora.app.ui.theme.MemoraMotion
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Time windows used by the private Time Trace viewer. */
enum class TimeTracePeriod(val months: Long?) { ONE(1), THREE(3), SIX(6), TWELVE(12), ALL(null) }

@Composable
fun TimeTraceScreen(
    photos: List<TimeTracePhotoEntity>,
    language: AppLanguage,
    settings: UserSettings,
    repository: MemoraRepository,
    preferences: UserPreferences,
    onRequestNotifications: () -> Unit,
    onRequestExactTiming: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val storage = remember(context) { TimeTraceStorage(context.applicationContext) }
    val scheduler = remember(context) { TimeTraceScheduler(context.applicationContext) }
    val introPrefs = remember(context) { context.getSharedPreferences("memora_time_trace_intro", 0) }
    val todayKey = remember { TimeTraceStorage.todayKey() }
    val todayPhoto = photos.firstOrNull { it.dateKey == todayKey }

    var introPage by remember { mutableIntStateOf(if (introPrefs.getBoolean("seen", false)) -1 else 0) }
    var targetDate by remember { mutableStateOf(LocalDate.now()) }
    var reviewCapture by remember { mutableStateOf<Pair<File, LocalDate>?>(null) }
    var pendingReplace by remember { mutableStateOf<Pair<Uri, LocalDate>?>(null) }
    var selectedDelete by remember { mutableStateOf<TimeTracePhotoEntity?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var slideshowPeriod by remember { mutableStateOf<TimeTracePeriod?>(null) }
    var cameraCaptureDate by remember { mutableStateOf<LocalDate?>(null) }
    var pendingCameraPermissionDate by remember { mutableStateOf<LocalDate?>(null) }
    var showCameraPermissionPrompt by remember { mutableStateOf(false) }
    var showCameraSettingsPrompt by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    val historyPhotos = remember(photos) { photos.sortedByDescending { it.dateKey } }

    fun persistUri(uri: Uri, date: LocalDate, imported: Boolean) {
        scope.launch(Dispatchers.IO) {
            val key = date.format(TimeTraceStorage.dateFormatter)
            repository.timeTracePhoto(key)?.let { storage.delete(key) }
            val stored = storage.importAndNormalize(uri, key)
            repository.saveTimeTracePhoto(
                TimeTracePhotoEntity(
                    dateKey = key,
                    relativePath = stored.relativePath,
                    capturedAt = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    imported = imported
                )
            )
        }
    }

    fun persistFile(file: File, date: LocalDate) {
        scope.launch(Dispatchers.IO) {
            val key = date.format(TimeTraceStorage.dateFormatter)
            repository.timeTracePhoto(key)?.let { storage.delete(key) }
            val stored = storage.importAndNormalize(file, key)
            repository.saveTimeTracePhoto(
                TimeTracePhotoEntity(
                    dateKey = key,
                    relativePath = stored.relativePath,
                    capturedAt = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    imported = false
                )
            )
            file.delete()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val date = pendingCameraPermissionDate
        if (granted && date != null) {
            pendingCameraPermissionDate = null
            cameraCaptureDate = date
        } else if (!granted) {
            val blocked = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            if (blocked) showCameraSettingsPrompt = true else pendingCameraPermissionDate = null
        }
    }

    fun startCamera(date: LocalDate) {
        targetDate = date
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraCaptureDate = date
        } else {
            pendingCameraPermissionDate = date
            showCameraPermissionPrompt = true
        }
    }

    if (showCameraPermissionPrompt) {
        MemoraPermissionDialog(
            language = language,
            title = if (language == AppLanguage.UZ) "Kamera ruxsati" else "Camera permission",
            text = if (language == AppLanguage.UZ) "Vaqt izi bir xil kadrdagi suratni olish uchun kamera ruxsatidan foydalanadi." else "Time Trace uses camera access only to capture your private daily frame.",
            confirmLabel = if (language == AppLanguage.UZ) "Davom etish" else "Continue",
            onConfirm = {
                showCameraPermissionPrompt = false
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onDismiss = { showCameraPermissionPrompt = false; pendingCameraPermissionDate = null }
        )
    }

    if (showCameraSettingsPrompt) {
        MemoraPermissionDialog(
            language = language,
            title = if (language == AppLanguage.UZ) "Kamera ruxsati" else "Camera permission",
            text = if (language == AppLanguage.UZ) "Kamera ruxsati o‘chirib qo‘yilgan. Uni telefon Sozlamalaridan yoqing." else "Camera permission is disabled. Enable it in the app settings.",
            confirmLabel = if (language == AppLanguage.UZ) "Sozlamalarni ochish" else "Open settings",
            onConfirm = {
                showCameraSettingsPrompt = false
                pendingCameraPermissionDate = null
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
            },
            onDismiss = { showCameraSettingsPrompt = false; pendingCameraPermissionDate = null }
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val key = targetDate.format(TimeTraceStorage.dateFormatter)
        if (photos.any { it.dateKey == key }) pendingReplace = uri to targetDate
        else persistUri(uri, targetDate, true)
    }

    cameraCaptureDate?.let { activeDate ->
        TimeTraceCameraCapture(
            date = activeDate,
            language = language,
            storage = storage,
            onCancel = { cameraCaptureDate = null },
            onCaptured = { file, savedDate ->
                cameraCaptureDate = null
                reviewCapture = file to savedDate
            }
        )
        return
    }

    if (slideshowPeriod != null) {
        TimeTraceSlideshow(
            photos = photos,
            period = slideshowPeriod!!,
            language = language,
            settings = settings,
            storage = storage,
            onBack = { slideshowPeriod = null }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        MemoraTopBar(
            title = timeTraceTitle(language),
            navigation = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = null) } },
            actions = { IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Settings, contentDescription = settingsLabel(language)) } }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = MemoraDesign.screenPadding,
                vertical = 10.dp
            )
        ) {
            item {
                TodayTimeTraceCard(
                    photo = todayPhoto,
                    language = language,
                    storage = storage,
                    streak = currentStreak(photos),
                    onCamera = { startCamera(LocalDate.now()) },
                    onGallery = {
                        targetDate = LocalDate.now()
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
            }
            item {
                TimeTraceStats(photos = photos, language = language)
            }
            item {
                Text(
                    viewPeriodsLabel(language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)
                )
                TimeTracePeriod.entries.filter { it != TimeTracePeriod.ALL }.chunked(2).forEach { rowPeriods ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowPeriods.forEach { period ->
                            FilterChip(
                                selected = false,
                                onClick = { slideshowPeriod = period },
                                label = { Text(periodName(language, period)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowPeriods.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                TextButton(onClick = { slideshowPeriod = TimeTracePeriod.ALL }) { Text(allHistoryLabel(language)) }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        historyLabel(language),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        val d = targetDate
                        DatePickerDialog(
                            context,
                            { _, y, m, day ->
                                targetDate = LocalDate.of(y, m + 1, day)
                                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            d.year,
                            d.monthValue - 1,
                            d.dayOfMonth
                        ).show()
                    }) { Text(importOldLabel(language)) }
                }
            }
            if (historyPhotos.isEmpty()) {
                item("trace-empty") {
                    MemoraEmptyState(
                        title = emptyTraceLabel(language),
                        body = if (language == AppLanguage.UZ)
                            "Kunlik suratlar vaqt o‘tishi bilan shu yerda sizning Vaqt izingizni yaratadi."
                        else "Daily photos will build your Time Trace here over time.",
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                historyPhotos.chunked(3).forEachIndexed { rowIndex, rowPhotos ->
                    item("trace-row-$rowIndex") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPhotos.forEach { photo ->
                                val photoIndex = historyPhotos.indexOf(photo)
                                TimeTraceThumb(
                                    photo = photo,
                                    storage = storage,
                                    onDelete = { selectedDelete = photo },
                                    onOpen = { viewerIndex = photoIndex },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowPhotos.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }

    viewerIndex?.let { initialIndex ->
        TimeTracePhotoViewer(
            photos = historyPhotos,
            initialIndex = initialIndex,
            storage = storage,
            onClose = { viewerIndex = null }
        )
    }

    if (introPage >= 0) {
        val pages = introPages(language)
        val page = pages[introPage.coerceIn(0, pages.lastIndex)]
        AlertDialog(
            onDismissRequest = {},
            title = { Text(page.first) },
            text = { Text(page.second) },
            confirmButton = {
                TextButton(onClick = {
                    if (introPage < pages.lastIndex) introPage++
                    else {
                        introPrefs.edit().putBoolean("seen", true).apply()
                        introPage = -1
                    }
                }) { Text(if (introPage < pages.lastIndex) nextLabel(language) else startLabel(language)) }
            },
            dismissButton = if (introPage > 0) {
                { TextButton(onClick = { introPage-- }) { Text(backLabel(language)) } }
            } else null
        )
    }

    reviewCapture?.let { (file, date) ->
        AlertDialog(
            onDismissRequest = {
                file.delete()
                reviewCapture = null
            },
            title = { Text(frameCheckTitle(language)) },
            text = {
                Column {
                    TimeTraceImage(
                        file = file,
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 5f),
                        showGuide = true
                    )
                    Text(
                        guideHint(language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    reviewCapture = null
                    persistFile(file, date)
                }) { Text(savePhotoLabel(language)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    file.delete()
                    reviewCapture = null
                    startCamera(date)
                }) { Text(retakeLabel(language)) }
            }
        )
    }

    pendingReplace?.let { pair ->
        AlertDialog(
            onDismissRequest = { pendingReplace = null },
            title = { Text(replaceTitle(language)) },
            text = { Text(replaceText(language)) },
            confirmButton = {
                TextButton(onClick = {
                    persistUri(pair.first, pair.second, true)
                    pendingReplace = null
                }) { Text(replaceLabel(language)) }
            },
            dismissButton = { TextButton(onClick = { pendingReplace = null }) { Text(cancelLabel(language)) } }
        )
    }

    selectedDelete?.let { photo ->
        MemoraConfirmationDialog(
            language = language,
            title = deletePhotoTitle(language),
            text = deletePhotoText(language),
            onConfirm = {
                scope.launch(Dispatchers.IO) {
                    storage.delete(photo.dateKey)
                    repository.deleteTimeTracePhoto(photo)
                }
                selectedDelete = null
            },
            onDismiss = { selectedDelete = null }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(settingsLabel(language)) },
            text = {
                Column {
                    ToggleRow(reminderLabel(language), settings.timeTraceReminderEnabled) { enabled ->
                        scope.launch { preferences.setTimeTraceReminderEnabled(enabled) }
                        if (enabled) {
                            onRequestNotifications()
                            onRequestExactTiming()
                            scheduler.scheduleDaily(settings.timeTraceReminderHour, settings.timeTraceReminderMinute)
                        } else {
                            scheduler.cancelDaily()
                        }
                    }
                    if (settings.timeTraceReminderEnabled) {
                        TextButton(onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    scope.launch { preferences.setTimeTraceReminderTime(h, m) }
                                    scheduler.scheduleDaily(h, m)
                                },
                                settings.timeTraceReminderHour,
                                settings.timeTraceReminderMinute,
                                true
                            ).show()
                        }) {
                            Text("%02d:%02d".format(Locale.US, settings.timeTraceReminderHour, settings.timeTraceReminderMinute))
                        }
                    }
                    ToggleRow(milestonesLabel(language), settings.timeTraceMilestonesEnabled) { enabled ->
                        scope.launch { preferences.setTimeTraceMilestonesEnabled(enabled) }
                        if (enabled) {
                            onRequestNotifications()
                            onRequestExactTiming()
                            scheduler.scheduleMilestoneCheck()
                        } else {
                            scheduler.cancelMilestoneCheck()
                        }
                    }
                    Text(speedLabel(language), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TimeTraceSpeed.entries.forEach { speed ->
                            FilterChip(
                                selected = settings.timeTraceSpeed == speed,
                                onClick = { scope.launch { preferences.setTimeTraceSpeed(speed) } },
                                label = { Text(speedName(language, speed)) }
                            )
                        }
                    }
                    ToggleRow(dateOverlayLabel(language), settings.timeTraceDateOverlay) {
                        scope.launch { preferences.setTimeTraceDateOverlay(it) }
                    }
                    ToggleRow(backupTraceLabel(language), settings.timeTraceIncludeInBackup) {
                        scope.launch { preferences.setTimeTraceIncludeInBackup(it) }
                    }
                    Text(
                        privacyLabel(language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Text(
                        soundtrackLabel(language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        storageLabel(language, storage.totalBytes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text(okLabel(language)) } }
        )
    }
}

@Composable
private fun TodayTimeTraceCard(
    photo: TimeTracePhotoEntity?,
    language: AppLanguage,
    storage: TimeTraceStorage,
    streak: Int,
    onCamera: () -> Unit,
    onGallery: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(MemoraDesign.radiusLarge),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = MemoraDesign.shadowSubtle,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (photo == null) Icons.Rounded.CameraAlt else Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        if (photo == null) todayAddLabel(language) else todaySavedLabel(language),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        streakLabel(language, streak),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (photo != null) {
                TimeTraceImage(
                    storage.resolve(photo.relativePath),
                    Modifier.fillMaxWidth().aspectRatio(4f / 5f),
                    showGuide = false
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 5f)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), RoundedCornerShape(MemoraDesign.radiusMedium))
                ) {
                    FaceGuide(Modifier.fillMaxSize())
                }
                Text(
                    guideHint(language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onCamera, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                    Text(cameraLabel(language), modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                    Text(galleryLabel(language), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun TimeTraceStats(photos: List<TimeTracePhotoEntity>, language: AppLanguage) {
    val dates = remember(photos) {
        photos.mapNotNull { runCatching { TimeTraceStorage.parseDate(it.dateKey) }.getOrNull() }.distinct().sorted()
    }
    val longest = remember(dates) { longestStreak(dates) }
    val first = dates.firstOrNull()?.toString() ?: "—"
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TraceStatCard(totalFramesLabel(language), photos.size.toString(), Modifier.weight(1f))
        TraceStatCard(longestTraceStreakLabel(language), longest.toString(), Modifier.weight(1f))
        TraceStatCard(firstPhotoLabel(language), first, Modifier.weight(1f))
    }
}

@Composable
private fun TraceStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MemoraDesign.radiusSmall),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun TimeTraceThumb(
    photo: TimeTracePhotoEntity,
    storage: TimeTraceStorage,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onOpen),
        shape = RoundedCornerShape(MemoraDesign.radiusSmall),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)
    ) {
        Box {
            TimeTraceImage(
                storage.thumbFor(photo.dateKey).takeIf { it.exists() } ?: storage.resolve(photo.relativePath),
                Modifier.fillMaxWidth().aspectRatio(4f / 5f),
                false,
                contentScale = ContentScale.Fit
            )
            Text(
                photo.dateKey.substring(5),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.Black.copy(alpha = .45f), RoundedCornerShape(topEnd = 8.dp))
                    .padding(horizontal = 5.dp, vertical = 3.dp)
            )
            IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd).size(30.dp)) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
fun TimeTraceImage(
    file: File,
    modifier: Modifier = Modifier,
    showGuide: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop
) {
    val bitmap by produceState<Bitmap?>(null, file.absolutePath, file.lastModified()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 })
            }.getOrNull()
        }
    }
    Box(modifier) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = contentScale)
        }
        if (showGuide) FaceGuide(Modifier.fillMaxSize())
    }
}

@Composable
private fun TimeTracePhotoViewer(
    photos: List<TimeTracePhotoEntity>,
    initialIndex: Int,
    storage: TimeTraceStorage,
    onClose: () -> Unit
) {
    if (photos.isEmpty()) return
    var index by remember(initialIndex, photos) { mutableIntStateOf(initialIndex.coerceIn(0, photos.lastIndex)) }
    var dragDistance by remember { mutableStateOf(0f) }
    val current = photos[index]

    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(index, photos.size) {
                detectHorizontalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragDistance += amount
                    },
                    onDragEnd = {
                        when {
                            dragDistance < -80f && index < photos.lastIndex -> index++
                            dragDistance > 80f && index > 0 -> index--
                        }
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f }
                )
            }
    ) {
        TimeTraceImage(
            file = storage.resolve(current.relativePath),
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 54.dp),
            showGuide = false,
            contentScale = ContentScale.Fit
        )

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = null, tint = Color.White)
            }
            Text(
                current.dateKey,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (index > 0) {
            IconButton(
                onClick = { index-- },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = Color.White)
            }
        }
        if (index < photos.lastIndex) {
            IconButton(
                onClick = { index++ },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun FaceGuide(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height * .47f)
        val guide = Color.White.copy(alpha = .84f)
        drawOval(
            color = guide,
            topLeft = Offset(size.width * .20f, size.height * .14f),
            size = androidx.compose.ui.geometry.Size(size.width * .60f, size.height * .67f),
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawLine(guide.copy(alpha = .62f), Offset(center.x, size.height * .11f), Offset(center.x, size.height * .83f), 1.dp.toPx())
        drawLine(guide.copy(alpha = .62f), Offset(size.width * .16f, size.height * .44f), Offset(size.width * .84f, size.height * .44f), 1.dp.toPx())
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun TimeTraceCameraCapture(
    date: LocalDate,
    language: AppLanguage,
    storage: TimeTraceStorage,
    onCancel: () -> Unit,
    onCaptured: (File, LocalDate) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var captureInProgress by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(previewView, lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build()
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                val selector = runCatching {
                    if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) CameraSelector.DEFAULT_FRONT_CAMERA
                    else CameraSelector.DEFAULT_BACK_CAMERA
                }.getOrDefault(CameraSelector.DEFAULT_BACK_CAMERA)
                provider.unbindAll()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                imageCapture = capture
                cameraError = null
            } catch (t: Throwable) {
                imageCapture = null
                cameraError = cameraUnavailableLabel(language)
            }
        }
        providerFuture.addListener(listener, executor)
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
        }
    }

    BackHandler(onBack = onCancel)

    Column(Modifier.fillMaxSize()) {
        MemoraTopBar(
            title = guidedCameraTitle(language),
            navigation = { IconButton(onClick = onCancel) { Icon(Icons.Rounded.ArrowBack, contentDescription = null) } }
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = MemoraDesign.screenPadding, vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(MemoraDesign.radiusLarge),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = MemoraDesign.shadowSubtle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 5f)
                            .clip(RoundedCornerShape(MemoraDesign.radiusMedium))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f))
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                        FaceGuide(Modifier.fillMaxSize())
                    }
                    Text(
                        guideHint(language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    cameraError?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                            Text(cancelLabel(language))
                        }
                        Button(
                            enabled = imageCapture != null && !captureInProgress,
                            onClick = {
                                val capture = imageCapture ?: return@Button
                                val file = storage.createCaptureTarget(date.format(TimeTraceStorage.dateFormatter))
                                captureInProgress = true
                                val output = ImageCapture.OutputFileOptions.Builder(file).build()
                                capture.takePicture(
                                    output,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            captureInProgress = false
                                            onCaptured(file, date)
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            captureInProgress = false
                                            file.delete()
                                            cameraError = captureFailedLabel(language)
                                        }
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                            Text(
                                if (captureInProgress) savingLabel(language) else takePhotoLabel(language),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeTraceSlideshow(
    photos: List<TimeTracePhotoEntity>,
    period: TimeTracePeriod,
    language: AppLanguage,
    settings: UserSettings,
    storage: TimeTraceStorage,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = remember(context, storage) { TimeTraceVideoExporter(context.applicationContext, storage) }
    val today = LocalDate.now()
    val cutoff = period.months?.let { today.minusMonths(it) }
    val selected = remember(photos, period) {
        photos.filter { photo -> cutoff == null || !TimeTraceStorage.parseDate(photo.dateKey).isBefore(cutoff) }
            .sortedBy { it.dateKey }
    }
    var index by remember(selected) { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(selected.size > 1) }
    var compare by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var exportFeedback by remember { mutableStateOf<MemoraFeedbackMessage?>(null) }
    val exportCancelled = remember { AtomicBoolean(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        if (selected.isEmpty()) return@rememberLauncherForActivityResult
        exporting = true
        exportProgress = 0
        exportFeedback = null
        exportCancelled.set(false)
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                exporter.export(
                    destination = uri,
                    photos = selected,
                    speed = settings.timeTraceSpeed,
                    showDate = settings.timeTraceDateOverlay,
                    onProgress = { value ->
                        scope.launch { exportProgress = value.coerceIn(0, 100) }
                    },
                    isCancelled = { exportCancelled.get() || !isActive }
                )
            }
            val cancelled = exportCancelled.get() || result.exceptionOrNull() is CancellationException
            withContext(Dispatchers.Main) {
                exporting = false
                if (!cancelled) {
                    exportFeedback = MemoraFeedbackMessage(
                        text = if (result.isSuccess) exportDoneLabel(language) else exportFailedLabel(language),
                        kind = if (result.isSuccess) MemoraFeedbackKind.SUCCESS else MemoraFeedbackKind.ERROR
                    )
                }
            }
        }
    }

    val delayMs = when (settings.timeTraceSpeed) {
        TimeTraceSpeed.SLOW -> 4_200L
        TimeTraceSpeed.MEDIUM -> 2_800L
        TimeTraceSpeed.FAST -> 1_600L
    }
    LaunchedEffect(playing, compare, index, selected) {
        if (playing && !compare && selected.size > 1) {
            delay(delayMs)
            index = (index + 1) % selected.size
        }
    }

    Column(Modifier.fillMaxSize()) {
        MemoraTopBar(
            title = periodName(language, period),
            navigation = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = null) } },
            actions = {
                IconButton(
                    enabled = !exporting && selected.isNotEmpty(),
                    onClick = {
                        val suffix = period.name.lowercase(Locale.US)
                        exportLauncher.launch("Memora-Vaqt-izi-$suffix.mp4")
                    }
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = exportVideoLabel(language))
                }
            }
        )
        if (selected.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
                MemoraEmptyState(
                    title = emptyTraceLabel(language),
                    body = if (language == AppLanguage.UZ) "Slayd-shou uchun avval Vaqt iziga surat qo‘shing." else "Add photos to Time Trace before creating a slideshow."
                )
            }
            return@Column
        }

        if (compare && selected.size >= 2) {
            Row(
                Modifier.fillMaxWidth().weight(1f).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    TimeTraceImage(storage.resolve(selected.first().relativePath), Modifier.fillMaxWidth().aspectRatio(4f / 5f))
                    Text(selected.first().dateKey, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
                }
                Column(Modifier.weight(1f)) {
                    TimeTraceImage(storage.resolve(selected.last().relativePath), Modifier.fillMaxWidth().aspectRatio(4f / 5f))
                    Text(selected.last().dateKey, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().weight(1f).padding(18.dp), contentAlignment = Alignment.Center) {
                Crossfade(
                    targetState = index,
                    animationSpec = tween(
                        durationMillis = MemoraMotion.Ambient,
                        easing = MemoraMotion.EmphasizedDecelerate
                    ),
                    label = "time-trace"
                ) { i ->
                    Box {
                        TimeTraceImage(storage.resolve(selected[i].relativePath), Modifier.fillMaxWidth().aspectRatio(4f / 5f))
                        if (settings.timeTraceDateOverlay) {
                            Text(
                                selected[i].dateKey,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                                    .background(Color.Black.copy(alpha = .38f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                enabled = !compare && selected.size > 1,
                onClick = { playing = false; index = (index - 1 + selected.size) % selected.size }
            ) { Icon(Icons.Rounded.ChevronLeft, contentDescription = previousLabel(language)) }
            Button(
                enabled = !compare && selected.size > 1,
                onClick = { playing = !playing }
            ) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                Text(if (playing) pauseLabel(language) else playLabel(language), modifier = Modifier.padding(start = 5.dp))
            }
            IconButton(
                enabled = !compare && selected.size > 1,
                onClick = { playing = false; index = (index + 1) % selected.size }
            ) { Icon(Icons.Rounded.ChevronRight, contentDescription = nextImageLabel(language)) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { compare = !compare; playing = false }) {
                Text(if (compare) slideshowLabel(language) else beforeAfterLabel(language))
            }
            Text(if (compare) "${selected.first().dateKey} — ${selected.last().dateKey}" else "${index + 1}/${selected.size}", style = MaterialTheme.typography.labelLarge)
        }
        if (exporting) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${exportingLabel(language)} $exportProgress%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        exportCancelled.set(true)
                    }) { Text(cancelLabel(language)) }
                }
                LinearProgressIndicator(
                    progress = { exportProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        MemoraFeedbackHost(
            message = exportFeedback,
            onDismiss = { exportFeedback = null },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
        )
        Spacer(Modifier.height(10.dp))
    }
}

private fun currentStreak(photos: List<TimeTracePhotoEntity>): Int {
    val dates = photos.mapNotNull { runCatching { TimeTraceStorage.parseDate(it.dateKey) }.getOrNull() }.toSet()
    if (dates.isEmpty()) return 0
    var cursor = LocalDate.now()
    var count = 0
    if (cursor !in dates) cursor = cursor.minusDays(1)
    while (cursor in dates) { count++; cursor = cursor.minusDays(1) }
    return count
}

private fun longestStreak(days: List<LocalDate>): Int {
    if (days.isEmpty()) return 0
    var best = 1
    var current = 1
    for (i in 1 until days.size) {
        if (days[i - 1].plusDays(1) == days[i]) {
            current++
            best = maxOf(best, current)
        } else current = 1
    }
    return best
}

private fun introPages(l: AppLanguage): List<Pair<String, String>> = if (l == AppLanguage.UZ) {
    listOf(
        "Har kuni bitta kadr" to "Vaqt izi har kuni bitta shaxsiy suratni saqlab, vaqt davomida xotiralaringizni bir joyda jamlaydi.",
        "Bir xil kadr" to "Yuzni markazda va ko‘zlarni bir balandlikda tuting. Memora suratlarni bir xil 4:5 kadrga joylaydi; bezatish yoki yuzni o‘zgartirish qo‘llanmaydi.",
        "Vaqtni ko‘ring" to "1, 3, 6 va 12 oylik davrlarni slayd, birinchi/oxirgi taqqoslash yoki shaxsiy MP4 video ko‘rinishida tomosha qilishingiz mumkin."
    )
} else {
    listOf(
        "One frame a day" to "Time Trace keeps one private photo per day so your memories can form a calm visual timeline.",
        "Keep the frame consistent" to "Center your face and keep your eyes level. Memora normalizes photos to the same 4:5 frame without beautifying or changing your appearance.",
        "See time together" to "View 1, 3, 6 or 12 months as a slideshow, first/last comparison, or a private MP4 export."
    )
}

private fun timeTraceTitle(l: AppLanguage) = if (l == AppLanguage.UZ) "Vaqt izi" else if (l == AppLanguage.RU) "След времени" else "Time Trace"
private fun todayAddLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Bugungi suratni qo‘shish" else "Add today’s photo"
private fun todaySavedLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Bugungi surat saqlandi" else "Today’s photo is saved"
private fun streakLabel(l: AppLanguage, n: Int) = if (l == AppLanguage.UZ) "$n kun ketma-ket" else "$n day streak"
private fun cameraLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Kamera" else "Camera"
private fun galleryLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Galereya" else "Gallery"
private fun guideHint(l: AppLanguage) = if (l == AppLanguage.UZ) "Yuzni markazda, ko‘zlarni bir balandlikda tuting. Memora suratni bir xil 4:5 kadrga joylaydi." else "Keep your face centered and eyes level. Memora normalizes each photo to the same 4:5 frame."
private fun viewPeriodsLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Vaqtni ko‘rish" else "View over time"
private fun periodName(l: AppLanguage, p: TimeTracePeriod) = when (p) { TimeTracePeriod.ONE -> if (l == AppLanguage.UZ) "1 oy" else "1 month"; TimeTracePeriod.THREE -> if (l == AppLanguage.UZ) "3 oy" else "3 months"; TimeTracePeriod.SIX -> if (l == AppLanguage.UZ) "6 oy" else "6 months"; TimeTracePeriod.TWELVE -> if (l == AppLanguage.UZ) "12 oy" else "12 months"; TimeTracePeriod.ALL -> if (l == AppLanguage.UZ) "Barcha vaqt" else "All time" }
private fun allHistoryLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Barcha tarix" else "All history"
private fun historyLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Suratlar tarixi" else "Photo history"
private fun importOldLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Eski surat qo‘shish" else "Add older photo"
private fun emptyTraceLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Hali Vaqt izi surati yo‘q" else "No Time Trace photos yet"
private fun replaceTitle(l: AppLanguage) = if (l == AppLanguage.UZ) "Surat mavjud" else "Photo already exists"
private fun replaceText(l: AppLanguage) = if (l == AppLanguage.UZ) "Bu sana uchun surat bor. Uni yangisi bilan almashtirasizmi?" else "A photo already exists for this date. Replace it?"
private fun replaceLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Almashtirish" else "Replace"
private fun cancelLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Bekor qilish" else "Cancel"
private fun deletePhotoTitle(l: AppLanguage) = if (l == AppLanguage.UZ) "Suratni o‘chirish" else "Delete photo"
private fun deletePhotoText(l: AppLanguage) = if (l == AppLanguage.UZ) "Bu kun Vaqt izida bo‘sh qoladi." else "This day will remain empty in Time Trace."
private fun deleteLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "O‘chirish" else "Delete"
private fun settingsLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Vaqt izi sozlamalari" else "Time Trace settings"
private fun reminderLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Kunlik eslatma" else "Daily reminder"
private fun milestonesLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "1/3/6/12 oy xabarlari" else "1/3/6/12 month milestones"
private fun dateOverlayLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Slaydda sanani ko‘rsatish" else "Show dates in slideshow"
private fun backupTraceLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Backup’ga suratlarni qo‘shish" else "Include photos in backup"
private fun storageLabel(l: AppLanguage, bytes: Long): String { val mb = bytes / 1024.0 / 1024.0; return if (l == AppLanguage.UZ) "Egallangan joy: %.1f MB".format(Locale.US, mb) else "Storage used: %.1f MB".format(Locale.US, mb) }
private fun okLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Tayyor" else "Done"
private fun beforeAfterLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Birinchi / oxirgi" else "Before / after"
private fun slideshowLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Slayd" else "Slideshow"
private fun pauseLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Pauza" else "Pause"
private fun playLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Davom" else "Play"
private fun speedLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Slayd tezligi" else "Slideshow speed"
private fun speedName(l: AppLanguage, s: TimeTraceSpeed) = when (s) { TimeTraceSpeed.SLOW -> if (l == AppLanguage.UZ) "Sekin" else "Slow"; TimeTraceSpeed.MEDIUM -> if (l == AppLanguage.UZ) "O‘rta" else "Medium"; TimeTraceSpeed.FAST -> if (l == AppLanguage.UZ) "Tez" else "Fast" }
private fun guidedCameraTitle(l: AppLanguage) = if (l == AppLanguage.UZ) "Ramkali kamera" else "Guided camera"
private fun takePhotoLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Surat olish" else "Take photo"
private fun savingLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Saqlanmoqda…" else "Saving…"
private fun cameraPermissionDeniedLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Ramkali kamera uchun kamera ruxsati kerak." else "Camera permission is required for the guided camera."
private fun cameraUnavailableLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Kamera ochilmadi. Qurilmadagi kamerani tekshirib qayta urinib ko‘ring." else "The camera could not start. Check your device camera and try again."
private fun captureFailedLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Surat olib bo‘lmadi. Qayta urinib ko‘ring." else "Couldn’t take the photo. Please try again."
private fun soundtrackLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Soundtrack: Ovozsiz (standart). Original Memora audiosi keyingi yangilanishda shu yerga ulanadi." else "Soundtrack: Silent (default). Original Memora tracks can be connected here later."
private fun privacyLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Suratlar qurilmada shaxsiy fayl sifatida saqlanadi va oddiy Galereyaga avtomatik chiqarilmaydi." else "Photos stay in private app storage and are not automatically added to your Gallery."
private fun frameCheckTitle(l: AppLanguage) = if (l == AppLanguage.UZ) "Kadrni tekshiring" else "Check the frame"
private fun savePhotoLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Saqlash" else "Save"
private fun retakeLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Qayta olish" else "Retake"
private fun nextLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Keyingi" else "Next"
private fun backLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Orqaga" else "Back"
private fun startLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Boshlash" else "Start"
private fun totalFramesLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Jami kadr" else "Frames"
private fun longestTraceStreakLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Eng uzun" else "Longest"
private fun firstPhotoLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Birinchi" else "First"
private fun exportVideoLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "MP4 saqlash" else "Save MP4"
private fun exportingLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Video tayyorlanmoqda…" else "Creating video…"
private fun exportDoneLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Vaqt izi videosi saqlandi" else "Time Trace video saved"
private fun exportFailedLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Videoni saqlab bo‘lmadi" else "Couldn’t save video"
private fun previousLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Oldingi" else "Previous"
private fun nextImageLabel(l: AppLanguage) = if (l == AppLanguage.UZ) "Keyingi" else "Next"
