package com.memora.app.export

import com.memora.app.data.local.TopilmaCardEntity
import com.memora.app.data.local.TopilmaCardTagEntity
import com.memora.app.data.local.TopilmaCommentEntity
import com.memora.app.data.local.TopilmaImageEntity
import com.memora.app.data.local.TopilmaReviewEventEntity
import com.memora.app.data.local.TopilmaReviewSessionEntity
import com.memora.app.data.local.TopilmaReviewSessionItemEntity
import com.memora.app.data.local.TopilmaSourceEntity
import com.memora.app.data.local.TopilmaSourceTagEntity
import com.memora.app.data.local.TopilmaTagEntity
import com.memora.app.data.repository.TopilmalarBackup
import org.json.JSONArray
import org.json.JSONObject

object TopilmalarBackupCodec {
    fun toJson(backup: TopilmalarBackup): JSONObject = JSONObject().apply {
        put("sources", JSONArray().apply { backup.sources.forEach { put(sourceToJson(it)) } })
        put("cards", JSONArray().apply { backup.cards.forEach { put(cardToJson(it)) } })
        put("comments", JSONArray().apply { backup.comments.forEach { put(commentToJson(it)) } })
        put("images", JSONArray().apply { backup.images.forEach { put(imageToJson(it)) } })
        put("tags", JSONArray().apply { backup.tags.forEach { put(tagToJson(it)) } })
        put("cardTags", JSONArray().apply { backup.cardTags.forEach { put(JSONObject().put("cardId", it.cardId).put("tagId", it.tagId)) } })
        put("sourceTags", JSONArray().apply { backup.sourceTags.forEach { put(JSONObject().put("sourceId", it.sourceId).put("tagId", it.tagId)) } })
        put("reviewEvents", JSONArray().apply { backup.reviewEvents.forEach { put(eventToJson(it)) } })
        put("reviewSessions", JSONArray().apply { backup.reviewSessions.forEach { put(sessionToJson(it)) } })
        put("reviewSessionItems", JSONArray().apply { backup.reviewSessionItems.forEach { put(sessionItemToJson(it)) } })
    }

    fun fromJson(root: JSONObject): TopilmalarBackup = TopilmalarBackup(
        sources = root.array("sources") { sourceFromJson(it) },
        cards = root.array("cards") { cardFromJson(it) },
        comments = root.array("comments") { commentFromJson(it) },
        images = root.array("images") { imageFromJson(it) },
        tags = root.array("tags") { tagFromJson(it) },
        cardTags = root.array("cardTags") { TopilmaCardTagEntity(it.getString("cardId"), it.getString("tagId")) },
        sourceTags = root.array("sourceTags") { TopilmaSourceTagEntity(it.getString("sourceId"), it.getString("tagId")) },
        reviewEvents = root.array("reviewEvents") { eventFromJson(it) },
        reviewSessions = root.array("reviewSessions") { sessionFromJson(it) },
        reviewSessionItems = root.array("reviewSessionItems") { sessionItemFromJson(it) }
    )

    private fun sourceToJson(item: TopilmaSourceEntity) = JSONObject().apply {
        put("id", item.id); put("title", item.title); put("normalizedTitle", item.normalizedTitle)
        item.author?.let { put("author", it) }; item.normalizedAuthor?.let { put("normalizedAuthor", it) }
        item.coverPath?.let { put("coverPath", it) }; item.canonicalUri?.let { put("canonicalUri", it) }
        item.originAppPackage?.let { put("originAppPackage", it) }; item.originAppName?.let { put("originAppName", it) }
        item.originAppIconPath?.let { put("originAppIconPath", it) }; put("sourceType", item.sourceType)
        put("createdAt", item.createdAt); put("updatedAt", item.updatedAt); put("lastAddedAt", item.lastAddedAt)
        put("archived", item.archived); put("excludedFromReview", item.excludedFromReview); put("userEdited", item.userEdited)
    }

    private fun sourceFromJson(o: JSONObject) = TopilmaSourceEntity(
        id = o.getString("id"), title = o.getString("title"), normalizedTitle = o.getString("normalizedTitle"),
        author = o.optNullable("author"), normalizedAuthor = o.optNullable("normalizedAuthor"),
        coverPath = o.optNullable("coverPath"), canonicalUri = o.optNullable("canonicalUri"),
        originAppPackage = o.optNullable("originAppPackage"), originAppName = o.optNullable("originAppName"),
        originAppIconPath = o.optNullable("originAppIconPath"), sourceType = o.optString("sourceType", "OTHER"),
        createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"), lastAddedAt = o.getLong("lastAddedAt"),
        archived = o.optBoolean("archived"), excludedFromReview = o.optBoolean("excludedFromReview"), userEdited = o.optBoolean("userEdited")
    )

