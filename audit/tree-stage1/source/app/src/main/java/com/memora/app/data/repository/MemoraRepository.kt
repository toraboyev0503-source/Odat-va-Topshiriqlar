package com.memora.app.data.repository

import com.memora.app.data.local.AppDatabase
import com.memora.app.data.local.NoteEntity
import com.memora.app.data.local.NoteSummary
import com.memora.app.data.local.ArchiveYearCount
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.memora.app.data.local.NoteType
import com.memora.app.data.model.NoteBlock
import com.memora.app.data.model.NoteBlocksCodec
import androidx.room.withTransaction
import com.memora.app.data.local.ReminderEntity
import com.memora.app.data.local.SavedTitleEntity
import com.memora.app.data.local.TimeTracePhotoEntity
import com.memora.app.obsidian.ObsidianSyncManager
import com.memora.app.util.HashtagUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class NoteWriteAccessException : IllegalStateException("An active Memora subscription is required to change notes")

sealed interface NoteSaveResult {
    data class Success(val noteId: String) : NoteSaveResult
    data object ReadOnly : NoteSaveResult
    data object TitleRequired : NoteSaveResult
    data class Failed(val cause: Throwable) : NoteSaveResult
}

class MemoraRepository(
    private val db: AppDatabase,
    private val canMutateNotes: () -> Boolean = { true },
    private val obsidianSync: ObsidianSyncManager? = null
) {
    private val notes = db.noteDao()
    private val titles = db.savedTitleDao()
    private val reminders = db.reminderDao()
    private val timeTrace = db.timeTraceDao()

    /** Changes only presentation spans, never note text or last-edited timestamp.
     * The optimistic compare-and-swap prevents a late highlight write overwriting an editor save.
     */
    suspend fun setNoteHighlight(
        noteId: String, blockId: String, start: Int, end: Int, color: String?
    ): Boolean {
        if (!canMutateNotes()) return false
        repeat(3) {
            val current = notes.getById(noteId) ?: return false
            val blocks = NoteBlocksCodec.decode(current.blocksJson, current.plainText)
            val index = blocks.indexOfFirst { it is NoteBlock.Text && it.id == blockId }
            if (index < 0) return false
            val textBlock = blocks[index] as NoteBlock.Text
            val altered = com.memora.app.data.model.NoteHighlightPolicy.setColor(textBlock, start, end, color)
            if (altered == textBlock) return true
            val nextBlocks = blocks.toMutableList().apply { this[index] = altered }
            if (notes.changeStyleSpans(noteId, current.blocksJson, NoteBlocksCodec.encode(nextBlocks)) == 1) {
                return true
            }
        }
        return false
    }

    fun observeNotes(type: NoteType): Flow<List<NoteEntity>> =
        notes.observeByType(type.name)

    fun observeAllNotes(): Flow<List<NoteEntity>> = notes.observeAll()

    /** Lightweight calendar and archive data. Full text/JSON is fetched for a single open note. */
    fun observeNoteSummaries(): Flow<List<NoteSummary>> = notes.observeSummaries()
    fun observeArchiveYears(): Flow<List<ArchiveYearCount>> = notes.observeArchiveYears()
    fun observeFilteredCount(filter: NoteListFilter): Flow<Int> =
        notes.observeFilteredCount(NoteSearchQuery.buildCount(filter))
    fun pagedNotes(filter: NoteListFilter): Flow<PagingData<NoteSummary>> = Pager(
        config = PagingConfig(pageSize = 32, initialLoadSize = 64,
            prefetchDistance = 10, enablePlaceholders = false, maxSize = 224),
        pagingSourceFactory = { notes.pageSummaries(NoteSearchQuery.build(filter)) }
    ).flow
    fun observeNote(id: String): Flow<NoteEntity?> = notes.observeById(id)
    fun observeCount(type: NoteType): Flow<Int> = notes.observeCount(type.name)
    fun observeTitles(): Flow<List<SavedTitleEntity>> = titles.observeAll()
    fun observeReminders(): Flow<List<ReminderEntity>> = reminders.observeAll()

    private val albumDao = db.memoryAlbumDao()
    private val recallDao = db.memoryRecallDao()
    fun observeMemoryAlbum() = albumDao.observeAll()
    suspend fun allMemoryAlbum() = albumDao.getAll()
    suspend fun addMemoryAlbum(photo: com.memora.app.data.local.MemoryAlbumPhotoEntity) = albumDao.insert(photo)
    suspend fun hasSameAlbumPhoto(at: Long, caption: String, effect: String) = albumDao.matching(at, caption, effect) > 0
    suspend fun editMemoryAlbum(id: Long, caption: String, effect: String) = albumDao.updateDetails(id, caption, effect)
    suspend fun deleteMemoryAlbum(photo: com.memora.app.data.local.MemoryAlbumPhotoEntity) = albumDao.delete(photo)
    fun observeDeliveredMemories() = recallDao.observeDelivered()
    suspend fun recallForDay(day: String) = recallDao.forDay(day)
    suspend fun recentRecalls(since: Long) = recallDao.recent(since)
    suspend fun insertRecalls(events: List<com.memora.app.data.local.MemoryRecallEventEntity>) = recallDao.insert(events)
    suspend fun getRecall(id: String) = recallDao.byId(id)
    suspend fun markRecall(id: String, state: String, at: Long? = null) = recallDao.mark(id, state, at)
    suspend fun markReadForNote(id: String) = recallDao.markReadForNote(id)
    suspend fun snoozeRecall(id: String, at: Long) = recallDao.snooze(id, at)
    suspend fun pruneRecalls(day: String) = recallDao.prune(day)
    suspend fun allNoteCandidates(): List<NoteEntity> = notes.getForExport(null, null, null)
    suspend fun getNoteForMemory(id: String): NoteEntity? = notes.getById(id)

    fun observeTimeTracePhotos(): Flow<List<TimeTracePhotoEntity>> = timeTrace.observeAll()
    fun observeTimeTracePhoto(dateKey: String): Flow<TimeTracePhotoEntity?> = timeTrace.observeByDate(dateKey)
    suspend fun timeTracePhoto(dateKey: String): TimeTracePhotoEntity? = timeTrace.getByDate(dateKey)
    suspend fun allTimeTracePhotos(): List<TimeTracePhotoEntity> = timeTrace.getAll()
    suspend fun saveTimeTracePhoto(photo: TimeTracePhotoEntity) = timeTrace.upsert(photo)
    suspend fun deleteTimeTracePhoto(photo: TimeTracePhotoEntity) = timeTrace.delete(photo)

    suspend fun getNote(id: String): NoteEntity? = notes.getById(id)
    // Do not hold the editor open for the external vault write. Normal saves call
    // noteChanged(), which schedules the sync in the application-owned scope.
    suspend fun syncNoteToObsidianNow(id: String) { obsidianSync?.forceNote(id) }

    suspend fun <T> withImportTransaction(block: suspend () -> T): T = db.withTransaction { block() }

    /** Delete a file only after a committed save no longer references it anywhere. */
    suspend fun isMediaReferenced(path: String): Boolean =
        notes.getForExport(null, null, null).any { note ->
            NoteBlocksCodec.decode(note.blocksJson, note.plainText).any { block ->
                when (block) {
                    is NoteBlock.Image -> block.relativePath == path
                    is NoteBlock.Audio -> block.relativePath == path
                    is NoteBlock.Text -> false
                }
            }
        }

    /**
     * Note writes never use expected states such as an expired subscription as exceptions.
     * Callers can safely keep an in-memory/recovery snapshot when writing becomes unavailable.
     */
    suspend fun saveNote(
        id: String?,
        type: NoteType,
        title: String,
        html: String,
        plainText: String,
        createdAt: Long?,
        updatedAt: Long = System.currentTimeMillis(),
        blocksJson: String = "",
        hasImages: Boolean = false,
        hasAudio: Boolean = false,
        mood: String? = null,
        hashtagsJson: String? = null,
        notifyObsidian: Boolean = true
    ): NoteSaveResult {
        if (!NoteTitlePolicy.canPersist(title)) return NoteSaveResult.TitleRequired
        if (!canMutateNotes()) return NoteSaveResult.ReadOnly
        val noteId = id ?: UUID.randomUUID().toString()
        val created = createdAt ?: updatedAt
        return try {
            notes.upsert(
                NoteEntity(
                    id = noteId,
                    type = type.name,
                    title = title.trim(),
                    contentHtml = html,
                    plainText = plainText,
                    createdAt = created,
                    updatedAt = updatedAt,
                    wordCount = countWords(plainText),
                    blocksJson = blocksJson,
                    hasImages = hasImages,
                    hasAudio = hasAudio,
                    mood = mood,
                    hashtagsJson = hashtagsJson?.takeIf { it.isNotBlank() }
                        ?: HashtagUtils.encode(HashtagUtils.extract(plainText))
                )
            )
            // A vault/SAF failure must not convert a committed Room write into a
            // misleading "save failed" result. It can be retried on app launch.
            if (notifyObsidian) runCatching { obsidianSync?.noteChanged(noteId) }
            NoteSaveResult.Success(noteId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            NoteSaveResult.Failed(t)
        }
    }

    suspend fun deleteNotes(ids: List<String>) {
        requireWritable()
        notes.deleteByIds(ids)
    }

    suspend fun addTitle(value: String, createdAt: Long = System.currentTimeMillis()): Boolean {
        val cleaned = value.trim()
        if (cleaned.isBlank()) return false
        return titles.insert(SavedTitleEntity(value = cleaned, createdAt = createdAt)) != -1L
    }

    suspend fun deleteTitle(title: SavedTitleEntity) = titles.delete(title)

    suspend fun renameTitle(title: SavedTitleEntity, newValue: String, affectExistingNotes: Boolean = false) {
        // Saved titles are templates/suggestions. Renaming a template must never rewrite history.
        val cleaned = newValue.trim()
        if (cleaned.isBlank() || cleaned == title.value) return
        titles.rename(title.id, cleaned)
    }

    suspend fun setTitlePinned(title: SavedTitleEntity, pinned: Boolean) {
        titles.setPinned(title.id, if (pinned) System.currentTimeMillis() else null)
    }

    suspend fun addReminder(
        hour: Int,
        minute: Int,
        enabled: Boolean = true,
        createdAt: Long = System.currentTimeMillis()
    ): Long = reminders.insert(
        ReminderEntity(hour = hour, minute = minute, enabled = enabled, createdAt = createdAt)
    )

    suspend fun updateReminder(reminder: ReminderEntity) = reminders.upsert(reminder)
    suspend fun deleteReminder(reminder: ReminderEntity) = reminders.delete(reminder)
    suspend fun enabledReminders(): List<ReminderEntity> = reminders.getEnabled()
    suspend fun reminderById(id: Long): ReminderEntity? = reminders.getById(id)

    fun observeCountBetween(type: NoteType, start: Long, end: Long): Flow<Int> =
        notes.observeCountBetween(type.name, start, end)

    suspend fun countBetween(type: NoteType, start: Long, end: Long): Int =
        notes.countBetween(type.name, start, end)

    suspend fun count(type: NoteType): Int = notes.count(type.name)

    suspend fun notesForExport(
        type: NoteType?,
        start: Long?,
        end: Long?
    ): List<NoteEntity> = notes.getForExport(type?.name, start, end)

    suspend fun isDuplicate(title: String, plainText: String, createdAt: Long): Boolean =
        notes.duplicateCount(title, plainText, createdAt) > 0

    suspend fun allTitles(): List<SavedTitleEntity> = titles.getAll()
    suspend fun allReminders(): List<ReminderEntity> = reminders.getAll()

    fun requireWritable() {
        if (!canMutateNotes()) throw NoteWriteAccessException()
    }

    companion object {
        fun countWords(text: String): Int =
            text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }
}
