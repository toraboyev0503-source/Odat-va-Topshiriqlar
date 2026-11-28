package com.memora.app.ui.screens

import com.memora.app.ui.components.MemoraDialog as AlertDialog
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.memora.app.data.local.TopilmaSourceRow
import com.memora.app.share.TopilmaOcrManager
import com.memora.app.ui.theme.MemoraDesign
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TopilmaCreateModeScreen(
    sourceLabel: String = "Topilma",
    onBack: () -> Unit,
    onWrite: () -> Unit,
    onCopy: () -> Unit
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Orqaga") }
            Column {
                Text(sourceLabel, style = MaterialTheme.typography.titleLarge)
                Text("Qanday qo‘shmoqchisiz?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CreateModeCard(
                icon = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
                title = "Yozish",
                subtitle = "Topilmani klaviaturada yozish",
                onClick = onWrite
            )
            CreateModeCard(
                icon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
                title = "Nusxalash",
                subtitle = "Kitob yoki rasmdagi matnni belgilab olish",
                onClick = onCopy
            )
        }
    }
}

@Composable
private fun CreateModeCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(MemoraDesign.radiusMedium),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = MemoraDesign.shadowSubtle,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(MemoraDesign.radiusSmall),
                color = MaterialTheme.colorScheme.primary.copy(alpha = .11f)
            ) { Box(Modifier.padding(12.dp)) { icon() } }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun TopilmaCopyMethodScreen(
    source: TopilmaSourceRow,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onGalleryFile: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onCamera() else error = "Kameradan foydalanish uchun ruxsat kerak."
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val file = withContext(Dispatchers.IO) { copyUriToOcrCache(context, uri) }
                if (file != null) onGalleryFile(file) else error = "Rasmni ochib bo‘lmadi."
            }
        }
    }

    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Orqaga") }
            Column(Modifier.weight(1f)) {
                Text("Nusxalash", style = MaterialTheme.typography.titleLarge)
                Text(source.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CreateModeCard(
                icon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
                title = "Kamera",
                subtitle = "Sahifani hozir suratga olish",
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) onCamera()
                    else permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            )
            CreateModeCard(
                icon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
                title = "Galereya",
                subtitle = "Telefondagi mavjud rasmni tanlash",
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

@Composable
fun TopilmaCameraCaptureScreen(
    onBack: () -> Unit,
    onCaptured: (File) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var launching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scanner = remember {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        launching = false
        if (result.resultCode == Activity.RESULT_OK) {
            val scanned = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uri = scanned?.pages?.firstOrNull()?.imageUri
            if (uri == null) {
                error = "Skaner natijasini ochib bo‘lmadi."
            } else {
                val file = copyUriToOcrCache(context, uri)
                if (file != null) onCaptured(file) else error = "Skaner rasmini saqlab bo‘lmadi."
            }
        } else {
            onBack()
        }
    }

    fun launchScanner() {
        val host = activity ?: run {
            error = "Skanerni ochib bo‘lmadi."
            return
        }
        if (launching) return
        launching = true
        error = null
        scanner.getStartScanIntent(host)
            .addOnSuccessListener { sender ->
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener {
                launching = false
                error = "Hujjat skaneri ishga tushmadi. Qayta urinib ko‘ring."
            }
    }

    LaunchedEffect(Unit) { launchScanner() }
    BackHandler(onBack = onBack)

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (launching) {
            CircularProgressIndicator()
            Text("Skaner tayyorlanmoqda…", modifier = Modifier.padding(top = 14.dp))
        } else {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Button(onClick = ::launchScanner, modifier = Modifier.padding(top = 14.dp)) { Text("Qayta ochish") }
            }
            TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text("Ortga") }
        }
    }
}

