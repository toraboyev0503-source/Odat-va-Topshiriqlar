package com.memora.app.timetrace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.media.FaceDetector
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TimeTraceStorage(private val context: Context) {
    private val root = File(context.filesDir, "time_trace").apply { mkdirs() }
    private val originals = File(root, "photos").apply { mkdirs() }
    private val thumbs = File(root, "thumbs").apply { mkdirs() }

    fun fileFor(dateKey: String): File = File(originals, "$dateKey.jpg")
    fun thumbFor(dateKey: String): File = File(thumbs, "$dateKey.jpg")
    fun relativePath(dateKey: String): String = "time_trace/photos/$dateKey.jpg"
    fun resolve(relativePath: String): File = File(context.filesDir, relativePath)

    fun createCaptureTarget(dateKey: String): File {
        val pending = File(context.cacheDir, "camera").apply { mkdirs() }
        return File(pending, "time-trace-$dateKey-${System.currentTimeMillis()}.jpg")
    }

    /**
     * Normalises every Time Trace image to a consistent portrait canvas. We deliberately do not
     * beautify, smooth, recolour, or otherwise alter the face. The only transformation is a
     * face-aligned crop + scale when local geometric detection succeeds, otherwise a centred crop.
     * No biometric identity, score, beautification or cloud processing is created.
     */
    fun importAndNormalize(uri: Uri, dateKey: String): StoredTimeTraceImage {
        val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open image")
        val bytes = input.use { it.readBytes() }
        return importAndNormalize(bytes, dateKey)
    }

    fun importAndNormalize(sourceFile: File, dateKey: String): StoredTimeTraceImage {
        val bytes = sourceFile.readBytes()
        val result = importAndNormalize(bytes, dateKey)
        if (sourceFile.parentFile == File(context.cacheDir, "camera")) sourceFile.delete()
        return result
    }

    fun importAndNormalize(bytes: ByteArray, dateKey: String): StoredTimeTraceImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 2400) sample *= 2
        val decodedRaw = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: error("Unable to decode image")
        val rotation = runCatching {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val decoded = if (rotation == 0f) decodedRaw else {
            val rotated = Bitmap.createBitmap(
                decodedRaw, 0, 0, decodedRaw.width, decodedRaw.height,
                Matrix().apply { postRotate(rotation) }, true
            )
            if (rotated !== decodedRaw) decodedRaw.recycle()
            rotated
        }

        val targetWidth = 1080
        val targetHeight = 1350
        val targetRatio = targetWidth.toFloat() / targetHeight
        // Align around the face when Android can detect it locally. This does not identify the
        // person and never leaves the device; it only estimates eye midpoint/distance so daily
        // frames land in a stable place. A deterministic centered crop is the safe fallback.
        val crop = alignedFaceCropRect(decoded, targetRatio)
            ?: centerCropRect(decoded.width, decoded.height, targetRatio)
        val normalized = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(normalized)
        canvas.drawColor(Color.rgb(250, 247, 239))
        canvas.drawBitmap(
            decoded,
            crop,
            RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat()),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )

        val output = fileFor(dateKey)
        FileOutputStream(output).use { normalized.compress(Bitmap.CompressFormat.JPEG, 92, it) }

        val thumbWidth = 240
        val thumbHeight = 300
        val thumb = Bitmap.createScaledBitmap(normalized, thumbWidth, thumbHeight, true)
        FileOutputStream(thumbFor(dateKey)).use { thumb.compress(Bitmap.CompressFormat.JPEG, 82, it) }

        if (thumb !== normalized) thumb.recycle()
        normalized.recycle()
        decoded.recycle()
        return StoredTimeTraceImage(relativePath(dateKey), targetWidth, targetHeight)
    }

    fun delete(dateKey: String) {
        fileFor(dateKey).delete()
        thumbFor(dateKey).delete()
    }

    fun totalBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    companion object {
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        fun todayKey(zoneId: ZoneId = ZoneId.systemDefault()): String = LocalDate.now(zoneId).format(dateFormatter)
        fun dateKey(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
            Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate().format(dateFormatter)
        fun parseDate(dateKey: String): LocalDate = LocalDate.parse(dateKey, dateFormatter)


        /**
         * Uses Android's local FaceDetector only as a geometric helper. No face template, identity
         * or biometric profile is stored. The returned crop keeps the eyes near 37% of the frame
         * height and normalises apparent face size, producing a calmer time-lapse.
         */
        private fun alignedFaceCropRect(bitmap: Bitmap, targetRatio: Float): Rect? {
            if (bitmap.width < 96 || bitmap.height < 96) return null
            val maxDetectWidth = 720
            val scale = minOf(1f, maxDetectWidth.toFloat() / bitmap.width.toFloat())
            var detectWidth = (bitmap.width * scale).toInt().coerceAtLeast(2)
            if (detectWidth % 2 != 0) detectWidth -= 1
            val detectHeight = (bitmap.height * (detectWidth.toFloat() / bitmap.width)).toInt().coerceAtLeast(2)
            val source565 = runCatching {
                val scaled = if (detectWidth == bitmap.width && detectHeight == bitmap.height) bitmap
                else Bitmap.createScaledBitmap(bitmap, detectWidth, detectHeight, true)
                val copy = scaled.copy(Bitmap.Config.RGB_565, false)
                if (scaled !== bitmap) scaled.recycle()
                copy
            }.getOrNull() ?: return null

            return try {
                val faces = arrayOfNulls<FaceDetector.Face>(1)
                val count = runCatching { FaceDetector(source565.width, source565.height, 1).findFaces(source565, faces) }
                    .getOrDefault(0)
                val face = if (count > 0) faces[0] else null
                if (face == null || face.confidence() < 0.25f || face.eyesDistance() <= 1f) return null

                val mid = PointF()
                face.getMidPoint(mid)
                val backScale = bitmap.width.toFloat() / source565.width.toFloat()
                val eyeX = mid.x * backScale
                val eyeY = mid.y * backScale
                val eyeDistance = face.eyesDistance() * backScale

                // Around 4.4 eye-distances wide is a natural head-and-shoulders framing.
                var cropWidth = (eyeDistance * 4.4f).coerceIn(
                    minOf(bitmap.width.toFloat(), bitmap.height * targetRatio) * 0.48f,
                    bitmap.width.toFloat()
                )
                var cropHeight = cropWidth / targetRatio
                if (cropHeight > bitmap.height) {
                    cropHeight = bitmap.height.toFloat()
                    cropWidth = cropHeight * targetRatio
                }

                var left = eyeX - cropWidth / 2f
                // Eyes look most stable around the upper third rather than dead centre.
                var top = eyeY - cropHeight * 0.37f
                left = left.coerceIn(0f, (bitmap.width - cropWidth).coerceAtLeast(0f))
                top = top.coerceIn(0f, (bitmap.height - cropHeight).coerceAtLeast(0f))

                Rect(
                    left.toInt(),
                    top.toInt(),
                    (left + cropWidth).toInt().coerceAtMost(bitmap.width),
                    (top + cropHeight).toInt().coerceAtMost(bitmap.height)
                )
            } finally {
                source565.recycle()
            }
        }

        private fun centerCropRect(width: Int, height: Int, targetRatio: Float): Rect {
            val sourceRatio = width.toFloat() / height.coerceAtLeast(1)
            return if (sourceRatio > targetRatio) {
                val cropWidth = (height * targetRatio).toInt().coerceAtLeast(1)
                val left = ((width - cropWidth) / 2).coerceAtLeast(0)
                Rect(left, 0, (left + cropWidth).coerceAtMost(width), height)
            } else {
                val cropHeight = (width / targetRatio).toInt().coerceAtLeast(1)
                val top = ((height - cropHeight) / 2).coerceAtLeast(0)
                Rect(0, top, width, (top + cropHeight).coerceAtMost(height))
            }
        }
    }
}

data class StoredTimeTraceImage(val relativePath: String, val width: Int, val height: Int)
