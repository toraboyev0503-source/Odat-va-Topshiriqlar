package com.memora.app.recovery

import android.content.Context
import com.memora.app.data.local.NoteType
import org.json.JSONObject
import java.io.File

/** Small crash-recovery snapshots. Stored only in app-private internal storage. */
class EditorRecoveryStore(context: Context) {
    data class Draft(
        val noteId: String?,
        val type: NoteType,
        val title: String,
        val blocksJson: String,
        val mood: String?,
        val createdAt: Long,
        val savedAt: Long
    )

    private val dir = File(context.applicationContext.filesDir, "editor-recovery").apply { mkdirs() }

    fun read(sessionKey: String): Draft? {
        val file = fileFor(sessionKey)
        if (!file.isFile) return null
        return runCatching {
            val root = JSONObject(file.readText())
            Draft(
                noteId = root.optString("noteId").takeIf { it.isNotBlank() },
                type = runCatching { NoteType.valueOf(root.getString("type")) }.getOrDefault(NoteType.LONG),
                title = root.optString("title", ""),
                blocksJson = root.optString("blocksJson", ""),
                mood = root.optString("mood").takeIf { it.isNotBlank() },
                createdAt = root.optLong("createdAt", System.currentTimeMillis()),
                savedAt = root.optLong("savedAt", file.lastModified())
            )
        }.getOrNull()
    }

    fun write(sessionKey: String, draft: Draft) {
        val target = fileFor(sessionKey)
        val temp = File(target.parentFile, target.name + ".tmp")
        val json = JSONObject().apply {
            put("noteId", draft.noteId ?: "")
            put("type", draft.type.name)
            put("title", draft.title)
            put("blocksJson", draft.blocksJson)
            put("mood", draft.mood ?: "")
            put("createdAt", draft.createdAt)
            put("savedAt", draft.savedAt)
        }.toString()
        temp.writeText(json)
        if (!temp.renameTo(target)) {
            target.writeText(json)
            temp.delete()
        }
    }

    fun clear(sessionKey: String) {
        runCatching { fileFor(sessionKey).delete() }
    }

    companion object {
        fun sessionKey(noteId: String?, type: NoteType): String =
            noteId?.let { "note-$it" } ?: "new-${type.name.lowercase()}"
    }

    private fun fileFor(sessionKey: String): File {
        val safe = sessionKey.replace(Regex("[^A-Za-z0-9._-]"), "_").take(180)
        return File(dir, "$safe.json")
    }
}
