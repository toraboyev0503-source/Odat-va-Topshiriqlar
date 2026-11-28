package com.memora.app.util

import org.json.JSONArray

/** Canonical hashtag parsing shared by notes, Topilmalar and Obsidian sync. */
object HashtagUtils {
    private val pattern = Regex("""(?<![\p{L}\p{N}_])#([\p{L}\p{N}_/\-]+)""")

    fun extract(text: String): List<String> {
        val seen = linkedSetOf<String>()
        pattern.findAll(text).forEach { match ->
            val raw = match.groupValues[1].trim()
            if (raw.isNotBlank()) seen += raw
        }
        return seen.toList()
    }

    fun ranges(text: String): List<IntRange> = pattern.findAll(text).map { it.range }.toList()

    fun encode(tags: List<String>): String = JSONArray().apply { tags.forEach(::put) }.toString()

    fun decode(json: String): List<String> = runCatching {
        val array = JSONArray(json.ifBlank { "[]" })
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    fun normalize(raw: String): String = raw.trim().removePrefix("#").lowercase()

    fun markdown(tags: List<String>): String = tags.joinToString(" ") { "#${it.removePrefix("#")}" }

    /**
     * Removes hashtag tokens from a reader/card body while preserving the surrounding prose.
     * Hashtags remain canonical metadata and are rendered separately as chips / Obsidian tags.
     */
    fun withoutTags(text: String): String {
        if (text.isBlank()) return text
        return text
            .lineSequence()
            .map { line ->
                pattern.replace(line, "")
                    .replace(Regex("[ \t]{2,}"), " ")
                    .trimEnd()
            }
            .toList()
            .let { lines ->
                // Keep intentional paragraph breaks, but collapse blank runs created by tag-only lines.
                buildList {
                    var previousBlank = false
                    for (line in lines) {
                        val blank = line.isBlank()
                        if (!blank || !previousBlank) add(line)
                        previousBlank = blank
                    }
                }
            }
            .joinToString("\n")
            .trim()
    }
}