@Composable
fun TopilmaOcrSelectionScreen(
    imageFile: File,
    onBack: () -> Unit,
    onRetryCamera: () -> Unit,
    onReplaceImage: (File) -> Unit,
    onConfirm: (String) -> Unit
) {
    var bitmap by remember(imageFile.absolutePath) { mutableStateOf(loadPreparedOcrBitmap(imageFile)) }
    var generation by remember { mutableIntStateOf(0) }
    var tokens by remember { mutableStateOf<List<TopilmaOcrManager.Token>>(emptyList()) }
    var ranges by remember { mutableStateOf<List<OcrSelectionRange>>(emptyList()) }
    var addPassage by remember { mutableStateOf(false) }
    var detecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var reviewText by remember { mutableStateOf<String?>(null) }
    val accent = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selected = remember(tokens, ranges) {
        ranges.flatMapTo(linkedSetOf<String>()) { range ->
            TopilmaOcrManager.rangeKeys(tokens, range.anchorKey, range.extentKey)
        }
    }
    val selectedPreview = remember(tokens, selected) { TopilmaOcrManager.selectedText(tokens, selected) }
    val latestRanges by rememberUpdatedState(ranges)
    val latestAddPassage by rememberUpdatedState(addPassage)
    val replacementGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val replacement = withContext(Dispatchers.IO) { copyUriToOcrCache(context, uri) }
                if (replacement != null) onReplaceImage(replacement)
            }
        }
    }

    LaunchedEffect(bitmap, generation) {
        val current = bitmap
        if (current == null) {
            error = "Rasmni ochib bo‘lmadi."
            tokens = emptyList()
            return@LaunchedEffect
        }
        detecting = true
        error = null
        ranges = emptyList()
        addPassage = false
        val result = withContext(Dispatchers.Default) { TopilmaOcrManager.recognize(current) }
        detecting = false
        result.onSuccess { found ->
            tokens = found
            if (found.isEmpty()) error = "Matn topilmadi"
        }.onFailure {
            tokens = emptyList()
            error = "Matnni aniqlab bo‘lmadi."
        }
    }

    BackHandler {
        if (reviewText != null) reviewText = null else onBack()
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .82f)).padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (reviewText != null) reviewText = null else onBack() }) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Orqaga", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Matnni belgilang", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Satrga teging yoki parcha bo‘ylab suring · 2 barmoq: surish/yaqinlashtirish", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(
                onClick = {
                    bitmap = bitmap?.let { rotateBitmap(it, 90f) }
                    zoom = 1f
                    pan = Offset.Zero
                    generation++
                },
                enabled = bitmap != null && !detecting
            ) { Icon(Icons.Rounded.RotateRight, contentDescription = "90° aylantirish", tint = Color.White) }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { viewport = it }
                .pointerInput(bitmap, tokens) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var multiTouch = false
                        var dragged = false
                        var lastSingle = down.position
                        val downToken = nearestTokenAt(down.position, viewport, bitmap, zoom, pan, tokens)
                        var gestureAnchor = downToken?.key
                        var gestureExtent = gestureAnchor
                        var baseRanges = if (latestAddPassage) latestRanges else emptyList()

                        if (!latestAddPassage && downToken != null) {
                            val adjustable = latestRanges.lastOrNull()
                            if (adjustable != null) {
                                if (downToken.key == adjustable.anchorKey) {
                                    gestureAnchor = adjustable.extentKey
                                    baseRanges = latestRanges.dropLast(1)
                                } else if (downToken.key == adjustable.extentKey) {
                                    gestureAnchor = adjustable.anchorKey
                                    baseRanges = latestRanges.dropLast(1)
                                }
                            }
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val active = event.changes.filter { it.pressed }
                            if (active.isEmpty()) {
                                if (!multiTouch && gestureAnchor != null && gestureExtent != null) {
                                    if (!dragged && downToken != null) {
                                        val line = TopilmaOcrManager.readingLines(tokens)
                                            .firstOrNull { row -> row.any { it.key == downToken.key } }
                                        ranges = if (line.isNullOrEmpty()) {
                                            baseRanges + OcrSelectionRange(gestureAnchor!!, gestureExtent!!)
                                        } else {
                                            baseRanges + OcrSelectionRange(line.first().key, line.last().key)
                                        }
                                    } else {
                                        ranges = baseRanges + OcrSelectionRange(gestureAnchor!!, gestureExtent!!)
                                    }
                                    addPassage = false
                                }
                                break
                            }
                            if (active.size >= 2) {
                                multiTouch = true
                                val first = active[0]
                                val second = active[1]
                                val previousCentroid = Offset(
                                    (first.previousPosition.x + second.previousPosition.x) / 2f,
                                    (first.previousPosition.y + second.previousPosition.y) / 2f
                                )
                                val currentCentroid = Offset(
                                    (first.position.x + second.position.x) / 2f,
                                    (first.position.y + second.position.y) / 2f
                                )
                                val previousDistance = (first.previousPosition - second.previousPosition).getDistance()
                                val currentDistance = (first.position - second.position).getDistance()
                                val factor = if (previousDistance > 1f) currentDistance / previousDistance else 1f
                                val nextZoom = (zoom * factor).coerceIn(1f, 6f)
                                zoom = nextZoom
                                pan = clampPan(pan + (currentCentroid - previousCentroid), viewport, bitmap, nextZoom)
                            } else if (!multiTouch) {
                                val change = active.first()
                                val movement = change.position - lastSingle
                                if (movement.getDistance() > 3f) dragged = true
                                if (dragged) {
                                    nearestTokenAt(change.position, viewport, bitmap, zoom, pan, tokens)?.let { token ->
                                        gestureExtent = token.key
                                        val anchor = gestureAnchor
                                        if (anchor != null) {
                                            ranges = baseRanges + OcrSelectionRange(anchor, token.key)
                                        }
                                    }
                                }
                                lastSingle = change.position
                            }
                        }
                    }
                }
        ) {
            val current = bitmap
            if (current != null) {
                Canvas(Modifier.fillMaxSize()) {
                    drawOcrImage(current, zoom, pan)
                    drawSelectionHighlights(current, tokens, selected, zoom, pan, accent.copy(alpha = .34f))
                }
            }
            if (detecting) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = .62f)
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                        Text("Matn aniqlanmoqda…", color = Color.White, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            } else if (error != null && tokens.isEmpty()) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(22.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = .68f)
                ) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: "Matn topilmadi", color = Color.White)
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onRetryCamera) { Text("Qayta olish") }
                            OutlinedButton(onClick = { replacementGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Boshqa rasm") }
                        }
                    }
                }
            }
        }

        if (selectedPreview.isNotBlank()) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .98f)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)) {
                    Text("Tanlangan matn", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        selectedPreview,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { ranges = emptyList(); addPassage = false }, enabled = selected.isNotEmpty()) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Tozalash")
                }
                IconButton(
                    onClick = {
                        val ordered = TopilmaOcrManager.readingOrder(tokens)
                        ranges = if (ordered.isEmpty()) emptyList() else listOf(OcrSelectionRange(ordered.first().key, ordered.last().key))
                        addPassage = false
                    },
                    enabled = tokens.isNotEmpty()
                ) {
                    Icon(Icons.Rounded.SelectAll, contentDescription = "Barchasini tanlash")
                }
                IconButton(
                    onClick = { addPassage = !addPassage },
                    enabled = selected.isNotEmpty()
                ) {
                    Icon(
                        Icons.Rounded.AddCircleOutline,
                        contentDescription = "Yana parcha",
                        tint = if (addPassage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                Text("${selected.size} tanlandi", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = {
                        if (selectedPreview.isNotBlank()) reviewText = selectedPreview
                    },
                    enabled = selected.isNotEmpty()
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Text(" Tasdiqlash")
                }
            }
        }
    }

    reviewText?.let { draft ->
        var editable by remember(draft) { mutableStateOf(draft) }
        AlertDialog(
            onDismissRequest = { reviewText = null },
            title = { Text("Matnni tekshiring") },
            text = {
                Column {
                    Text(
                        "Keraksiz joylarni o‘chiring yoki OCR xatosini tuzating.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = editable,
                        onValueChange = { editable = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = editable.trim()
                        if (clean.isNotBlank()) onConfirm(clean)
                    },
                    enabled = editable.isNotBlank()
                ) { Text("Qo‘shish") }
            },
            dismissButton = { TextButton(onClick = { reviewText = null }) { Text("Ortga") } }
        )
    }
}

