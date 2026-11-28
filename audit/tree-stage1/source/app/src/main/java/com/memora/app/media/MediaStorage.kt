package com.memora.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.memora.app.data.model.NoteBlock
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class MediaStorage(private val context: Context) {
    data class CameraCapture(val uri: Uri, val file: File)
    data class ImportedFile(val relativePath: String, val byteCount: Long)

    private val root: File get() = context.filesDir.canonicalFile

    fun importImage(uri: Uri): NoteBlock.Image {
        val staged = stageImage(uri)
        try {
            val bounds = BitmapFactory.Options().also { options ->
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(staged.absolutePath, options)
            }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
            val sample = ImageSampling.sampleSize(
                bounds.outWidth,
                bounds.outHeight,
                ImageSampling.STORED_MAX_DIMENSION,
                ImageSampling.STORED_MAX_PIXELS
            )
            val bitmap = try {
                BitmapFactory.decodeFile(
                    staged.absolutePath,
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                        // The stored result is JPEG, so an alpha channel is unnecessary. RGB_565
                        // halves peak bitmap memory and prevents OOM exits on large camera photos.
                        inPreferredConfig = Bitmap.Config.RGB_565
                        inDither = true
                        inScaled = false
                    }
                ) ?: error("Unable to decode image")
            } catch (oom: OutOfMemoryError) {
                throw IllegalStateException("Image is too large to decode safely", oom)
            }

            val relative = "media/images/${UUID.randomUUID()}.jpg"
            val target = resolve(relative)
            target.parentFile?.mkdirs()
            try {
                FileOutputStream(target).buffered().use { out ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)) {
                        "Unable to save image"
                    }
                }
                require(target.length() > 0L) { "Saved image is empty" }
            } catch (error: Throwable) {
                runCatching { target.delete() }
                throw error
            } finally {
                bitmap.recycle()
            }
            return NoteBlock.Image(relativePath = relative, mimeType = "image/jpeg")
        } finally {
            runCatching { staged.delete() }
        }
    }

    private fun stageImage(uri: Uri): File {
        val staged = File.createTempFile("memora-image-", ".tmp", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Unable to read image")
            input.use { source ->
                FileOutputStream(staged).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_IMAGE_INPUT_BYTES) { "Image file is too large" }
                        output.write(buffer, 0, read)
                    }
                    require(total > 0L) { "Image file is empty" }
                }
            }
            return staged
        } catch (error: Throwable) {
            runCatching { staged.delete() }
            throw error
        }
    }

    fun createCameraCapture(): CameraCapture {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        if (!file.exists()) file.createNewFile()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return CameraCapture(uri, file)
    }

    fun importCameraImage(capture: CameraCapture): NoteBlock.Image =
        try {
            importImage(capture.uri)
        } finally {
            runCatching { capture.file.delete() }
        }

    fun discardCameraCapture(capture: CameraCapture) {
        runCatching { capture.file.delete() }
    }

    fun newAudioFile(): Pair<String, File> {
        val relative = "media/audio/${UUID.randomUUID()}.m4a"
        val target = resolve(relative)
        target.parentFile?.mkdirs()
        return relative to target
    }

    fun normalizedMediaPath(relativePath: String): String? {
        val normalized = relativePath.replace('\\', '/').trim()
        if (normalized.length !in 1..MAX_RELATIVE_PATH_LENGTH) return null
        if (normalized.startsWith('/') || normalized.split('/').any { it.isBlank() || it == "." || it == ".." }) {
            return null
        }
        if (!SAFE_MEDIA_PATH.matches(normalized)) return null
        return normalized
    }

    fun resolve(relativePath: String): File {
        val normalized = requireNotNull(normalizedMediaPath(relativePath)) { "Invalid media path" }
        val resolved = File(root, normalized).canonicalFile
        require(resolved.path.startsWith(root.path + File.separator)) { "Media path escapes app storage" }
        return resolved
    }

    fun delete(relativePath: String) {
        runCatching { resolve(relativePath).delete() }
    }

    fun storeImported(
        relativeFolder: String,
        extension: String,
        input: InputStream,
        maxBytes: Long
    ): ImportedFile {
        require(relativeFolder == "images" || relativeFolder == "audio") { "Invalid media folder" }
        val cleanExt = extension.trimStart('.').lowercase().takeIf { SAFE_EXTENSION.matches(it) } ?: "bin"
        val relative = "media/$relativeFolder/${UUID.randomUUID()}.$cleanExt"
        val target = resolve(relative)
        target.parentFile?.mkdirs()
        var written = 0L
        try {
            FileOutputStream(target).buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    written += read
                    require(written <= maxBytes) { "Backup media entry is too large" }
                    output.write(buffer, 0, read)
                }
            }
        } catch (error: Throwable) {
            runCatching { target.delete() }
            throw error
        }
        return ImportedFile(relative, written)
    }

    companion object {
        private const val MAX_IMAGE_INPUT_BYTES = 64L * 1024L * 1024L
        private const val MAX_RELATIVE_PATH_LENGTH = 240
        private val SAFE_MEDIA_PATH = Regex("^media/(images|audio)/[A-Za-z0-9._-]+$")
        private val SAFE_EXTENSION = Regex("^[a-z0-9]{1,8}$")
    }
}
