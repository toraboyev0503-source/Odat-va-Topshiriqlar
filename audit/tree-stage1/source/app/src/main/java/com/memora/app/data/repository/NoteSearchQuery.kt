package com.memora.app.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery

/** All values are bound; only fixed SQL fragments are concatenated. */
data class NoteListFilter(
    val type: String? = null,
    val search: String = "",
    val mood: String? = null,
    val media: String = "ANY",
    val from: Long? = null,
    val untilExclusive: Long? = null
)

object NoteSearchQuery {
    /** FTS4 prefix search: no user-supplied operators reach MATCH. */
    fun safeFtsQuery(input: String): String =
        Regex("[\\p{L}\\p{N}_]+")
            .findAll(input.trim().take(160))
            .take(12)
            .map { match -> "\"" + match.value.replace("\"", "") + "\"*" }
            .joinToString(" ")

    fun build(filter: NoteListFilter): SimpleSQLiteQuery = buildFor(filter, countOnly = false)

    fun buildCount(filter: NoteListFilter): SimpleSQLiteQuery = buildFor(filter, countOnly = true)

    private fun buildFor(filter: NoteListFilter, countOnly: Boolean): SimpleSQLiteQuery {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()
        filter.type?.let { where += "n.type = ?"; args += it }
        filter.mood?.let { where += "n.mood = ?"; args += it }
        when (filter.media) {
            "IMAGE" -> where += "n.hasImages = 1"
            "AUDIO" -> where += "n.hasAudio = 1"
            "ANY_MEDIA" -> where += "(n.hasImages = 1 OR n.hasAudio = 1)"
            "NO_MEDIA" -> where += "(n.hasImages = 0 AND n.hasAudio = 0)"
        }
        filter.from?.let { where += "n.createdAt >= ?"; args += it }
        filter.untilExclusive?.let { where += "n.createdAt < ?"; args += it }
        if (filter.search.isNotBlank()) {
            val expression = safeFtsQuery(filter.search)
            if (expression.isBlank()) where += "0 = 1"
            else {
                where += "n.id IN (SELECT noteId FROM notes_fts WHERE notes_fts MATCH ?)"
                args += expression
            }
        }
        val clause = if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")
        val selection = if (countOnly) "SELECT COUNT(*)" else
            "SELECT n.id, n.type, n.title, substr(n.plainText, 1, 360) AS plainText, " +
            "n.createdAt, n.updatedAt, n.wordCount, n.hasImages, n.hasAudio, " +
            "CASE WHEN n.hasImages = 0 THEN 0 ELSE MAX(1, " +
            "(LENGTH(n.blocksJson) - LENGTH(REPLACE(n.blocksJson, '\"type\":\"image\"', ''))) / 14) END AS imageCount, " +
            "CASE WHEN n.hasAudio = 0 THEN 0 ELSE MAX(1, " +
            "(LENGTH(n.blocksJson) - LENGTH(REPLACE(n.blocksJson, '\"type\":\"audio\"', ''))) / 14) END AS audioCount, " +
            "n.mood, n.hashtagsJson"
        val order = if (countOnly) "" else " ORDER BY n.createdAt DESC, n.id DESC"
        return SimpleSQLiteQuery(selection + " FROM notes AS n" + clause + order, args.toTypedArray())
    }
}
