package com.memora.app.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import com.memora.app.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.system.exitProcess

/**
 * Device-local diagnostics only. No network transport is used and no note/title/media content is
 * included. Reports contain runtime metadata and stack-frame locations only.
 */
object LocalCrashDiagnostics {
    private const val MAX_REPORTS = 6
    private const val DIR_NAME = "diagnostics"
    private const val PREFIX = "crash-"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeReport(appContext, "uncaught", thread.name, throwable) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun recordNonFatal(context: Context, tag: String, throwable: Throwable) {
        runCatching { writeReport(context.applicationContext, tag, Thread.currentThread().name, throwable) }
    }

    private fun writeReport(context: Context, tag: String, threadName: String, throwable: Throwable) {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val now = System.currentTimeMillis()
        val file = File(dir, "$PREFIX$now.txt")
        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE.toLong()

        file.writeText(buildString {
            appendLine("Memora local diagnostic")
            appendLine("timeUtc=${utcTimestamp(now)}")
            appendLine("tag=${tag.take(80)}")
            appendLine("app=$versionName ($versionCode)")
            appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("thread=${threadName.take(120)}")
            appendThrowable(this, throwable)
        })
        prune(dir)
    }

    private fun appendThrowable(out: StringBuilder, throwable: Throwable) {
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < 6) {
            out.appendLine("exception[$depth]=${current::class.java.name}")
            current.stackTrace.take(120).forEach { frame ->
                out.append("  at ")
                    .append(frame.className)
                    .append('.')
                    .append(frame.methodName)
                    .append('(')
                    .append(frame.fileName ?: "Unknown")
                    .append(':')
                    .append(frame.lineNumber)
                    .appendLine(')')
            }
            current = current.cause
            depth++
        }
    }

    private fun prune(dir: File) {
        dir.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_REPORTS)
            ?.forEach { runCatching { it.delete() } }
    }

    private fun utcTimestamp(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMs))
}
