package com.memora.app.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.memora.app.data.local.AppDatabase
import com.memora.app.data.local.TopilmaCardEntity
import com.memora.app.data.local.TopilmaCardRow
import com.memora.app.data.local.TopilmaCardTagEntity
import com.memora.app.data.local.TopilmaCommentEntity
import com.memora.app.data.local.TopilmaImageEntity
import com.memora.app.data.local.TopilmaReviewEventEntity
import com.memora.app.data.local.TopilmaReviewRating
import com.memora.app.data.local.TopilmaReviewSessionEntity
import com.memora.app.data.local.TopilmaReviewSessionItemEntity
import com.memora.app.data.local.TopilmaSourceEntity
import com.memora.app.data.local.TopilmaSourceRow
import com.memora.app.data.local.TopilmaSourceTagEntity
import com.memora.app.data.local.TopilmaSourceType
import com.memora.app.data.local.TopilmaStatsRow
import com.memora.app.data.local.TopilmaTagEntity
import com.memora.app.obsidian.ObsidianSyncManager
import com.memora.app.util.HashtagUtils
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class TopilmaCaptureRequest(
    val text: String,
    val sourceTitle: String? = null,
    val author: String? = null,
    val sourceUri: String? = null,
    val originAppPackage: String? = null,
    val originAppName: String? = null,
    val originAppIconPath: String? = null,
    val sourceType: TopilmaSourceType? = null,
    val originalSourceDate: Long? = null,
    val images: List<TopilmaImageDraft> = emptyList()
)

data class TopilmaImageDraft(val relativePath: String, val mimeType: String)

data class TopilmaCaptureResult(
    val cardId: String,
    val sourceId: String?,
    val duplicateOf: String? = null
)

data class TopilmaReviewSessionState(
    val session: TopilmaReviewSessionEntity,
    val items: List<TopilmaReviewSessionItemEntity>,
    val cards: List<TopilmaCardEntity>
) {
    val currentIndex: Int
        get() = items.indexOfFirst { it.ratedAt == null }.let { if (it < 0) items.size else it }
    val completed: Boolean get() = items.isNotEmpty() && currentIndex >= items.size
}

data class TopilmalarBackup(
    val sources: List<TopilmaSourceEntity>,
    val cards: List<TopilmaCardEntity>,
    val comments: List<TopilmaCommentEntity>,
    val images: List<TopilmaImageEntity>,
    val tags: List<TopilmaTagEntity>,
    val cardTags: List<TopilmaCardTagEntity>,
    val sourceTags: List<TopilmaSourceTagEntity>,
    val reviewEvents: List<TopilmaReviewEventEntity>,
    val reviewSessions: List<TopilmaReviewSessionEntity>,
    val reviewSessionItems: List<TopilmaReviewSessionItemEntity>
)

