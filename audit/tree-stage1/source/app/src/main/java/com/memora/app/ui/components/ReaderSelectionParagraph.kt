package com.memora.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.awaitCancellation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.memora.app.data.model.NoteBlock
import com.memora.app.data.model.NoteHighlightPolicy
import com.memora.app.prefs.AppLanguage
import com.memora.app.prefs.FontChoice
import com.memora.app.ui.theme.fontFamily
import com.memora.app.ui.theme.highlightComposeColor
import kotlin.math.roundToInt

/**
 * The reader owns its selection controls. Compose Foundation 1.9+ uses its new text-context-menu
 * provider instead of LocalTextToolbar; intercept BOTH APIs to suppress the OS selection menu.
 * Memora's popup is driven directly by TextFieldValue.selection and the text layout, independent
 * of whether either framework provider invokes a show-menu callback. Native handles, drag selection
 * and accessibility selection semantics are preserved.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderSelectionParagraph(
    block: NoteBlock.Text,
    fontChoice: FontChoice,
    language: AppLanguage,
    query: String,
    focusedMatchOffset: Int?,
    canHighlight: Boolean,
    onHighlight: (blockId: String, start: Int, end: Int, color: String?) -> Unit,
    onShare: (selected: String, asImage: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("memora_reader_marker", 0) }
    var defaultColor by remember { mutableStateOf(prefs.getString("default_color", "YELLOW")
        ?.takeIf { it in NoteHighlightPolicy.colors } ?: "YELLOW") }
    val labels = ReaderActionsLabels.forLanguage(language)
    val ink = MaterialTheme.colorScheme.onSurface
    val clipboard = LocalClipboardManager.current
    val annotated = remember(block, query) {
        buildAnnotatedString {
            append(block.text)
            block.styles.forEach { span ->
                if (span.start >= 0 && span.end <= block.text.length && span.end > span.start) {
                    addStyle(SpanStyle(
                        fontWeight = if (span.bold) FontWeight.Bold else null,
                        fontStyle = if (span.italic) FontStyle.Italic else null,
                        background = highlightComposeColor(span.highlight) ?: Color.Unspecified
                    ), span.start, span.end)
                }
            }
            if (query.isNotBlank()) {
                var start = block.text.indexOf(query, ignoreCase = true)
                while (start >= 0) {
                    addStyle(SpanStyle(background = Color(0xFFD5DCE0)), start, start + query.length)
                    start = block.text.indexOf(query, start + query.length, ignoreCase = true)
                }
            }
        }
    }
    var value by remember(block.id) { mutableStateOf(TextFieldValue(annotatedString = annotated)) }
    var nativeMenuRect by remember(block.id) { mutableStateOf<Rect?>(null) }
    var activatedHighlight by remember(block.id) { mutableStateOf<NoteHighlightPolicy.HighlightRun?>(null) }
    var colorPickerOpen by remember(block.id) { mutableStateOf(false) }
    var textLayout by remember(block.id) { mutableStateOf<TextLayoutResult?>(null) }
    var layoutCoordinates by remember(block.id) { mutableStateOf<LayoutCoordinates?>(null) }
    val searchRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(annotated) {
        value = value.copy(
            annotatedString = annotated,
            selection = TextRange(value.selection.start.coerceIn(0, block.text.length),
                value.selection.end.coerceIn(0, block.text.length)),
            composition = null
        )
    }
    LaunchedEffect(focusedMatchOffset, textLayout) {
        val offset = focusedMatchOffset ?: return@LaunchedEffect
        val layout = textLayout ?: return@LaunchedEffect
        if (block.text.isNotEmpty()) {
            runCatching { layout.getBoundingBox(offset.coerceIn(0, block.text.lastIndex)) }
                .getOrNull()?.let { searchRequester.bringIntoView(it) }
        }
    }

    val selectionStart = minOf(value.selection.start, value.selection.end).coerceIn(0, block.text.length)
    val selectionEnd = maxOf(value.selection.start, value.selection.end).coerceIn(0, block.text.length)
    val selected = block.text.substring(selectionStart, selectionEnd)
    val activeRun = activatedHighlight?.takeIf {
        it.start == selectionStart && it.end == selectionEnd &&
            NoteHighlightPolicy.runAt(block, it.start)?.let { latest ->
                latest.start == it.start && latest.end == it.end && latest.color == it.color
            } == true
    }

    // Foundation 1.9+ displays the Android ACTION_PROCESS_TEXT menu (including third-party
    // actions such as Ask Gemini) through a new context menu provider, not LocalTextToolbar.
    // This no-UI provider keeps the native selection session open so that the selection handles
    // continue to work, while the only visible menu is Memora's own three-action popup.
    val suppressPlatformMenu = remember(block.id) {
        object : TextContextMenuProvider {
            override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
                awaitCancellation()
            }
        }
    }
    val memoraToolbar = remember(block.id) {
        object : TextToolbar {
            override val status: TextToolbarStatus
                get() = if (nativeMenuRect != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden
            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                nativeMenuRect = rect
            }
            override fun hide() { nativeMenuRect = null }
        }
    }
    fun endSelection() {
        value = value.copy(selection = TextRange(selectionEnd))
        nativeMenuRect = null
        activatedHighlight = null
        colorPickerOpen = false
    }
    fun applyMarker(shade: String?) {
        if (canHighlight && selectionStart < selectionEnd) {
            onHighlight(block.id, selectionStart, selectionEnd, shade)
        }
        endSelection()
    }
    fun updateDefault(shade: String) {
        defaultColor = shade
        prefs.edit().putString("default_color", shade).apply()
        applyMarker(shade)
    }
    // The native layout rectangle is in window coordinates. For a tap on an existing marker we
    // can also derive the exact marker position from the text layout, without a long press.
    val quickBarRect = if (activeRun != null) {
        val bounds = textLayout?.getBoundingBox(activeRun.start)
        val coordinates = layoutCoordinates
        if (bounds != null && coordinates != null && coordinates.isAttached) {
            val windowPoint = coordinates.localToWindow(Offset(bounds.left, bounds.top))
            Rect(windowPoint.x, windowPoint.y, windowPoint.x + bounds.width, windowPoint.y + bounds.height)
        } else null
    } else null
    // The modern Compose context-menu provider does not call TextToolbar.showMenu. Derive
    // the menu anchor from the actual selected characters so the Memora popup still appears.
    val selectionBarRect = if (selectionEnd > selectionStart) {
        val layout = textLayout
        val coordinates = layoutCoordinates
        if (layout != null && coordinates != null && coordinates.isAttached) {
            runCatching {
                val first = layout.getBoundingBox(selectionStart)
                val last = layout.getBoundingBox(selectionEnd - 1)
                val startPoint = coordinates.localToWindow(Offset(first.left, first.top))
                val endPoint = coordinates.localToWindow(Offset(last.right, last.bottom))
                Rect(
                    startPoint.x,
                    startPoint.y,
                    maxOf(startPoint.x + first.width, endPoint.x),
                    maxOf(startPoint.y + first.height, endPoint.y)
                )
            }.getOrNull()
        } else null
    } else null
    val barRect = if (activeRun != null) {
        quickBarRect ?: selectionBarRect ?: nativeMenuRect
    } else {
        selectionBarRect ?: nativeMenuRect
    }
    val showBar = selectionEnd > selectionStart && barRect != null

    CompositionLocalProvider(
        LocalTextToolbar provides memoraToolbar,
        LocalTextContextMenuToolbarProvider provides suppressPlatformMenu,
        LocalTextContextMenuDropdownProvider provides suppressPlatformMenu
    ) {
        Box(modifier) {
            BasicTextField(
                value = value,
                onValueChange = { incoming ->
                    if (incoming.text == block.text) {
                        if (incoming.selection != value.selection) activatedHighlight = null
                        value = incoming.copy(annotatedString = annotated, composition = null)
                    }
                },
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
                    .onGloballyPositioned { layoutCoordinates = it }
                    .bringIntoViewRequester(searchRequester)
                    // Observe completed taps without consuming gestures used for native selection.
                    .pointerInput(block.id, block.styles) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var upPosition: Offset? = null
                            var upTime = 0L
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    upPosition = change.position
                                    upTime = change.uptimeMillis
                                    break
                                }
                            }
                            val up = upPosition
                            if (up != null && (up - down.position).getDistance() < 17.dp.toPx() &&
                                upTime - down.uptimeMillis < 430L) {
                                val offset = textLayout?.getOffsetForPosition(up) ?: -1
                                val run = NoteHighlightPolicy.runAt(block, offset)
                                if (run != null) {
                                    activatedHighlight = run
                                    value = value.copy(selection = TextRange(run.start, run.end))
                                }
                            }
                        }
                    },
                onTextLayout = { textLayout = it },
                textStyle = TextStyle(
                    color = ink,
                    fontFamily = fontFamily(fontChoice),
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                    hyphens = Hyphens.Auto,
                    lineBreak = LineBreak.Paragraph
                ),
                cursorBrush = SolidColor(Color.Transparent),
                readOnly = true
            )
        }
        if (showBar && barRect != null) {
            Popup(
                popupPositionProvider = remember(barRect) { MemoraSelectionPopupPosition(barRect) },
                properties = PopupProperties(focusable = false),
                onDismissRequest = { nativeMenuRect = null; colorPickerOpen = false }
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 8.dp,
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    if (activeRun != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            MemoraSelectionItem("✕") { applyMarker(null) }
                            listOf("YELLOW", "GREEN", "ROSE").forEach { shade ->
                                MarkerColorCircle(shade, activeRun.color == shade, onClick = { applyMarker(shade) })
                            }
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            MemoraSelectionItem(labels.copy) {
                                clipboard.setText(AnnotatedString(selected)); endSelection()
                            }
                            MemoraSelectionItem(labels.image) {
                                onShare(selected, true); endSelection()
                            }
                            if (canHighlight) {
                                Box {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.combinedClickable(
                                            onClick = { applyMarker(defaultColor) },
                                            onLongClick = { colorPickerOpen = true }
                                        ).padding(horizontal = 9.dp, vertical = 9.dp)
                                    ) {
                                        Text(labels.marker, style = MaterialTheme.typography.labelMedium)
                                        Surface(
                                            shape = CircleShape,
                                            color = swatchColor(defaultColor),
                                            modifier = Modifier.padding(start = 5.dp).size(13.dp)
                                        ) {}
                                    }
                                    DropdownMenu(
                                        expanded = colorPickerOpen,
                                        onDismissRequest = { colorPickerOpen = false }
                                    ) {
                                        listOf("YELLOW", "GREEN", "ROSE").forEach { shade ->
                                            DropdownMenuItem(
                                                text = { Row(verticalAlignment = Alignment.CenterVertically) {
                                                    MarkerColorCircle(shade, defaultColor == shade, onClick = { updateDefault(shade) })
                                                    Text(shadeLabel(shade, language), modifier = Modifier.padding(start = 8.dp))
                                                } },
                                                onClick = { updateDefault(shade) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoraSelectionItem(label: String, onClick: () -> Unit) {
    Box(Modifier.combinedClickable(onClick = onClick, onLongClick = onClick)
        .padding(horizontal = 9.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MarkerColorCircle(shade: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = swatchColor(shade),
        shape = CircleShape,
        border = if (active) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
                 else null,
        modifier = Modifier.padding(3.dp).size(30.dp)
    ) {}
}

private fun swatchColor(shade: String): Color = when (shade) {
    "GREEN" -> Color(0xFFB4DCCE)
    "ROSE" -> Color(0xFFE7C8D2)
    else -> Color(0xFFF6E8B5)
}

private fun shadeLabel(shade: String, language: AppLanguage): String = when (language) {
    AppLanguage.UZ -> when (shade) { "GREEN" -> "Yashil"; "ROSE" -> "Pushti"; else -> "Sariq" }
    AppLanguage.RU -> when (shade) { "GREEN" -> "Зелёный"; "ROSE" -> "Розовый"; else -> "Жёлтый" }
    else -> when (shade) { "GREEN" -> "Green"; "ROSE" -> "Pink"; else -> "Yellow" }
}

/** Positions the same slim capsule as the native text menu, but fully owned by Memora. */
private class MemoraSelectionPopupPosition(private val selectionRect: Rect) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val center = (selectionRect.left + selectionRect.right) / 2f
        val x = (center - popupContentSize.width / 2f).roundToInt()
            .coerceIn(6, maxOf(6, windowSize.width - popupContentSize.width - 6))
        val above = selectionRect.top.roundToInt() - popupContentSize.height - 12
        val below = selectionRect.bottom.roundToInt() + 12
        val y = if (above >= 44) above else below
        return IntOffset(x, y.coerceIn(0, maxOf(0, windowSize.height - popupContentSize.height)))
    }
}

