package com.memora.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A durable projection of one existing reminder occurrence, not a second scheduler.
 * currentKey is non-null only for the pending/current-treasure occurrence.
 * SQLite UNIQUE permits multiple historical NULL keys but only one current key.
 */
@Entity(tableName = "memory_tree_recall", indices = [
    Index("sourceKey"), Index(value = ["currentKey"], unique = true),
    Index(value = ["state", "deliveredAt"])
])
data class TreeRecallEntity(
    @PrimaryKey val id: String,
    val sourceKey: String,
    val currentKey: String?,
    val scheduledAt: Long,
    val deliveredAt: Long?,
    val state: String,
    val completedAt: Long? = null,
    val returnedAt: Long? = null,
    val likedAt: Long? = null,
    val impression: String? = null,
    val impressionCreatedAt: Long? = null,
    val impressionEditedAt: Long? = null
)

data class TreeSource(val sourceKey: String, val kind: String, val createdAt: Long)
data class TreeCounts(val total: Long, val onTree: Long, val pending: Long, val treasure: Long) {
    val visible: Long get() = minOf(pending, 5L)
    val queued: Long get() = maxOf(0L, pending - 5L)
    val growthStage: Int get() = when {
        total == 0L -> 0
        total <= 10L -> 1
        total <= 50L -> 2
        total <= 200L -> 3
        total <= 1000L -> 4
        else -> 5
    }
}

/** Only scalar values are observed by Home; no note or photo payload is loaded. */
data class TreeVisualCounts(
    val total: Long, val onTree: Long, val pending: Long, val treasure: Long,
    val shortOnTree: Long, val longOnTree: Long, val photoOnTree: Long
) {
    val counts: TreeCounts get() = TreeCounts(total,onTree,pending,treasure)
}

data class TreeVisibleLeaf(
    val id: String, val sourceKey: String, val kind: String,
    val scheduledAt: Long, val deliveredAt: Long?
)
