package com.memora.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryTreeDao {
    // The live source tables are authoritative. No duplicate leaf rows and no full note payloads.
    @Query("""WITH sources AS (
        SELECT 'NOTE:' || id AS sourceKey FROM notes WHERE type IN ('SHORT','LONG')
        UNION ALL SELECT 'PHOTO:' || id AS sourceKey FROM memory_album_photos
    ) SELECT COUNT(*) AS total,
        COALESCE(SUM(CASE WHEN r.id IS NULL THEN 1 ELSE 0 END),0) AS onTree,
        COALESCE(SUM(CASE WHEN r.state='PENDING' THEN 1 ELSE 0 END),0) AS pending,
        COALESCE(SUM(CASE WHEN r.state='TREASURE' THEN 1 ELSE 0 END),0) AS treasure
        FROM sources s LEFT JOIN memory_tree_recall r ON r.currentKey=s.sourceKey""")
    fun observeCounts(): Flow<TreeCounts>

    @Query("""WITH sources AS (
        SELECT 'NOTE:' || id AS sourceKey,type AS kind FROM notes WHERE type IN ('SHORT','LONG')
        UNION ALL SELECT 'PHOTO:' || id AS sourceKey,'PHOTO' AS kind FROM memory_album_photos
    ) SELECT COUNT(*) AS total,
        COALESCE(SUM(CASE WHEN r.id IS NULL THEN 1 ELSE 0 END),0) AS onTree,
        COALESCE(SUM(CASE WHEN r.state='PENDING' THEN 1 ELSE 0 END),0) AS pending,
        COALESCE(SUM(CASE WHEN r.state='TREASURE' THEN 1 ELSE 0 END),0) AS treasure,
        COALESCE(SUM(CASE WHEN r.id IS NULL AND s.kind='SHORT' THEN 1 ELSE 0 END),0) AS shortOnTree,
        COALESCE(SUM(CASE WHEN r.id IS NULL AND s.kind='LONG' THEN 1 ELSE 0 END),0) AS longOnTree,
        COALESCE(SUM(CASE WHEN r.id IS NULL AND s.kind='PHOTO' THEN 1 ELSE 0 END),0) AS photoOnTree
        FROM sources s LEFT JOIN memory_tree_recall r ON r.currentKey=s.sourceKey""")
    fun observeVisualCounts(): Flow<TreeVisualCounts>

    @Query("""WITH sources AS (
        SELECT 'NOTE:' || id AS sourceKey,type AS kind FROM notes WHERE type IN ('SHORT','LONG')
        UNION ALL SELECT 'PHOTO:' || id AS sourceKey,'PHOTO' AS kind FROM memory_album_photos
    ) SELECT r.id,r.sourceKey,s.kind,r.scheduledAt,r.deliveredAt
        FROM memory_tree_recall r JOIN sources s ON s.sourceKey=r.currentKey
        WHERE r.state='PENDING' ORDER BY COALESCE(r.deliveredAt,r.scheduledAt),r.id LIMIT 5""")
    fun observeVisualLeaves(): Flow<List<TreeVisibleLeaf>>

    @Query("""WITH sources AS (
        SELECT 'NOTE:' || id AS sourceKey,type AS kind,createdAt FROM notes WHERE type IN ('SHORT','LONG')
        UNION ALL SELECT 'PHOTO:' || id AS sourceKey,'PHOTO' AS kind,capturedAt AS createdAt FROM memory_album_photos
    ) SELECT s.sourceKey,s.kind,s.createdAt FROM sources s
        WHERE s.createdAt < :cutoff AND s.createdAt > 0 AND NOT EXISTS
        (SELECT 1 FROM memory_tree_recall r WHERE r.currentKey=s.sourceKey)
        ORDER BY EXISTS(SELECT 1 FROM memory_tree_recall r WHERE r.sourceKey=s.sourceKey AND r.state IN ('TREASURE','RETURNED')),
        s.createdAt,s.sourceKey LIMIT :limit""")
    suspend fun candidates(cutoff: Long, limit: Int): List<TreeSource>

    @Query("""SELECT EXISTS(SELECT 1 FROM notes WHERE 'NOTE:' || id = :key AND type IN ('SHORT','LONG')
        UNION ALL SELECT 1 FROM memory_album_photos WHERE 'PHOTO:' || id = :key)""")
    suspend fun sourceExists(key: String): Boolean

    @Query("SELECT * FROM memory_tree_recall WHERE id=:id")
    suspend fun byId(id: String): TreeRecallEntity?

    @Query("SELECT * FROM memory_tree_recall WHERE id=:id")
    fun observeEvent(id: String): Flow<TreeRecallEntity?>

    @Query("SELECT * FROM memory_tree_recall WHERE currentKey=:key AND state='PENDING' LIMIT 1")
    suspend fun currentPending(key: String): TreeRecallEntity?

    @Query("""SELECT COUNT(*) FROM memory_tree_recall WHERE sourceKey=:key AND likedAt IS NOT NULL""")
    fun observeLikeCount(key: String): Flow<Long>

    @Query("""SELECT * FROM memory_tree_recall WHERE sourceKey=:key AND impression IS NOT NULL
        ORDER BY impressionCreatedAt,id""")
    fun observeImpressions(key: String): Flow<List<TreeRecallEntity>>

    @Query("""WITH sources AS (
        SELECT 'NOTE:' || id AS sourceKey,type AS kind,createdAt FROM notes WHERE type IN ('SHORT','LONG')
        UNION ALL SELECT 'PHOTO:' || id AS sourceKey,'PHOTO' AS kind,capturedAt AS createdAt FROM memory_album_photos
    ) SELECT s.sourceKey,s.kind,s.createdAt FROM sources s
        WHERE s.createdAt < :cutoff AND s.createdAt > 0
        AND NOT EXISTS (SELECT 1 FROM memory_tree_recall r WHERE r.currentKey=s.sourceKey)
        AND NOT EXISTS (SELECT 1 FROM memory_recall_events e WHERE e.state='PLANNED' AND
            CASE WHEN substr(e.id,1,5)='tree:' THEN e.noteId ELSE 'NOTE:' || e.noteId END=s.sourceKey)
        AND (:freshOnly=0 OR NOT EXISTS (SELECT 1 FROM memory_tree_recall r
            WHERE r.sourceKey=s.sourceKey AND r.state IN ('TREASURE','RETURNED')))
        ORDER BY s.createdAt,s.sourceKey LIMIT :limit""")
    suspend fun planningCandidates(cutoff: Long, freshOnly: Boolean, limit: Int): List<TreeSource>

    @Query("SELECT * FROM memory_recall_events WHERE state='PLANNED' ORDER BY scheduledAt,id")
    suspend fun plannedEvents(): List<MemoryRecallEventEntity>

    @Query("UPDATE memory_recall_events SET state=:state, deliveredAt=:at WHERE id=:id AND state='PLANNED'")
    suspend fun resolvePlan(id: String,state: String,at: Long?): Int

    @Query("UPDATE memory_recall_events SET state='READ' WHERE id=:id AND state='DELIVERED'")
    suspend fun markOperationalRead(id: String): Int

    @Query("DELETE FROM memory_recall_events WHERE dayKey < :cutoff AND state!='PLANNED'")
    suspend fun pruneOperational(cutoff: String)

    @Query("SELECT * FROM memory_album_photos WHERE id=:id LIMIT 1")
    fun observePhoto(id: Long): Flow<MemoryAlbumPhotoEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: TreeRecallEntity): Long

    @Query("""SELECT * FROM memory_tree_recall WHERE state='PENDING' AND currentKey IS NOT NULL
        ORDER BY COALESCE(deliveredAt,scheduledAt),id LIMIT 5""")
    fun observeVisible(): Flow<List<TreeRecallEntity>>

    @Query("""SELECT * FROM memory_tree_recall WHERE state='TREASURE' AND currentKey IS NOT NULL
        ORDER BY COALESCE(completedAt,deliveredAt,scheduledAt) DESC,id DESC""")
    fun observeTreasure(): Flow<List<TreeRecallEntity>>

    @Query("SELECT * FROM memory_tree_recall WHERE sourceKey=:key ORDER BY scheduledAt,id")
    fun observeHistory(key: String): Flow<List<TreeRecallEntity>>

    @Query("""UPDATE memory_tree_recall SET state='TREASURE',completedAt=:at
        WHERE id=:id AND state='PENDING' AND currentKey IS NOT NULL
        AND :at >= COALESCE(deliveredAt,scheduledAt)""")
    suspend fun complete(id: String, at: Long): Int

    @Query("""UPDATE memory_tree_recall SET likedAt=CASE WHEN :liked THEN COALESCE(likedAt,:at) ELSE NULL END
        WHERE id=:id AND state='PENDING' AND currentKey IS NOT NULL""")
    suspend fun setLike(id: String, liked: Boolean, at: Long): Int

    @Query("""UPDATE memory_tree_recall SET impression=:body,
        impressionEditedAt=CASE WHEN impressionCreatedAt IS NULL THEN NULL ELSE :at END,
        impressionCreatedAt=COALESCE(impressionCreatedAt,:at)
        WHERE id=:id AND state='PENDING' AND currentKey IS NOT NULL""")
    suspend fun setImpression(id: String, body: String, at: Long): Int

    @Query("""UPDATE memory_tree_recall SET state='RETURNED',currentKey=NULL,returnedAt=:at
        WHERE state='TREASURE' AND currentKey IS NOT NULL AND :at >= COALESCE(completedAt,deliveredAt,scheduledAt)""")
    suspend fun returnTreasure(at: Long): Int
}
