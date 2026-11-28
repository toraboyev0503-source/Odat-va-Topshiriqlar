package com.memora.app.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Inline presentation spans are stored separately from plain text so IME composing text stays stable. */
data class TextInlineStyle(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val highlight: String? = null
)

sealed interface NoteBlock {
    val id: String

    /**
     * Since 0.5.5 one Text block represents one real paragraph.
     * A single '\n' may still exist inside a paragraph as a deliberate line break, but paragraph
     * spacing is never represented by a blank '\n\n' line anymore.
     */
    data class Text(
        override val id: String = UUID.randomUUID().toString(),
        val text: String = "",
        val styles: List<TextInlineStyle> = emptyList()
    ) : NoteBlock

    data class Image(
        override val id: String = UUID.randomUUID().toString(),
        val relativePath: String,
        val mimeType: String = "image/jpeg"
    ) : NoteBlock

    data class Audio(
        override val id: String = UUID.randomUUID().toString(),
        val relativePath: String,
        val mimeType: String = "audio/mp4",
        val durationMs: Long = 0L
    ) : NoteBlock
}

object NoteBlocksCodec {
    data class TextEditResult(
        val blocks: List<NoteBlock>,
        val focusTextId: String,
        val focusCursor: Int
    )

    fun decode(json: String?, legacyPlainText: String = ""): List<NoteBlock> {
        if (json.isNullOrBlank()) return legacy(legacyPlainText)
        return runCatching {
            val array = JSONArray(json)
            val result = buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val id = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
                    when (obj.optString("type")) {
                        "text" -> {
                            val text = obj.optString("text", "")
                            val styleArray = obj.optJSONArray("styles")
                            val styles = buildList {
                                if (styleArray != null) {
                                    for (i in 0 until styleArray.length()) {
                                        val style = styleArray.optJSONObject(i) ?: continue
                                        val start = style.optInt("start", 0).coerceIn(0, text.length)
                                        val end = style.optInt("end", start).coerceIn(start, text.length)
                                        if (end > start) {
                                            add(
                                                TextInlineStyle(
                                                    start = start,
                                                    end = end,
                                                    bold = style.optBoolean("bold", false),
                                                    italic = style.optBoolean("italic", false),
                                                    highlight = style.optString("highlight").takeIf { it in setOf("YELLOW", "GREEN", "ROSE") }
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            add(NoteBlock.Text(id = id, text = text, styles = styles))
                        }
                        "image" -> {
                            val path = obj.optString("path", "")
                            if (path.isNotBlank()) add(
                                NoteBlock.Image(
                                    id = id,
                                    relativePath = path,
                                    mimeType = obj.optString("mime", "image/jpeg")
                                )
                            )
                        }
                        "audio" -> {
                            val path = obj.optString("path", "")
                            if (path.isNotBlank()) add(
                                NoteBlock.Audio(
                                    id = id,
                                    relativePath = path,
                                    mimeType = obj.optString("mime", "audio/mp4"),
                                    durationMs = obj.optLong("durationMs", 0L)
                                )
                            )
                        }
                    }
                }
            }
            normalize(result, legacyPlainText)
        }.getOrElse { legacy(legacyPlainText) }
    }

    fun encode(blocks: List<NoteBlock>): String = JSONArray().apply {
        normalize(blocks).forEach { block ->
            put(JSONObject().apply {
                put("id", block.id)
                when (block) {
                    is NoteBlock.Text -> {
                        put("type", "text")
                        put("text", block.text)
                        put("styles", JSONArray().apply {
                            block.styles.forEach { span ->
                                put(JSONObject().apply {
                                    put("start", span.start)
                                    put("end", span.end)
                                    put("bold", span.bold)
                                    put("italic", span.italic)
                                    span.highlight?.let { put("highlight", it) }
                                })
                            }
                        })
                    }
                    is NoteBlock.Image -> {
                        put("type", "image")
                        put("path", block.relativePath)
                        put("mime", block.mimeType)
                    }
                    is NoteBlock.Audio -> {
                        put("type", "audio")
                        put("path", block.relativePath)
                        put("mime", block.mimeType)
                        put("durationMs", block.durationMs)
                    }
                }
            })
        }
    }.toString()

    /** Plain text/export compatibility keeps conventional blank lines between paragraphs. */
    fun plainText(blocks: List<NoteBlock>): String = normalize(blocks)
        .filterIsInstance<NoteBlock.Text>()
        .map { it.text.trimEnd() }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")

    fun hasImages(blocks: List<NoteBlock>): Boolean = blocks.any { it is NoteBlock.Image }
    fun hasAudio(blocks: List<NoteBlock>): Boolean = blocks.any { it is NoteBlock.Audio }
    fun audioDuration(blocks: List<NoteBlock>): Long = blocks.filterIsInstance<NoteBlock.Audio>().sumOf { it.durationMs }

    /**
     * Migration point for all old notes. Any old Text block containing one or more blank lines is
     * expanded into real paragraph Text blocks. This is intentionally done on decode/normalize so
     * old databases and imported backups remain compatible without a destructive DB migration.
     */
    fun normalize(blocks: List<NoteBlock>, fallbackPlain: String = ""): List<NoteBlock> {
        val source = if (blocks.isEmpty()) listOf(NoteBlock.Text(text = fallbackPlain)) else blocks
        val expanded = source.flatMap { block ->
            if (block is NoteBlock.Text) splitStoredParagraphs(block) else listOf(block)
        }.toMutableList()
        if (expanded.none { it is NoteBlock.Text }) expanded.add(0, NoteBlock.Text(text = fallbackPlain))
        return expanded
    }

    fun legacy(plainText: String): List<NoteBlock> = normalize(listOf(NoteBlock.Text(text = plainText)))

    /**
     * Update a paragraph. Pasted legacy-style blank lines are immediately promoted to real
     * paragraph blocks. The returned focus target keeps the caret in the equivalent paragraph.
     */
    fun editText(
        blocks: List<NoteBlock>,
        id: String,
        text: String,
        styles: List<TextInlineStyle>,
        cursor: Int
    ): TextEditResult {
        // Fast path for ordinary IME input: only one paragraph changes. This avoids
        // normalizing and copying the text of every other paragraph per character.
        // Paste with blank-line separators still takes the established split path.
        if (!text.contains("\n\n") && !text.contains("\r\n\r\n")) {
            val target = blocks.indexOfFirst { it is NoteBlock.Text && it.id == id }
            if (target >= 0) {
                val original = blocks[target] as NoteBlock.Text
                val sanitized = styles.mapNotNull { span ->
                    val start = span.start.coerceIn(0, text.length)
                    val end = span.end.coerceIn(start, text.length)
                    if (end > start) span.copy(start = start, end = end) else null
                }
                val updated = blocks.toMutableList()
                updated[target] = original.copy(text = text, styles = sanitized)
                return TextEditResult(updated, id, cursor.coerceIn(0, text.length))
            }
        }
        val mutable = normalize(blocks).toMutableList()
        val index = mutable.indexOfFirst { it is NoteBlock.Text && it.id == id }
        if (index < 0) {
            val fallback = NoteBlock.Text(text = text, styles = styles)
            mutable.add(fallback)
            return TextEditResult(mutable, fallback.id, cursor.coerceIn(0, text.length))
        }
        val original = mutable[index] as NoteBlock.Text
        val edited = original.copy(text = text, styles = styles)
        val paragraphs = splitStoredParagraphs(edited)
        if (paragraphs.size == 1) {
            mutable[index] = paragraphs.first()
            return TextEditResult(mutable, paragraphs.first().id, cursor.coerceIn(0, text.length))
        }

        mutable.removeAt(index)
        mutable.addAll(index, paragraphs)
        val mapping = paragraphCursorMapping(text, cursor, paragraphs)
        return TextEditResult(mutable, paragraphs[mapping.first].id, mapping.second)
    }

    /** Enter creates a new paragraph block rather than inserting a visual blank line. */
    fun splitParagraph(
        blocks: List<NoteBlock>,
        id: String,
        cursor: Int,
        nextPrefix: String = ""
    ): TextEditResult {
        val mutable = normalize(blocks).toMutableList()
        val index = mutable.indexOfFirst { it is NoteBlock.Text && it.id == id }
        val current = mutable.getOrNull(index) as? NoteBlock.Text
            ?: return TextEditResult(mutable, id, cursor.coerceAtLeast(0))
        val split = cursor.coerceIn(0, current.text.length)
        val beforeText = current.text.substring(0, split)
        val afterText = current.text.substring(split)
        val beforeStyles = sliceStyles(current.styles, 0, split)
        val afterStyles = sliceStyles(current.styles, split, current.text.length)
        val before = current.copy(text = beforeText, styles = beforeStyles)
        val next = NoteBlock.Text(text = nextPrefix + afterText, styles = afterStyles.map {
            it.copy(start = it.start + nextPrefix.length, end = it.end + nextPrefix.length)
        })
        mutable[index] = before
        mutable.add(index + 1, next)
        return TextEditResult(mutable, next.id, nextPrefix.length)
    }

    /** Backspace at the beginning of a paragraph joins it to the immediately previous paragraph. */
    fun mergeWithPrevious(blocks: List<NoteBlock>, id: String): TextEditResult? {
        val mutable = normalize(blocks).toMutableList()
        val index = mutable.indexOfFirst { it is NoteBlock.Text && it.id == id }
        if (index <= 0) return null
        val current = mutable[index] as? NoteBlock.Text ?: return null
        val previous = mutable[index - 1] as? NoteBlock.Text ?: return null
        val cursor = previous.text.length
        val merged = previous.copy(
            text = previous.text + current.text,
            styles = previous.styles + current.styles.map {
                it.copy(start = it.start + cursor, end = it.end + cursor)
            }
        )
        mutable[index - 1] = merged
        mutable.removeAt(index)
        return TextEditResult(mutable, merged.id, cursor)
    }

    /** Insert media exactly at the current caret position and keep text editable on both sides. */
    fun insertAtCursor(
        blocks: List<NoteBlock>,
        activeTextId: String?,
        cursor: Int,
        media: NoteBlock
    ): Pair<List<NoteBlock>, String> {
        val mutable = normalize(blocks).toMutableList()
        val activeIndex = mutable.indexOfFirst { it is NoteBlock.Text && it.id == activeTextId }
            .takeIf { it >= 0 }
            ?: mutable.indexOfLast { it is NoteBlock.Text }.takeIf { it >= 0 }
            ?: 0
        val active = mutable.getOrNull(activeIndex) as? NoteBlock.Text ?: NoteBlock.Text()
        val split = cursor.coerceIn(0, active.text.length)
        val before = active.text.substring(0, split)
        val after = active.text.substring(split)
        val beforeStyles = sliceStyles(active.styles, 0, split)
        val afterStyles = sliceStyles(active.styles, split, active.text.length)
        mutable[activeIndex] = active.copy(text = before, styles = beforeStyles)
        val nextText = NoteBlock.Text(text = after, styles = afterStyles)
        mutable.add((activeIndex + 1).coerceAtMost(mutable.size), media)
        mutable.add((activeIndex + 2).coerceAtMost(mutable.size), nextText)
        return mutable to nextText.id
    }

    /**
     * Removing media no longer joins the surrounding paragraphs with "\n\n". They remain two real
     * paragraph blocks, which preserves the exact editor/reader spacing contract.
     */
    fun remove(blocks: List<NoteBlock>, blockId: String): List<NoteBlock> {
        val result = normalize(blocks).filterNot { it.id == blockId }.toMutableList()
        if (result.isEmpty()) return listOf(NoteBlock.Text())
        if (result.none { it is NoteBlock.Text }) result.add(NoteBlock.Text())
        return result
    }

    private data class Segment(val start: Int, val end: Int)

    private fun splitStoredParagraphs(block: NoteBlock.Text): List<NoteBlock.Text> {
        val segments = paragraphSegments(block.text)
        if (segments.size == 1) return listOf(block.copy(styles = block.styles.sanitize(block.text.length)))
        return segments.mapIndexed { index, segment ->
            val segmentText = block.text.substring(segment.start, segment.end)
            NoteBlock.Text(
                id = if (index == 0) block.id else UUID.randomUUID().toString(),
                text = segmentText,
                styles = sliceStyles(block.styles, segment.start, segment.end)
            )
        }
    }

    private fun paragraphSegments(text: String): List<Segment> {
        val regex = Regex("\\n{2,}")
        val matches = regex.findAll(text).toList()
        if (matches.isEmpty()) return listOf(Segment(0, text.length))
        val result = mutableListOf<Segment>()
        var start = 0
        matches.forEach { match ->
            result += Segment(start, match.range.first)
            start = match.range.last + 1
        }
        result += Segment(start, text.length)
        return result
    }

    private fun paragraphCursorMapping(
        sourceText: String,
        sourceCursor: Int,
        paragraphs: List<NoteBlock.Text>
    ): Pair<Int, Int> {
        val safeCursor = sourceCursor.coerceIn(0, sourceText.length)
        val segments = paragraphSegments(sourceText)
        segments.forEachIndexed { index, segment ->
            if (safeCursor <= segment.end) {
                return index.coerceAtMost(paragraphs.lastIndex) to (safeCursor - segment.start).coerceIn(0, paragraphs[index].text.length)
            }
            val nextStart = segments.getOrNull(index + 1)?.start
            if (nextStart != null && safeCursor < nextStart) {
                return (index + 1).coerceAtMost(paragraphs.lastIndex) to 0
            }
        }
        val last = paragraphs.lastIndex
        return last to paragraphs[last].text.length
    }

    private fun sliceStyles(styles: List<TextInlineStyle>, from: Int, to: Int): List<TextInlineStyle> =
        styles.mapNotNull { span ->
            val overlapStart = maxOf(span.start, from)
            val overlapEnd = minOf(span.end, to)
            if (overlapEnd > overlapStart) {
                span.copy(start = overlapStart - from, end = overlapEnd - from)
            } else null
        }.sanitize((to - from).coerceAtLeast(0))

    private fun List<TextInlineStyle>.sanitize(length: Int): List<TextInlineStyle> = mapNotNull { span ->
        val start = span.start.coerceIn(0, length)
        val end = span.end.coerceIn(start, length)
        if (end > start && (span.bold || span.italic || span.highlight != null)) span.copy(start = start, end = end) else null
    }
}
