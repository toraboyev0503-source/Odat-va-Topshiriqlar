package com.memora.app.util

object TextUtils {
    fun countWords(text: String): Int =
        text.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    fun preview(text: String, maxChars: Int = 180): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        return if (normalized.length <= maxChars) normalized else normalized.take(maxChars).trimEnd() + "…"
    }

    fun plainTextToHtml(text: String): String =
        xmlEscape(text)
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\n", "<br>")

    fun xmlEscape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }
}