private data class OcrSelectionRange(val anchorKey: String, val extentKey: String)
private data class ImagePlacement(val left: Float, val top: Float, val scale: Float)

private fun placement(viewport: IntSize, bitmap: Bitmap?, zoom: Float, pan: Offset): ImagePlacement? {
    if (bitmap == null || viewport.width <= 0 || viewport.height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) return null
    val base = min(viewport.width.toFloat() / bitmap.width, viewport.height.toFloat() / bitmap.height)
    val scale = base * zoom
    val width = bitmap.width * scale
    val height = bitmap.height * scale
    return ImagePlacement(
        left = (viewport.width - width) / 2f + pan.x,
        top = (viewport.height - height) / 2f + pan.y,
        scale = scale
    )
}

private fun nearestTokenAt(
    position: Offset,
    viewport: IntSize,
    bitmap: Bitmap?,
    zoom: Float,
    pan: Offset,
    tokens: List<TopilmaOcrManager.Token>
): TopilmaOcrManager.Token? {
    val p = placement(viewport, bitmap, zoom, pan) ?: return null
    val x = (position.x - p.left) / p.scale
    val y = (position.y - p.top) / p.scale
    val currentBitmap = bitmap ?: return null
    val outerPadding = 18f / p.scale
    if (x < -outerPadding || y < -outerPadding ||
        x > currentBitmap.width + outerPadding || y > currentBitmap.height + outerPadding
    ) return null
    val direct = tokens.firstOrNull { token ->
        val padding = max(3f, 5f / p.scale)
        x >= token.bounds.left - padding && x <= token.bounds.right + padding &&
            y >= token.bounds.top - padding && y <= token.bounds.bottom + padding
    }
    if (direct != null) return direct
    if (tokens.isEmpty()) return null

    // Do not snap to an arbitrarily distant token. The previous implementation always chose
    // the nearest word, which could jump across whitespace/columns and pull unselected text into
    // the passage. We only bridge normal inter-word/inter-line gaps.
    val medianHeight = tokens.map { it.bounds.height }.sorted().let { it[it.size / 2].toFloat() }.coerceAtLeast(1f)
    val nearest = tokens.minByOrNull { token ->
        val dx = when {
            x < token.bounds.left -> token.bounds.left - x
            x > token.bounds.right -> x - token.bounds.right
            else -> 0f
        }
        val dy = when {
            y < token.bounds.top -> token.bounds.top - y
            y > token.bounds.bottom -> y - token.bounds.bottom
            else -> 0f
        }
        dx * dx + dy * dy * 1.55f
    } ?: return null
    val dx = when {
        x < nearest.bounds.left -> nearest.bounds.left - x
        x > nearest.bounds.right -> x - nearest.bounds.right
        else -> 0f
    }
    val dy = when {
        y < nearest.bounds.top -> nearest.bounds.top - y
        y > nearest.bounds.bottom -> y - nearest.bounds.bottom
        else -> 0f
    }
    val maxGap = max(medianHeight * 1.45f, 18f / p.scale)
    return nearest.takeIf { dx * dx + dy * dy * 1.55f <= maxGap * maxGap }
}

