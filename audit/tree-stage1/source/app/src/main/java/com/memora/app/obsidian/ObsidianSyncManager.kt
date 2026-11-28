package com.memora.app.obsidian

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.memora.app.data.local.AppDatabase
import com.memora.app.data.local.NoteEntity
import com.memora.app.data.local.NoteType
import com.memora.app.data.local.ObsidianSyncRecordEntity
import com.memora.app.data.local.TopilmaCardRow
import com.memora.app.data.model.NoteBlock
import com.memora.app.data.model.NoteBlocksCodec
import com.memora.app.media.MediaStorage
import com.memora.app.prefs.UserPreferences
import com.memora.app.share.TopilmaShareManager
import com.memora.app.ui.components.tagNames
import com.memora.app.util.HashtagUtils
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ObsidianSyncManager(
    context: Context,
    private val db: AppDatabase,
    private val preferences: UserPreferences,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val notes = db.noteDao()
    private val cards = db.topilmaCardDao()
    private val sync = db.obsidianSyncDao()
    private val mediaStorage = MediaStorage(appContext)
    private val debounceJobs = ConcurrentHashMap<String, Job>()

    val pendingCount: Flow<Int> = sync.observePendingCount()
    val lastSyncedAt: Flow<Long?> = sync.observeLastSyncedAt()
    val lastError: Flow<String?> = sync.observeLastError()

    companion object {
        const val TYPE_SHORT = "SHORT"
        const val TYPE_LONG = "LONG"
        const val TYPE_TOPILMA = "TOPILMA"
        private const val LEGACY_START = "<!-- MEMORA:START -->"
        private const val LEGACY_END = "<!-- MEMORA:END -->"
        private const val LEGACY_HINT = "<!-- Bu joydan pastga Obsidian ichida qo‘shimcha yozishingiz mumkin. Memora uni saqlab qoladi. -->"
        private const val DEBOUNCE_MS = 1500L
    }

    suspend fun connect(treeUri: Uri, exportExisting: Boolean) {
        preferences.setObsidianTreeUri(treeUri.toString())
        preferences.setObsidianExportExisting(exportExisting)
        preferences.setObsidianEnabled(true)
        ensureStructure(treeUri)
        if (exportExisting) markAllExistingPending()
        // Existing pending writes (for example after a moved vault) resume immediately.
        syncPending()
    }

    suspend fun setEnabled(enabled: Boolean) {
        if (enabled) {
            val uri = currentTreeUri() ?: return
            ensureStructure(uri)
        }
        preferences.setObsidianEnabled(enabled)
        if (enabled) syncPending()
    }

    suspend fun clearConnection() {
        preferences.setObsidianEnabled(false)
        preferences.setObsidianTreeUri(null)
    }

    suspend fun noteChanged(noteId: String) {
        val settings = preferences.settings.first()
        if (!settings.obsidianEnabled || settings.obsidianTreeUri.isNullOrBlank()) return
        val note = notes.getById(noteId) ?: return
        val type = if (note.type == NoteType.SHORT.name) TYPE_SHORT else TYPE_LONG
        markPending(type, noteId)
        schedule(type, noteId)
    }

    suspend fun topilmaChanged(cardId: String) {
        val settings = preferences.settings.first()
        if (!settings.obsidianEnabled || settings.obsidianTreeUri.isNullOrBlank()) return
        if (cards.getById(cardId) == null) return
        markPending(TYPE_TOPILMA, cardId)
        schedule(TYPE_TOPILMA, cardId)
    }

    suspend fun sourceChanged(sourceId: String) {
        cards.idsForSource(sourceId).forEach { topilmaChanged(it) }
    }

    suspend fun forceNote(noteId: String) {
        val settings = preferences.settings.first()
        if (!settings.obsidianEnabled || settings.obsidianTreeUri.isNullOrBlank()) return
        debounceJobs.remove("note:$noteId")?.cancel()
        val note = notes.getById(noteId) ?: return
        val type = if (note.type == NoteType.SHORT.name) TYPE_SHORT else TYPE_LONG
        markPending(type, noteId)
        syncOne(type, noteId)
    }

    suspend fun forceTopilma(cardId: String) {
        val settings = preferences.settings.first()
        if (!settings.obsidianEnabled || settings.obsidianTreeUri.isNullOrBlank()) return
        debounceJobs.remove("topilma:$cardId")?.cancel()
        if (cards.getById(cardId) == null) return
        markPending(TYPE_TOPILMA, cardId)
        syncOne(TYPE_TOPILMA, cardId)
    }

    suspend fun markAllExistingPending() {
        notes.getForExport(null, null, null).forEach { note ->
            markPending(if (note.type == NoteType.SHORT.name) TYPE_SHORT else TYPE_LONG, note.id)
        }
        cards.getAll().forEach { card -> markPending(TYPE_TOPILMA, card.id) }
    }

    suspend fun resyncAll() {
        markAllExistingPending()
        syncPending()
    }

    suspend fun syncPending() {
        val settings = preferences.settings.first()
        if (!settings.obsidianEnabled || settings.obsidianTreeUri.isNullOrBlank()) return
        sync.pending().forEach { record -> syncOne(record.entityType, record.entityId) }
    }

    suspend fun ensureStructure(treeUri: Uri? = null) {
        val resolvedTreeUri = treeUri ?: currentTreeUri() ?: return
        withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(appContext, resolvedTreeUri) ?: error("Obsidian papkasini ochib bo‘lmadi")
        val memora = root.ensureDirectory("Memora")
        memora.ensureDirectory("Qisqa matn").ensureDirectory("_assets")
        memora.ensureDirectory("Topilmalar").ensureDirectory("_assets")
        memora.ensureDirectory("Uzun matn").ensureDirectory("_assets")
        }
    }

    fun kickPendingOnLaunch() {
        scope.launch(Dispatchers.IO) {
            runCatching { syncPending() }
        }
    }

    private suspend fun markPending(type: String, id: String) {
        val key = key(type, id)
        val existing = sync.get(key)
        sync.upsert(
            (existing ?: ObsidianSyncRecordEntity(key, type, id)).copy(
                state = "PENDING",
                lastError = null,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun schedule(type: String, id: String) {
        val jobKey = if (type == TYPE_TOPILMA) "topilma:$id" else "note:$id"
        debounceJobs.remove(jobKey)?.cancel()
        debounceJobs[jobKey] = scope.launch(Dispatchers.IO) {
            delay(DEBOUNCE_MS)
            runCatching { syncOne(type, id) }
            debounceJobs.remove(jobKey)
        }
    }

    private suspend fun syncOne(type: String, id: String) = withContext(Dispatchers.IO) {
        val tree = currentTreeUri() ?: return@withContext
        val settings = preferences.settings.first()
        if (!settings.obsidianEnabled) return@withContext
        val now = System.currentTimeMillis()
        val record = sync.get(key(type, id)) ?: ObsidianSyncRecordEntity(key(type, id), type, id)
        try {
            val relative = when (type) {
                TYPE_SHORT, TYPE_LONG -> {
                    val note = notes.getById(id)
                    if (note == null) {
                        // Memora deletion intentionally leaves the already-exported Obsidian file intact.
                        sync.delete(key(type, id))
                        return@withContext
                    }
                    syncNote(tree, note, record)
                }
                TYPE_TOPILMA -> {
                    val row = cards.getRowById(id)
                    if (row == null) {
                        // Do not delete the Obsidian copy when its Memora source is gone.
                        sync.delete(key(type, id))
                        return@withContext
                    }
                    syncTopilma(
                        tree,
                        row,
                        record,
                        TopilmaShareManager.activeBackgroundArgb(appContext, settings)
                    )
                }
                else -> return@withContext
            }
            sync.upsert(
                record.copy(
                    relativePath = relative,
                    state = "SYNCED",
                    lastSyncedAt = now,
                    lastError = null,
                    updatedAt = now
                )
            )
        } catch (t: Throwable) {
            sync.upsert(
                record.copy(
                    state = "PENDING",
                    lastError = t.message ?: t.javaClass.simpleName,
                    updatedAt = now
                )
            )
        }
    }

    private fun syncNote(tree: Uri, note: NoteEntity, record: ObsidianSyncRecordEntity): String {
        val folderName = if (note.type == NoteType.SHORT.name) "Qisqa matn" else "Uzun matn"
        val folder = folder(tree, folderName)
        val assets = folder.ensureDirectory("_assets")
        val fileName = markdownFileName(note.title.ifBlank { "Sarlavhasiz" })
        val file = resolveOrCreateMarkdown(folder, record.relativePath, fileName, note.id)
        val blocks = NoteBlocksCodec.decode(note.blocksJson, note.plainText)
        val body = buildString {
            append("# ").append(note.title.ifBlank { "Sarlavhasiz" }).append("\n\n")
            blocks.forEach { block ->
                when (block) {
                    is NoteBlock.Text -> HashtagUtils.withoutTags(block.text).takeIf { it.isNotBlank() }?.let { append(it.trimEnd()).append("\n\n") }
                    is NoteBlock.Image -> {
                        val source = mediaStorage.resolve(block.relativePath)
                        if (source.exists()) {
                            val ext = extensionFor(block.mimeType, source.extension.ifBlank { "jpg" })
                            val assetName = "${note.id.take(8)}-${block.id.take(8)}.$ext"
                            writeAsset(assets, assetName, block.mimeType, source)
                            append("![[_assets/").append(assetName).append("]]\n\n")
                        }
                    }
                    is NoteBlock.Audio -> {
                        val source = mediaStorage.resolve(block.relativePath)
                        if (source.exists()) {
                            val ext = extensionFor(block.mimeType, source.extension.ifBlank { "m4a" })
                            val assetName = "${note.id.take(8)}-${block.id.take(8)}.$ext"
                            writeAsset(assets, assetName, block.mimeType, source)
                            append("![[_assets/").append(assetName).append("]]\n\n")
                        }
                    }
                }
            }
            val tags = HashtagUtils.decode(note.hashtagsJson)
            if (tags.isNotEmpty()) append(HashtagUtils.markdown(tags)).append('\n')
        }.trimEnd()
        writeManagedMarkdown(tree, file, note.id, note.type.lowercase(), body)
        return "Memora/$folderName/${file.name}"
    }

    private fun syncTopilma(
        tree: Uri,
        row: TopilmaCardRow,
        record: ObsidianSyncRecordEntity,
        outerBackgroundArgb: Int
    ): String {
        val folder = folder(tree, "Topilmalar")
        val assets = folder.ensureDirectory("_assets")
        val displayTitle = buildString {
            val author = row.sourceAuthor?.trim().orEmpty()
            if (author.isNotBlank()) append(author).append(": ")
            append(row.sourceTitle?.takeIf { it.isNotBlank() } ?: "Topilma")
        }
        val fileName = markdownFileName(displayTitle)
        val file = resolveOrCreateMarkdown(folder, record.relativePath, fileName, row.id)

        val cardAssetName = "topilma-${row.id}.png"
        val cardBitmap = TopilmaShareManager.renderCardBitmap(appContext, row, outerBackgroundArgb)
        val cardAsset = assets.findFile(cardAssetName) ?: assets.createFile("image/png", cardAssetName)
            ?: error("Topilma kartasi uchun rasm fayli yaratilmadi")
        appContext.contentResolver.openOutputStream(cardAsset.uri, "wt")?.use { stream ->
            cardBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        } ?: error("Topilma kartasi yozilmadi")
        cardBitmap.recycle()

        val tags = row.tagNames()
        val body = buildString {
            append("# ").append(displayTitle).append("\n\n")
            append("![[_assets/").append(cardAssetName).append("]]\n\n")
            append("<details>\n<summary>Matn</summary>\n\n")
            append(HashtagUtils.withoutTags(row.text)).append("\n\n</details>\n")
            if (tags.isNotEmpty()) append("\n").append(HashtagUtils.markdown(tags)).append('\n')
        }.trimEnd()
        writeManagedMarkdown(tree, file, row.id, "topilma", body)
        return "Memora/Topilmalar/${file.name}"
    }

    private fun folder(tree: Uri, child: String): DocumentFile {
        val root = DocumentFile.fromTreeUri(appContext, tree) ?: error("Obsidian vault topilmadi")
        return root.ensureDirectory("Memora").ensureDirectory(child)
    }

    private fun resolveOrCreateMarkdown(
        folder: DocumentFile,
        storedRelativePath: String?,
        desiredName: String,
        id: String
    ): DocumentFile {
        val storedName = storedRelativePath?.substringAfterLast('/')
        var file = storedName?.let(folder::findFile)

        // Migrate 0.9.25 technical filenames such as "Title --a1b2c3d4.md".
        if (file == null) {
            val legacySuffix = "--${id.take(8)}.md"
            file = folder.listFiles().firstOrNull { it.name?.endsWith(legacySuffix) == true }
        }

        if (file != null) {
            val target = availableMarkdownName(folder, desiredName, ignoreName = file.name)
            if (file.name != target) {
                file.renameTo(target)
                file = folder.findFile(target) ?: file
            }
            return file
        }

        val target = availableMarkdownName(folder, desiredName, ignoreName = null)
        return folder.createFile("text/markdown", target)
            ?: error("Markdown fayli yaratilmadi")
    }

    /**
     * Writes a clean user-facing note with no YAML/front-matter or visible Memora markers.
     * The previous managed body is stored under vault/.memora/managed, allowing us to
     * replace only Memora content while preserving user text appended in Obsidian.
     */
    private fun writeManagedMarkdown(tree: Uri, file: DocumentFile, id: String, type: String, managedBody: String) {
        val resolver = appContext.contentResolver
        val existingRaw = runCatching {
            resolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        }.getOrDefault("")
        val previousManaged = readManagedSnapshot(tree, type, id)
        val existing = stripLegacyFrontMatter(existingRaw)

        val userSuffix = when {
            existing.contains(LEGACY_START) && existing.contains(LEGACY_END) -> {
                val end = existing.indexOf(LEGACY_END) + LEGACY_END.length
                existing.substring(end)
            }
            previousManaged.isNotBlank() && existing.startsWith(previousManaged) -> existing.substring(previousManaged.length)
            previousManaged.isNotBlank() -> preserveSuffixByLastManagedLine(existing, previousManaged)
            else -> ""
        }
            .replace(LEGACY_HINT, "")
            .trim()

        val cleanManaged = managedBody.trim()
        val next = buildString {
            append(cleanManaged)
            if (userSuffix.isNotBlank()) append("\n\n").append(userSuffix)
            append('\n')
        }
        resolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { it.write(next) }
            ?: error("Markdown fayliga yozib bo‘lmadi")

        writeManagedSnapshot(tree, type, id, cleanManaged)
        writeSyncMetadata(tree, type, id, file.name.orEmpty())
    }

    private fun stripLegacyFrontMatter(text: String): String {
        if (!text.startsWith("---\n")) return text
        val end = text.indexOf("\n---\n", startIndex = 4)
        if (end < 0) return text
        val header = text.substring(0, end + 5)
        return if ("memora_id:" in header || "memora_type:" in header) text.substring(end + 5).trimStart() else text
    }

    private fun preserveSuffixByLastManagedLine(existing: String, previousManaged: String): String {
        val anchor = previousManaged.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.lastOrNull() ?: return ""
        val index = existing.indexOf(anchor)
        if (index < 0) return ""
        return existing.substring(index + anchor.length)
    }

    private fun managedDirectory(tree: Uri): DocumentFile {
        val root = DocumentFile.fromTreeUri(appContext, tree) ?: error("Obsidian vault topilmadi")
        return root.ensureDirectory(".memora").ensureDirectory("managed")
    }

    private fun snapshotName(type: String, id: String): String =
        "${type.replace(Regex("[^a-zA-Z0-9_-]"), "_")}-${id.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.txt"

    private fun readManagedSnapshot(tree: Uri, type: String, id: String): String {
        val file = managedDirectory(tree).findFile(snapshotName(type, id)) ?: return ""
        return runCatching {
            appContext.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        }.getOrDefault("")
    }

    private fun writeManagedSnapshot(tree: Uri, type: String, id: String, content: String) {
        val dir = managedDirectory(tree)
        val name = snapshotName(type, id)
        val file = dir.findFile(name) ?: dir.createFile("text/plain", name) ?: return
        appContext.contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { it.write(content) }
    }

    private fun writeSyncMetadata(tree: Uri, type: String, id: String, visibleFileName: String) {
        val root = DocumentFile.fromTreeUri(appContext, tree) ?: return
        val dir = root.ensureDirectory(".memora").ensureDirectory("index")
        val name = snapshotName(type, id).removeSuffix(".txt") + ".json"
        val file = dir.findFile(name) ?: dir.createFile("application/json", name) ?: return
        val payload = JSONObject()
            .put("memora_id", id)
            .put("memora_type", type)
            .put("file", visibleFileName)
            .toString()
        appContext.contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { it.write(payload) }
    }

    private fun writeAsset(folder: DocumentFile, name: String, mime: String, source: File) {
        val file = folder.findFile(name) ?: folder.createFile(mime, name) ?: return
        appContext.contentResolver.openOutputStream(file.uri, "wt")?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        }
    }

    private suspend fun currentTreeUri(): Uri? = preferences.settings.first().obsidianTreeUri
        ?.takeIf { it.isNotBlank() }
        ?.let(Uri::parse)

    private fun DocumentFile.ensureDirectory(name: String): DocumentFile =
        findFile(name)?.takeIf { it.isDirectory }
            ?: createDirectory(name)
            ?: error("$name papkasini yaratib bo‘lmadi")

    private fun availableMarkdownName(folder: DocumentFile, desiredName: String, ignoreName: String?): String {
        if (desiredName == ignoreName || folder.findFile(desiredName) == null) return desiredName
        val base = desiredName.removeSuffix(".md")
        var index = 2
        while (true) {
            val candidate = "$base ($index).md"
            if (candidate == ignoreName || folder.findFile(candidate) == null) return candidate
            index++
        }
    }

    private fun markdownFileName(title: String): String {
        val safe = title
            .replace(Regex("[\\/:*?\"<>|]"), "-")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(100)
            .ifBlank { "Memora" }
        return "$safe.md"
    }

    private fun extensionFor(mime: String, fallback: String): String = when {
        mime.contains("jpeg") -> "jpg"
        mime.contains("png") -> "png"
        mime.contains("webp") -> "webp"
        mime.contains("mp4") -> "m4a"
        mime.contains("mpeg") -> "mp3"
        mime.contains("wav") -> "wav"
        else -> fallback.ifBlank { "bin" }
    }

    private fun key(type: String, id: String) = "$type:$id"
}