    private fun cardToJson(item: TopilmaCardEntity) = JSONObject().apply {
        put("id", item.id); item.sourceId?.let { put("sourceId", it) }; put("text", item.text)
        item.sourceUri?.let { put("sourceUri", it) }; item.originAppPackage?.let { put("originAppPackage", it) }; item.originAppName?.let { put("originAppName", it) }
        put("createdAt", item.createdAt); item.originalSourceDate?.let { put("originalSourceDate", it) }; put("updatedAt", item.updatedAt)
        item.lastEditedAt?.let { put("lastEditedAt", it) }; put("favorite", item.favorite); put("likeCount", item.likeCount); put("excludedFromReview", item.excludedFromReview)
        item.deletedAt?.let { put("deletedAt", it) }; put("contentFingerprint", item.contentFingerprint); put("firstReviewAt", item.firstReviewAt)
        item.lastReviewedAt?.let { put("lastReviewedAt", it) }; put("nextReviewAt", item.nextReviewAt); put("reviewIntervalDays", item.reviewIntervalDays)
        put("reviewCount", item.reviewCount); item.lastRating?.let { put("lastRating", it) }
    }

    private fun cardFromJson(o: JSONObject) = TopilmaCardEntity(
        id = o.getString("id"), sourceId = o.optNullable("sourceId"), text = o.optString("text"),
        sourceUri = o.optNullable("sourceUri"), originAppPackage = o.optNullable("originAppPackage"), originAppName = o.optNullable("originAppName"),
        createdAt = o.getLong("createdAt"), originalSourceDate = o.optLongOrNull("originalSourceDate"), updatedAt = o.getLong("updatedAt"),
        lastEditedAt = o.optLongOrNull("lastEditedAt"), favorite = o.optBoolean("favorite"), likeCount = o.optLong("likeCount", 0L), excludedFromReview = o.optBoolean("excludedFromReview"),
        deletedAt = o.optLongOrNull("deletedAt"), contentFingerprint = o.getString("contentFingerprint"), firstReviewAt = o.getLong("firstReviewAt"),
        lastReviewedAt = o.optLongOrNull("lastReviewedAt"), nextReviewAt = o.getLong("nextReviewAt"), reviewIntervalDays = o.optInt("reviewIntervalDays"),
        reviewCount = o.optInt("reviewCount"), lastRating = o.optNullable("lastRating")
    )

    private fun commentToJson(i: TopilmaCommentEntity) = JSONObject().put("id", i.id).put("cardId", i.cardId).put("text", i.text).put("createdAt", i.createdAt).apply { i.editedAt?.let { put("editedAt", it) } }
    private fun commentFromJson(o: JSONObject) = TopilmaCommentEntity(o.getString("id"), o.getString("cardId"), o.getString("text"), o.getLong("createdAt"), o.optLongOrNull("editedAt"))
    private fun imageToJson(i: TopilmaImageEntity) = JSONObject().put("id", i.id).put("cardId", i.cardId).put("relativePath", i.relativePath).put("mimeType", i.mimeType).put("position", i.position).put("createdAt", i.createdAt)
    private fun imageFromJson(o: JSONObject) = TopilmaImageEntity(o.getString("id"), o.getString("cardId"), o.getString("relativePath"), o.optString("mimeType", "image/jpeg"), o.optInt("position"), o.getLong("createdAt"))
    private fun tagToJson(i: TopilmaTagEntity) = JSONObject().put("id", i.id).put("name", i.name).put("normalizedName", i.normalizedName).put("createdAt", i.createdAt)
    private fun tagFromJson(o: JSONObject) = TopilmaTagEntity(o.getString("id"), o.getString("name"), o.getString("normalizedName"), o.getLong("createdAt"))
    private fun eventToJson(i: TopilmaReviewEventEntity) = JSONObject().put("id", i.id).put("cardId", i.cardId).put("rating", i.rating).put("reviewedAt", i.reviewedAt).put("previousIntervalDays", i.previousIntervalDays).put("nextIntervalDays", i.nextIntervalDays)
    private fun eventFromJson(o: JSONObject) = TopilmaReviewEventEntity(o.getString("id"), o.getString("cardId"), o.getString("rating"), o.getLong("reviewedAt"), o.optInt("previousIntervalDays"), o.optInt("nextIntervalDays"))
    private fun sessionToJson(i: TopilmaReviewSessionEntity) = JSONObject().put("id", i.id).put("dayKey", i.dayKey).put("createdAt", i.createdAt).put("targetCount", i.targetCount).put("currentIndex", i.currentIndex).apply { i.completedAt?.let { put("completedAt", it) } }
    private fun sessionFromJson(o: JSONObject) = TopilmaReviewSessionEntity(o.getString("id"), o.getString("dayKey"), o.getLong("createdAt"), o.getInt("targetCount"), o.optInt("currentIndex"), o.optLongOrNull("completedAt"))
    private fun sessionItemToJson(i: TopilmaReviewSessionItemEntity) = JSONObject().put("sessionId", i.sessionId).put("cardId", i.cardId).put("position", i.position).apply { i.ratedAt?.let { put("ratedAt", it) }; i.rating?.let { put("rating", it) } }
    private fun sessionItemFromJson(o: JSONObject) = TopilmaReviewSessionItemEntity(o.getString("sessionId"), o.getString("cardId"), o.getInt("position"), o.optLongOrNull("ratedAt"), o.optNullable("rating"))

    private fun <T> JSONObject.array(name: String, transform: (JSONObject) -> T): List<T> {
        val array = optJSONArray(name) ?: return emptyList()
        return buildList { for (index in 0 until array.length()) array.optJSONObject(index)?.let { add(transform(it)) } }
    }

    private fun JSONObject.optNullable(name: String): String? = if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null
    private fun JSONObject.optLongOrNull(name: String): Long? = if (has(name) && !isNull(name)) optLong(name) else null
}
