package com.memora.app.media

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorderController(private val context: Context) {
    data class Session(
        val relativePath: String,
        val file: File,
        val startedAt: Long
    )

    private var recorder: MediaRecorder? = null
    private var session: Session? = null
    private var pausedAt: Long? = null
    private var pausedTotalMs: Long = 0L

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun start(storage: MediaStorage): Session {
        check(recorder == null) { "Recording is already active" }
        val (relative, file) = storage.newAudioFile()
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        val created = Session(relative, file, System.currentTimeMillis())
        recorder = mediaRecorder
        session = created
        pausedAt = null
        pausedTotalMs = 0L
        return created
    }

    fun pause(): Boolean {
        val active = recorder ?: return false
        if (pausedAt != null) return true
        return runCatching {
            active.pause()
            pausedAt = System.currentTimeMillis()
            true
        }.getOrDefault(false)
    }

    fun resume(): Boolean {
        val active = recorder ?: return false
        val pausedSince = pausedAt ?: return true
        return runCatching {
            active.resume()
            pausedTotalMs += (System.currentTimeMillis() - pausedSince).coerceAtLeast(0L)
            pausedAt = null
            true
        }.getOrDefault(false)
    }

    fun elapsedMs(now: Long = System.currentTimeMillis()): Long {
        val current = session ?: return 0L
        val activePause = pausedAt?.let { (now - it).coerceAtLeast(0L) } ?: 0L
        return (now - current.startedAt - pausedTotalMs - activePause).coerceAtLeast(0L)
    }

    fun stop(): Long? {
        val current = session ?: return null
        val elapsed = elapsedMs()
        val active = recorder
        recorder = null
        session = null
        val wasPaused = pausedAt != null
        pausedAt = null
        pausedTotalMs = 0L
        var successful = false
        if (active != null) {
            try {
                // Some OEM MediaRecorder implementations are more reliable when resumed before stop.
                if (wasPaused) runCatching { active.resume() }
                active.stop()
                successful = true
            } catch (_: RuntimeException) {
                current.file.delete()
            } finally {
                runCatching { active.reset() }
                active.release()
            }
        }
        return elapsed.takeIf { successful }
    }

    fun cancel() {
        val current = session
        val active = recorder
        recorder = null
        session = null
        pausedAt = null
        pausedTotalMs = 0L
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.reset() }
            runCatching { active.release() }
        }
        current?.file?.delete()
    }

    fun isRecording(): Boolean = recorder != null
    fun isPaused(): Boolean = recorder != null && pausedAt != null
}
