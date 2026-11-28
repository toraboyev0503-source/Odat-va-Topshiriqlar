package com.memora.app.performance

import android.content.Context
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.view.Window
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import com.memora.app.BuildConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Opt-in non-content telemetry for the non-debuggable performanceProbe build only.
 * Frame listeners never touch disk, perform JSON parsing, or capture any user data.
 */
object MemoraPerformance {
    private class Counters {
        val frames = AtomicLong()
        val jank = AtomicLong()
        val totalNs = AtomicLong()
        val maximumNs = AtomicLong()
        fun add(data: FrameData) {
            frames.incrementAndGet()
            if (data.isJank) jank.incrementAndGet()
            val ns = data.frameDurationUiNanos
            totalNs.addAndGet(ns)
            maximumNs.accumulateAndGet(ns) { old, value -> maxOf(old, value) }
        }
        fun asJson(): String {
            val count = frames.get()
            val average = if (count == 0L) 0L else totalNs.get() / count
            return """{"frames":$count,"jankFrames":${jank.get()},"averageFrameNs":$average,"longestFrameNs":${maximumNs.get()}}"""
        }
    }

    private val buckets = ConcurrentHashMap<String, Counters>()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startedAt = SystemClock.elapsedRealtime()
    @Volatile private var currentScreen = "startup"
    private var tracker: JankStats? = null

    /** Screen IDs must be static app constants; never note titles, IDs, or content. */
    fun screen(label: String) {
        if (BuildConfig.PERF_INSTRUMENTED) currentScreen = label
    }

    fun attach(window: Window) {
        if (!BuildConfig.PERF_INSTRUMENTED || tracker != null) return
        tracker = JankStats.createAndTrack(window) { frame: FrameData ->
            // A volatile read plus atomics; no allocations proportional to the note data.
            buckets.getOrPut(currentScreen) { Counters() }.add(frame)
        }
    }

    fun setForeground(value: Boolean) { tracker?.isTrackingEnabled = value }

    fun detach() {
        tracker?.isTrackingEnabled = false
        tracker = null
    }

    fun snapshot(context: Context, event: String) {
        if (!BuildConfig.PERF_INSTRUMENTED) return
        val screens = buckets.entries.sortedBy { it.key }.joinToString(",") { (k, v) ->
            "\"$k\":${v.asJson()}"
        }
        val row = """{"event":"$event","uptimeMs":${SystemClock.elapsedRealtime()-startedAt},"screens":{$screens}}"""
        Log.i("MemoraPerf", row)
        io.launch {
            runCatching {
                val external = context.getExternalFilesDir(null) ?: return@launch
                val dir = File(external, "performance")
                if (dir.exists() || dir.mkdirs()) File(dir, "frames.jsonl").appendText(row + "\n")
            }
        }
    }

    fun <T> traced(section: String, block: () -> T): T {
        if (!BuildConfig.PERF_INSTRUMENTED) return block()
        Trace.beginSection("Memora.$section")
        return try { block() } finally { Trace.endSection() }
    }
}