class TopilmalarRepository(
    private val db: AppDatabase,
    private val canMutate: () -> Boolean = { true },
    private val obsidianSync: ObsidianSyncManager? = null
) {
    private val sources = db.topilmaSourceDao()
    private val cards = db.topilmaCardDao()
    private val comments = db.topilmaCommentDao()
    private val images = db.topilmaImageDao()
    private val tags = db.topilmaTagDao()
    private val reviews = db.topilmaReviewDao()

    fun observeSources(): Flow<List<TopilmaSourceRow>> = sources.observeActiveRows()
    fun observeArchivedSources(): Flow<List<TopilmaSourceRow>> = sources.observeArchivedRows()
    fun observeCards(limit: Int = 300): Flow<List<TopilmaCardRow>> = cards.observeActiveRows(limit.coerceIn(50, 10_000))
    fun observeSourceCards(sourceId: String): Flow<List<TopilmaCardRow>> = cards.observeSourceRows(sourceId)
    fun search(query: String): Flow<List<TopilmaCardRow>> = cards.searchRows(query.trim())
    fun observeSource(id: String): Flow<TopilmaSourceEntity?> = sources.observeById(id)
    fun observeComments(cardId: String): Flow<List<TopilmaCommentEntity>> = comments.observeForCard(cardId)
    fun observeImages(cardId: String): Flow<List<TopilmaImageEntity>> = images.observeForCard(cardId)
    fun observeTags(): Flow<List<TopilmaTagEntity>> = tags.observeAll()
    fun observeCardTags(cardId: String): Flow<List<TopilmaTagEntity>> = tags.observeForCard(cardId)
    fun observeSourceTags(sourceId: String): Flow<List<TopilmaTagEntity>> = tags.observeForSource(sourceId)
    fun observeReviewHistory(cardId: String): Flow<List<TopilmaReviewEventEntity>> = reviews.observeHistory(cardId)
    fun observeStats(monthStart: Long): Flow<TopilmaStatsRow> = cards.observeStats(monthStart)

    suspend fun source(id: String): TopilmaSourceEntity? = sources.getById(id)
    suspend fun card(id: String): TopilmaCardEntity? = cards.getById(id)
    suspend fun cardRow(id: String): TopilmaCardRow? = cards.getRowById(id)
    fun observeCardRow(id: String): Flow<TopilmaCardRow?> = cards.observeRowById(id)
    suspend fun syncTopilmaToObsidianNow(id: String) { obsidianSync?.forceTopilma(id) }

    suspend fun capture(request: TopilmaCaptureRequest): TopilmaCaptureResult {
        requireWritable()
        val cleanedText = request.text.trim()
        require(cleanedText.isNotBlank() || request.images.isNotEmpty()) { "Topilma is empty" }
        val now = System.currentTimeMillis()
        val sourceUri = canonicalizeUri(request.sourceUri)
        val title = request.sourceTitle?.trim()?.takeIf { it.isNotBlank() }
        val author = request.author?.trim()?.takeIf { it.isNotBlank() }
        val source = if (title != null || sourceUri != null) {
            findOrCreateSource(
                title = title ?: sourceUriTitle(sourceUri!!),
                author = author,
                uri = sourceUri,
                originPackage = request.originAppPackage,
                originName = request.originAppName,
                originIconPath = request.originAppIconPath,
                type = request.sourceType ?: inferSourceType(sourceUri, request.originAppPackage),
                now = now
            )
        } else null

        val fingerprint = fingerprint(cleanedText, sourceUri, request.images.map { it.relativePath })
        val duplicate = cards.findDuplicate(fingerprint)
        val cardId = UUID.randomUUID().toString()
        val firstReviewAt = firstReviewAt(now, cardId)
        db.withTransaction {
            cards.upsert(
                TopilmaCardEntity(
                    id = cardId,
                    sourceId = source?.id,
                    text = cleanedText,
                    sourceUri = sourceUri,
                    originAppPackage = request.originAppPackage,
                    originAppName = request.originAppName,
                    createdAt = now,
                    originalSourceDate = request.originalSourceDate,
                    updatedAt = now,
                    contentFingerprint = fingerprint,
                    firstReviewAt = firstReviewAt,
                    nextReviewAt = firstReviewAt
                )
            )
            if (request.images.isNotEmpty()) {
                images.insertAll(request.images.mapIndexed { index, draft ->
                    TopilmaImageEntity(
                        id = UUID.randomUUID().toString(),
                        cardId = cardId,
                        relativePath = draft.relativePath,
                        mimeType = draft.mimeType,
                        position = index,
                        createdAt = now
                    )
                })
            }
            source?.let { sources.touch(it.id, now) }
        }
        addHashtags(cardId, HashtagUtils.extract(cleanedText))
        obsidianSync?.topilmaChanged(cardId)
        return TopilmaCaptureResult(cardId, source?.id, duplicate?.id)
    }

    suspend fun createSource(
        title: String,
        author: String? = null,
        type: TopilmaSourceType = TopilmaSourceType.OTHER,
        uri: String? = null
    ): String {
        requireWritable()
        val cleaned = title.trim()
        require(cleaned.isNotBlank())
        val now = System.currentTimeMillis()
        val entity = TopilmaSourceEntity(
            id = UUID.randomUUID().toString(),
            title = cleaned,
            normalizedTitle = normalize(cleaned),
            author = author?.trim()?.takeIf { it.isNotBlank() },
            normalizedAuthor = author?.let(::normalize)?.takeIf { it.isNotBlank() },
            canonicalUri = canonicalizeUri(uri),
            sourceType = type.name,
            createdAt = now,
            updatedAt = now,
            lastAddedAt = now,
            userEdited = true
        )
        sources.upsert(entity)
        return entity.id
    }

    suspend fun createManualCard(sourceId: String, text: String): String {
        requireWritable()
        val cleaned = text.trim()
        require(cleaned.isNotBlank())
        val source = sources.getById(sourceId) ?: error("Source not found")
        val now = System.currentTimeMillis()
        val cardId = UUID.randomUUID().toString()
        val firstReviewAt = firstReviewAt(now, cardId)
        cards.upsert(
            TopilmaCardEntity(
                id = cardId,
                sourceId = source.id,
                text = cleaned,
                sourceUri = source.canonicalUri,
                originAppPackage = source.originAppPackage,
                originAppName = source.originAppName,
                createdAt = now,
                updatedAt = now,
                contentFingerprint = fingerprint(cleaned, source.canonicalUri, emptyList()),
                firstReviewAt = firstReviewAt,
                nextReviewAt = firstReviewAt
            )
        )
        sources.touch(source.id, now)
        addHashtags(cardId, HashtagUtils.extract(cleaned))
        obsidianSync?.topilmaChanged(cardId)
        return cardId
    }

    suspend fun updateSource(source: TopilmaSourceEntity) {
        requireWritable()
        val title = source.title.trim()
        require(title.isNotBlank())
        sources.upsert(
            source.copy(
                title = title,
                normalizedTitle = normalize(title),
                author = source.author?.trim()?.takeIf { it.isNotBlank() },
                normalizedAuthor = source.author?.let(::normalize)?.takeIf { it.isNotBlank() },
                canonicalUri = canonicalizeUri(source.canonicalUri),
                updatedAt = System.currentTimeMillis(),
                userEdited = true
            )
        )
        obsidianSync?.sourceChanged(source.id)
    }

    suspend fun updateCard(card: TopilmaCardEntity, newText: String) {
        requireWritable()
        val now = System.currentTimeMillis()
        val cleaned = newText.trim()
        cards.upsert(
            card.copy(
                text = cleaned,
                updatedAt = now,
                lastEditedAt = now,
                contentFingerprint = fingerprint(cleaned, card.sourceUri, emptyList())
            )
        )
        reconcileHashtags(card.id, HashtagUtils.extract(card.text), HashtagUtils.extract(cleaned))
        obsidianSync?.topilmaChanged(card.id)
    }

    suspend fun addComment(cardId: String, text: String): String {
        requireWritable()
        val cleaned = text.trim()
        require(cleaned.isNotBlank())
        val id = UUID.randomUUID().toString()
        comments.upsert(TopilmaCommentEntity(id, cardId, cleaned, System.currentTimeMillis()))
        obsidianSync?.topilmaChanged(cardId)
        return id
    }

    suspend fun editComment(comment: TopilmaCommentEntity, text: String) {
        requireWritable()
        val cleaned = text.trim()
        require(cleaned.isNotBlank())
        comments.upsert(comment.copy(text = cleaned, editedAt = System.currentTimeMillis()))
        obsidianSync?.topilmaChanged(comment.cardId)
    }

    suspend fun deleteComment(id: String) {
        requireWritable()
        val cardId = comments.getById(id)?.cardId
        comments.delete(id)
        cardId?.let { obsidianSync?.topilmaChanged(it) }
    }
    suspend fun setFavorite(ids: List<String>, favorite: Boolean) { requireWritable(); if (ids.isNotEmpty()) cards.setFavorite(ids, favorite, System.currentTimeMillis()) }
    suspend fun incrementLike(id: String) { requireWritable(); cards.incrementLike(id, System.currentTimeMillis()); obsidianSync?.topilmaChanged(id) }
    suspend fun moveCards(ids: List<String>, sourceId: String?) {
        requireWritable()
        if (ids.isNotEmpty()) {
            val source = sourceId?.let { sources.getById(it) }
            cards.move(
                ids = ids,
                sourceId = source?.id,
                sourceUri = source?.canonicalUri,
                originAppPackage = source?.originAppPackage,
                originAppName = source?.originAppName,
                at = System.currentTimeMillis()
            )
            source?.let { sources.touch(it.id, System.currentTimeMillis()) }
            ids.forEach { obsidianSync?.topilmaChanged(it) }
        }
    }
    suspend fun softDelete(ids: List<String>) { requireWritable(); if (ids.isNotEmpty()) cards.softDelete(ids, System.currentTimeMillis()) }
    suspend fun undoDelete(ids: List<String>) { requireWritable(); if (ids.isNotEmpty()) cards.restore(ids, System.currentTimeMillis()) }
    suspend fun setCardReviewExcluded(id: String, excluded: Boolean) { requireWritable(); cards.setExcluded(id, excluded, System.currentTimeMillis()) }
    suspend fun setSourceReviewExcluded(id: String, excluded: Boolean) { requireWritable(); sources.setExcluded(id, excluded, System.currentTimeMillis()) }
    suspend fun setSourceArchived(id: String, archived: Boolean) { requireWritable(); sources.setArchived(id, archived, System.currentTimeMillis()) }
    suspend fun setSourceCover(id: String, coverPath: String?) { requireWritable(); sources.setCover(id, coverPath, System.currentTimeMillis()); obsidianSync?.sourceChanged(id) }
    suspend fun applyEnrichment(id: String, title: String?, author: String? = null, coverPath: String?) {
        val cleanTitle = title?.trim()?.takeIf { it.isNotBlank() }
        val cleanAuthor = author?.trim()?.takeIf { it.isNotBlank() }
        sources.applyEnrichment(
            id = id,
            title = cleanTitle,
            normalizedTitle = cleanTitle?.let(::normalize),
            author = cleanAuthor,
            normalizedAuthor = cleanAuthor?.let(::normalize),
            coverPath = coverPath,
            at = System.currentTimeMillis()
        )
        obsidianSync?.sourceChanged(id)
    }

    suspend fun mergeSources(sourceId: String, targetId: String) {
        requireWritable()
        require(sourceId != targetId)
        val affected = cards.idsForSource(sourceId)
        db.withTransaction {
            cards.moveSourceCards(sourceId, targetId, System.currentTimeMillis())
            tags.copySourceRefs(sourceId, targetId)
            tags.deleteSourceRefs(sourceId)
            sources.delete(sourceId)
            sources.touch(targetId, System.currentTimeMillis())
        }
        affected.forEach { obsidianSync?.topilmaChanged(it) }
    }

    suspend fun deleteSourceKeepCards(sourceId: String) {
        requireWritable()
        val affected = cards.idsForSource(sourceId)
        db.withTransaction {
            cards.clearSourceCards(sourceId, System.currentTimeMillis())
            tags.deleteSourceRefs(sourceId)
            sources.delete(sourceId)
        }
        affected.forEach { obsidianSync?.topilmaChanged(it) }
    }

    suspend fun addTagToCard(cardId: String, rawName: String): String {
        requireWritable()
        val tag = getOrCreateTag(rawName)
        tags.addCardRef(TopilmaCardTagEntity(cardId, tag.id))
        obsidianSync?.topilmaChanged(cardId)
        return tag.id
    }

    suspend fun addTagToSource(sourceId: String, rawName: String): String {
        requireWritable()
        val tag = getOrCreateTag(rawName)
        tags.addSourceRef(TopilmaSourceTagEntity(sourceId, tag.id))
        return tag.id
    }

    suspend fun removeTagFromCard(cardId: String, tagId: String) { requireWritable(); tags.removeCardRef(cardId, tagId); obsidianSync?.topilmaChanged(cardId) }
    suspend fun removeTagFromSource(sourceId: String, tagId: String) { requireWritable(); tags.removeSourceRef(sourceId, tagId) }
    suspend fun renameTag(tag: TopilmaTagEntity, name: String) {
        requireWritable()
        val cleaned = name.trim()
        require(cleaned.isNotBlank())
        val affected = tags.cardIdsForTag(tag.id)
        tags.rename(tag.id, cleaned, normalize(cleaned))
        affected.forEach { obsidianSync?.topilmaChanged(it) }
    }

    suspend fun mergeTags(sourceTagId: String, targetTagId: String) {
        requireWritable()
        require(sourceTagId != targetTagId)
        val affected = (tags.cardIdsForTag(sourceTagId) + tags.cardIdsForTag(targetTagId)).distinct()
        db.withTransaction {
            tags.mergeCardRefs(sourceTagId, targetTagId)
            tags.mergeSourceRefs(sourceTagId, targetTagId)
            tags.deleteCardRefsForTag(sourceTagId)
            tags.deleteSourceRefsForTag(sourceTagId)
            tags.delete(sourceTagId)
        }
        affected.forEach { obsidianSync?.topilmaChanged(it) }
    }

    suspend fun deleteTag(id: String) {
        requireWritable()
        val affected = tags.cardIdsForTag(id)
        db.withTransaction {
            tags.deleteCardRefsForTag(id)
            tags.deleteSourceRefsForTag(id)
            tags.delete(id)
        }
        affected.forEach { obsidianSync?.topilmaChanged(it) }
    }

    suspend fun getOrCreateDailySession(targetCount: Int): TopilmaReviewSessionState {
        val safeTarget = targetCount.coerceIn(3, 20)
        val dayKey = LocalDate.now().toString()
        val existing = reviews.getSession(dayKey)
        if (existing != null) return loadSession(existing)
        requireWritable()
        val now = System.currentTimeMillis()
        val eligible = cards.eligibleForReview(now, 10_000)
        val commentCounts = if (eligible.isEmpty()) emptyMap() else cards.commentCounts(eligible.map { it.id }).associate { it.cardId to it.commentCount }
        val selected = selectBalanced(eligible, safeTarget, dayKey, commentCounts)
        val session = TopilmaReviewSessionEntity(
            id = "daily-$dayKey",
            dayKey = dayKey,
            createdAt = now,
            targetCount = safeTarget
        )
        db.withTransaction {
            reviews.upsertSession(session)
            reviews.insertSessionItems(selected.mapIndexed { index, card ->
                TopilmaReviewSessionItemEntity(session.id, card.id, index)
            })
        }
        return loadSession(session)
    }

    suspend fun createSourceReviewSession(sourceId: String): TopilmaReviewSessionState {
        requireWritable()
        val now = System.currentTimeMillis()
        val sourceCards = cards.sourceCardsForManualReview(sourceId)
        val key = "manual-$sourceId-$now"
        val session = TopilmaReviewSessionEntity(key, key, now, sourceCards.size)
        db.withTransaction {
            reviews.upsertSession(session)
            reviews.insertSessionItems(sourceCards.mapIndexed { index, card -> TopilmaReviewSessionItemEntity(key, card.id, index) })
        }
        return loadSession(session)
    }

    suspend fun randomEligibleCard(): TopilmaCardEntity? {
        val pool = cards.eligibleForReview(System.currentTimeMillis(), 10_000)
        if (pool.isEmpty()) return null
        val seed = LocalDate.now().toEpochDay().toInt()
        return pool[(seed and Int.MAX_VALUE) % pool.size]
    }

    suspend fun rateReview(sessionId: String, cardId: String, rating: TopilmaReviewRating) {
        requireWritable()
        val card = cards.getById(cardId) ?: return
        val now = System.currentTimeMillis()
        val interval = TopilmaReviewPolicy.nextIntervalDays(
            card.reviewIntervalDays,
            card.reviewCount,
            rating,
            stableHash("${rating.name}:${card.id}:${card.reviewCount}")
        )
        val nextAt = now + interval * DAY_MS
        db.withTransaction {
            cards.recordReview(cardId, rating.name, now, nextAt, interval)
            reviews.insertEvent(
                TopilmaReviewEventEntity(
                    id = UUID.randomUUID().toString(),
                    cardId = cardId,
                    rating = rating.name,
                    reviewedAt = now,
                    previousIntervalDays = card.reviewIntervalDays,
                    nextIntervalDays = interval
                )
            )
            reviews.rateSessionItem(sessionId, cardId, now, rating.name)
        }
    }

    suspend fun backupSnapshot(): TopilmalarBackup = TopilmalarBackup(
        sources = sources.getAll(),
        cards = cards.getAll(),
        comments = comments.getAll(),
        images = images.getAll(),
        tags = tags.getAll(),
        cardTags = tags.getAllCardRefs(),
        sourceTags = tags.getAllSourceRefs(),
        reviewEvents = reviews.getAllEvents(),
        reviewSessions = reviews.getAllSessions(),
        reviewSessionItems = reviews.getAllSessionItems()
    )

    suspend fun reviewStreak(): Int {
        val sessions = reviews.getAllSessions().filter { it.dayKey.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        val items = reviews.getAllSessionItems().groupBy { it.sessionId }
        val completedDays = sessions.filter { session ->
            val sessionItems = items[session.id].orEmpty()
            sessionItems.isNotEmpty() && sessionItems.all { it.ratedAt != null }
        }.mapNotNull { runCatching { LocalDate.parse(it.dayKey) }.getOrNull() }.toSet()
        var cursor = LocalDate.now()
        if (cursor !in completedDays) cursor = cursor.minusDays(1)
        var count = 0
        while (cursor in completedDays) { count++; cursor = cursor.minusDays(1) }
        return count
    }

    suspend fun restoreBackup(backup: TopilmalarBackup) {
        requireWritable()
        db.withTransaction {
            sources.upsertAll(backup.sources)
            cards.upsertAll(backup.cards)
            comments.upsertAll(backup.comments)
            images.insertAll(backup.images)
            tags.upsertAll(backup.tags)
            tags.addCardRefs(backup.cardTags)
            tags.addSourceRefs(backup.sourceTags)
            reviews.insertEvents(backup.reviewEvents)
            backup.reviewSessions.forEach { reviews.upsertSession(it) }
            reviews.insertSessionItems(backup.reviewSessionItems)
        }
    }

    fun requireWritable() {
        if (!canMutate()) throw NoteWriteAccessException()
    }

    private suspend fun loadSession(session: TopilmaReviewSessionEntity): TopilmaReviewSessionState {
        val items = reviews.getSessionItems(session.id)
        val cardMap = items.mapNotNull { item -> cards.getById(item.cardId) }.associateBy { it.id }
        return TopilmaReviewSessionState(session, items, items.mapNotNull { cardMap[it.cardId] })
    }

    private suspend fun findOrCreateSource(
        title: String,
        author: String?,
        uri: String?,
        originPackage: String?,
        originName: String?,
        originIconPath: String?,
        type: TopilmaSourceType,
        now: Long
    ): TopilmaSourceEntity {
        val normalizedTitle = normalize(title)
        val normalizedAuthor = author?.let(::normalize)?.takeIf { it.isNotBlank() }
        val existing = uri?.let { sources.findByUri(it) }
            ?: normalizedAuthor?.let { sources.findByTitleAuthor(normalizedTitle, it) }
            ?: originPackage?.let { sources.findByTitleOrigin(normalizedTitle, it) }
            ?: sources.findByTitle(normalizedTitle).singleOrNull()
        if (existing != null) return existing
        val source = TopilmaSourceEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            normalizedTitle = normalizedTitle,
            author = author,
            normalizedAuthor = normalizedAuthor,
            coverPath = if (type == TopilmaSourceType.ARTICLE_WEBSITE) null else originIconPath,
            canonicalUri = uri,
            originAppPackage = originPackage,
            originAppName = originName,
            originAppIconPath = originIconPath,
            sourceType = type.name,
            createdAt = now,
            updatedAt = now,
            lastAddedAt = now
        )
        sources.upsert(source)
        return source
    }

    private suspend fun getOrCreateTag(rawName: String): TopilmaTagEntity {
        val cleaned = rawName.trim().removePrefix("#")
        require(cleaned.isNotBlank())
        val normalized = normalize(cleaned)
        tags.find(normalized)?.let { return it }
        val entity = TopilmaTagEntity(UUID.randomUUID().toString(), cleaned, normalized, System.currentTimeMillis())
        tags.upsert(entity)
        return entity
    }

    private suspend fun addHashtags(cardId: String, names: List<String>) {
        names.forEach { raw ->
            val cleaned = raw.trim().removePrefix("#")
            if (cleaned.isNotBlank()) {
                val tag = getOrCreateTag(cleaned)
                tags.addCardRef(TopilmaCardTagEntity(cardId, tag.id))
            }
        }
    }

    private suspend fun reconcileHashtags(cardId: String, oldNames: List<String>, newNames: List<String>) {
        val oldMap = oldNames.associateBy(::normalize)
        val newMap = newNames.associateBy(::normalize)
        (oldMap.keys - newMap.keys).forEach { normalized ->
            tags.find(normalized)?.let { tags.removeCardRef(cardId, it.id) }
        }
        (newMap.keys - oldMap.keys).forEach { normalized ->
            val raw = newMap.getValue(normalized)
            val tag = getOrCreateTag(raw)
            tags.addCardRef(TopilmaCardTagEntity(cardId, tag.id))
        }
    }

    private fun selectBalanced(
        pool: List<TopilmaCardEntity>,
        target: Int,
        dayKey: String,
        commentCounts: Map<String, Int>
    ): List<TopilmaCardEntity> {
        if (pool.size <= target) return pool
        val now = System.currentTimeMillis()
        val scored = pool.sortedByDescending { card ->
            val overdueDays = ((now - card.nextReviewAt).coerceAtLeast(0L) / DAY_MS).toDouble()
            val unseenBoost = if (card.reviewCount == 0) 100.0 else 0.0
            val oldBoost = card.lastReviewedAt?.let { ChronoUnit.DAYS.between(
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate(),
                LocalDate.now()
            ).toDouble().coerceAtMost(365.0) } ?: 30.0
            val favoriteBoost = if (card.favorite) 8.0 else 0.0
            val commentBoost = (commentCounts[card.id] ?: 0).coerceAtMost(5) * 1.2
            val stableNoise = (stableHash("$dayKey:${card.id}") % 1000) / 1000.0
            unseenBoost + overdueDays * 2.0 + oldBoost * 0.2 + favoriteBoost + commentBoost + stableNoise
        }
        return TopilmaReviewSelector.select(
            scored = scored,
            target = target,
            sourceId = { it.sourceId },
            favorite = { it.favorite }
        )
    }

    private fun firstReviewAt(createdAt: Long, id: String): Long = createdAt + TopilmaReviewPolicy.firstDelayDays(stableHash(id)) * DAY_MS

    private fun normalize(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")

    private fun canonicalizeUri(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val parsed = URI(value)
            if (parsed.scheme !in listOf("http", "https")) return@runCatching value
            val query = parsed.rawQuery
                ?.split('&')
                ?.filterNot { it.substringBefore('=').lowercase(Locale.ROOT).startsWith("utm_") }
                ?.joinToString("&")
                ?.takeIf { it.isNotBlank() }
            URI(
                parsed.scheme.lowercase(Locale.ROOT),
                parsed.userInfo,
                parsed.host?.lowercase(Locale.ROOT),
                parsed.port,
                parsed.path?.trimEnd('/'),
                query,
                null
            ).toString()
        }.getOrDefault(value)
    }

    private fun sourceUriTitle(uri: String): String = runCatching {
        Uri.parse(uri).host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: "Manba"

    private fun inferSourceType(uri: String?, packageName: String?): TopilmaSourceType = when {
        packageName?.contains("reader", true) == true || packageName?.contains("book", true) == true -> TopilmaSourceType.BOOK
        packageName?.contains("chat", true) == true -> TopilmaSourceType.CHAT
        packageName?.contains("instagram", true) == true || packageName?.contains("telegram", true) == true -> TopilmaSourceType.SOCIAL
        uri?.startsWith("http", true) == true -> TopilmaSourceType.ARTICLE_WEBSITE
        else -> TopilmaSourceType.OTHER
    }

    private fun fingerprint(text: String, uri: String?, imagePaths: List<String>): String {
        val raw = buildString {
            append(text.trim().replace(Regex("\\s+"), " "))
            append('|')
            append(uri.orEmpty())
            imagePaths.sorted().forEach { append('|').append(it) }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun stableHash(value: String): Int = value.fold(17) { acc, char -> (acc * 31 + char.code) and Int.MAX_VALUE }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
