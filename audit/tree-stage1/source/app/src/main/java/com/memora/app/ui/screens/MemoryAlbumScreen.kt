package com.memora.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.memora.app.data.local.MemoryAlbumPhotoEntity
import com.memora.app.data.repository.MemoraRepository
import com.memora.app.memories.MemoryAlbumStorage
import com.memora.app.memories.MemoryText
import com.memora.app.prefs.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class AlbumEffect(val title: String) {
    ORIGINAL("Oddiy"), WARM("Warm Memory"), VINTAGE("Vintage Fade"), INSTANT("Instant Color"), MONO("Mono Story")
}

/** Reversible filters: source JPEG is retained; effect is applied in the renderer. */
private fun albumFilter(effect: String): ColorFilter? {
    val matrix = when (effect) {
        "WARM" -> ColorMatrix(floatArrayOf(
            1.06f, .02f, 0f, 0f, 4f,
            .01f, 1.01f, 0f, 0f, 2f,
            0f, 0f, .90f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        "VINTAGE" -> ColorMatrix(floatArrayOf(
            .78f, .16f, .02f, 0f, 14f,
            .07f, .78f, .08f, 0f, 8f,
            .07f, .15f, .69f, 0f, 3f,
            0f, 0f, 0f, 1f, 0f
        ))
        "INSTANT" -> ColorMatrix().apply { setToSaturation(1.20f) }
        "MONO" -> ColorMatrix().apply { setToSaturation(0f) }
        else -> return null
    }
    return ColorFilter.colorMatrix(matrix)
}

private fun words(value: String): Int = value.trim().split(Regex("\\s+")).count { it.isNotBlank() }

@Composable
fun MemoryAlbumScreen(
    photos: List<MemoryAlbumPhotoEntity>,
    language: AppLanguage,
    repository: MemoraRepository,
    canEdit: Boolean,
    initialCamera: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val storage = remember(context) { MemoryAlbumStorage(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var cameraOpen by remember { mutableStateOf(false) }
    var captured by remember { mutableStateOf<File?>(null) }
    var caption by remember { mutableStateOf("") }
    var captionStage by remember { mutableStateOf(false) }
    var effect by remember { mutableStateOf(AlbumEffect.ORIGINAL) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingPhoto by remember { mutableStateOf<MemoryAlbumPhotoEntity?>(null) }
    var deleting by remember { mutableStateOf<MemoryAlbumPhotoEntity?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) cameraOpen = true else error = if (language == AppLanguage.UZ) "Kamera ruxsatini bering" else "Camera permission is required"
    }
    fun openCamera() {
        if (!canEdit) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraOpen = true
        } else permission.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(initialCamera) { if (initialCamera) openCamera() }

    BackHandler(enabled = cameraOpen || captured != null) {
        when {
            captured != null -> { captured?.delete(); captured = null; caption = "" }
            cameraOpen -> cameraOpen = false
        }
    }
    if (cameraOpen) {
        MemoryAlbumCamera(
            language = language,
            storage = storage,
            onDismiss = { cameraOpen = false },
            onCaptured = { file -> cameraOpen = false; captured = file; caption = ""; captionStage = false; effect = AlbumEffect.ORIGINAL }
        )
        return
    }
    val photoFile = captured
    if (photoFile != null) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp)) {
            AlbumFloatingBack(onClick = { photoFile.delete(); captured = null })
            Text(if (language == AppLanguage.UZ) "Xotirani tayyorlash" else "Prepare your memory",
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 14.dp))
            val preview by produceState<Bitmap?>(initialValue = null, photoFile.path) {
                value = withContext(Dispatchers.IO) {
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeFile(photoFile.path, options)
                }
            }
            MemoryPolaroid(preview, 0, caption, System.currentTimeMillis(), effect.name)
            Text(if (language == AppLanguage.UZ) "Surat effekti" else "Photo effect",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlbumEffect.entries.forEach { choice ->
                    FilterChip(selected = effect == choice, onClick = { effect = choice }, label = { Text(choice.title) })
                }
            }
            if (captionStage) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { input -> if (words(input) <= 20) caption = input },
                    label = { Text(MemoryText.get(language, MemoryText.Key.CAPTION)) },
                    supportingText = { Text("${words(caption)} / 20") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !saving,
                onClick = {
                    if (!captionStage) { captionStage = true; return@Button }
                    saving = true
                    scope.launch {
                        try {
                            val path = withContext(Dispatchers.IO) { storage.importPhoto(photoFile) }
                            try {
                                withContext(Dispatchers.IO) {
                                    repository.addMemoryAlbum(MemoryAlbumPhotoEntity(
                                        relativePath = path,
                                        capturedAt = System.currentTimeMillis(),
                                        caption = caption.trim(), effect = effect.name
                                    ))
                                }
                            } catch (t: Throwable) {
                                withContext(Dispatchers.IO) { storage.delete(path) }
                                throw t
                            }
                            photoFile.delete()
                            captured = null
                            caption = ""
                            error = null
                        } catch (t: Throwable) {
                            error = if (language == AppLanguage.UZ) "Surat saqlanmadi. Qayta urinib ko‘ring." else "Could not save. Try again."
                        } finally { saving = false }
                    }
                }, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
            ) { Text(if (captionStage) MemoryText.get(language, MemoryText.Key.SAVE)
                else if (language == AppLanguage.UZ) "Izoh yozish" else "Add caption") }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumFloatingBack(onClick = onBack)
            Text(MemoryText.get(language, MemoryText.Key.ALBUM),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 14.dp))
            if (canEdit) IconButton(onClick = ::openCamera) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = MemoryText.get(language, MemoryText.Key.CAMERA))
            }
        }
        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Rounded.PhotoLibrary, null, modifier = Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (language == AppLanguage.UZ) "Birinchi xotirangiz uchun joy tayyor" else "Your first memory belongs here")
                    if (canEdit) Button(onClick = ::openCamera) { Text(MemoryText.get(language, MemoryText.Key.CAMERA)) }
                }
            }
        } else {
            val pager = rememberPagerState(pageCount = { photos.size })
            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 18.dp)) { page ->
                val item = photos[page]
                val bitmap by produceState<Bitmap?>(initialValue = null, item.relativePath) {
                    value = withContext(Dispatchers.IO) { storage.load(item.relativePath) }
                }
                // Gentle book-page turn: low displacement / no abrupt opacity change.
                val offset = (pager.currentPage - page + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
                Box(
                    Modifier.fillMaxSize().padding(vertical = 10.dp)
                        .graphicsLayer {
                            rotationY = offset * 12f
                            translationX = offset * -10.dp.toPx()
                            cameraDistance = 12 * density
                        }, contentAlignment = Alignment.Center
                ) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f),
                        shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("#${item.id.toString().padStart(3, '0')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp))
                            MemoryPolaroid(bitmap, item.id, item.caption, item.capturedAt, item.effect)
                            if (canEdit) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { editingPhoto = item; caption = item.caption; effect = AlbumEffect.entries.firstOrNull { it.name == item.effect } ?: AlbumEffect.ORIGINAL }) {
                                    Text(if (language == AppLanguage.UZ) "Tahrirlash" else "Edit")
                                }
                                IconButton(onClick = { deleting = item }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = pager.currentPage > 0, onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                    Icon(Icons.Rounded.ChevronLeft, null)
                }
                Text("${pager.currentPage + 1} / ${photos.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(enabled = pager.currentPage < photos.size - 1, onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
        }
    }
    editingPhoto?.let { item ->
        AlertDialog(onDismissRequest = { editingPhoto = null }, title = { Text(if (language == AppLanguage.UZ) "Izohni o‘zgartirish" else "Edit caption") },
            text = {
                Column {
                    OutlinedTextField(value = caption, onValueChange = { if (words(it) <= 20) caption = it },
                        supportingText = { Text("${words(caption)} / 20") })
                    AlbumEffect.entries.forEach { option ->
                        Row(Modifier.fillMaxWidth().clickable { effect = option }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = effect == option, onClick = { effect = option })
                            Text(option.title)
                        }
                    }
                }
            }, confirmButton = {
                TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { repository.editMemoryAlbum(item.id, caption.trim(), effect.name) }; editingPhoto = null } }) {
                    Text(MemoryText.get(language, MemoryText.Key.SAVE))
                }
            }, dismissButton = { TextButton(onClick = { editingPhoto = null }) { Text(if (language == AppLanguage.UZ) "Bekor qilish" else "Cancel") } })
    }
    deleting?.let { item ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(if (language == AppLanguage.UZ) "Surat o‘chirilsinmi?" else "Delete photo?") },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { repository.deleteMemoryAlbum(item); storage.delete(item.relativePath) }
                    deleting = null
                }
            }) { Text(if (language == AppLanguage.UZ) "O‘chirish" else "Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(if (language == AppLanguage.UZ) "Bekor qilish" else "Cancel") } })
    }
}

