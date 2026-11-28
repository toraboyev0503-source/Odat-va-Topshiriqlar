package com.memora.app.data.local

/** Small database projection. Never use these objects for saving or exporting a note. */
data class NoteSummary(
    val id: String,
    val type: String,
    val title: String,
    val plainText: String,
    val createdAt: Long,
    val updatedAt: Long,
    val wordCount: Int,
    val hasImages: Boolean,
    val hasAudio: Boolean,
    val imageCount: Int,
    val audioCount: Int,
    val mood: String?,
    val hashtagsJson: String
) {
    /** Compatibility with the existing visual-only NoteCard. This is NOT a persisted entity. */
    fun asCardEntity(): NoteEntity = NoteEntity(
        id = id, type = type, title = title, contentHtml = "", plainText = plainText,
        createdAt = createdAt, updatedAt = updatedAt, wordCount = wordCount,
        blocksJson = "", hasImages = hasImages, hasAudio = hasAudio,
        mood = mood, hashtagsJson = hashtagsJson
    )
}

data class ArchiveYearCount(val year: Int, val count: Int)
