package com.memora.app.integration

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import com.memora.app.MemoraApplication
import com.memora.app.data.local.TopilmaSourceType
import com.memora.app.data.repository.TopilmaCaptureRequest
import com.memora.app.data.repository.TopilmaCaptureResult
import com.memora.app.media.TopilmalarMediaStorage
import com.memora.app.share.TopilmalarMetadataWorker

object TopilmaExternalCapture {
    data class Metadata(
        val packageName: String?,
        val appName: String?,
        val sourceTitle: String?,
        val author: String? = null,
        val sourceUri: String? = null,
        val sourceType: TopilmaSourceType? = null,
        val appIconPath: String? = null
    ) {
        fun isMeaningfulSourceSuggestion(): Boolean {
            val title = sourceTitle?.trim().orEmpty()
            if (!sourceUri.isNullOrBlank()) return true
            if (!author.isNullOrBlank()) return true
            return title.isNotBlank() && !title.equals(appName.orEmpty(), ignoreCase = true)
        }

        fun contextKey(): String? {
            val pkg = packageName?.trim().orEmpty()
            val uri = sourceUri?.trim()?.lowercase().orEmpty()
            val title = sourceTitle?.trim()?.lowercase().orEmpty()
            return when {
                uri.isNotBlank() -> "uri|$pkg|$uri"
                isMeaningfulSourceSuggestion() && title.isNotBlank() -> "title|$pkg|$title"
                else -> null
            }
        }
    }

    suspend fun save(
        context: Context,
        text: String,
        packageName: String?,
        root: AccessibilityNodeInfo? = null
    ): TopilmaCaptureResult = save(context, text, metadata(context, packageName, root))

    suspend fun save(
        applicationContext: Context,
        text: String,
        metadata: Metadata
    ): TopilmaCaptureResult {
        val context = applicationContext.applicationContext
        val app = context as MemoraApplication
        val clean = text.trim()
        require(clean.isNotBlank())
        val result = app.topilmalarRepository.capture(
            TopilmaCaptureRequest(
                text = clean,
                sourceTitle = metadata.sourceTitle,
                author = metadata.author,
                sourceUri = metadata.sourceUri,
                originAppPackage = metadata.packageName,
                originAppName = metadata.appName,
                originAppIconPath = metadata.appIconPath,
                sourceType = metadata.sourceType
            )
        )
        if (result.sourceId != null && !metadata.sourceUri.isNullOrBlank()) {
            TopilmalarMetadataWorker.enqueue(context, result.sourceId, metadata.sourceUri)
        }
        return result
    }

