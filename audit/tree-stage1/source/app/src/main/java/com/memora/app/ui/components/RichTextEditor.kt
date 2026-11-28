package com.memora.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.memora.app.ui.theme.highlightComposeColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memora.app.data.model.TextInlineStyle
import com.memora.app.prefs.FontChoice
import com.memora.app.ui.theme.fontFamily
import com.memora.app.util.EditorInputNormalizer
import com.memora.app.util.HashtagUtils
import kotlinx.coroutines.delay

/**
 * One instance edits exactly one paragraph. Paragraph spacing is deliberately NOT encoded inside
 * this text field; EditorScreen owns the physical gap between adjacent paragraph blocks.
 */
@Composable
fun PlainTextEditor(
    text: String,
    styles: List<TextInlineStyle> = emptyList(),
    onValueChanged: (String, List<TextInlineStyle>, Int) -> Unit,
    onParagraphBreak: (cursor: Int, nextPrefix: String) -> Unit,
    onMergeWithPrevious: () -> Boolean,
    fontChoice: FontChoice,
    ink: Color,
    uiScale: Float = 1.0f,
    onFocused: () -> Unit = {},
    onCursorChanged: (Int) -> Unit = {},
    requestFocus: Boolean = false,
    requestCursorPosition: Int? = null,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var localStyles by remember(styles) { mutableStateOf(styles.sanitize(text.length)) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(text = text, selection = TextRange(text.length))) }
    var paragraphBreakInFlight by remember { mutableStateOf(false) }

    LaunchedEffect(text) {
        paragraphBreakInFlight = false
        if (text != fieldValue.text) {
            fieldValue = fieldValue.copy(
                text = text,
                selection = TextRange(fieldValue.selection.start.coerceIn(0, text.length)),
                composition = null
            )
            localStyles = styles.sanitize(text.length)
        }
    }
    LaunchedEffect(styles) { localStyles = styles.sanitize(fieldValue.text.length) }

    LaunchedEffect(requestFocus, requestCursorPosition) {
        if (requestFocus) {
            val cursor = (requestCursorPosition ?: fieldValue.text.length).coerceIn(0, fieldValue.text.length)
            fieldValue = fieldValue.copy(selection = TextRange(cursor), composition = null)
            onCursorChanged(cursor)
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(fieldValue.selection, fieldValue.text.length, textLayoutResult) {
        if (!fieldValue.selection.collapsed) return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect
        delay(16)
        // Text layout can lag one frame behind fast IME edits. Asking an older layout for a
        // cursor offset from the newer text can throw and terminate the activity on some keyboards.
        // Clamp to the layout that actually produced this result and skip relocation if it is stale.
        val layoutLength = layout.layoutInput.text.length
        val cursor = fieldValue.selection.start.coerceIn(0, minOf(fieldValue.text.length, layoutLength))
        val cursorRect = runCatching { layout.getCursorRect(cursor) }.getOrNull() ?: return@LaunchedEffect
        val extraBottom = with(density) { 30.dp.toPx() }
        bringIntoViewRequester.bringIntoView(
            Rect(
                left = cursorRect.left,
                top = cursorRect.top,
                right = (cursorRect.right + 2f).coerceAtLeast(cursorRect.left + 2f),
                bottom = cursorRect.bottom + extraBottom
            )
        )
    }

    fun emitParagraphBreak(
        cursor: Int,
        committedText: String = fieldValue.text,
        committedStyles: List<TextInlineStyle> = localStyles
    ): Boolean {
        if (paragraphBreakInFlight) return true
        val safeCursor = cursor.coerceIn(0, committedText.length)
        paragraphBreakInFlight = true

        // Some OEM keyboards commit the last punctuation mark and Enter together. Persist the
        // committed text first, then split structurally, so punctuation is never lost and the
        // numbered-list prefix is calculated from the text the user actually sees.
        if (committedText != fieldValue.text || committedStyles != localStyles) {
            fieldValue = fieldValue.copy(
                text = committedText,
                selection = TextRange(safeCursor),
                composition = null
            )
            localStyles = committedStyles
            onValueChanged(committedText, committedStyles, safeCursor)
        } else {
            fieldValue = fieldValue.copy(
                selection = TextRange(safeCursor),
                composition = null
            )
        }

        onParagraphBreak(
            safeCursor,
            EditorInputNormalizer.numberedListPrefix(committedText, safeCursor)
        )
        return true
    }

    androidx.compose.foundation.layout.Column(modifier) {
        CompositionLocalProvider(LocalTextToolbar provides SuppressedComposeTextToolbar) {
            BasicTextField(
                value = fieldValue,
                onValueChange = { incoming ->
                    val paragraphBreak = EditorInputNormalizer.paragraphBreakEdit(
                        previousText = fieldValue.text,
                        incomingText = incoming.text,
                        incomingCursor = incoming.selection.start
                    )
                    if (paragraphBreak != null) {
                        val committedStyles = adjustStylesForEdit(
                            fieldValue.text,
                            paragraphBreak.text,
                            localStyles
                        )
                        emitParagraphBreak(
                            cursor = paragraphBreak.cursor,
                            committedText = paragraphBreak.text,
                            committedStyles = committedStyles
                        )
                        return@BasicTextField
                    }

                    val normalized = EditorInputNormalizer.normalize(
                        previousText = fieldValue.text,
                        incomingText = incoming.text,
                        incomingCursor = incoming.selection.start,
                        hasComposition = incoming.composition != null
                    )
                    val next = if (normalized.changed) incoming.copy(
                        text = normalized.text,
                        selection = TextRange(normalized.cursor),
                        composition = null
                    ) else incoming
                    val nextStyles = if (next.text != fieldValue.text) {
                        adjustStylesForEdit(fieldValue.text, next.text, localStyles)
                    } else localStyles
                    fieldValue = next
                    localStyles = nextStyles
                    onCursorChanged(next.selection.start)
                    if (next.text != text || nextStyles != styles) {
                        onValueChanged(next.text, nextStyles, next.selection.start)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        when {
                            event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                                emitParagraphBreak(fieldValue.selection.start)
                            }
                            event.type == KeyEventType.KeyDown &&
                                event.key == Key.Backspace &&
                                fieldValue.selection.collapsed &&
                                fieldValue.selection.start == 0 -> {
                                onMergeWithPrevious()
                            }
                            else -> false
                        }
                    }
                    .onFocusChanged {
                        if (it.isFocused) {
                            onFocused()
                            onCursorChanged(fieldValue.selection.start)
                        }
                    },
                onTextLayout = { textLayoutResult = it },
                textStyle = TextStyle(
                    color = ink,
                    fontFamily = fontFamily(fontChoice),
                    fontSize = (16.2f * uiScale).sp,
                    lineHeight = (24.0f * uiScale).sp,
                    hyphens = Hyphens.Auto,
                    lineBreak = LineBreak.Paragraph
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text
                ),
                cursorBrush = SolidColor(ink),
                visualTransformation = StoredStyleTransformation(
                    styles = localStyles,
                    hashtagColor = MaterialTheme.colorScheme.primary,
                    hashtagBackground = MaterialTheme.colorScheme.primary.copy(alpha = .10f)
                )
            )
        }
    }
}

/**
 * Memora intentionally does not add its own copy/cut/select-all floating toolbar. Selection itself
 * stays enabled, so an OEM/keyboard-provided native action surface can still work independently.
 */
private object SuppressedComposeTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden

    override fun hide() = Unit

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) = Unit

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
        onAutofillRequested: (() -> Unit)?
    ) = Unit
}

