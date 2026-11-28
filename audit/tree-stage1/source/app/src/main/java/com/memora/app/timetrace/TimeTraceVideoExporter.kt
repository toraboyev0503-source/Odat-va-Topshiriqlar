package com.memora.app.timetrace

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.memora.app.data.local.TimeTracePhotoEntity
import com.memora.app.prefs.TimeTraceSpeed
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException
import kotlin.math.roundToInt

/**
 * Creates a small, private H.264 MP4 from the Time Trace source photos. The exporter never sends
 * images to a server. It uses variable presentation timestamps so still photos remain on screen
 * without encoding dozens of duplicate frames, and generates a handful of blended transition
 * frames for the calm Memora crossfade.
 */
class TimeTraceVideoExporter(private val context: Context, private val storage: TimeTraceStorage) {
    private val width = 720
    private val height = 900

    fun export(
        destination: Uri,
        photos: List<TimeTracePhotoEntity>,
        speed: TimeTraceSpeed,
        showDate: Boolean,
        onProgress: (Int) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ) {
        require(photos.isNotEmpty()) { "No photos to export" }
        val temp = File(context.cacheDir, "time-trace-${System.currentTimeMillis()}.mp4")
        try {
            onProgress(0)
            encode(temp, photos, speed, showDate, onProgress, isCancelled)
            if (isCancelled()) throw CancellationException("cancelled")
            val total = temp.length().coerceAtLeast(1L)
            var copied = 0L
            context.contentResolver.openOutputStream(destination, "w")?.use { out ->
                temp.inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        if (isCancelled()) throw CancellationException("cancelled")
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        copied += read
                        onProgress((90 + copied * 10 / total).toInt().coerceIn(90, 100))
                    }
                }
            } ?: error("Unable to open video destination")
            onProgress(100)
        } catch (cancelled: CancellationException) {
            runCatching { context.contentResolver.delete(destination, null, null) }
            throw cancelled
        } finally {
            temp.delete()
        }
    }

    private fun encode(
        file: File,
        photos: List<TimeTracePhotoEntity>,
        speed: TimeTraceSpeed,
        showDate: Boolean,
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean
    ) {
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val colorFormat = chooseColorFormat(codec.codecInfo)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_400_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var trackIndex = -1
        val info = MediaCodec.BufferInfo()
        val holdUs = when (speed) {
            TimeTraceSpeed.SLOW -> 1_250_000L
            TimeTraceSpeed.MEDIUM -> 780_000L
            TimeTraceSpeed.FAST -> 430_000L
        }
        val transitionUs = minOf(260_000L, holdUs / 3)
        var pts = 0L

        fun drain(end: Boolean = false) {
            if (end) codec.signalEndOfInputStreamSafely()
            while (true) {
                val outIndex = codec.dequeueOutputBuffer(info, if (end) 10_000 else 0)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!end) return else continue
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start(); muxerStarted = true
                        }
                    }
                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)
                        if (buffer != null && info.size > 0 && muxerStarted) {
                            buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, buffer, info)
                        }
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outIndex, false)
                        if (eos) return
                    }
                }
            }
        }

        fun queue(bitmap: Bitmap, presentationUs: Long) {
            if (isCancelled()) throw CancellationException("cancelled")
            var input = -1
            while (input < 0) {
                input = codec.dequeueInputBuffer(10_000)
                if (input < 0) drain(false)
            }
            val buffer = codec.getInputBuffer(input) ?: error("Encoder input buffer missing")
            buffer.clear()
            val bytes = bitmapToYuv(bitmap, colorFormat)
            require(buffer.capacity() >= bytes.size) { "Encoder input buffer too small" }
            buffer.put(bytes)
            codec.queueInputBuffer(input, 0, bytes.size, presentationUs, 0)
            drain(false)
        }

        try {
            var current = renderPhoto(photos.first(), showDate)
            queue(current, pts)
            onProgress((90f / photos.size).toInt().coerceAtLeast(1))
            for (i in 1 until photos.size) {
                if (isCancelled()) throw CancellationException("cancelled")
                val next = renderPhoto(photos[i], showDate)
                val transitionStart = pts + (holdUs - transitionUs).coerceAtLeast(1L)
                queue(current, transitionStart)
                val steps = 4
                repeat(steps) { step ->
                    val alpha = (step + 1f) / (steps + 1f)
                    val blended = blend(current, next, alpha)
                    queue(blended, transitionStart + transitionUs * (step + 1) / (steps + 1))
                    blended.recycle()
                }
                pts += holdUs
                queue(next, pts)
                current.recycle()
                current = next
                onProgress((((i + 1).toFloat() / photos.size.toFloat()) * 90f).toInt().coerceIn(1, 90))
            }
            pts += holdUs
            queue(current, pts)
            current.recycle()

            var input = -1
            while (input < 0) { input = codec.dequeueInputBuffer(10_000); if (input < 0) drain(false) }
            codec.queueInputBuffer(input, 0, 0, pts + 33_333L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            while (true) {
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !muxerStarted) {
                    trackIndex = muxer.addTrack(codec.outputFormat); muxer.start(); muxerStarted = true
                } else if (outIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outIndex)
                    if (buffer != null && info.size > 0 && muxerStarted) {
                        buffer.position(info.offset); buffer.limit(info.offset + info.size); muxer.writeSampleData(trackIndex, buffer, info)
                    }
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outIndex, false)
                    if (eos) break
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
        }
    }

    private fun MediaCodec.signalEndOfInputStreamSafely() { /* ByteBuffer-input encoder: EOS is queued explicitly. */ }

    private fun renderPhoto(photo: TimeTracePhotoEntity, showDate: Boolean): Bitmap {
        val source = BitmapFactory.decodeFile(storage.resolve(photo.relativePath).absolutePath)
            ?: error("Missing Time Trace photo: ${photo.dateKey}")
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(250, 247, 239))
        canvas.drawBitmap(source, null, Rect(0, 0, width, height), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        if (showDate) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 28f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                setShadowLayer(5f, 0f, 1f, Color.argb(150, 0, 0, 0))
            }
            canvas.drawText(photo.dateKey, 28f, height - 30f, paint)
        }
        return out
    }

    private fun blend(a: Bitmap, b: Bitmap, alpha: Float): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(a, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
        c.drawBitmap(b, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = (255 * alpha).roundToInt().coerceIn(0, 255) })
        return out
    }

    private fun chooseColorFormat(info: MediaCodecInfo): Int {
        val supported = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).colorFormats.toSet()
        return when {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar in supported -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar in supported -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible in supported -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            else -> error("No YUV420 encoder format available")
        }
    }

    private fun bitmapToYuv(bitmap: Bitmap, format: Int): ByteArray {
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        val out = ByteArray(width * height * 3 / 2)
        val ySize = width * height
        var yIndex = 0
        var uvIndex = ySize
        // Flexible YUV420 is safest to feed as planar I420 when using ByteBuffer input.
        val semi = format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        val uPlane = ySize
        val vPlane = ySize + ySize / 4
        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[j * width + i]
                val r = c shr 16 and 0xff
                val g = c shr 8 and 0xff
                val b = c and 0xff
                val y = ((((66*r + 129*g + 25*b + 128) shr 8) + 16)).coerceIn(0,255)
                val u = ((((-38*r - 74*g + 112*b + 128) shr 8) + 128)).coerceIn(0,255)
                val v = ((((112*r - 94*g - 18*b + 128) shr 8) + 128)).coerceIn(0,255)
                out[yIndex++] = y.toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    if (semi) {
                        out[uvIndex++] = u.toByte(); out[uvIndex++] = v.toByte()
                    } else {
                        val chromaIndex = (j/2)*(width/2)+(i/2)
                        out[uPlane + chromaIndex] = u.toByte()
                        out[vPlane + chromaIndex] = v.toByte()
                    }
                }
            }
        }
        return out
    }
}