    fun metadataFromProcessIntent(
        context: Context,
        packageName: String?,
        intent: Intent,
        selectedText: String
    ): Metadata {
        val fallback = metadata(context, packageName, null)
        val strings = mutableListOf<String>()
        intent.dataString?.let(strings::add)
        intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.toString()?.let(strings::add)
                clip.getItemAt(index).text?.toString()?.let(strings::add)
            }
        }
        val extras: Bundle? = intent.extras
        extras?.keySet()?.forEach { key ->
            val value = runCatching { extras.get(key) }.getOrNull()
            when (value) {
                is String -> strings += value
                is CharSequence -> strings += value.toString()
                is Uri -> strings += value.toString()
            }
        }
        val cleaned = strings
            .map { it.trim() }
            .filter { it.isNotBlank() && it != selectedText.trim() }
            .distinct()

        val sourceUri = cleaned.asSequence().mapNotNull(::normalizeUrl).firstOrNull()
        val explicitTitle = intent.getStringExtra(Intent.EXTRA_TITLE)?.trim()?.takeIf { it.isNotBlank() }
        val titleCandidate = explicitTitle ?: cleaned.firstOrNull { candidate ->
            candidate.length in 4..180 &&
                normalizeUrl(candidate) == null &&
                !candidate.equals(fallback.appName.orEmpty(), ignoreCase = true)
        }
        val authorCandidate = cleaned.firstOrNull { raw ->
            val lower = raw.lowercase()
            raw.length in 3..100 && (lower.startsWith("by ") || lower.startsWith("author:") || lower.startsWith("muallif:"))
        }?.let(::stripAuthorPrefix)

        val type = when {
            fallback.sourceType == TopilmaSourceType.CHAT -> TopilmaSourceType.CHAT
            sourceUri != null -> TopilmaSourceType.ARTICLE_WEBSITE
            fallback.sourceType == TopilmaSourceType.BOOK -> TopilmaSourceType.BOOK
            else -> fallback.sourceType
        }
        val title = when {
            fallback.sourceType == TopilmaSourceType.CHAT -> fallback.sourceTitle
            !titleCandidate.isNullOrBlank() -> titleCandidate
            sourceUri != null -> runCatching { Uri.parse(sourceUri).host }.getOrNull()?.removePrefix("www.")
            else -> fallback.sourceTitle
        }
        return fallback.copy(
            sourceTitle = title,
            author = authorCandidate ?: fallback.author,
            sourceUri = sourceUri ?: fallback.sourceUri,
            sourceType = type
        )
    }

    fun metadata(context: Context, packageName: String?, root: AccessibilityNodeInfo? = null): Metadata {
        val pm = context.packageManager
        val pkg = packageName?.takeIf { it.isNotBlank() }
        val appName = pkg?.let {
            runCatching {
                val info = pm.getApplicationInfo(it, 0)
                pm.getApplicationLabel(info).toString()
            }.getOrNull()
        }
        val iconPath = pkg?.let {
            runCatching {
                val drawable: Drawable = pm.getApplicationIcon(it)
                TopilmalarMediaStorage(context).storeAppIcon(it, drawable)
            }.getOrNull()
        }

        val url = root?.let(::findUrl)
        val visible = root?.let(::collectVisibleText).orEmpty()
        val titleCandidate = visible.firstOrNull { candidate ->
            candidate.length in 4..180 && candidate != url && !candidate.startsWith("http", ignoreCase = true)
        }
        val authorCandidate = visible.firstOrNull { candidate ->
            val lower = candidate.lowercase()
            candidate.length in 3..90 && (lower.startsWith("by ") || lower.startsWith("muallif") || lower.startsWith("author"))
        }?.let { raw ->
            when {
                raw.startsWith("By ", true) -> raw.substring(3).trim()
                ':' in raw -> raw.substringAfter(':').trim()
                else -> raw.trim()
            }.ifBlank { null }
        }

        val lowerPkg = pkg.orEmpty().lowercase()
        val type = when {
            lowerPkg.contains("chatgpt") || lowerPkg.contains("openai") -> TopilmaSourceType.CHAT
            url != null || lowerPkg.contains("chrome") || lowerPkg.contains("browser") -> TopilmaSourceType.ARTICLE_WEBSITE
            lowerPkg.contains("reader") || lowerPkg.contains("ebook") || lowerPkg.contains("book") -> TopilmaSourceType.BOOK
            else -> TopilmaSourceType.OTHER
        }

        val title = when {
            type == TopilmaSourceType.CHAT -> appName ?: "ChatGPT"
            titleCandidate != null -> titleCandidate
            url != null -> runCatching { Uri.parse(url).host }.getOrNull()?.removePrefix("www.") ?: appName
            else -> appName
        }
        return Metadata(pkg, appName, title, authorCandidate, url, type, iconPath)
    }

    private fun findUrl(root: AccessibilityNodeInfo): String? {
        val byId = sequenceOf(
            "com.android.chrome:id/url_bar",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.microsoft.emmx:id/url_bar"
        ).flatMap { id -> runCatching { root.findAccessibilityNodeInfosByViewId(id).asSequence() }.getOrDefault(emptySequence()) }
            .mapNotNull { it.text?.toString()?.trim() }
            .firstOrNull { it.isNotBlank() }
        normalizeUrl(byId)?.let { return it }
        return collectVisibleText(root).asSequence().mapNotNull(::normalizeUrl).firstOrNull()
    }

    private fun normalizeUrl(raw: String?): String? {
        val value = raw?.trim()?.trimEnd('.', ',', ')', ']', '}') ?: return null
        val candidate = when {
            value.startsWith("https://", true) || value.startsWith("http://", true) -> value
            value.matches(Regex("[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(/.*)?")) -> "https://$value"
            else -> return null
        }
        return runCatching { Uri.parse(candidate) }.getOrNull()?.takeIf { !it.host.isNullOrBlank() }?.toString()
    }

    private fun stripAuthorPrefix(raw: String): String? {
        val clean = when {
            raw.startsWith("By ", true) -> raw.substring(3)
            ':' in raw -> raw.substringAfter(':')
            else -> raw
        }.trim()
        return clean.ifBlank { null }
    }

    private fun collectVisibleText(root: AccessibilityNodeInfo): List<String> {
        val out = ArrayList<String>(64)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 500) {
            val node = queue.removeFirst()
            visited++
            node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(out::add)
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(out::add)
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return out.distinct()
    }
}
