package com.memora.app.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.suspendCancellableCoroutine

/** On-device OCR plus deterministic geometric reading order. */
object TopilmaOcrManager {
    /** Pure Kotlin bounds keep ordering/range behavior unit-testable without an emulator. */
    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = (right - left).coerceAtLeast(1)
        val height: Int get() = (bottom - top).coerceAtLeast(1)
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
    }

    data class Token(
        val blockIndex: Int,
        val lineIndex: Int,
        val tokenIndex: Int,
        val text: String,
        val bounds: Bounds
    ) {
        val key: String get() = "$blockIndex:$lineIndex:$tokenIndex"
    }

    suspend fun recognize(bitmap: Bitmap): Result<List<Token>> = suspendCancellableCoroutine { continuation ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val tokens = buildList {
                    result.textBlocks.forEachIndexed { blockIndex, block ->
                        block.lines.forEachIndexed { lineIndex, line ->
                            line.elements.forEachIndexed { tokenIndex, element ->
                                val box = element.boundingBox ?: return@forEachIndexed
                                val text = element.text.trim()
                                if (text.isNotBlank()) {
                                    add(
                                        Token(
                                            blockIndex = blockIndex,
                                            lineIndex = lineIndex,
                                            tokenIndex = tokenIndex,
                                            text = text,
                                            bounds = Bounds(box.left, box.top, box.right, box.bottom)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                if (continuation.isActive) continuation.resume(Result.success(tokens))
                recognizer.close()
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resume(Result.failure(error))
                recognizer.close()
            }
        continuation.invokeOnCancellation { recognizer.close() }
    }

    /**
     * ML Kit block/line indices describe detector output, not guaranteed page reading
     * order. Reconstruct real lines from bounding-box overlap, then sort top-to-bottom
     * and left-to-right. This is the authoritative order for selection and output.
     */
    fun readingLines(tokens: List<Token>): List<List<Token>> {
        if (tokens.isEmpty()) return emptyList()
        val medianHeight = tokens.map { it.bounds.height }.sorted().let { it[it.size / 2].toFloat() }
        val lines = mutableListOf<MutableLine>()
        tokens.sortedWith(compareBy<Token> { it.bounds.centerY }.thenBy { it.bounds.left }).forEach { token ->
            val best = lines
                .asSequence()
                .map { line -> line to lineAffinity(line, token, medianHeight) }
                .filter { it.second >= 0f }
                .maxByOrNull { it.second }
                ?.first
            if (best == null) lines += MutableLine(token) else best.add(token)
        }
        return lines
            .sortedWith(compareBy<MutableLine> { it.centerY }.thenBy { it.left })
            .map { line -> line.tokens.sortedBy { it.bounds.left } }
    }

    /**
     * Groups reconstructed lines into visual paragraphs. A paragraph break is inferred from a
     * larger vertical gap or a strong indentation change. Keeping this geometry separate from
     * ML Kit block ids avoids accidentally pulling neighbouring text into a selected passage.
     */
    fun readingParagraphs(tokens: List<Token>): List<List<List<Token>>> {
        val lines = readingLines(tokens)
        if (lines.isEmpty()) return emptyList()
        val medianHeight = tokens.map { it.bounds.height }.sorted().let { it[it.size / 2].toFloat() }.coerceAtLeast(1f)
        val paragraphs = mutableListOf<MutableList<List<Token>>>()
        lines.forEach { line ->
            val top = line.minOf { it.bounds.top }
            val bottom = line.maxOf { it.bounds.bottom }
            val left = line.minOf { it.bounds.left }
            val previous = paragraphs.lastOrNull()?.lastOrNull()
            if (previous == null) {
                paragraphs += mutableListOf(line)
            } else {
                val previousBottom = previous.maxOf { it.bounds.bottom }
                val previousLeft = previous.minOf { it.bounds.left }
                val gap = top - previousBottom
                val indentShift = abs(left - previousLeft)
                val sameParagraph = gap <= medianHeight * 1.05f && indentShift <= medianHeight * 2.4f
                if (sameParagraph) paragraphs.last() += line else paragraphs += mutableListOf(line)
            }
        }
        return paragraphs.map { it.toList() }
    }

    fun readingOrder(tokens: List<Token>): List<Token> = readingParagraphs(tokens).flatten().flatten()

    /** Inclusive continuous range; reverse dragging produces the same passage. */
    fun rangeKeys(tokens: List<Token>, anchorKey: String, extentKey: String): LinkedHashSet<String> {
        val ordered = readingOrder(tokens)
        val anchor = ordered.indexOfFirst { it.key == anchorKey }
        val extent = ordered.indexOfFirst { it.key == extentKey }
        if (anchor < 0 || extent < 0) return linkedSetOf()
        val from = min(anchor, extent)
        val to = max(anchor, extent)
        return ordered.subList(from, to + 1).mapTo(linkedSetOf()) { it.key }
    }

    fun selectedText(tokens: List<Token>, selectedKeys: Set<String>): String {
        if (selectedKeys.isEmpty()) return ""
        val paragraphs = readingParagraphs(tokens)
        val allLines = paragraphs.flatten()
        val paragraphByKey = buildMap<String, Int> {
            paragraphs.forEachIndexed { paragraphIndex, lines ->
                lines.flatten().forEach { token -> put(token.key, paragraphIndex) }
            }
        }
        val medianHeight = tokens.map { it.bounds.height }.sorted().let { it[it.size / 2].toFloat() }
        val selectedSegments = mutableListOf<SelectedSegment>()

        allLines.forEachIndexed { lineIndex, line ->
            var current = mutableListOf<Token>()
            fun flush() {
                if (current.isNotEmpty()) {
                    selectedSegments += SelectedSegment(
                        lineIndex = lineIndex,
                        paragraphIndex = paragraphByKey[current.first().key] ?: 0,
                        tokens = current.toList(),
                        top = current.minOf { it.bounds.top },
                        bottom = current.maxOf { it.bounds.bottom }
                    )
                    current = mutableListOf()
                }
            }
            line.forEach { token ->
                if (token.key in selectedKeys) current += token else flush()
            }
            flush()
        }
        if (selectedSegments.isEmpty()) return ""

        return buildString {
            selectedSegments.forEachIndexed { index, segment ->
                if (index > 0) {
                    val previous = selectedSegments[index - 1]
                    val skippedLine = segment.lineIndex > previous.lineIndex + 1
                    val sameLineGap = segment.lineIndex == previous.lineIndex
                    val visualParagraphBreak = segment.paragraphIndex != previous.paragraphIndex
                    val verticalGap = segment.top - previous.bottom
                    val paragraphBreak = visualParagraphBreak || sameLineGap || skippedLine || verticalGap > medianHeight * 0.95f
                    append(if (paragraphBreak) "\n\n" else " ")
                }
                append(joinTokens(segment.tokens))
            }
        }.trim()
    }

    private data class SelectedSegment(
        val lineIndex: Int,
        val paragraphIndex: Int,
        val tokens: List<Token>,
        val top: Int,
        val bottom: Int
    )

    private fun joinTokens(tokens: List<Token>): String = buildString {
        tokens.forEach { token ->
            val value = token.text.trim()
            if (value.isEmpty()) return@forEach
            val noSpaceBefore = value.first() in ".,!?;:%)]}»”’…"
            val previous = lastOrNull()
            val noSpaceAfterPrevious = previous != null && previous in "([{«“‘-—/"
            if (isNotEmpty() && !noSpaceBefore && !noSpaceAfterPrevious) append(' ')
            append(value)
        }
    }

    private class MutableLine(first: Token) {
        val tokens = mutableListOf(first)
        var left = first.bounds.left
        var top = first.bounds.top
        var right = first.bounds.right
        var bottom = first.bounds.bottom
        val centerY: Float get() = (top + bottom) / 2f

        fun add(token: Token) {
            tokens += token
            left = min(left, token.bounds.left)
            top = min(top, token.bounds.top)
            right = max(right, token.bounds.right)
            bottom = max(bottom, token.bounds.bottom)
        }
    }

    private fun lineAffinity(line: MutableLine, token: Token, medianHeight: Float): Float {
        val overlap = min(line.bottom, token.bounds.bottom) - max(line.top, token.bounds.top)
        val overlapRatio = overlap.toFloat() / min((line.bottom - line.top).coerceAtLeast(1), token.bounds.height)
        val centerDistance = abs(line.centerY - token.bounds.centerY)
        if (overlapRatio < 0.24f && centerDistance > medianHeight * 0.72f) return -1f
        return overlapRatio * 3f - centerDistance / medianHeight.coerceAtLeast(1f)
    }

    fun open(context: Context, image: File, mimeType: String) {
        val directory = File(context.cacheDir, "shares").apply { mkdirs() }
        val shared = File(directory, "ocr-${image.name}")
        image.inputStream().use { input -> shared.outputStream().use { input.copyTo(it) } }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shared)
        val base = Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "image/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val lens = Intent(base).setPackage("com.google.ar.lens")
        val target = if (lens.resolveActivity(context.packageManager) != null) lens else Intent.createChooser(base, "Matnni aniqlash")
        context.startActivity(target)
    }
}
