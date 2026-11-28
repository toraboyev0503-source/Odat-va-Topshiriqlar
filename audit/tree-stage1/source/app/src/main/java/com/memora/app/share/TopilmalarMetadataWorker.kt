package com.memora.app.share

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.memora.app.MemoraApplication
import com.memora.app.media.TopilmalarMediaStorage
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TopilmalarMetadataWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sourceId = inputData.getString(KEY_SOURCE_ID) ?: return@withContext Result.failure()
        val pageUrl = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        runCatching {
            val html = fetchText(pageUrl)
            val title = meta(html, "og:title") ?: TITLE_REGEX.find(html)?.groupValues?.getOrNull(1)?.let(::decodeHtml)
            val author = meta(html, "article:author") ?: meta(html, "author")
            val imageUrl = (meta(html, "og:image") ?: pageIcon(html))?.let { resolveUrl(pageUrl, it) }
            val coverPath = imageUrl
                ?.takeIf { sameSite(pageUrl, it) }
                ?.let(::fetchCover)
            val app = applicationContext as MemoraApplication
            val current = app.topilmalarRepository.source(sourceId)
            val hostLabel = runCatching { URI(pageUrl).host?.removePrefix("www.") }.getOrNull()
            val currentTitleIsFallback = current?.title.equals("Manba", ignoreCase = true) ||
                (!hostLabel.isNullOrBlank() && current?.title.equals(hostLabel, ignoreCase = true))
            // Exact title metadata supplied by the sharing app has precedence over page metadata.
            // Only replace the generic host/fallback title; a user edit is additionally protected
            // by the DAO's userEdited guard.
            app.topilmalarRepository.applyEnrichment(
                id = sourceId,
                title = title.takeIf { currentTitleIsFallback },
                author = author,
                coverPath = coverPath
            )
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { if (runAttemptCount < 2) Result.retry() else Result.success() }
        )
    }

    private fun fetchText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Memora/1.0 metadata preview")
            setRequestProperty("Accept", "text/html")
        }
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(8_192)
            val output = StringBuilder()
            while (output.length < MAX_HTML_CHARS) {
                val read = reader.read(buffer, 0, minOf(buffer.size, MAX_HTML_CHARS - output.length))
                if (read < 0) break
                output.append(buffer, 0, read)
            }
            return output.toString()
        }
    }

    private fun fetchCover(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Memora/1.0 cover preview")
            setRequestProperty("Accept", "image/*")
        }
        val type = connection.contentType ?: return null
        if (!type.startsWith("image/")) return null
        return TopilmalarMediaStorage(applicationContext)
            .storeDownloadedImage(connection.inputStream, type)
            .relativePath
    }

    private fun meta(html: String, property: String): String? {
        val patternA = Regex("<meta[^>]+(?:property|name)=[\\\"']${Regex.escape(property)}[\\\"'][^>]+content=[\\\"']([^\\\"']+)", RegexOption.IGNORE_CASE)
        val patternB = Regex("<meta[^>]+content=[\\\"']([^\\\"']+)[\\\"'][^>]+(?:property|name)=[\\\"']${Regex.escape(property)}[\\\"']", RegexOption.IGNORE_CASE)
        return (patternA.find(html)?.groupValues?.getOrNull(1) ?: patternB.find(html)?.groupValues?.getOrNull(1))?.let(::decodeHtml)
    }


    private fun pageIcon(html: String): String? {
        val pattern = Regex("<link[^>]+rel=[\"'](?:shortcut\\s+icon|icon)[\"'][^>]+href=[\"']([^\"']+)", RegexOption.IGNORE_CASE)
        val reverse = Regex("<link[^>]+href=[\"']([^\"']+)[\"'][^>]+rel=[\"'](?:shortcut\\s+icon|icon)[\"']", RegexOption.IGNORE_CASE)
        return pattern.find(html)?.groupValues?.getOrNull(1) ?: reverse.find(html)?.groupValues?.getOrNull(1)
    }

    private fun resolveUrl(base: String, candidate: String): String = URI(base).resolve(candidate).toString()
    private fun sameSite(page: String, image: String): Boolean = runCatching {
        val pageHost = URI(page).host?.removePrefix("www.")
        val imageHost = URI(image).host?.removePrefix("www.")
        pageHost != null && imageHost != null && (pageHost == imageHost || imageHost.endsWith(".$pageHost"))
    }.getOrDefault(false)

    private fun decodeHtml(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()

    companion object {
        private const val KEY_SOURCE_ID = "source_id"
        private const val KEY_URL = "url"
        private const val MAX_HTML_CHARS = 512_000
        private val TITLE_REGEX = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        fun enqueue(context: Context, sourceId: String, url: String) {
            val request = OneTimeWorkRequestBuilder<TopilmalarMetadataWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_SOURCE_ID to sourceId, KEY_URL to url))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("topilmalar-enrich-$sourceId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
