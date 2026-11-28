package com.memora.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class TopilmaSourceRow(
    val id: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val canonicalUri: String?,
    val originAppName: String?,
    val originAppIconPath: String?,
    val sourceType: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastAddedAt: Long,
    val archived: Boolean,
    val excludedFromReview: Boolean,
    val cardCount: Int
)

data class TopilmaCardRow(
    val id: String,
    val sourceId: String?,
    val text: String,
    val sourceUri: String?,
    val originAppName: String?,
    val createdAt: Long,
    val originalSourceDate: Long?,
    val updatedAt: Long,
    val lastEditedAt: Long?,
    val favorite: Boolean,
    val likeCount: Long,
    val excludedFromReview: Boolean,
    val lastReviewedAt: Long?,
    val nextReviewAt: Long,
    val reviewIntervalDays: Int,
    val reviewCount: Int,
    val lastRating: String?,
    val sourceTitle: String?,
    val sourceAuthor: String?,
    val sourceCoverPath: String?,
    val sourceType: String?,
    val commentCount: Int,
    val imageCount: Int,
    val tagNamesCsv: String?
)

data class TopilmaStatsRow(
    val cardCount: Int,
    val sourceCount: Int,
    val favoriteCount: Int,
    val reviewedCount: Int,
    val addedThisMonth: Int
)

data class TopilmaCardCommentCount(val cardId: String, val commentCount: Int)

@Dao
interface TopilmaSourceDao {
    @Query(
        """SELECT s.id, s.title, s.author, s.coverPath, s.canonicalUri, s.originAppName,
        s.originAppIconPath, s.sourceType, s.createdAt, s.updatedAt, s.lastAddedAt,
        s.archived, s.excludedFromReview,
        (SELECT COUNT(*) FROM topilma_cards c WHERE c.sourceId = s.id AND c.deletedAt IS NULL) AS cardCount
        FROM topilma_sources s WHERE s.archived = 0 ORDER BY s.lastAddedAt DESC"""
    )
    fun observeActiveRows(): Flow<List<TopilmaSourceRow>>

    @Query(
        """SELECT s.id, s.title, s.author, s.coverPath, s.canonicalUri, s.originAppName,
        s.originAppIconPath, s.sourceType, s.createdAt, s.updatedAt, s.lastAddedAt,
        s.archived, s.excludedFromReview,
        (SELECT COUNT(*) FROM topilma_cards c WHERE c.sourceId = s.id AND c.deletedAt IS NULL) AS cardCount
        FROM topilma_sources s WHERE s.archived = 1 ORDER BY s.lastAddedAt DESC"""
    )
    fun observeArchivedRows(): Flow<List<TopilmaSourceRow>>