private fun clampPan(candidate: Offset, viewport: IntSize, bitmap: Bitmap?, zoom: Float): Offset {
    if (bitmap == null || viewport == IntSize.Zero) return Offset.Zero
    val base = min(viewport.width.toFloat() / bitmap.width, viewport.height.toFloat() / bitmap.height)
    val width = bitmap.width * base * zoom
    val height = bitmap.height * base * zoom
    val maxX = max(0f, (width - viewport.width) / 2f + viewport.width * .18f)
    val maxY = max(0f, (height - viewport.height) / 2f + viewport.height * .18f)
    return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
}

private fun DrawScope.drawOcrImage(bitmap: Bitmap, zoom: Float, pan: Offset) {
    val viewport = IntSize(size.width.toInt(), size.height.toInt())
    val p = placement(viewport, bitmap, zoom, pan) ?: return
    val width = (bitmap.width * p.scale).toInt().coerceAtLeast(1)
    val height = (bitmap.height * p.scale).toInt().coerceAtLeast(1)
    drawImage(
        image = bitmap.asImageBitmap(),
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset = IntOffset(p.left.toInt(), p.top.toInt()),
        dstSize = IntSize(width, height)
    )
}

private fun DrawScope.drawSelectionHighlights(
    bitmap: Bitmap,
    tokens: List<TopilmaOcrManager.Token>,
    selectedKeys: Set<String>,
    zoom: Float,
    pan: Offset,
    color: Color
) {
    if (selectedKeys.isEmpty()) return
    val viewport = IntSize(size.width.toInt(), size.height.toInt())
    val p = placement(viewport, bitmap, zoom, pan) ?: return
    TopilmaOcrManager.readingLines(tokens).forEach { line ->
        var segment = mutableListOf<TopilmaOcrManager.Token>()
        fun flush() {
            if (segment.isEmpty()) return
            val left = p.left + segment.minOf { it.bounds.left } * p.scale
            val top = p.top + segment.minOf { it.bounds.top } * p.scale
            val right = p.left + segment.maxOf { it.bounds.right } * p.scale
            val bottom = p.top + segment.maxOf { it.bounds.bottom } * p.scale
            drawRoundRect(
                color = color,
                topLeft = Offset(left - 3f, top - 2f),
                size = Size((right - left) + 6f, (bottom - top) + 4f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f)
            )
            segment = mutableListOf()
        }
        line.forEach { token ->
            if (token.key in selectedKeys) segment += token else flush()
        }
        flush()
    }

    val orderedSelected = TopilmaOcrManager.readingOrder(tokens).filter { it.key in selectedKeys }
    val first = orderedSelected.firstOrNull()
    val last = orderedSelected.lastOrNull()
    if (first != null && last != null) {
        val handleRadius = 9f
        drawCircle(
            color = color.copy(alpha = .95f),
            radius = handleRadius,
            center = Offset(
                p.left + first.bounds.left * p.scale,
                p.top + first.bounds.bottom * p.scale + handleRadius * .55f
            )
        )
        drawCircle(
            color = color.copy(alpha = .95f),
            radius = handleRadius,
            center = Offset(
                p.left + last.bounds.right * p.scale,
                p.top + last.bounds.bottom * p.scale + handleRadius * .55f
            )
        )
    }
}

