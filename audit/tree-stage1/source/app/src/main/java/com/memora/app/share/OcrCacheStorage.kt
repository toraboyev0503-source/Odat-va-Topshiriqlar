package com.memora.app.share

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/** Shared bounded staging for OCR images, used by both Topilmalar entry points. */
object OcrCacheStorage {
    private const val MAX_BYTES = 24L * 1024L * 1024L

    fun copyUri(context: Context, uri: Uri): File? {
        val dir = File(context.cacheDir, "topilma-ocr").apply { mkdirs() }
        val target = File(dir, "ocr-${UUID.randomUUID()}.jpg")
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { source ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var copied = 0L
                    while (true) {
                        val length = source.read(buffer)
                        if (length < 0) break
                        copied += length
                        require(copied <= MAX_BYTES) { "OCR input exceeds 24 MiB" }
                        output.write(buffer, 0, length)
                    }
                    require(copied > 0) { "OCR image is empty" }
                }
            }
            target
        } catch (e: Exception) {
            target.delete()
            null
        }
    }
}