/** These labels remain available for all thirteen interface languages. */
data class ReaderActionsLabels(
    val copy: String, val share: String, val image: String, val marker: String,
    val clear: String, val search: String, val pdf: String, val preview: String
) {
    companion object {
        fun forLanguage(l: AppLanguage): ReaderActionsLabels = when (l) {
            AppLanguage.UZ -> ReaderActionsLabels("Nusxalash", "Ulashish", "Rasm", "Marker", "Olib tashlash", "Qidirish", "PDF", "Ko‘rinish")
            AppLanguage.RU -> ReaderActionsLabels("Копировать", "Поделиться", "Фото", "Маркер", "Убрать", "Поиск", "PDF", "Просмотр")
            AppLanguage.EN -> ReaderActionsLabels("Copy", "Share", "Image", "Mark", "Clear", "Search", "PDF", "Preview")
            AppLanguage.AR -> ReaderActionsLabels("نسخ", "مشاركة", "صورة", "تحديد", "إزالة", "بحث", "PDF", "معاينة")
            AppLanguage.TR -> ReaderActionsLabels("Kopyala", "Paylaş", "Resim", "İşaretle", "Kaldır", "Ara", "PDF", "Önizleme")
            AppLanguage.FR -> ReaderActionsLabels("Copier", "Partager", "Image", "Marquer", "Effacer", "Chercher", "PDF", "Aperçu")
            AppLanguage.DE -> ReaderActionsLabels("Kopieren", "Teilen", "Bild", "Markieren", "Entfernen", "Suchen", "PDF", "Vorschau")
            AppLanguage.ES -> ReaderActionsLabels("Copiar", "Compartir", "Imagen", "Marcar", "Quitar", "Buscar", "PDF", "Vista previa")
            AppLanguage.PT_BR -> ReaderActionsLabels("Copiar", "Compartilhar", "Imagem", "Marcar", "Remover", "Buscar", "PDF", "Prévia")
            AppLanguage.HI -> ReaderActionsLabels("कॉपी", "साझा", "चित्र", "चिह्नित", "हटाएँ", "खोजें", "PDF", "झलक")
            AppLanguage.ID -> ReaderActionsLabels("Salin", "Bagikan", "Gambar", "Tandai", "Hapus", "Cari", "PDF", "Pratinjau")
            AppLanguage.JA -> ReaderActionsLabels("コピー", "共有", "画像", "マーク", "解除", "検索", "PDF", "プレビュー")
            AppLanguage.KO -> ReaderActionsLabels("복사", "공유", "이미지", "강조", "해제", "검색", "PDF", "미리보기")
        }
    }
}