/** Only legacy stored bold/italic spans are displayed; no formatting command is exposed. */
private class StoredStyleTransformation(
    private val styles: List<TextInlineStyle>,
    private val hashtagColor: Color,
    private val hashtagBackground: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val transformed = buildAnnotatedString {
            append(text.text)
            styles.forEach { span ->
                if (span.end > span.start && span.start >= 0 && span.end <= text.length) {
                    addStyle(
                        SpanStyle(
                            fontWeight = if (span.bold) FontWeight.Bold else null,
                            fontStyle = if (span.italic) FontStyle.Italic else null,
                            background = highlightComposeColor(span.highlight) ?: Color.Unspecified
                        ),
                        span.start,
                        span.end
                    )
                }
            }
            HashtagUtils.ranges(text.text).forEach { range ->
                addStyle(
                    SpanStyle(
                        color = hashtagColor,
                        background = hashtagBackground,
                        fontWeight = FontWeight.Medium
                    ),
                    range.first,
                    range.last + 1
                )
            }
        }
        return TransformedText(transformed, OffsetMapping.Identity)
    }
}

private fun List<TextInlineStyle>.sanitize(length: Int): List<TextInlineStyle> = mapNotNull { span ->
    val start = span.start.coerceIn(0, length)
    val end = span.end.coerceIn(start, length)
    if (end > start && (span.bold || span.italic || span.highlight != null)) span.copy(start = start, end = end) else null
}

private fun adjustStylesForEdit(oldText: String, newText: String, styles: List<TextInlineStyle>): List<TextInlineStyle> {
    if (oldText == newText || styles.isEmpty()) return styles
    var prefix = 0
    val min = minOf(oldText.length, newText.length)
    while (prefix < min && oldText[prefix] == newText[prefix]) prefix++
    var suffix = 0
    while (suffix < min - prefix && oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]) suffix++
    val oldEnd = oldText.length - suffix
    val newEnd = newText.length - suffix
    val delta = (newEnd - prefix) - (oldEnd - prefix)
    return styles.mapNotNull { span ->
        when {
            span.end <= prefix -> span
            span.start >= oldEnd -> span.copy(start = span.start + delta, end = span.end + delta)
            else -> {
                val newStart = minOf(span.start, prefix)
                val newSpanEnd = if (span.end >= oldEnd) span.end + delta else newEnd
                if (newSpanEnd > newStart) span.copy(start = newStart, end = newSpanEnd) else null
            }
        }
    }.sanitize(newText.length)
}