@Composable
private fun MemoryPolaroid(bitmap: Bitmap?, index: Long, caption: String, capturedAt: Long, effect: String) {
    Surface(color = Color(0xFFF8F5EC), shape = RoundedCornerShape(4.dp), shadowElevation = 5.dp) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 24.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFFD3CBBE)), contentAlignment = Alignment.Center) {
                if (bitmap != null) Image(bitmap.asImageBitmap(), null, contentScale = ContentScale.Crop,
                    colorFilter = albumFilter(effect), modifier = Modifier.fillMaxSize())
            }
            val date = remember(capturedAt) { Instant.ofEpochMilli(capturedAt).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm")) }
            Text(date, color = Color(0xFF71685E), style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 12.dp))
            Text(caption.ifBlank { " " }, color = Color(0xFF514338), fontFamily = FontFamily.Cursive,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 21.sp, lineHeight = 28.sp),
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun AlbumFloatingBack(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp, shadowElevation = 3.dp, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface) }
    }
}

@Composable
private fun MemoryAlbumCamera(language: AppLanguage, storage: MemoryAlbumStorage,
    onDismiss: () -> Unit, onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(previewView, lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build()
                val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                provider.unbindAll()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                capture = imageCapture
            }.onFailure { error = it.localizedMessage ?: "Camera unavailable" }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            // Avoid blocking the UI thread while CameraX provider is initializing.
            if (future.isDone) runCatching { future.get().unbindAll() }
        }
    }
    BackHandler(onBack = onDismiss)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        AlbumFloatingBack(onDismiss)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp))) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = capture != null && !capturing,
            onClick = {
                val tool = capture ?: return@Button
                val file = storage.captureFile()
                capturing = true
                tool.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                            capturing = false
                            onCaptured(file)
                        }
                        override fun onError(exception: ImageCaptureException) {
                            capturing = false
                            file.delete()
                            error = exception.localizedMessage
                        }
                    })
            },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
        ) { Icon(Icons.Rounded.CameraAlt, null); Text(MemoryText.get(language, MemoryText.Key.CAMERA), Modifier.padding(start = 8.dp)) }
    }
}

