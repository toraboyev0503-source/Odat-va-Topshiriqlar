package com.memora.app.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.memora.app.data.local.NoteEntity
import com.memora.app.data.model.NoteBlock
import com.memora.app.data.model.NoteBlocksCodec
import com.memora.app.media.MediaStorage
import com.memora.app.prefs.AppLanguage
import com.memora.app.util.TimeUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Memora Paper exports; media blocks and original note dates are preserved. */
class ReaderShareManager(private val context: Context) {
    private val shareDir = File(context.cacheDir, "shares").apply { mkdirs() }
    private val renderer = PaperShareRenderer(context)
    init {
        // Share intents may outlive the app process. Only purge expired files, not active shares.
        shareDir.listFiles()?.filter { it.isFile && it.name.startsWith("paper-") &&
            System.currentTimeMillis() - it.lastModified() > 7L * 24 * 60 * 60 * 1000
        }?.forEach { it.delete() }
    }

    fun shareText(note: NoteEntity, chooserTitle: String) {
        shareSelection("${note.title}\n\n${note.plainText}", chooserTitle)
    }

    fun shareSelection(text: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    /** A short selection becomes a short Paper card; long selections become numbered cards. */
    suspend fun renderSelectionImages(selection: String): List<File> = withContext(Dispatchers.IO) {
        renderer.renderSelection(selection)
    }

    fun shareSelectionFiles(files: List<File>, chooserTitle: String) {
        require(files.isNotEmpty())
        val uris = ArrayList<Uri>(files.map(::uriFor))
        val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uris.first())
        } else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    suspend fun shareSelectionImage(selection: String, language: AppLanguage, chooserTitle: String) {
        val files = renderSelectionImages(selection)
        withContext(Dispatchers.Main) { shareSelectionFiles(files, chooserTitle) }
    }

    suspend fun shareImages(note: NoteEntity, language: AppLanguage, chooserTitle: String, includeDates: Boolean = true) {
        val files = withContext(Dispatchers.IO) { renderer.render(note, language, includeDates) }
        val uris = ArrayList<Uri>(files.map(::uriFor))
        val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uris.first())
        } else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), chooserTitle))
        }
    }

    suspend fun sharePdf(note: NoteEntity, language: AppLanguage, chooserTitle: String) {
        val file = withContext(Dispatchers.IO) {
            val pages = renderer.render(note, language)
            val pdf = PdfDocument()
            try {
                pages.forEachIndexed { index, pageFile ->
                    val bitmap = BitmapFactory.decodeFile(pageFile.absolutePath) ?: error("Invalid Paper page")
                    try {
                        val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                        page.canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, 595, 842), null)
                        pdf.finishPage(page)
                    } finally { bitmap.recycle() }
                }
                File(shareDir, "paper-${UUID.randomUUID()}.pdf").also { result ->
                    result.outputStream().use { pdf.writeTo(it) }
                }
            } finally {
                pdf.close()
                pages.forEach { it.delete() }
            }
        }
        sendFile(file, "application/pdf", chooserTitle)
    }

    suspend fun shareHtml(note: NoteEntity, language: AppLanguage, chooserTitle: String) {
        val file = withContext(Dispatchers.IO) {
            File(shareDir, "paper-${UUID.randomUUID()}.html").also {
                it.writeText(buildHtml(note, language), Charsets.UTF_8)
            }
        }
        sendFile(file, "text/html", chooserTitle)
    }

    private suspend fun sendFile(file: File, mime: String, title: String) = withContext(Dispatchers.Main) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uriFor(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    private fun buildHtml(note: NoteEntity, language: AppLanguage): String {
        val blocks = NoteBlocksCodec.decode(note.blocksJson, note.plainText)
        val storage = MediaStorage(context)
        val body = buildString {
            blocks.forEach { block ->
                when (block) {
                    is NoteBlock.Text -> {
                        val sorted = block.styles.filter { it.end > it.start && it.start >= 0 && it.end <= block.text.length }
                        val markers = (listOf(0, block.text.length) + sorted.flatMap { listOf(it.start, it.end) }).distinct().sorted()
                        append("<p>")
                        markers.zipWithNext { a, b ->
                            val text = htmlEscape(block.text.substring(a, b)).replace("\n", "<br>")
                            val active = sorted.filter { it.start <= a && it.end >= b }
                            val styles = mutableListOf<String>()
                            if (active.any { it.bold }) styles += "font-weight:bold"
                            if (active.any { it.italic }) styles += "font-style:italic"
                            active.firstNotNullOfOrNull { span -> when (span.highlight) {
                                "YELLOW" -> "#f7e4a3"; "GREEN" -> "#d6ecd1"; "ROSE" -> "#f0d2da"; else -> null
                            } }?.let { styles += "background:$it" }
                            if (styles.isNotEmpty()) append("<span style=\"${styles.joinToString(";")}\">$text</span>") else append(text)
                        }
                        append("</p>")
                    }
                    is NoteBlock.Image -> {
                        val file = runCatching { storage.resolve(block.relativePath) }.getOrNull()
                        if (file?.isFile == true) {
                            val imageData = imageDataUri(file)
                            if (imageData != null) {
                                append("<figure><img alt=\"Note image\" src=\"$imageData\"></figure>")
                            } else append("<p>[Image]</p>")
                        } else append("<p>[Image]</p>")
                    }
                    is NoteBlock.Audio -> append("<p class=\"attachment\">♫ Audio</p>")
                }
            }
        }
        val created = TimeUtils.dateTime(note.createdAt, language)
        val edited = if (note.updatedAt > note.createdAt + 1000) TimeUtils.dateTime(note.updatedAt, language) else null
        val labels = when (language) {
            AppLanguage.UZ -> "Yaratilgan" to "Tahrirlangan"
            AppLanguage.RU -> "Создано" to "Изменено"
            else -> "Created" to "Edited"
        }
        val footer = "<div class=\"dates\">${labels.first}: ${htmlEscape(created)}${if (edited != null) "<br>${labels.second}: ${htmlEscape(edited)}" else ""}</div>"
        return """<!doctype html><html lang="${language.tag}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${htmlEscape(note.title)}</title>
<style>body{margin:0;background:#eff2f3;color:#24282a;font-family:system-ui,sans-serif}.wrap{max-width:850px;margin:0 auto;padding:22px 14px 36px}.paper{background:#fffdfa;border-radius:18px;padding:26px clamp(18px,4vw,42px);box-shadow:0 2px 8px #26343b14}h1{font-size:2.45rem;line-height:1.23;margin:0 0 24px;font-weight:650;overflow-wrap:anywhere}p{font-size:1.12rem;line-height:1.55;margin:0 0 1.22em;white-space:normal;overflow-wrap:anywhere;font-family:Georgia,serif}figure{margin:16px 0}img{display:block;width:100%;height:auto;max-height:600px;object-fit:contain}.attachment{color:#647179;font-family:system-ui}.dates{border-top:1px solid #e4e5e5;margin-top:30px;padding-top:12px;color:#70777a;font-size:.8rem;line-height:1.7}.brand{text-align:center;margin-top:14px;font-size:.82rem;color:#647179}@media print{body{background:white}.wrap{max-width:none;padding:0}.paper{box-shadow:none;border-radius:0}p,figure{break-inside:avoid}}</style></head>
<body><main class="wrap"><article class="paper"><h1>${htmlEscape(note.title)}</h1>$body$footer</article><div class="brand">Memora</div></main></body></html>"""
    }

    private fun imageDataUri(file: File): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 1600)
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val bytes = try {
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
        "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }.getOrNull()

    private fun htmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
