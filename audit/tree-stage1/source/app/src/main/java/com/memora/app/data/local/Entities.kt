package com.memora.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class NoteType { SHORT, LONG }

enum class TopilmaSourceType { BOOK, ARTICLE_WEBSITE, CHAT, SOCIAL, DOCUMENT, MESSAGE, OTHER }
enum class TopilmaReviewRating { SOONER, NORMAL, LATER }

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["type", "createdAt"]),
        Index(value = ["title"]),
        Index(value = ["createdAt"])
    ]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val contentHtml: String,
    val plainText: String,
    val createdAt: Long,
    val updatedAt: Long,
    val wordCount: Int,
    val blocksJson: String = "",
    val hasImages: Boolean = false,
    val hasAudio: Boolean = false,
    val mood: String? = null,
    val hashtagsJson: String = "[]"
)


@Entity(
    tableName = "obsidian_sync_records",
    indices = [
        Index(value = ["entityType", "entityId"], unique = true),
        Index(value = ["state", "updatedAt"])
    ]
)
data class ObsidianSyncRecordEntity(
    @PrimaryKey val key: String,
    val entityType: String,
    val entityId: String,
    val relativePath: String? = null,
    val state: String = "PENDING",
    val lastSyncedAt: Long? = null,
    val lastError: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "saved_titles",
    indices = [Index(value = ["value"], unique = true)]
)
data class SavedTitleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val value: String,
    val createdAt: Long = System.currentTimeMillis(),
    val pinnedAt: Long? = null
)

@Entity(
    tableName = "reminders",
    indices = [Index(value = ["hour", "minute"], unique = true)]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "time_trace_photos",
    indices = [Index(value = ["capturedAt"])]
)
data class TimeTracePhotoEntity(
    @PrimaryKey val dateKey: String,
    val relativePath: String,
    val capturedAt: Long,
    val imported: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "topilma_sources",
    indices = [
        Index(value = ["canonicalUri"], unique = true),
        Index(value = ["normalizedTitle", "normalizedAuthor"]),
        Index(value = ["archived", "lastAddedAt"]),
        Index(value = ["sourceType"])
    ]
)
data class TopilmaSourceEntity(
    @PrimaryKey val id: String,
    val title: String,
    val normalizedTitle: String,
    val author: String? = null,
    val normalizedAuthor: String? = null,
    val coverPath: String? = null,
    val canonicalUri: String? = null,
    val originAppPackage: String? = null,
    val originAppName: String? = null,
    val originAppIconPath: String? = null,
    val sourceType: String = TopilmaSourceType.OTHER.name,
    val createdAt: Long,
    val updatedAt: Long,
    val lastAddedAt: Long,
    val archived: Boolean = false,
    val excludedFromReview: Boolean = false,
    val userEdited: Boolean = false
)

@Entity(
    tableName = "topilma_cards",
    indices = [
        Index(value = ["sourceId", "createdAt"]),
        Index(value = ["createdAt"]),
        Index(value = ["updatedAt"]),
        Index(value = ["nextReviewAt"]),
        Index(value = ["favorite"]),
        Index(value = ["contentFingerprint"]),
        Index(value = ["deletedAt"])
    ]
)
data class TopilmaCardEntity(
    @PrimaryKey val id: String,
    val sourceId: String? = null,
    val text: String,
    val sourceUri: String? = null,
    val originAppPackage: String? = null,
    val originAppName: String? = null,
    val createdAt: Long,
    val originalSourceDate: Long? = null,
    val updatedAt: Long,
    val lastEditedAt: Long? = null,
    val favorite: Boolean = false,
    val likeCount: Long = 0L,
    val excludedFromReview: Boolean = false,
    val deletedAt: Long? = null,
    val contentFingerprint: String,
    val firstReviewAt: Long,
    val lastReviewedAt: Long? = null,
    val nextReviewAt: Long,
    val reviewIntervalDays: Int = 0,
    val reviewCount: Int = 0,
    val lastRating: String? = null
)

@Entity(
    tableName = "topilma_comments",
    indices = [Index(value = ["cardId", "createdAt"])]
)
data class TopilmaCommentEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val text: String,
    val createdAt: Long,
    val editedAt: Long? = null
)

@Entity(
    tableName = "topilma_images",
    indices = [Index(value = ["cardId", "position"])]
)
data class TopilmaImageEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val relativePath: String,
    val mimeType: String,
    val position: Int,
    val createdAt: Long
)

@Entity(
    tableName = "topilma_tags",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class TopilmaTagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val createdAt: Long
)

@Entity(tableName = "topilma_card_tags", primaryKeys = ["cardId", "tagId"], indices = [Index("tagId")])
data class TopilmaCardTagEntity(val cardId: String, val tagId: String)

@Entity(tableName = "topilma_source_tags", primaryKeys = ["sourceId", "tagId"], indices = [Index("tagId")])
data class TopilmaSourceTagEntity(val sourceId: String, val tagId: String)

@Entity(
    tableName = "topilma_review_events",
    indices = [Index(value = ["cardId", "reviewedAt"])]
)
data class TopilmaReviewEventEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val rating: String,
    val reviewedAt: Long,
    val previousIntervalDays: Int,
    val nextIntervalDays: Int
)

@Entity(tableName = "topilma_review_sessions", indices = [Index(value = ["dayKey"], unique = true)])
data class TopilmaReviewSessionEntity(
    @PrimaryKey val id: String,
    val dayKey: String,
    val createdAt: Long,
    val targetCount: Int,
    val currentIndex: Int = 0,
    val completedAt: Long? = null
)

@Entity(
    tableName = "topilma_review_session_items",
    primaryKeys = ["sessionId", "cardId"],
    indices = [Index(value = ["sessionId", "position"], unique = true), Index("cardId")]
)
data class TopilmaReviewSessionItemEntity(
    val sessionId: String,
    val cardId: String,
    val position: Int,
    val ratedAt: Long? = null,
    val rating: String? = null
)

/** New album entries never overwrite the user's legacy Time Trace photos. */
@Entity(tableName = "memory_album_photos", indices = [Index("capturedAt")])
data class MemoryAlbumPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val relativePath: String,
    val capturedAt: Long,
    val caption: String = "",
    val effect: String = "ORIGINAL"
)

/** A persistent daily selection; notifications and Home render the same entries. */
@Entity(tableName = "memory_recall_events", indices = [Index("dayKey"), Index("noteId"), Index("scheduledAt")])
data class MemoryRecallEventEntity(
    @PrimaryKey val id: String,
    val dayKey: String,
    val noteId: String,
    val scheduledAt: Long,
    val state: String = "PLANNED",
    val deliveredAt: Long? = null
)

