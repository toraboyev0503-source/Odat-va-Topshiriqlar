package com.memora.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE type = :type ORDER BY createdAt DESC")
    fun observeByType(type: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    // The home/calendar/list no longer materialize contentHtml or blocksJson.
    @Query("""SELECT id, type, title, substr(plainText, 1, 360) AS plainText,
        createdAt, updatedAt, wordCount, hasImages, hasAudio,
        CASE WHEN hasImages = 0 THEN 0 ELSE MAX(1,
            (LENGTH(blocksJson) - LENGTH(REPLACE(blocksJson, '"type":"image"', ''))) / 14) END AS imageCount,
        CASE WHEN hasAudio = 0 THEN 0 ELSE MAX(1,
            (LENGTH(blocksJson) - LENGTH(REPLACE(blocksJson, '"type":"audio"', ''))) / 14) END AS audioCount,
        mood, hashtagsJson
        FROM notes ORDER BY createdAt DESC, id DESC""")
    fun observeSummaries(): Flow<List<NoteSummary>>

    @RawQuery(observedEntities = [NoteEntity::class])
    fun pageSummaries(query: SupportSQLiteQuery): PagingSource<Int, NoteSummary>

    @RawQuery(observedEntities = [NoteEntity::class])
    fun observeFilteredCount(query: SupportSQLiteQuery): Flow<Int>

    @Query("""SELECT CAST(strftime('%Y', createdAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS year,
        COUNT(*) AS count FROM notes GROUP BY year ORDER BY year DESC""")
    fun observeArchiveYears(): Flow<List<ArchiveYearCount>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NoteEntity?

    /** Optimistic update: an editor save made since the reader loaded the note cannot be overwritten. */
    @Query("UPDATE notes SET blocksJson = :newJson WHERE id = :id AND blocksJson = :expectedJson")
    suspend fun changeStyleSpans(id: String, expectedJson: String, newJson: String): Int

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE notes SET title = :newTitle, updatedAt = :updatedAt WHERE title = :oldTitle")
    suspend fun renameTitleInNotes(oldTitle: String, newTitle: String, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM notes WHERE type = :type")
    fun observeCount(type: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE type = :type AND createdAt >= :start AND createdAt < :end")
    fun observeCountBetween(type: String, start: Long, end: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE type = :type AND createdAt >= :start AND createdAt < :end")
    suspend fun countBetween(type: String, start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM notes WHERE type = :type")
    suspend fun count(type: String): Int

    @Query("""
        SELECT * FROM notes
        WHERE (:type IS NULL OR type = :type)
          AND (:start IS NULL OR createdAt >= :start)
          AND (:end IS NULL OR createdAt < :end)
        ORDER BY createdAt ASC
    """)
    suspend fun getForExport(type: String?, start: Long?, end: Long?): List<NoteEntity>

    @Query("""
        SELECT COUNT(*) FROM notes
        WHERE title = :title
          AND plainText = :plainText
          AND createdAt = :createdAt
    """)
    suspend fun duplicateCount(title: String, plainText: String, createdAt: Long): Int
}

@Dao
interface SavedTitleDao {
    @Query("SELECT * FROM saved_titles ORDER BY CASE WHEN pinnedAt IS NULL THEN 1 ELSE 0 END, pinnedAt ASC, createdAt ASC")
    fun observeAll(): Flow<List<SavedTitleEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(title: SavedTitleEntity): Long

    @Delete
    suspend fun delete(title: SavedTitleEntity)

    @Query("UPDATE saved_titles SET value = :newValue WHERE id = :id")
    suspend fun rename(id: Long, newValue: String)

    @Query("UPDATE saved_titles SET pinnedAt = :pinnedAt WHERE id = :id")
    suspend fun setPinned(id: Long, pinnedAt: Long?)

    @Query("SELECT * FROM saved_titles ORDER BY CASE WHEN pinnedAt IS NULL THEN 1 ELSE 0 END, pinnedAt ASC, createdAt ASC")
    suspend fun getAll(): List<SavedTitleEntity>
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY hour ASC, minute ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE enabled = 1 ORDER BY hour ASC, minute ASC")
    suspend fun getEnabled(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders ORDER BY hour ASC, minute ASC")
    suspend fun getAll(): List<ReminderEntity>
}


@Dao
interface TimeTraceDao {
    @Query("SELECT * FROM time_trace_photos ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<TimeTracePhotoEntity>>

    @Query("SELECT * FROM time_trace_photos WHERE dateKey = :dateKey LIMIT 1")
    fun observeByDate(dateKey: String): Flow<TimeTracePhotoEntity?>

    @Query("SELECT * FROM time_trace_photos WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getByDate(dateKey: String): TimeTracePhotoEntity?

    @Query("SELECT * FROM time_trace_photos ORDER BY capturedAt ASC")
    suspend fun getAll(): List<TimeTracePhotoEntity>

    @Upsert
    suspend fun upsert(photo: TimeTracePhotoEntity)

    @Delete
    suspend fun delete(photo: TimeTracePhotoEntity)
}

@Dao
interface ObsidianSyncDao {
    @Query("SELECT * FROM obsidian_sync_records WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): ObsidianSyncRecordEntity?

    @Query("SELECT * FROM obsidian_sync_records WHERE entityType = :entityType AND entityId = :entityId LIMIT 1")
    suspend fun get(entityType: String, entityId: String): ObsidianSyncRecordEntity?

    @Query("SELECT * FROM obsidian_sync_records WHERE state = 'PENDING' ORDER BY updatedAt ASC")
    suspend fun pending(): List<ObsidianSyncRecordEntity>

    @Query("SELECT COUNT(*) FROM obsidian_sync_records WHERE state = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT MAX(lastSyncedAt) FROM obsidian_sync_records WHERE lastSyncedAt IS NOT NULL")
    fun observeLastSyncedAt(): Flow<Long?>

    @Query("SELECT lastError FROM obsidian_sync_records WHERE lastError IS NOT NULL AND lastError != '' ORDER BY updatedAt DESC LIMIT 1")
    fun observeLastError(): Flow<String?>

    @Upsert
    suspend fun upsert(record: ObsidianSyncRecordEntity)

    @Query("DELETE FROM obsidian_sync_records WHERE `key` = :key")
    suspend fun delete(key: String)
}


@Dao
interface MemoryAlbumDao {
    @Query("SELECT * FROM memory_album_photos ORDER BY capturedAt DESC, id DESC")
    fun observeAll(): Flow<List<MemoryAlbumPhotoEntity>>

    @Query("SELECT * FROM memory_album_photos ORDER BY capturedAt ASC, id ASC")
    suspend fun getAll(): List<MemoryAlbumPhotoEntity>

    @Query("SELECT COUNT(*) FROM memory_album_photos WHERE capturedAt = :at AND caption = :caption AND effect = :effect")
    suspend fun matching(at: Long, caption: String, effect: String): Int

    @Insert
    suspend fun insert(photo: MemoryAlbumPhotoEntity): Long

    @Query("UPDATE memory_album_photos SET caption = :caption, effect = :effect WHERE id = :id")
    suspend fun updateDetails(id: Long, caption: String, effect: String)

    @Delete
    suspend fun delete(photo: MemoryAlbumPhotoEntity)
}

@Dao
interface MemoryRecallDao {
    @Query("SELECT * FROM memory_recall_events WHERE dayKey = :day ORDER BY scheduledAt")
    suspend fun forDay(day: String): List<MemoryRecallEventEntity>

    @Query("SELECT * FROM memory_recall_events WHERE scheduledAt >= :since")
    suspend fun recent(since: Long): List<MemoryRecallEventEntity>

    @Query("SELECT * FROM memory_recall_events WHERE state = 'DELIVERED' ORDER BY deliveredAt DESC")
    fun observeDelivered(): Flow<List<MemoryRecallEventEntity>>

    @Query("SELECT * FROM memory_recall_events WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): MemoryRecallEventEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(events: List<MemoryRecallEventEntity>)

    @Query("UPDATE memory_recall_events SET state = :state, deliveredAt = :at WHERE id = :id")
    suspend fun mark(id: String, state: String, at: Long?)

    @Query("UPDATE memory_recall_events SET state = 'READ' WHERE noteId = :noteId AND state = 'DELIVERED'")
    suspend fun markReadForNote(noteId: String)

    @Query("UPDATE memory_recall_events SET scheduledAt = :at, state = 'PLANNED' WHERE id = :id")
    suspend fun snooze(id: String, at: Long)

    @Query("DELETE FROM memory_recall_events WHERE dayKey < :cutoff")
    suspend fun prune(cutoff: String)
}