private fun createOcrCacheFile(context: android.content.Context): File {
    val dir = File(context.cacheDir, "topilma-ocr").apply { mkdirs() }
    return File(dir, "ocr-${UUID.randomUUID()}.jpg")
}

private fun copyUriToOcrCache(context: android.content.Context, uri: android.net.Uri): File? =
    com.memora.app.share.OcrCacheStorage.copyUri(context, uri)

private fun loadPreparedOcrBitmap(file: File, maxDimension: Int = 2600): Bitmap? =
    loadUprightBitmap(file, maxDimension)

private fun loadUprightBitmap(file: File, maxDimension: Int = 2600): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension * 2) sample *= 2
    val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val rotation = runCatching { ExifInterface(file).rotationDegrees }.getOrDefault(0)
    return if (rotation == 0) decoded else rotateBitmap(decoded, rotation.toFloat())
}

/**
 * Lightweight page rectification with no external scanner dependency. It looks for the bright
 * page boundary on upper/lower scan bands and corrects horizontal keystone using a 4-point
 * perspective matrix. When confidence is low it deliberately leaves the image unchanged.
 */
private fun autoPerspectiveRectify(source: Bitmap): Bitmap {
    if (source.width < 300 || source.height < 300) return source
    val previewMax = 720
    val scale = min(1f, previewMax.toFloat() / max(source.width, source.height))
    val preview = if (scale < .999f) Bitmap.createScaledBitmap(
        source,
        (source.width * scale).toInt().coerceAtLeast(1),
        (source.height * scale).toInt().coerceAtLeast(1),
        true
    ) else source
    val w = preview.width
    val h = preview.height
    val centerBrightness = averageBrightness(preview, w / 4, h / 4, w * 3 / 4, h * 3 / 4)
    val threshold = (centerBrightness * .72f).toInt().coerceIn(145, 220)
    val run = max(5, w / 120)

    fun bright(pixel: Int): Boolean = luminance(pixel) >= threshold
    fun leftEdge(y: Int): Int? {
        val limit = (w * .46f).toInt()
        for (x in 0 until max(1, limit - run)) {
            var ok = true
            for (i in 0 until run) if (!bright(preview.getPixel((x + i).coerceAtMost(w - 1), y))) { ok = false; break }
            if (ok) return x
        }
        return null
    }
    fun rightEdge(y: Int): Int? {
        val start = (w * .54f).toInt()
        for (x in w - 1 downTo min(w - 1, start + run)) {
            var ok = true
            for (i in 0 until run) if (!bright(preview.getPixel((x - i).coerceAtLeast(0), y))) { ok = false; break }
            if (ok) return x
        }
        return null
    }
    fun median(values: List<Int>): Float? {
        if (values.size < 3) return null
        val sorted = values.sorted()
        return sorted[sorted.size / 2].toFloat()
    }

    val upperRows = (5..28 step 4).map { (h * it / 100f).toInt().coerceIn(0, h - 1) }
    val lowerRows = (72..95 step 4).map { (h * it / 100f).toInt().coerceIn(0, h - 1) }
    val lt = median(upperRows.mapNotNull(::leftEdge)) ?: return source
    val rt = median(upperRows.mapNotNull(::rightEdge)) ?: return source
    val lb = median(lowerRows.mapNotNull(::leftEdge)) ?: return source
    val rb = median(lowerRows.mapNotNull(::rightEdge)) ?: return source
    val topWidth = rt - lt
    val bottomWidth = rb - lb
    if (topWidth < w * .55f || bottomWidth < w * .55f) return source
    val sideShift = max(abs(lt - lb), abs(rt - rb))
    if (sideShift < w * .012f) return source

    val inv = 1f / scale
    val src = floatArrayOf(
        lt * inv, 0f,
        rt * inv, 0f,
        rb * inv, source.height.toFloat(),
        lb * inv, source.height.toFloat()
    )
    val destWidth = (((topWidth + bottomWidth) / 2f) * inv).toInt().coerceIn(1, source.width)
    val destHeight = source.height
    val dst = floatArrayOf(
        0f, 0f,
        destWidth.toFloat(), 0f,
        destWidth.toFloat(), destHeight.toFloat(),
        0f, destHeight.toFloat()
    )
    val matrix = Matrix()
    if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return source
    val out = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(out)
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    return out
}

