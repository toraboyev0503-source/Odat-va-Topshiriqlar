package com.memora.app.media

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class TopilmalarMediaStorage(private val context: Context) {
    private val root = File(context.filesDir, "topilmalar")

    fun storeSharedImage(uri: Uri, mimeType: String?): StoredTopilmaMedia {
        val safeMime = mimeType?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val extension = when (safeMime.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heic", "image/heif" -> "heic"
            else -> "jpg"
        }
        val directory = File(root, "images").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.$extension")
        val input = context.contentResolver.openInputStream(uri) ?: error("Shared image cannot be opened")
        input.use { copyLimited(it, file, MAX_IMAGE_BYTES) }
        return StoredTopilmaMedia(relative(file), safeMime)
    }

    fun storeDownloadedImage(input: InputStream, mimeType: String?): StoredTopilmaMedia {
        val safeMime = mimeType?.substringBefore(';')?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val extension = when (safeMime.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        val directory = File(root, "images").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.$extension")
        input.use { copyLimited(it, file, MAX_IMAGE_BYTES) }
        return StoredTopilmaMedia(relative(file), safeMime)
    }

    fun storeAppIcon(packageName: String, drawable: Drawable): String {
        val directory = File(root, "app-icons").apply { mkdirs() }
        val safeName = packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(directory, "$safeName.png")
        if (!file.exists()) {
            val width = drawable.intrinsicWidth.coerceAtLeast(1).coerceAtMost(192)
            val height = drawable.intrinsicHeight.coerceAtLeast(1).coerceAtMost(192)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        return relative(file)
    }

    fun resolve(relativePath: String): File {
        val canonicalRoot = root.canonicalFile
        val resolved = File(context.filesDir, relativePath).canonicalFile
        require(resolved.path.startsWith(canonicalRoot.path + File.separator)) { "Unsafe Topilmalar media path" }
        return resolved
    }

    fun delete(relativePath: String) {
        runCatching { resolve(relativePath) }.getOrNull()?.takeIf { it.isFile }?.delete()
    }

    /** Returns true only for a newly installed file (safe to roll back on failure). */
    fun restoreBackupEntry(relativePath: String, input: InputStream): Boolean {
        require(relativePath.startsWith("topilmalar/images/") || relativePath.startsWith("topilmalar/app-icons/")) { "Unsafe Topilmalar backup path" }
        require(!relativePath.contains("..")) { "Unsafe Topilmalar backup path" }
        val target = resolve(relativePath)
        if (target.exists()) return false // An import must never replace an existing file.
        target.parentFile?.mkdirs()
        val staged = File.createTempFile(".memora-restore-", ".tmp", target.parentFile)
        try {
            copyLimited(input, staged, MAX_IMAGE_BYTES)
            if (target.exists()) return false
            Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return true
        } finally {
            staged.delete()
        }
    }

    private fun relative(file: File): String = file.relativeTo(context.filesDir).invariantSeparatorsPath

    private fun copyLimited(input: InputStream, target: File, maxBytes: Long) {
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= maxBytes) { "Shared image is too large" }
                output.write(buffer, 0, read)
            }
        }
    }

    companion object {
        private const val MAX_IMAGE_BYTES = 30L * 1024L * 1024L
    }
}

data class StoredTopilmaMedia(val relativePath: String, val mimeType: String)