    @Query("SELECT * FROM topilma_sources WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<TopilmaSourceEntity?>

    @Query("SELECT * FROM topilma_sources WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TopilmaSourceEntity?

    @Query("SELECT * FROM topilma_sources WHERE canonicalUri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): TopilmaSourceEntity?

    @Query("SELECT * FROM topilma_sources WHERE normalizedTitle = :title AND normalizedAuthor = :author LIMIT 1")
    suspend fun findByTitleAuthor(title: String, author: String?): TopilmaSourceEntity?

    @Query("SELECT * FROM topilma_sources WHERE normalizedTitle = :title AND originAppPackage = :originPackage LIMIT 1")
    suspend fun findByTitleOrigin(title: String, originPackage: String?): TopilmaSourceEntity?

    @Query("SELECT * FROM topilma_sources WHERE normalizedTitle = :title LIMIT 2")
    suspend fun findByTitle(title: String): List<TopilmaSourceEntity>

    @Query("SELECT * FROM topilma_sources ORDER BY createdAt ASC")
    suspend fun getAll(): List<TopilmaSourceEntity>

    @Upsert
    suspend fun upsert(source: TopilmaSourceEntity)

    @Upsert
    suspend fun upsertAll(items: List<TopilmaSourceEntity>)

    @Query("UPDATE topilma_sources SET lastAddedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun touch(id: String, at: Long)

    @Query("UPDATE topilma_sources SET archived = :archived, updatedAt = :at WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, at: Long)

    @Query("UPDATE topilma_sources SET excludedFromReview = :excluded, updatedAt = :at WHERE id = :id")
    suspend fun setExcluded(id: String, excluded: Boolean, at: Long)

    @Query("UPDATE topilma_sources SET coverPath = :coverPath, updatedAt = :at, userEdited = 1 WHERE id = :id")
    suspend fun setCover(id: String, coverPath: String?, at: Long)

    @Query("""UPDATE topilma_sources SET
        title = CASE WHEN :title IS NULL THEN title ELSE :title END,
        normalizedTitle = CASE WHEN :normalizedTitle IS NULL THEN normalizedTitle ELSE :normalizedTitle END,
        author = CASE WHEN author IS NULL THEN :author ELSE author END,
        normalizedAuthor = CASE WHEN normalizedAuthor IS NULL THEN :normalizedAuthor ELSE normalizedAuthor END,
        coverPath = CASE WHEN coverPath IS NULL THEN :coverPath ELSE coverPath END,
        updatedAt = :at
        WHERE id = :id AND userEdited = 0""")
    suspend fun applyEnrichment(
        id: String,
        title: String?,
        normalizedTitle: String?,
        author: String?,
        normalizedAuthor: String?,
        coverPath: String?,
        at: Long
    )

    @Query("DELETE FROM topilma_sources WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TopilmaCardDao {
    @Query(
        """SELECT c.id, c.sourceId, c.text, c.sourceUri, c.originAppName, c.createdAt,
        c.originalSourceDate, c.updatedAt, c.lastEditedAt, c.favorite, c.likeCount, c.excludedFromReview,
        c.lastReviewedAt, c.nextReviewAt, c.reviewIntervalDays, c.reviewCount, c.lastRating,
        s.title AS sourceTitle, s.author AS sourceAuthor, s.coverPath AS sourceCoverPath,
        s.sourceType AS sourceType,
        (SELECT COUNT(*) FROM topilma_comments m WHERE m.cardId = c.id) AS commentCount,
        (SELECT COUNT(*) FROM topilma_images i WHERE i.cardId = c.id) AS imageCount,
        (SELECT GROUP_CONCAT(t.name, ' ') FROM topilma_card_tags ct JOIN topilma_tags t ON t.id = ct.tagId WHERE ct.cardId = c.id) AS tagNamesCsv
        FROM topilma_cards c LEFT JOIN topilma_sources s ON s.id = c.sourceId
        WHERE c.deletedAt IS NULL ORDER BY c.createdAt DESC LIMIT :limit"""
    )
    fun observeActiveRows(limit: Int): Flow<List<TopilmaCardRow>>

    @Query(
        """SELECT c.id, c.sourceId, c.text, c.sourceUri, c.originAppName, c.createdAt,
        c.originalSourceDate, c.updatedAt, c.lastEditedAt, c.favorite, c.likeCount, c.excludedFromReview,
        c.lastReviewedAt, c.nextReviewAt, c.reviewIntervalDays, c.reviewCount, c.lastRating,
        s.title AS sourceTitle, s.author AS sourceAuthor, s.coverPath AS sourceCoverPath,
        s.sourceType AS sourceType,
        (SELECT COUNT(*) FROM topilma_comments m WHERE m.cardId = c.id) AS commentCount,
        (SELECT COUNT(*) FROM topilma_images i WHERE i.cardId = c.id) AS imageCount,
        (SELECT GROUP_CONCAT(t.name, ' ') FROM topilma_card_tags ct JOIN topilma_tags t ON t.id = ct.tagId WHERE ct.cardId = c.id) AS tagNamesCsv
        FROM topilma_cards c LEFT JOIN topilma_sources s ON s.id = c.sourceId
        WHERE c.deletedAt IS NULL AND c.sourceId = :sourceId ORDER BY c.createdAt DESC"""
    )
    fun observeSourceRows(sourceId: String): Flow<List<TopilmaCardRow>>

    @Query(
        """SELECT c.id, c.sourceId, c.text, c.sourceUri, c.originAppName, c.createdAt,
        c.originalSourceDate, c.updatedAt, c.lastEditedAt, c.favorite, c.likeCount, c.excludedFromReview,
        c.lastReviewedAt, c.nextReviewAt, c.reviewIntervalDays, c.reviewCount, c.lastRating,
        s.title AS sourceTitle, s.author AS sourceAuthor, s.coverPath AS sourceCoverPath,
        s.sourceType AS sourceType,
        (SELECT COUNT(*) FROM topilma_comments m WHERE m.cardId = c.id) AS commentCount,
        (SELECT COUNT(*) FROM topilma_images i WHERE i.cardId = c.id) AS imageCount,
        (SELECT GROUP_CONCAT(t.name, ' ') FROM topilma_card_tags ct JOIN topilma_tags t ON t.id = ct.tagId WHERE ct.cardId = c.id) AS tagNamesCsv
        FROM topilma_cards c LEFT JOIN topilma_sources s ON s.id = c.sourceId
        WHERE c.deletedAt IS NULL AND (
          c.text LIKE '%' || :query || '%' COLLATE NOCASE OR
          s.title LIKE '%' || :query || '%' COLLATE NOCASE OR
          s.author LIKE '%' || :query || '%' COLLATE NOCASE OR
          EXISTS(SELECT 1 FROM topilma_comments m WHERE m.cardId = c.id AND m.text LIKE '%' || :query || '%' COLLATE NOCASE) OR
          EXISTS(SELECT 1 FROM topilma_card_tags ct JOIN topilma_tags t ON t.id = ct.tagId WHERE ct.cardId = c.id AND t.name LIKE '%' || :query || '%' COLLATE NOCASE)
        ) ORDER BY c.createdAt DESC LIMIT 500"""
    )
    fun searchRows(query: String): Flow<List<TopilmaCardRow>>

    @Query(
        """SELECT c.id, c.sourceId, c.text, c.sourceUri, c.originAppName, c.createdAt,
        c.originalSourceDate, c.updatedAt, c.lastEditedAt, c.favorite, c.likeCount, c.excludedFromReview,
        c.lastReviewedAt, c.nextReviewAt, c.reviewIntervalDays, c.reviewCount, c.lastRating,
        s.title AS sourceTitle, s.author AS sourceAuthor, s.coverPath AS sourceCoverPath,
        s.sourceType AS sourceType,
        (SELECT COUNT(*) FROM topilma_comments m WHERE m.cardId = c.id) AS commentCount,
        (SELECT COUNT(*) FROM topilma_images i WHERE i.cardId = c.id) AS imageCount,
        (SELECT GROUP_CONCAT(t.name, ' ') FROM topilma_card_tags ct JOIN topilma_tags t ON t.id = ct.tagId WHERE ct.cardId = c.id) AS tagNamesCsv
        FROM topilma_cards c LEFT JOIN topilma_sources s ON s.id = c.sourceId
        WHERE c.id = :id AND c.deletedAt IS NULL LIMIT 1"""
    )
    suspend fun getRowById(id: String): TopilmaCardRow?

    @Query(
        """SELECT c.id, c.sourceId, c.text, c.sourceUri, c.originAppName, c.createdAt,
        c.originalSourceDate, c.updatedAt, c.lastEditedAt, c.favorite, c.likeCount, c.excludedFromReview,
        c.lastReviewedAt, c.nextReviewAt, c.reviewIntervalDays, c.reviewCount, c.lastRating,
        s.title AS sourceTitle, s.author AS sourceAuthor, s.coverPath AS sourceCoverPath,
        s.sourceType AS sourceType,
        (SELECT COUNT(*) FROM topilma_comments m WHERE m.cardId = c.id) AS commentCount,
        (SELECT COUNT(*) FROM topilma_images i WHERE i.cardId = c.id) AS imageCount,
        (SELECT GROUP_CONCAT(t.name, ' ') FROM topilma_card_tags ct JOIN topilma_tags t ON t.id = ct.tagId WHERE ct.cardId = c.id) AS tagNamesCsv
        FROM topilma_cards c LEFT JOIN topilma_sources s ON s.id = c.sourceId
        WHERE c.id = :id AND c.deletedAt IS NULL LIMIT 1"""
    )
    fun observeRowById(id: String): Flow<TopilmaCardRow?>

    @Query("SELECT * FROM topilma_cards WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TopilmaCardEntity?

    @Query("SELECT * FROM topilma_cards WHERE contentFingerprint = :fingerprint AND deletedAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    suspend fun findDuplicate(fingerprint: String): TopilmaCardEntity?

    @Query("SELECT * FROM topilma_cards WHERE deletedAt IS NULL ORDER BY createdAt ASC")
    suspend fun getAll(): List<TopilmaCardEntity>

    @Query(
        """SELECT c.* FROM topilma_cards c
        LEFT JOIN topilma_sources s ON s.id = c.sourceId
        WHERE c.deletedAt IS NULL AND c.excludedFromReview = 0
        AND (s.id IS NULL OR (s.archived = 0 AND s.excludedFromReview = 0))
        AND c.nextReviewAt <= :now ORDER BY c.nextReviewAt ASC LIMIT :limit"""
    )
    suspend fun eligibleForReview(now: Long, limit: Int): List<TopilmaCardEntity>

    @Query("SELECT * FROM topilma_cards WHERE sourceId = :sourceId AND deletedAt IS NULL AND excludedFromReview = 0 ORDER BY createdAt DESC")
    suspend fun sourceCardsForManualReview(sourceId: String): List<TopilmaCardEntity>

    @Query("SELECT id FROM topilma_cards WHERE sourceId = :sourceId AND deletedAt IS NULL")
    suspend fun idsForSource(sourceId: String): List<String>

    @Query("SELECT cardId, COUNT(*) AS commentCount FROM topilma_comments WHERE cardId IN (:cardIds) GROUP BY cardId")
    suspend fun commentCounts(cardIds: List<String>): List<TopilmaCardCommentCount>

    @Upsert
    suspend fun upsert(card: TopilmaCardEntity)

    @Upsert
    suspend fun upsertAll(items: List<TopilmaCardEntity>)

    @Query("""UPDATE topilma_cards SET
        sourceId = :sourceId,
        sourceUri = :sourceUri,
        originAppPackage = :originAppPackage,
        originAppName = :originAppName,
        updatedAt = :at
        WHERE id IN (:ids)""")
    suspend fun move(
        ids: List<String>,
        sourceId: String?,
        sourceUri: String?,
        originAppPackage: String?,
        originAppName: String?,
        at: Long
    )

    @Query("UPDATE topilma_cards SET favorite = :favorite, updatedAt = :at WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<String>, favorite: Boolean, at: Long)

    @Query("UPDATE topilma_cards SET likeCount = likeCount + 1, updatedAt = :at WHERE id = :id")
    suspend fun incrementLike(id: String, at: Long)

    @Query("UPDATE topilma_cards SET excludedFromReview = :excluded, updatedAt = :at WHERE id = :id")
    suspend fun setExcluded(id: String, excluded: Boolean, at: Long)

    @Query("UPDATE topilma_cards SET deletedAt = :at, updatedAt = :at WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<String>, at: Long)

    @Query("UPDATE topilma_cards SET deletedAt = NULL, updatedAt = :at WHERE id IN (:ids)")
    suspend fun restore(ids: List<String>, at: Long)

    @Query("DELETE FROM topilma_cards WHERE sourceId = :sourceId")
    suspend fun deleteForSource(sourceId: String)

    @Query("UPDATE topilma_cards SET sourceId = :targetId, updatedAt = :at WHERE sourceId = :sourceId")
    suspend fun moveSourceCards(sourceId: String, targetId: String, at: Long)

    @Query("UPDATE topilma_cards SET sourceId = NULL, updatedAt = :at WHERE sourceId = :sourceId")
    suspend fun clearSourceCards(sourceId: String, at: Long)

    @Query(
        """UPDATE topilma_cards SET lastReviewedAt = :reviewedAt, nextReviewAt = :nextReviewAt,
        reviewIntervalDays = :intervalDays, reviewCount = reviewCount + 1,
        lastRating = :rating, updatedAt = :reviewedAt WHERE id = :cardId"""
    )
    suspend fun recordReview(cardId: String, rating: String, reviewedAt: Long, nextReviewAt: Long, intervalDays: Int)

    @Query(
        """SELECT
        (SELECT COUNT(*) FROM topilma_cards WHERE deletedAt IS NULL) AS cardCount,
        (SELECT COUNT(*) FROM topilma_sources WHERE archived = 0) AS sourceCount,
        (SELECT COUNT(*) FROM topilma_cards WHERE deletedAt IS NULL AND favorite = 1) AS favoriteCount,
        (SELECT COUNT(*) FROM topilma_cards WHERE deletedAt IS NULL AND reviewCount > 0) AS reviewedCount,
        (SELECT COUNT(*) FROM topilma_cards WHERE deletedAt IS NULL AND createdAt >= :monthStart) AS addedThisMonth"""
    )
    fun observeStats(monthStart: Long): Flow<TopilmaStatsRow>
}

@Dao
interface TopilmaCommentDao {
    @Query("SELECT * FROM topilma_comments WHERE cardId = :cardId ORDER BY createdAt ASC")
    fun observeForCard(cardId: String): Flow<List<TopilmaCommentEntity>>

    @Query("SELECT * FROM topilma_comments ORDER BY createdAt ASC")
    suspend fun getAll(): List<TopilmaCommentEntity>

    @Query("SELECT * FROM topilma_comments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TopilmaCommentEntity?

    @Upsert suspend fun upsert(comment: TopilmaCommentEntity)
    @Upsert suspend fun upsertAll(items: List<TopilmaCommentEntity>)
    @Query("DELETE FROM topilma_comments WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface TopilmaImageDao {
    @Query("SELECT * FROM topilma_images WHERE cardId = :cardId ORDER BY position ASC")
    fun observeForCard(cardId: String): Flow<List<TopilmaImageEntity>>

    @Query("SELECT * FROM topilma_images ORDER BY cardId, position")
    suspend fun getAll(): List<TopilmaImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(images: List<TopilmaImageEntity>)
    @Query("DELETE FROM topilma_images WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface TopilmaTagDao {
    @Query("SELECT * FROM topilma_tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TopilmaTagEntity>>

    @Query("SELECT * FROM topilma_tags ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<TopilmaTagEntity>

    @Query("SELECT * FROM topilma_tags WHERE normalizedName = :name LIMIT 1")
    suspend fun find(name: String): TopilmaTagEntity?

    @Query("SELECT t.* FROM topilma_tags t JOIN topilma_card_tags x ON x.tagId = t.id WHERE x.cardId = :cardId ORDER BY t.name")
    fun observeForCard(cardId: String): Flow<List<TopilmaTagEntity>>

    @Query("SELECT t.* FROM topilma_tags t JOIN topilma_source_tags x ON x.tagId = t.id WHERE x.sourceId = :sourceId ORDER BY t.name")
    fun observeForSource(sourceId: String): Flow<List<TopilmaTagEntity>>

    @Upsert suspend fun upsert(tag: TopilmaTagEntity)
    @Upsert suspend fun upsertAll(items: List<TopilmaTagEntity>)
    @Query("SELECT * FROM topilma_card_tags") suspend fun getAllCardRefs(): List<TopilmaCardTagEntity>
    @Query("SELECT * FROM topilma_source_tags") suspend fun getAllSourceRefs(): List<TopilmaSourceTagEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addCardRefs(refs: List<TopilmaCardTagEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addSourceRefs(refs: List<TopilmaSourceTagEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addCardRef(ref: TopilmaCardTagEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addSourceRef(ref: TopilmaSourceTagEntity)
    @Query("DELETE FROM topilma_card_tags WHERE cardId = :cardId AND tagId = :tagId") suspend fun removeCardRef(cardId: String, tagId: String)
    @Query("DELETE FROM topilma_source_tags WHERE sourceId = :sourceId AND tagId = :tagId") suspend fun removeSourceRef(sourceId: String, tagId: String)
    @Query("UPDATE topilma_tags SET name = :name, normalizedName = :normalized WHERE id = :id") suspend fun rename(id: String, name: String, normalized: String)
    @Query("UPDATE OR IGNORE topilma_card_tags SET tagId = :targetId WHERE tagId = :sourceId") suspend fun mergeCardRefs(sourceId: String, targetId: String)
    @Query("UPDATE OR IGNORE topilma_source_tags SET tagId = :targetId WHERE tagId = :sourceId") suspend fun mergeSourceRefs(sourceId: String, targetId: String)
    @Query("DELETE FROM topilma_tags WHERE id = :id") suspend fun delete(id: String)
    @Query("INSERT OR IGNORE INTO topilma_source_tags(sourceId, tagId) SELECT :targetSourceId, tagId FROM topilma_source_tags WHERE sourceId = :sourceId") suspend fun copySourceRefs(sourceId: String, targetSourceId: String)
    @Query("DELETE FROM topilma_source_tags WHERE sourceId = :sourceId") suspend fun deleteSourceRefs(sourceId: String)
    @Query("DELETE FROM topilma_card_tags WHERE tagId = :tagId") suspend fun deleteCardRefsForTag(tagId: String)
    @Query("DELETE FROM topilma_source_tags WHERE tagId = :tagId") suspend fun deleteSourceRefsForTag(tagId: String)
    @Query("SELECT cardId FROM topilma_card_tags WHERE tagId = :tagId") suspend fun cardIdsForTag(tagId: String): List<String>
}

@Dao
interface TopilmaReviewDao {
    @Query("SELECT * FROM topilma_review_events WHERE cardId = :cardId ORDER BY reviewedAt DESC")
    fun observeHistory(cardId: String): Flow<List<TopilmaReviewEventEntity>>

    @Query("SELECT * FROM topilma_review_events ORDER BY reviewedAt ASC")
    suspend fun getAllEvents(): List<TopilmaReviewEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvent(event: TopilmaReviewEventEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvents(events: List<TopilmaReviewEventEntity>)
    @Query("SELECT * FROM topilma_review_sessions ORDER BY createdAt ASC") suspend fun getAllSessions(): List<TopilmaReviewSessionEntity>
    @Query("SELECT * FROM topilma_review_session_items ORDER BY sessionId, position") suspend fun getAllSessionItems(): List<TopilmaReviewSessionItemEntity>
    @Query("SELECT * FROM topilma_review_sessions WHERE dayKey = :dayKey LIMIT 1") suspend fun getSession(dayKey: String): TopilmaReviewSessionEntity?
    @Upsert suspend fun upsertSession(session: TopilmaReviewSessionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSessionItems(items: List<TopilmaReviewSessionItemEntity>)
    @Query("SELECT * FROM topilma_review_session_items WHERE sessionId = :sessionId ORDER BY position") suspend fun getSessionItems(sessionId: String): List<TopilmaReviewSessionItemEntity>
    @Query("UPDATE topilma_review_session_items SET ratedAt = :ratedAt, rating = :rating WHERE sessionId = :sessionId AND cardId = :cardId") suspend fun rateSessionItem(sessionId: String, cardId: String, ratedAt: Long, rating: String)
}