/** Small-angle text-line deskew. Horizontal projection peaks when book lines are level. */
private fun autoDeskew(source: Bitmap): Bitmap {
    if (source.width < 240 || source.height < 240) return source
    val maxSide = 520
    val scale = min(1f, maxSide.toFloat() / max(source.width, source.height))
    val preview = if (scale < .999f) Bitmap.createScaledBitmap(
        source,
        (source.width * scale).toInt().coerceAtLeast(1),
        (source.height * scale).toInt().coerceAtLeast(1),
        true
    ) else source
    var bestAngle = 0f
    var bestScore = projectionScore(preview)
    val baseline = bestScore
    for (angle in -5..5) {
        if (angle == 0) continue
        val rotated = rotateBitmapWithWhite(preview, angle.toFloat())
        val score = projectionScore(rotated)
        if (rotated !== preview) rotated.recycle()
        if (score > bestScore) {
            bestScore = score
            bestAngle = angle.toFloat()
        }
    }
    if (preview !== source) preview.recycle()
    if (abs(bestAngle) < .75f || bestScore < baseline * 1.035f) return source
    return rotateBitmapWithWhite(source, bestAngle)
}

private fun projectionScore(bitmap: Bitmap): Double {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return 0.0
    val step = if (w > 420) 2 else 1
    val rows = DoubleArray(h)
    for (y in 0 until h) {
        var dark = 0
        var x = w / 16
        val end = w - w / 16
        while (x < end) {
            if (luminance(bitmap.getPixel(x, y)) < 165) dark++
            x += step
        }
        rows[y] = dark.toDouble()
    }
    val mean = rows.average()
    return rows.sumOf { value -> val d = value - mean; d * d }
}

private fun averageBrightness(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Float {
    var total = 0L
    var count = 0
    val xStep = max(1, (right - left) / 80)
    val yStep = max(1, (bottom - top) / 80)
    var y = top.coerceIn(0, bitmap.height - 1)
    while (y < bottom.coerceAtMost(bitmap.height)) {
        var x = left.coerceIn(0, bitmap.width - 1)
        while (x < right.coerceAtMost(bitmap.width)) {
            total += luminance(bitmap.getPixel(x, y))
            count++
            x += xStep
        }
        y += yStep
    }
    return if (count == 0) 200f else total.toFloat() / count
}

private fun luminance(pixel: Int): Int {
    val r = (pixel shr 16) and 0xFF
    val g = (pixel shr 8) and 0xFF
    val b = pixel and 0xFF
    return ((r * 299 + g * 587 + b * 114) / 1000)
}

private fun rotateBitmapWithWhite(source: Bitmap, degrees: Float): Bitmap {
    if (abs(degrees) < .1f) return source
    val radians = Math.toRadians(abs(degrees).toDouble())
    val sin = kotlin.math.sin(radians)
    val cos = kotlin.math.cos(radians)
    val outWidth = (source.width * cos + source.height * sin).toInt().coerceAtLeast(1)
    val outHeight = (source.height * cos + source.width * sin).toInt().coerceAtLeast(1)
    val out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(out)
    canvas.drawColor(android.graphics.Color.WHITE)
    val matrix = Matrix().apply {
        postTranslate(-source.width / 2f, -source.height / 2f)
        postRotate(degrees)
        postTranslate(outWidth / 2f, outHeight / 2f)
    }
    canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    return out
}

private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
    if (abs(degrees) < .1f) return source
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}
