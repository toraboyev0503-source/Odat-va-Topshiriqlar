package com.memora.app.memories

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Originals are stored without an irreversible colour effect or text overlay. */
class MemoryAlbumStorage(private val context: Context) {
    private val root = File(context.filesDir, "memory_album").apply { mkdirs() }
    fun captureFile(): File = File(context.cacheDir, "album-camera-${UUID.randomUUID()}.jpg")
    fun resolve(path: String): File {
        require(path.startsWith("memory_album/") && !path.contains(".."))
        return File(context.filesDir, path)
    }

    fun importPhoto(input: File): String {
        require(input.isFile && input.length() in 1..30_000_000) { "Invalid photo" }
        val bytes = input.readBytes()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "Unsupported photo" }
        var sample = 1
        while (maxOf(options.outWidth / sample, options.outHeight / sample) > 2200) sample *= 2
        val raw = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: error("Image could not be decoded")
        val orientation = ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val normalized = if (degrees != 0f) Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height,
            Matrix().apply { postRotate(degrees) }, true) else raw
        val key = "${UUID.randomUUID()}.jpg"
        val output = File(root, key)
        try {
            FileOutputStream(output).use { check(normalized.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
            return "memory_album/$key"
        } finally {
            if (normalized !== raw) normalized.recycle()
            raw.recycle()
            // Keep camera staging file until its Room insert commits, so a failed
            // save can be retried without losing the captured photo.
        }
    }

    fun delete(path: String) { resolve(path).delete() }
    fun load(path: String, target: Int = 1400): Bitmap? {
        val file = runCatching { resolve(path) }.getOrNull() ?: return null
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > target) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
