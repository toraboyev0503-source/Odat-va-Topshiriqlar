package com.memora.app.export

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Base64OutputStream
import androidx.documentfile.provider.DocumentFile
import com.memora.app.data.local.NoteEntity
import com.memora.app.data.local.NoteType
import com.memora.app.data.model.NoteBlock
import com.memora.app.data.model.NoteBlocksCodec
import com.memora.app.data.model.TextInlineStyle
import com.memora.app.data.repository.MemoraRepository
import com.memora.app.data.repository.NoteSaveResult
import com.memora.app.data.repository.TopilmalarRepository
import com.memora.app.media.MediaStorage
import com.memora.app.media.TopilmalarMediaStorage
import com.memora.app.i18n.L
import com.memora.app.i18n.S
import com.memora.app.prefs.AppLanguage
import com.memora.app.prefs.FontChoice
import com.memora.app.prefs.LockInterval
import com.memora.app.prefs.PaletteChoice
import com.memora.app.prefs.ThemeMode
import com.memora.app.prefs.UserPreferences
import com.memora.app.prefs.TimeTraceSpeed
import com.memora.app.memories.MemoryAlbumStorage
import com.memora.app.data.local.MemoryAlbumPhotoEntity
import com.memora.app.timetrace.TimeTraceStorage
import com.memora.app.data.local.TimeTracePhotoEntity
import com.memora.app.util.TextUtils
import com.memora.app.util.TimeRange
import com.memora.app.util.TimeUtils
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream


data class ImportResult(val imported: Int, val duplicatesSkipped: Int)

data class ImportPreview(
    val notes: Int,
    val duplicateNotes: Int,
    val images: Int,
    val audio: Int,
    val savedTitles: Int,
    val reminders: Int,
    val timeTracePhotos: Int,
    val includesSettings: Boolean,
    val topilmaCards: Int = 0,
    val topilmaSources: Int = 0
) {
    val newNotes: Int get() = (notes - duplicateNotes).coerceAtLeast(0)
}

class ExportManager(
    private val context: Context,
    private val resolver: ContentResolver,
    private val repository: MemoraRepository,
    private val topilmalarRepository: TopilmalarRepository,
    private val preferences: UserPreferences
) {
    private val storage = MediaStorage(context.applicationContext)
    private val timeTraceStorage = TimeTraceStorage(context.applicationContext)
    private val memoryAlbumStorage = MemoryAlbumStorage(context.applicationContext)
    private val topilmalarStorage = TopilmalarMediaStorage(context.applicationContext)

    /**
     * HTML and Word exports are written into a real user-selected folder.
     * Memora backup continues to use a single .memora file via [write].
     */
    suspend fun writeFolder(request: ExportRequest, treeUri: Uri, language: AppLanguage): String {
        require(request.format != ExportFormat.BACKUP) { "Backup export requires a file destination" }
        val parent = DocumentFile.fromTreeUri(context, treeUri) ?: error("Unable to open destination folder")
        require(parent.canWrite()) { "Destination folder is not writable" }

        if (request.target == ExportTarget.TOPILMALAR) {
            return writeTopilmalarFolder(parent, request)
        }

        val range = request.range()
        val type = request.scopeNoteType()
        val notes = repository.notesForExport(type, range.start, range.end)
        return when (request.format) {
            ExportFormat.MULTIMEDIA -> writeHtmlFolder(parent, request, notes, language)
            ExportFormat.WORD -> writeWordFolder(parent, request, notes, language)
            ExportFormat.BACKUP -> error("Backup export requires a file destination")
        }
    }

    private suspend fun writeTopilmalarFolder(parent: DocumentFile, request: ExportRequest): String {
        val backup = topilmalarRepository.backupSnapshot()
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneId.systemDefault()).format(Instant.now())
        return when (request.format) {
            ExportFormat.MULTIMEDIA -> {
                val folderName = uniqueFolderName(parent, "Memora-Topilmalar-HTML-$stamp")
                val folder = parent.createDirectory(folderName) ?: error("Unable to create Topilmalar HTML folder")
                val file = folder.createFile("text/html", "index.html") ?: error("Unable to create Topilmalar HTML")
                resolver.openOutputStream(file.uri, "w")?.use { out ->
                    out.write(TopilmalarDocumentWriter.html(backup, request.includeComments).toByteArray(Charsets.UTF_8))
                } ?: error("Unable to write Topilmalar HTML")
                folderName
            }
            ExportFormat.WORD -> {
                val folderName = uniqueFolderName(parent, "Memora-Topilmalar-Word-$stamp")
                val folder = parent.createDirectory(folderName) ?: error("Unable to create Topilmalar Word folder")
                val file = folder.createFile(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "Memora-Topilmalar.docx"
                ) ?: error("Unable to create Topilmalar Word file")
                resolver.openOutputStream(file.uri, "w")?.use { out ->
                    out.write(TopilmalarDocumentWriter.docx(backup, request.includeComments))
                } ?: error("Unable to write Topilmalar Word file")
                folderName
            }
            ExportFormat.BACKUP -> error("Topilmalar backup is included in full Memora backup")
        }
    }

    private fun writeWordFolder(
        parent: DocumentFile,
        request: ExportRequest,
        notes: List<NoteEntity>,
        language: AppLanguage
    ): String {
        val folderName = uniqueFolderName(parent, exportFolderBaseName(request, "Word"))
        val root = parent.createDirectory(folderName) ?: error("Unable to create Word export folder")
        val audioDir = root.createDirectory("Audio") ?: error("Unable to create Audio folder")

        val built = DocxWriter.build(
            notes = notes,
            language = language,
            layout = request.wordLayout,
            storage = storage
        )
        val docxName = wordDocumentFileName(request)
        val docx = root.createFile(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            docxName
        ) ?: error("Unable to create Word file")
        resolver.openOutputStream(docx.uri, "w")?.use { it.write(built.docxBytes) }
            ?: error("Unable to write Word file")

        built.audios.forEach { audio ->
            val target = audioDir.createFile(audio.mimeType.ifBlank { "audio/mp4" }, audio.targetName)
                ?: error("Unable to create ${audio.targetName}")
            resolver.openOutputStream(target.uri, "w")?.use { out ->
                audio.file.inputStream().use { input -> input.copyTo(out) }
            } ?: error("Unable to write ${audio.targetName}")
        }

        val readme = root.createFile("text/plain", "README.txt")
        if (readme != null) {
            resolver.openOutputStream(readme.uri, "w")?.use { out ->
                out.write(wordFolderReadme(language, docxName, built.audios.isNotEmpty()).toByteArray(Charsets.UTF_8))
            }
        }
        return folderName
    }

    private fun writeHtmlFolder(
        parent: DocumentFile,
        request: ExportRequest,
        notes: List<NoteEntity>,
        language: AppLanguage
    ): String {
        val folderName = uniqueFolderName(parent, exportFolderBaseName(request, "HTML"))
        val root = parent.createDirectory(folderName) ?: error("Unable to create HTML export folder")
        val sorted = when (request.wordLayout) {
            WordLayout.CHRONOLOGICAL -> notes.sortedBy { it.createdAt }
            WordLayout.GROUP_BY_TITLE -> notes.sortedWith(
                compareBy<NoteEntity>({ multimediaDisplayTitle(it, language).lowercase() }, { it.createdAt })
            )
        }

        val chunks = splitHtmlChunks(sorted)
        val bundles = mutableListOf<MutableList<List<NoteEntity>>>()
        var current = mutableListOf<List<NoteEntity>>()
        var currentBytes = 0L
        chunks.forEach { chunk ->
            val bytes = chunk.sumOf(::estimateEmbeddedBytes)
            if (current.isNotEmpty() && currentBytes + bytes > HTML_RAW_MEDIA_TARGET_BYTES) {
                bundles += current
                current = mutableListOf()
                currentBytes = 0L
            }
            current += chunk
            currentBytes += bytes
        }
        if (current.isNotEmpty() || bundles.isEmpty()) bundles += current

        bundles.forEachIndexed { index, bundle ->
            val fileName = if (index == 0) "index.html" else "index-${(index + 1).toString().padStart(2, '0')}.html"
            val file = root.createFile("text/html", fileName) ?: error("Unable to create $fileName")
            resolver.openOutputStream(file.uri, "w")?.use { out ->
                writeInteractiveHtmlIndex(out, bundle, language, request.wordLayout, index + 1, bundles.size)
            } ?: error("Unable to write $fileName")
        }

        val readme = root.createFile("text/plain", "README.txt")
        if (readme != null) {
            resolver.openOutputStream(readme.uri, "w")?.use { out ->
                val message = when (language) {
                    AppLanguage.UZ -> "index.html faylini oching. Oy va matn turini tanlang. Rasm va audio HTML ichida, qo‘shimcha jild ruxsati kerak emas. Katta arxiv bir nechta index faylga bo‘linadi."
                    AppLanguage.RU -> "Откройте index.html, выберите месяц и тип записи. Изображения и аудио встроены в HTML; доступ к папке не требуется. Большой архив делится на несколько index-файлов."
                    else -> "Open index.html, choose a month and note type. Images and audio are embedded in HTML; no folder permission is needed. Large archives are split into multiple index files."
                }
                out.write(message.toByteArray(Charsets.UTF_8))
            }
        }
        return folderName
    }

    private fun writeInteractiveHtmlIndex(
        output: java.io.OutputStream,
        chunks: List<List<NoteEntity>>,
        language: AppLanguage,
        layout: WordLayout,
        bundleNumber: Int,
        bundleCount: Int
    ) {
        val labels = multimediaLabels(language)
        val writer = output.bufferedWriter(Charsets.UTF_8)
        val meta = chunks.mapIndexed { index, chunk ->
            HtmlPageInfo(
                fileName = "page-$index",
                monthKey = htmlFilePeriod(chunk),
                label = chunkPeriodLabel(chunk, language),
                noteType = htmlNoteType(chunk).name,
                noteCount = chunk.size,
                part = 1,
                partCount = 1
            )
        }
        val monthGroups = meta.groupBy { it.monthKey }.toSortedMap()
        val langCode = language.tag
        val chooseType = L.t(language, S.NOTE_SCOPE)
        val shortLabel = L.t(language, S.SHORT_NOTES)
        val longLabel = L.t(language, S.LONG_NOTES)
        val backLabel = L.t(language, S.BACK)
        val partLabel = when (language) {
            AppLanguage.UZ -> "Qism"
            AppLanguage.RU -> "Часть"
            else -> "Part"
        }

        writer.write("""<!doctype html><html lang="${TextUtils.xmlEscape(langCode)}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><meta name="color-scheme" content="light dark"><title>Memora</title><style>
:root{--bg:#f4f1ea;--paper:#fffdf8;--text:#202629;--muted:#788184;--line:#e4ded2;--accent:#2e6871;--accentSoft:#e9f0ef;--audio:#edf4f4;--shadow:0 10px 30px rgba(32,38,41,.06)}@media(prefers-color-scheme:dark){:root{--bg:#15191b;--paper:#202527;--text:#edf1ef;--muted:#a9b0ae;--line:#343b3d;--accent:#8bc1c7;--accentSoft:#263235;--audio:#263235;--shadow:none}}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}.page{max-width:850px;margin:auto;padding:max(30px,env(safe-area-inset-top)) 18px max(60px,env(safe-area-inset-bottom))}h1.brand{font-size:clamp(44px,10vw,64px);letter-spacing:-.045em;margin:0 0 22px}.bundle{color:var(--muted);font-size:13px;margin:-15px 0 22px}.month-btn,.choice,.part-btn{width:100%;border:1px solid var(--line);background:var(--paper);color:var(--text);border-radius:20px;padding:18px 20px;font:inherit;text-align:left;box-shadow:var(--shadow)}.month-btn{display:flex;justify-content:space-between;align-items:center;margin:10px 0}.month-btn small{display:block;color:var(--muted);margin-top:3px}.overlay{position:fixed;inset:0;background:rgba(0,0,0,.34);display:none;align-items:flex-end;z-index:20}.overlay.show{display:flex}.sheet{width:100%;max-width:850px;margin:0 auto;background:var(--paper);border-radius:26px 26px 0 0;padding:22px 18px max(24px,env(safe-area-inset-bottom));border:1px solid var(--line)}.sheet h2{margin:0 0 4px}.sub{color:var(--muted);margin:0 0 16px}.choices{display:grid;grid-template-columns:1fr 1fr;gap:10px}.choice{box-shadow:none}.choice small{display:block;color:var(--muted);margin-top:5px}.choice:disabled{opacity:.4}.part-list{display:grid;gap:9px}.close{border:0;background:transparent;color:var(--muted);font:inherit;width:100%;padding:16px 0 2px}.content{display:none}.content.show{display:block}.content-head{position:sticky;top:0;z-index:5;background:color-mix(in srgb,var(--bg) 94%,transparent);backdrop-filter:blur(14px);padding:max(8px,env(safe-area-inset-top)) 0 12px;margin-bottom:12px}.back{border:0;background:transparent;color:var(--text);font:inherit;font-weight:700;padding:9px 0}.note,.title-group{background:var(--paper);border:1px solid var(--line);border-radius:24px;padding:clamp(20px,5vw,34px);margin:0 0 22px;box-shadow:var(--shadow)}.note h2,.group-title{font-size:clamp(24px,5vw,34px);line-height:1.15;margin:0 0 6px}.note h3{font-size:19px;margin:0 0 18px}.date{color:var(--muted);font-size:14px;margin-bottom:20px}.text-block{white-space:pre-wrap;font-family:Georgia,"Times New Roman",serif;font-size:clamp(18px,4.4vw,22px);line-height:1.72;text-align:justify;hyphens:auto;margin:14px 0}.media-image{display:block;width:100%;height:auto;border-radius:18px;margin:22px 0;background:var(--bg)}.audio-card{margin:20px 0;padding:16px;border-radius:18px;background:var(--audio);border:1px solid var(--line)}.audio-meta{font-size:14px;color:var(--muted);margin-bottom:10px}.audio-card audio{display:block;width:100%;min-height:44px}.missing{color:var(--muted);font-size:14px;font-style:italic;margin:16px 0}.empty{text-align:center;color:var(--muted);padding:60px 10px}</style></head><body><main id="catalog" class="page"><h1 class="brand">Memora</h1>""")
        if (bundleCount > 1) writer.write("<div class=\"bundle\">$partLabel $bundleNumber / $bundleCount</div>")
        if (monthGroups.isEmpty()) {
            writer.write("<div class=\"empty\">${TextUtils.xmlEscape(labels.empty)}</div>")
        } else {
            monthGroups.entries.forEachIndexed { index, (_, pages) ->
                val shortCount = pages.filter { it.noteType == NoteType.SHORT.name }.sumOf { it.noteCount }
                val longCount = pages.filter { it.noteType == NoteType.LONG.name }.sumOf { it.noteCount }
                writer.write("<button class=\"month-btn\" type=\"button\" onclick=\"chooseMonth($index)\"><span><b>${TextUtils.xmlEscape(pages.first().label)}</b><small>$shortCount / $longCount</small></span><strong>→</strong></button>")
            }
        }
        writer.write("</main>")

        val monthJson = JSONArray().apply {
            monthGroups.values.forEach { pages ->
                put(JSONObject().apply {
                    put("label", pages.first().label)
                    put("short", JSONArray(pages.withIndex().filter { it.value.noteType == NoteType.SHORT.name }.map { meta.indexOf(it.value) }))
                    put("long", JSONArray(pages.withIndex().filter { it.value.noteType == NoteType.LONG.name }.map { meta.indexOf(it.value) }))
                    put("shortCount", pages.filter { it.noteType == NoteType.SHORT.name }.sumOf { it.noteCount })
                    put("longCount", pages.filter { it.noteType == NoteType.LONG.name }.sumOf { it.noteCount })
                })
            }
        }.toString().replace("</", "<\\/")

        writer.write("""<div id="typeOverlay" class="overlay" onclick="if(event.target.id==='typeOverlay')hideOverlay()"><div class="sheet"><h2 id="monthTitle"></h2><p class="sub">${TextUtils.xmlEscape(chooseType)}</p><div class="choices"><button id="shortBtn" class="choice"><b>${TextUtils.xmlEscape(shortLabel)}</b><small id="shortCount"></small></button><button id="longBtn" class="choice"><b>${TextUtils.xmlEscape(longLabel)}</b><small id="longCount"></small></button></div><button class="close" onclick="hideOverlay()">${TextUtils.xmlEscape(L.t(language,S.CLOSE))}</button></div></div>
<div id="partOverlay" class="overlay"><div class="sheet"><h2 id="partTitle"></h2><div id="partList" class="part-list"></div><button class="close" onclick="document.getElementById('partOverlay').classList.remove('show')">${TextUtils.xmlEscape(L.t(language,S.CLOSE))}</button></div></div>""")
        writer.flush()

        chunks.forEachIndexed { index, chunk ->
            val info = meta[index]
            writer.write("<section id=\"page-$index\" class=\"content page\"><div class=\"content-head\"><button class=\"back\" onclick=\"backToCatalog()\">← ${TextUtils.xmlEscape(backLabel)}</button><div><b>${TextUtils.xmlEscape(info.label)} · ${TextUtils.xmlEscape(htmlTypeLabel(htmlNoteType(chunk), language))}</b></div></div>")
            if (layout == WordLayout.CHRONOLOGICAL) {
                chunk.sortedBy { it.createdAt }.forEach { note ->
                    writer.write("<article class=\"note\"><h2>${TextUtils.xmlEscape(multimediaDisplayTitle(note, language))}</h2><div class=\"date\">${TextUtils.xmlEscape(TimeUtils.dateTime(note.createdAt, language))}</div>")
                    writer.flush()
                    writeEmbeddedBlocks(output, writer, note, labels)
                    writer.write("</article>")
                }
            } else {
                chunk.groupBy { multimediaDisplayTitle(it, language) }
                    .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                    .forEach { (title, grouped) ->
                        writer.write("<section class=\"title-group\"><h2 class=\"group-title\">${TextUtils.xmlEscape(title)}</h2>")
                        grouped.sortedBy { it.createdAt }.forEach { note ->
                            writer.write("<article class=\"note\"><h3>${TextUtils.xmlEscape(TimeUtils.dateTime(note.createdAt, language))}</h3>")
                            writer.flush()
                            writeEmbeddedBlocks(output, writer, note, labels)
                            writer.write("</article>")
                        }
                        writer.write("</section>")
                    }
            }
            writer.write("</section>")
            writer.flush()
        }

        writer.write("""<script>
const months=$monthJson;let selectedMonth=-1;
function chooseMonth(i){selectedMonth=i;const m=months[i];document.getElementById('monthTitle').textContent=m.label;document.getElementById('shortCount').textContent=m.shortCount;document.getElementById('longCount').textContent=m.longCount;const sb=document.getElementById('shortBtn'),lb=document.getElementById('longBtn');sb.disabled=!m.short.length;lb.disabled=!m.long.length;sb.onclick=()=>chooseType('short');lb.onclick=()=>chooseType('long');document.getElementById('typeOverlay').classList.add('show')}
function hideOverlay(){document.getElementById('typeOverlay').classList.remove('show')}
function chooseType(type){const m=months[selectedMonth];const pages=m[type]||[];hideOverlay();if(pages.length===1){showPage(pages[0]);return}const list=document.getElementById('partList');list.innerHTML='';document.getElementById('partTitle').textContent=m.label;pages.forEach((p,idx)=>{const b=document.createElement('button');b.className='part-btn';b.textContent='$partLabel '+(idx+1);b.onclick=()=>{document.getElementById('partOverlay').classList.remove('show');showPage(p)};list.appendChild(b)});document.getElementById('partOverlay').classList.add('show')}
function showPage(id){document.getElementById('catalog').style.display='none';document.querySelectorAll('.content').forEach(x=>x.classList.remove('show'));document.getElementById('page-'+id).classList.add('show');window.scrollTo(0,0)}
function backToCatalog(){document.querySelectorAll('.content').forEach(x=>x.classList.remove('show'));document.getElementById('catalog').style.display='block';window.scrollTo(0,0)}
</script></body></html>""")
        writer.flush()
    }

    private fun splitHtmlChunks(notes: List<NoteEntity>): List<List<NoteEntity>> {
        if (notes.isEmpty()) return emptyList()
        val byMonthAndType = notes.groupBy { note ->
            val month = YearMonth.from(Instant.ofEpochMilli(note.createdAt).atZone(ZoneId.systemDefault()))
            month to note.type
        }
        val keys = byMonthAndType.keys.sortedWith(
            compareBy<Pair<YearMonth, String>>(
                { it.first },
                { if (it.second == NoteType.SHORT.name) 0 else 1 }
            )
        )
        val result = mutableListOf<List<NoteEntity>>()
        keys.forEach { key ->
            val groupedNotes = byMonthAndType[key].orEmpty().sortedBy { it.createdAt }
            var current = mutableListOf<NoteEntity>()
            var currentBytes = 0L
            groupedNotes.forEach { note ->
                val size = estimateEmbeddedBytes(note)
                if (current.isNotEmpty() && currentBytes + size > HTML_RAW_MEDIA_TARGET_BYTES) {
                    result += current.toList()
                    current = mutableListOf()
                    currentBytes = 0L
                }
                current += note
                currentBytes += size
            }
            if (current.isNotEmpty()) result += current.toList()
        }
        return result
    }

    private fun estimateEmbeddedBytes(note: NoteEntity): Long {
        var bytes = note.plainText.toByteArray(Charsets.UTF_8).size.toLong()
        NoteBlocksCodec.decode(note.blocksJson, note.plainText).forEach { block ->
            val file = when (block) {
                is NoteBlock.Image -> storage.resolve(block.relativePath)
                is NoteBlock.Audio -> storage.resolve(block.relativePath)
                is NoteBlock.Text -> null
            }
            if (file != null && file.exists()) bytes += file.length()
        }
        return bytes
    }

    private fun writeSelfContainedHtmlPage(
        output: java.io.OutputStream,
        notes: List<NoteEntity>,
        language: AppLanguage,
        layout: WordLayout,
        periodLabel: String
    ) {
        val labels = multimediaLabels(language)
        val writer = output.bufferedWriter(Charsets.UTF_8)
        writer.write(htmlPageHeader(language, periodLabel))
        writer.flush()

        if (layout == WordLayout.CHRONOLOGICAL) {
            notes.sortedBy { it.createdAt }.forEach { note ->
                writer.write("<article class=\"note\"><h2>${TextUtils.xmlEscape(multimediaDisplayTitle(note, language))}</h2>")
                writer.write("<div class=\"date\">${TextUtils.xmlEscape(TimeUtils.dateTime(note.createdAt, language))}</div>")
                writer.flush()
                writeEmbeddedBlocks(output, writer, note, labels)
                writer.write("</article>")
            }
        } else {
            notes.groupBy { multimediaDisplayTitle(it, language) }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .forEach { (title, grouped) ->
                    writer.write("<section class=\"title-group\"><h2 class=\"group-title\">${TextUtils.xmlEscape(title)}</h2>")
                    grouped.sortedBy { it.createdAt }.forEach { note ->
                        writer.write("<article class=\"note grouped\"><h3>${TextUtils.xmlEscape(TimeUtils.dateTime(note.createdAt, language))}</h3>")
                        writer.flush()
                        writeEmbeddedBlocks(output, writer, note, labels)
                        writer.write("</article>")
                    }
                    writer.write("</section>")
                }
        }
        if (notes.isEmpty()) writer.write("<div class=\"empty\">${TextUtils.xmlEscape(labels.empty)}</div>")
        writer.write("<div class=\"footer\">Memora HTML export</div></main></body></html>")
        writer.flush()
    }

    private fun writeEmbeddedBlocks(
        output: java.io.OutputStream,
        writer: java.io.Writer,
        note: NoteEntity,
        labels: MultimediaLabels
    ) {
        NoteBlocksCodec.decode(note.blocksJson, note.plainText).forEach { block ->
            when (block) {
                is NoteBlock.Text -> if (block.text.isNotBlank()) {
                    writer.write("<div class=\"text-block\">")
                    writer.write(styledHtml(block.text, block.styles))
                    writer.write("</div>")
                }
                is NoteBlock.Image -> {
                    val file = storage.resolve(block.relativePath)
                    if (file.exists() && file.isFile) {
                        val optimized = optimizedImage(file, block.mimeType)
                        writer.write("<img class=\"media-image\" loading=\"lazy\" src=\"data:${optimized.mimeType};base64,")
                        writer.flush()
                        writeBase64(output, optimized.bytes)
                        writer.write("\" alt=\"${TextUtils.xmlEscape(labels.image)}\">")
                    } else writer.write("<div class=\"missing\">${TextUtils.xmlEscape(labels.missingImage)}</div>")
                }
                is NoteBlock.Audio -> {
                    val file = storage.resolve(block.relativePath)
                    if (file.exists() && file.isFile) {
                        val mime = block.mimeType.ifBlank { audioMimeForFile(file) }
                        writer.write("<div class=\"audio-card\"><div class=\"audio-meta\">🎙 ${TextUtils.xmlEscape(labels.audio)} · ${formatMultimediaDuration(block.durationMs)}</div>")
                        writer.write("<audio controls preload=\"metadata\" src=\"data:${TextUtils.xmlEscape(mime)};base64,")
                        writer.flush()
                        writeBase64(output, file)
                        writer.write("\">${TextUtils.xmlEscape(labels.audioFallback)}</audio></div>")
                    } else writer.write("<div class=\"missing\">${TextUtils.xmlEscape(labels.missingAudio)}</div>")
                }
            }
            writer.flush()
        }
    }

    private fun styledHtml(text: String, styles: List<TextInlineStyle>): String {
        if (text.isEmpty() || styles.isEmpty()) return TextUtils.xmlEscape(text)
        val boundaries = mutableSetOf(0, text.length)
        styles.forEach { span ->
            boundaries += span.start.coerceIn(0, text.length)
            boundaries += span.end.coerceIn(0, text.length)
        }
        val points = boundaries.sorted()
        return buildString {
            for (i in 0 until points.lastIndex) {
                val start = points[i]
                val end = points[i + 1]
                if (end <= start) continue
                val active = styles.filter { it.start < end && it.end > start }
                val bold = active.any { it.bold }
                val italic = active.any { it.italic }
                if (bold) append("<strong>")
                if (italic) append("<em>")
                append(TextUtils.xmlEscape(text.substring(start, end)))
                if (italic) append("</em>")
                if (bold) append("</strong>")
            }
        }
    }

    private fun writeBase64(output: java.io.OutputStream, file: File) {
        val base64 = Base64OutputStream(output, Base64.NO_WRAP or Base64.NO_CLOSE)
        file.inputStream().use { input -> input.copyTo(base64, 48 * 1024) }
        base64.close()
    }

    private fun writeBase64(output: java.io.OutputStream, bytes: ByteArray) {
        val base64 = Base64OutputStream(output, Base64.NO_WRAP or Base64.NO_CLOSE)
        base64.write(bytes)
        base64.close()
    }

    private fun optimizedImage(file: File, declaredMime: String): OptimizedImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > HTML_IMAGE_DECODE_MAX_DIMENSION) {
            sample *= 2
        }
        val bitmap = runCatching {
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull() ?: return OptimizedImage(
            file.readBytes(),
            declaredMime.ifBlank { imageMimeForExtension(file.extension) }
        )
        val longest = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        val scale = minOf(1f, HTML_IMAGE_MAX_DIMENSION.toFloat() / longest.toFloat())
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (targetWidth != bitmap.width || targetHeight != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } else bitmap
        val out = ByteArrayOutputStream()
        val hasAlpha = scaled.hasAlpha()
        if (hasAlpha) scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        else scaled.compress(Bitmap.CompressFormat.JPEG, HTML_JPEG_QUALITY, out)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return OptimizedImage(out.toByteArray(), if (hasAlpha) "image/png" else "image/jpeg")
    }

    private fun audioMimeForFile(file: File): String = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "webm" -> "audio/webm"
        "aac" -> "audio/aac"
        else -> "audio/mp4"
    }

    private fun chunkPeriodLabel(notes: List<NoteEntity>, language: AppLanguage): String {
        val first = notes.firstOrNull()?.createdAt ?: return "Memora"
        val ym = YearMonth.from(Instant.ofEpochMilli(first).atZone(ZoneId.systemDefault()))
        return ym.format(DateTimeFormatter.ofPattern("LLLL yyyy", TimeUtils.locale(language)))
    }

    private fun htmlFilePeriod(notes: List<NoteEntity>): String {
        val first = notes.firstOrNull()?.createdAt ?: return "Memora"
        val ym = YearMonth.from(Instant.ofEpochMilli(first).atZone(ZoneId.systemDefault()))
        return ym.toString()
    }


    private fun htmlNoteType(notes: List<NoteEntity>): NoteType = runCatching {
        NoteType.valueOf(notes.firstOrNull()?.type ?: NoteType.SHORT.name)
    }.getOrDefault(NoteType.SHORT)

    private fun htmlTypeSlug(type: NoteType): String = when (type) {
        NoteType.SHORT -> "short"
        NoteType.LONG -> "long"
    }

    private fun htmlTypeLabel(type: NoteType, language: AppLanguage): String =
        L.t(language, if (type == NoteType.SHORT) S.SHORT_NOTES else S.LONG_NOTES)

    private fun htmlPageHeader(language: AppLanguage, periodLabel: String): String {
        val langCode = language.tag
        return """<!doctype html><html lang="$langCode"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><meta name="color-scheme" content="light dark"><title>Memora · ${TextUtils.xmlEscape(periodLabel)}</title><style>
:root{--bg:#f4f1ea;--paper:#fffdf8;--text:#202629;--muted:#788184;--line:#e4ded2;--accent:#2e6871;--audio:#edf4f4;--shadow:0 10px 30px rgba(32,38,41,.06)}@media(prefers-color-scheme:dark){:root{--bg:#15191b;--paper:#202527;--text:#edf1ef;--muted:#a9b0ae;--line:#343b3d;--accent:#8bc1c7;--audio:#263235;--shadow:none}}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;line-height:1.65}.page{width:min(900px,100%);margin:0 auto;padding:max(28px,env(safe-area-inset-top)) 18px max(56px,env(safe-area-inset-bottom))}.brand{margin:0;font-size:clamp(34px,8vw,54px);letter-spacing:-.04em}.period{color:var(--muted);margin:2px 0 28px}.note,.title-group{background:var(--paper);border:1px solid var(--line);border-radius:24px;padding:clamp(20px,5vw,34px);margin:0 0 22px;box-shadow:var(--shadow)}.note h2,.group-title{font-size:clamp(24px,5vw,34px);line-height:1.15;margin:0 0 6px}.note h3{font-size:19px;margin:0 0 18px}.date{color:var(--muted);font-size:14px;margin-bottom:20px}.text-block{white-space:pre-wrap;font-family:Georgia,"Times New Roman",serif;font-size:clamp(18px,4.4vw,22px);line-height:1.72;margin:14px 0}.media-image{display:block;width:100%;height:auto;border-radius:18px;margin:22px 0;background:var(--bg)}.audio-card{margin:20px 0;padding:16px;border-radius:18px;background:var(--audio);border:1px solid var(--line)}.audio-meta{font-size:14px;color:var(--muted);margin-bottom:10px}.audio-card audio{display:block;width:100%;min-height:44px}.missing{color:var(--muted);font-size:14px;font-style:italic;margin:16px 0}.grouped{box-shadow:none;margin:14px 0 0;border-radius:18px}.empty{text-align:center;color:var(--muted);padding:70px 10px}.footer{text-align:center;color:var(--muted);font-size:12px;margin-top:28px}</style></head><body><main class="page"><h1 class="brand">Memora</h1><div class="period">${TextUtils.xmlEscape(periodLabel)}</div>"""
    }

    private fun htmlIndex(pages: List<HtmlPageInfo>, language: AppLanguage): String {
        val labels = when (language) {
            AppLanguage.EN -> HtmlIndexLabels(
                notes = "notes", open = "Open", chooseType = "Choose note type",
                short = "Short Notes", long = "Long Notes", folder = "Select export folder",
                folderReady = "Folder selected", folderHint = "On Android, select this Memora HTML export folder once. Then month and note-type buttons open the correct page without broken local links.",
                part = "Part", close = "Close", back = "Back to months", missing = "The requested HTML file was not found in the selected folder. Select the Memora HTML export folder.",
                empty = "No notes in this export"
            )
            AppLanguage.RU -> HtmlIndexLabels(
                notes = "записей", open = "Открыть", chooseType = "Выберите тип записи",
                short = "Короткие записи", long = "Длинные записи", folder = "Выбрать папку экспорта",
                folderReady = "Папка выбрана", folderHint = "На Android один раз выберите эту папку экспорта Memora HTML. После этого месяц и тип записи открываются без ошибок локальных ссылок.",
                part = "Часть", close = "Закрыть", back = "К месяцам", missing = "Нужный HTML-файл не найден в выбранной папке. Выберите папку экспорта Memora HTML.",
                empty = "В этом экспорте нет записей"
            )
            AppLanguage.UZ -> HtmlIndexLabels(
                notes = "yozuv", open = "Ochish", chooseType = "Matn turini tanlang",
                short = "Qisqa matn", long = "Uzun matn", folder = "Eksport papkasini tanlash",
                folderReady = "Papka tanlandi", folderHint = "Androidda shu Memora HTML eksport papkasini bir marta tanlang. Keyin oy va matn turi tugmalari kerakli sahifani xatosiz ochadi.",
                part = "Qism", close = "Yopish", back = "Oylarga qaytish", missing = "Kerakli HTML fayl tanlangan papkada topilmadi. Memora HTML eksport papkasini tanlang.",
                empty = "Bu eksportda yozuv yo‘q"
            )
            else -> HtmlIndexLabels(
                notes = "notes", open = "Open", chooseType = "Choose note type",
                short = L.t(language, S.SHORT_NOTES), long = L.t(language, S.LONG_NOTES), folder = "Select export folder",
                folderReady = "Folder selected", folderHint = "Open the self-contained Memora HTML index.",
                part = "Part", close = L.t(language, S.CLOSE), back = L.t(language, S.BACK), missing = "Content not found.",
                empty = "No notes in this export"
            )
        }

        val monthArray = JSONArray()
        pages.groupBy { it.monthKey }.toSortedMap().forEach { (_, monthPages) ->
            val month = JSONObject()
            month.put("label", monthPages.firstOrNull()?.label ?: "Memora")
            month.put("total", monthPages.sumOf { it.noteCount })
            fun pageArray(type: NoteType): JSONArray {
                val array = JSONArray()
                monthPages.filter { it.noteType == type.name }.sortedBy { it.part }.forEach { page ->
                    array.put(JSONObject().apply {
                        put("file", page.fileName)
                        put("count", page.noteCount)
                        put("part", page.part)
                        put("parts", page.partCount)
                    })
                }
                return array
            }
            month.put("short", pageArray(NoteType.SHORT))
            month.put("long", pageArray(NoteType.LONG))
            monthArray.put(month)
        }
        val monthsJson = monthArray.toString().replace("</", "<\\/")
        val monthButtons = if (pages.isEmpty()) {
            "<div class=\"empty\">${TextUtils.xmlEscape(labels.empty)}</div>"
        } else {
            pages.groupBy { it.monthKey }.toSortedMap().values.mapIndexed { index, monthPages ->
                val label = monthPages.first().label
                val total = monthPages.sumOf { it.noteCount }
                "<button class=\"item\" type=\"button\" onclick=\"chooseMonth($index)\"><span><b>${TextUtils.xmlEscape(label)}</b><small>$total ${TextUtils.xmlEscape(labels.notes)}</small></span><strong>${TextUtils.xmlEscape(labels.open)} →</strong></button>"
            }.joinToString("")
        }
        val langCode = language.tag

        return """<!doctype html><html lang="$langCode"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><meta name="color-scheme" content="light dark"><title>Memora</title><style>
:root{--bg:#f4f1ea;--paper:#fffdf8;--text:#202629;--muted:#788184;--line:#e4ded2;--accent:#2e6871;--accentSoft:#e9f0ef;--shadow:0 10px 30px rgba(32,38,41,.06)}@media(prefers-color-scheme:dark){:root{--bg:#15191b;--paper:#202527;--text:#edf1ef;--muted:#a9b0ae;--line:#343b3d;--accent:#8bc1c7;--accentSoft:#263235;--shadow:none}}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}.page{max-width:760px;margin:auto;padding:max(34px,env(safe-area-inset-top)) 18px max(60px,env(safe-area-inset-bottom))}h1{font-size:clamp(42px,10vw,62px);letter-spacing:-.045em;margin:0 0 22px}.folder-card{background:var(--accentSoft);border:1px solid var(--line);border-radius:18px;padding:15px 16px;margin:0 0 22px}.folder-card p{margin:0 0 12px;color:var(--muted);font-size:14px;line-height:1.5}.folder-btn,.choice,.part-btn{appearance:none;border:1px solid var(--line);background:var(--paper);color:var(--text);border-radius:14px;padding:12px 15px;font:inherit;font-weight:650}.folder-btn.ready{border-color:var(--accent);color:var(--accent)}.item{width:100%;display:flex;align-items:center;justify-content:space-between;gap:16px;padding:20px;margin:12px 0;border:1px solid var(--line);border-radius:20px;background:var(--paper);color:var(--text);text-align:left;font:inherit;box-shadow:var(--shadow)}.item span{display:flex;flex-direction:column;gap:5px}.item b{font-size:19px}.item small{color:var(--muted);font-size:14px}.item strong{white-space:nowrap}.empty{text-align:center;color:var(--muted);padding:70px 10px}.overlay{position:fixed;inset:0;background:rgba(0,0,0,.42);display:none;align-items:flex-end;z-index:20}.overlay.show{display:flex}.sheet{width:min(760px,100%);margin:0 auto;background:var(--paper);border-radius:26px 26px 0 0;padding:22px 18px max(28px,env(safe-area-inset-bottom));box-shadow:0 -16px 50px rgba(0,0,0,.16)}.sheet h2{margin:0 0 6px;font-size:25px}.sheet .sub{color:var(--muted);margin:0 0 18px}.choices{display:grid;grid-template-columns:1fr 1fr;gap:12px}.choice{display:flex;flex-direction:column;align-items:flex-start;gap:4px;padding:17px}.choice small{color:var(--muted);font-weight:500}.choice:disabled{opacity:.42}.parts{display:grid;gap:10px}.part-btn{text-align:left}.close-btn{margin-top:16px;border:0;background:transparent;color:var(--muted);font:inherit;padding:10px 0;width:100%}.viewer{position:fixed;inset:0;background:var(--bg);z-index:30;display:none;flex-direction:column}.viewer.show{display:flex}.viewer-bar{height:auto;min-height:58px;padding:max(8px,env(safe-area-inset-top)) 12px 8px;display:flex;align-items:center;gap:10px;background:var(--paper);border-bottom:1px solid var(--line)}.viewer-bar button{border:0;background:transparent;color:var(--text);font:inherit;font-weight:700;padding:10px}.viewer-title{font-weight:700;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.viewer iframe{border:0;width:100%;flex:1;background:var(--bg)}.toast{position:fixed;left:18px;right:18px;bottom:max(28px,env(safe-area-inset-bottom));margin:auto;max-width:680px;background:var(--text);color:var(--paper);padding:13px 16px;border-radius:14px;display:none;z-index:50}.toast.show{display:block}
</style></head><body><main class="page"><h1>Memora</h1><div class="folder-card"><p>${TextUtils.xmlEscape(labels.folderHint)}</p><button id="folderButton" class="folder-btn" type="button">${TextUtils.xmlEscape(labels.folder)}</button></div>$monthButtons</main>
<input id="folderInput" type="file" webkitdirectory directory multiple hidden>
<div id="typeOverlay" class="overlay" onclick="overlayClick(event,'typeOverlay')"><div class="sheet"><h2 id="monthTitle"></h2><p class="sub">${TextUtils.xmlEscape(labels.chooseType)}</p><div class="choices"><button id="shortButton" class="choice" type="button"><b>${TextUtils.xmlEscape(labels.short)}</b><small id="shortCount"></small></button><button id="longButton" class="choice" type="button"><b>${TextUtils.xmlEscape(labels.long)}</b><small id="longCount"></small></button></div><button class="close-btn" type="button" onclick="hideOverlay('typeOverlay')">${TextUtils.xmlEscape(labels.close)}</button></div></div>
<div id="partOverlay" class="overlay" onclick="overlayClick(event,'partOverlay')"><div class="sheet"><h2 id="partTitle"></h2><div id="partList" class="parts"></div><button class="close-btn" type="button" onclick="hideOverlay('partOverlay')">${TextUtils.xmlEscape(labels.close)}</button></div></div>
<div id="viewer" class="viewer"><div class="viewer-bar"><button type="button" onclick="closeViewer()">← ${TextUtils.xmlEscape(labels.back)}</button><div id="viewerTitle" class="viewer-title"></div></div><iframe id="viewerFrame" title="Memora"></iframe></div><div id="toast" class="toast"></div>
<script>
const months=$monthsJson;const filesByName=new Map();let selectedMonth=-1;let pending=null;let activeObjectUrl=null;
const folderInput=document.getElementById('folderInput');const folderButton=document.getElementById('folderButton');
folderButton.addEventListener('click',()=>folderInput.click());
folderInput.addEventListener('change',()=>{filesByName.clear();Array.from(folderInput.files||[]).forEach(f=>filesByName.set(f.name,f));if(filesByName.size){folderButton.textContent=${JSONObject.quote(labels.folderReady)};folderButton.classList.add('ready')}if(pending){const p=pending;pending=null;openPage(p.page,p.title)}});
function chooseMonth(i){selectedMonth=i;const m=months[i];document.getElementById('monthTitle').textContent=m.label;const s=m.short||[],l=m.long||[];const sc=s.reduce((a,p)=>a+(p.count||0),0),lc=l.reduce((a,p)=>a+(p.count||0),0);const sb=document.getElementById('shortButton'),lb=document.getElementById('longButton');document.getElementById('shortCount').textContent=sc+' '+${JSONObject.quote(labels.notes)};document.getElementById('longCount').textContent=lc+' '+${JSONObject.quote(labels.notes)};sb.disabled=!s.length;lb.disabled=!l.length;sb.onclick=()=>selectType('short');lb.onclick=()=>selectType('long');showOverlay('typeOverlay')}
function selectType(type){const m=months[selectedMonth];const pages=m[type]||[];hideOverlay('typeOverlay');const typeLabel=type==='short'?${JSONObject.quote(labels.short)}:${JSONObject.quote(labels.long)};if(pages.length===1){openPage(pages[0],m.label+' · '+typeLabel);return}const list=document.getElementById('partList');list.innerHTML='';document.getElementById('partTitle').textContent=m.label+' · '+typeLabel;pages.forEach((p,idx)=>{const b=document.createElement('button');b.className='part-btn';b.type='button';b.textContent=${JSONObject.quote(labels.part)}+' '+(idx+1)+' · '+(p.count||0)+' '+${JSONObject.quote(labels.notes)};b.onclick=()=>{hideOverlay('partOverlay');openPage(p,m.label+' · '+typeLabel+' · '+${JSONObject.quote(labels.part)}+' '+(idx+1))};list.appendChild(b)});showOverlay('partOverlay')}
function openPage(page,title){if(location.protocol!=='content:'){location.href=page.file;return}const f=filesByName.get(page.file);if(!f){pending={page,title};showToast(${JSONObject.quote(labels.missing)});folderInput.click();return}if(activeObjectUrl)URL.revokeObjectURL(activeObjectUrl);activeObjectUrl=URL.createObjectURL(f);document.getElementById('viewerFrame').src=activeObjectUrl;document.getElementById('viewerTitle').textContent=title;document.getElementById('viewer').classList.add('show');document.body.style.overflow='hidden'}
function closeViewer(){document.getElementById('viewer').classList.remove('show');document.getElementById('viewerFrame').src='about:blank';document.body.style.overflow='';if(activeObjectUrl){URL.revokeObjectURL(activeObjectUrl);activeObjectUrl=null}}
function showOverlay(id){document.getElementById(id).classList.add('show')}function hideOverlay(id){document.getElementById(id).classList.remove('show')}function overlayClick(e,id){if(e.target.id===id)hideOverlay(id)}function showToast(t){const el=document.getElementById('toast');el.textContent=t;el.classList.add('show');setTimeout(()=>el.classList.remove('show'),3500)}
</script></body></html>"""
    }

    private fun htmlFolderReadme(language: AppLanguage): String = when (language) {
        AppLanguage.EN -> "Open index.html. Tap a month, then choose Short Notes or Long Notes. On Android Chrome, select this Memora HTML export folder once when prompted; index.html then opens the requested self-contained month/part page inside the viewer. Images and audio are embedded directly in each page."
        AppLanguage.RU -> "Откройте index.html. Нажмите месяц, затем выберите короткие или длинные записи. В Chrome на Android один раз выберите эту папку экспорта Memora HTML по запросу; после этого index.html откроет нужную самостоятельную страницу месяца/части во встроенном просмотрщике. Изображения и аудио встроены прямо в страницы."
        AppLanguage.UZ -> "index.html faylini oching. Oyni, keyin Qisqa yoki Uzun matnni tanlang. Rasm va audio HTML ichida."
        else -> "Open index.html, choose a month, then Short or Long Notes. Images and audio are embedded in HTML."
    }

    private fun wordFolderReadme(language: AppLanguage, docxName: String, hasAudio: Boolean): String = when (language) {
        AppLanguage.EN -> buildString { append("Open $docxName. Images are embedded in Word."); if (hasAudio) append(" Audio files are in the Audio folder. Each audio has an A001/A002… marker and date in its file name; the same marker appears at the exact audio position in Word.") }
        AppLanguage.RU -> buildString { append("Откройте $docxName. Изображения встроены в Word."); if (hasAudio) append(" Аудиофайлы находятся в папке Audio. В имени каждого аудио есть метка A001/A002… и дата; такая же метка стоит в Word в точном месте аудио.") }
        AppLanguage.UZ -> buildString { append("$docxName faylini oching. Rasmlar Word ichida."); if (hasAudio) append(" Audiolar Audio papkasida; A001/A002 belgisi Worddagi joy bilan mos keladi.") }
        else -> buildString { append("Open $docxName. Images are embedded in Word."); if (hasAudio) append(" Audio files are in the Audio folder; A001/A002 markers match their positions in Word.") }
    }

    private fun exportFolderBaseName(request: ExportRequest, kind: String): String {
        val stamp = java.time.LocalDate.now().toString()
        return "Memora-$kind-${request.backupScope.name.lowercase()}-$stamp"
    }

    private fun uniqueFolderName(parent: DocumentFile, desired: String): String {
        if (parent.findFile(desired) == null) return desired
        var index = 2
        while (parent.findFile("$desired-$index") != null) index++
        return "$desired-$index"
    }

    suspend fun write(request: ExportRequest, uri: Uri, language: AppLanguage) {
        val range = request.range()
        val type = request.scopeNoteType()
        val notes = repository.notesForExport(type, range.start, range.end)
        when (request.format) {
            ExportFormat.WORD -> {
                resolver.openOutputStream(uri)?.use { out ->
                    writeWordPackage(
                        output = out,
                        request = request,
                        notes = notes,
                        language = language
                    )
                } ?: error("Unable to open output file")
            }
            ExportFormat.MULTIMEDIA -> {
                resolver.openOutputStream(uri)?.use { out ->
                    writeMultimediaZip(
                        output = out,
                        request = request,
                        notes = notes,
                        language = language
                    )
                } ?: error("Unable to open output file")
            }
            ExportFormat.BACKUP -> {
                resolver.openOutputStream(uri)?.use { out ->
                    writeBackupZip(out, request, notes)
                } ?: error("Unable to open output file")
            }
        }
    }

    private fun writeWordPackage(
        output: java.io.OutputStream,
        request: ExportRequest,
        notes: List<NoteEntity>,
        language: AppLanguage
    ) {
        val built = DocxWriter.build(
            notes = notes,
            language = language,
            layout = request.wordLayout,
            storage = storage
        )
        val docxName = wordDocumentFileName(request)

        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry(docxName))
            zip.write(built.docxBytes)
            zip.closeEntry()

            built.audios.forEach { audio ->
                zip.putNextEntry(ZipEntry("Audio/${audio.targetName}"))
                audio.file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }

            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(wordPackageReadme(language, docxName, built.audios.isNotEmpty()).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun writeMultimediaZip(
        output: java.io.OutputStream,
        request: ExportRequest,
        notes: List<NoteEntity>,
        language: AppLanguage
    ) {
        val images = linkedMapOf<String, MultimediaAsset>()
        val audios = linkedMapOf<String, MultimediaAsset>()
        var imageNumber = 1
        var audioNumber = 1

        notes.forEach { note ->
            NoteBlocksCodec.decode(note.blocksJson, note.plainText).forEach { block ->
                when (block) {
                    is NoteBlock.Image -> {
                        val file = storage.resolve(block.relativePath)
                        if (file.exists() && file.isFile && block.id !in images) {
                            val extension = imageExtension(block.mimeType, file)
                            images[block.id] = MultimediaAsset(
                                blockId = block.id,
                                file = file,
                                targetPath = "Images/image${imageNumber++}.$extension",
                                mimeType = block.mimeType.ifBlank { imageMimeForExtension(extension) }
                            )
                        }
                    }
                    is NoteBlock.Audio -> {
                        val file = storage.resolve(block.relativePath)
                        if (file.exists() && file.isFile && block.id !in audios) {
                            val extension = audioExtension(block.mimeType, file)
                            audios[block.id] = MultimediaAsset(
                                blockId = block.id,
                                file = file,
                                targetPath = "Audio/audio${audioNumber++}.$extension",
                                mimeType = block.mimeType.ifBlank { "audio/mp4" }
                            )
                        }
                    }
                    is NoteBlock.Text -> Unit
                }
            }
        }

        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry("Memora.html"))
            zip.write(
                multimediaHtml(
                    notes = notes,
                    language = language,
                    layout = request.wordLayout,
                    images = images,
                    audios = audios
                ).toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            images.values.forEach { asset ->
                zip.putNextEntry(ZipEntry(asset.targetPath))
                asset.file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            audios.values.forEach { asset ->
                zip.putNextEntry(ZipEntry(asset.targetPath))
                asset.file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }

            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(multimediaReadme(language).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun multimediaHtml(
        notes: List<NoteEntity>,
        language: AppLanguage,
        layout: WordLayout,
        images: Map<String, MultimediaAsset>,
        audios: Map<String, MultimediaAsset>
    ): String {
        val langCode = language.tag
        val labels = multimediaLabels(language)
        val body = buildString {
            if (layout == WordLayout.CHRONOLOGICAL) {
                notes.sortedBy { it.createdAt }.forEach { note ->
                    append("<article class=\"note\">")
                    append("<h2>").append(TextUtils.xmlEscape(multimediaDisplayTitle(note, language))).append("</h2>")
                    append("<div class=\"date\">").append(TextUtils.xmlEscape(TimeUtils.dateTime(note.createdAt, language))).append("</div>")
                    appendMultimediaBlocks(note, labels, images, audios)
                    append("</article>")
                }
            } else {
                val grouped = notes.groupBy { multimediaDisplayTitle(it, language) }
                    .entries.sortedBy { entry -> entry.value.minOfOrNull { it.createdAt } ?: Long.MAX_VALUE }
                grouped.forEach { (title, groupedNotes) ->
                    append("<section class=\"title-group\"><h2 class=\"group-title\">")
                        .append(TextUtils.xmlEscape(title)).append("</h2>")
                    groupedNotes.sortedBy { it.createdAt }.forEach { note ->
                        append("<article class=\"note grouped\">")
                        append("<h3>").append(TextUtils.xmlEscape(TimeUtils.dateTime(note.createdAt, language))).append("</h3>")
                        appendMultimediaBlocks(note, labels, images, audios)
                        append("</article>")
                    }
                    append("</section>")
                }
            }
            if (notes.isEmpty()) {
                append("<div class=\"empty\">").append(TextUtils.xmlEscape(labels.empty)).append("</div>")
            }
        }

        return """<!doctype html>
<html lang="$langCode">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="color-scheme" content="light dark">
<title>Memora</title>
<style>
:root{--bg:#f4f1ea;--paper:#fffdf8;--text:#202629;--muted:#788184;--line:#e4ded2;--accent:#2e6871;--audio:#edf4f4;--shadow:0 10px 30px rgba(32,38,41,.06)}
@media(prefers-color-scheme:dark){:root{--bg:#15191b;--paper:#202527;--text:#edf1ef;--muted:#a9b0ae;--line:#343b3d;--accent:#8bc1c7;--audio:#263235;--shadow:none}}
*{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;background:var(--bg);color:var(--text);font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;line-height:1.65}
.page{width:min(900px,100%);margin:0 auto;padding:max(28px,env(safe-area-inset-top)) 18px max(56px,env(safe-area-inset-bottom))}.brand{margin:0 0 30px;font-size:clamp(34px,8vw,54px);letter-spacing:-.04em}.note,.title-group{background:var(--paper);border:1px solid var(--line);border-radius:24px;padding:clamp(20px,5vw,34px);margin:0 0 22px;box-shadow:var(--shadow)}
.note h2,.group-title{font-size:clamp(24px,5vw,34px);line-height:1.15;margin:0 0 6px;letter-spacing:-.025em}.note h3{font-size:19px;margin:0 0 18px}.date{color:var(--muted);font-size:14px;margin-bottom:20px}.text-block{white-space:pre-wrap;font-family:Georgia,"Times New Roman",serif;font-size:clamp(18px,4.4vw,22px);line-height:1.72;margin:14px 0}
.media-image{display:block;width:100%;height:auto;border-radius:18px;margin:22px 0;background:var(--bg)}.audio-card{margin:20px 0;padding:16px;border-radius:18px;background:var(--audio);border:1px solid var(--line)}.audio-meta{font-size:14px;color:var(--muted);margin-bottom:10px}.audio-card audio{display:block;width:100%;min-height:44px}.audio-link{display:inline-block;margin-top:9px;color:var(--accent);text-decoration:none;font-size:14px;font-weight:600}.missing{color:var(--muted);font-size:14px;font-style:italic;margin:16px 0}.grouped{box-shadow:none;margin:14px 0 0;border-radius:18px}.title-group>.group-title{margin-bottom:14px}.empty{text-align:center;color:var(--muted);padding:70px 10px}.footer{text-align:center;color:var(--muted);font-size:12px;margin-top:28px}
</style>
</head>
<body><main class="page"><h1 class="brand">Memora</h1>$body<div class="footer">Memora multimedia export</div></main></body>
</html>"""
    }

    private fun StringBuilder.appendMultimediaBlocks(
        note: NoteEntity,
        labels: MultimediaLabels,
        images: Map<String, MultimediaAsset>,
        audios: Map<String, MultimediaAsset>
    ) {
        NoteBlocksCodec.decode(note.blocksJson, note.plainText).forEach { block ->
            when (block) {
                is NoteBlock.Text -> {
                    if (block.text.isNotBlank()) {
                        append("<div class=\"text-block\">")
                            .append(TextUtils.xmlEscape(block.text))
                            .append("</div>")
                    }
                }
                is NoteBlock.Image -> {
                    val asset = images[block.id]
                    if (asset != null) {
                        append("<img class=\"media-image\" loading=\"lazy\" src=\"")
                            .append(TextUtils.xmlEscape(asset.targetPath))
                            .append("\" alt=\"").append(TextUtils.xmlEscape(labels.image)).append("\">")
                    } else {
                        append("<div class=\"missing\">").append(TextUtils.xmlEscape(labels.missingImage)).append("</div>")
                    }
                }
                is NoteBlock.Audio -> {
                    val asset = audios[block.id]
                    if (asset != null) {
                        append("<div class=\"audio-card\"><div class=\"audio-meta\">🎙 ")
                            .append(TextUtils.xmlEscape(labels.audio)).append(" · ")
                            .append(formatMultimediaDuration(block.durationMs)).append("</div>")
                        append("<audio controls preload=\"metadata\"><source src=\"")
                            .append(TextUtils.xmlEscape(asset.targetPath)).append("\" type=\"")
                            .append(TextUtils.xmlEscape(asset.mimeType)).append("\">")
                            .append(TextUtils.xmlEscape(labels.audioFallback)).append("</audio>")
                        append("<a class=\"audio-link\" href=\"").append(TextUtils.xmlEscape(asset.targetPath))
                            .append("\" download>").append(TextUtils.xmlEscape(labels.openAudio)).append("</a></div>")
                    } else {
                        append("<div class=\"missing\">").append(TextUtils.xmlEscape(labels.missingAudio)).append("</div>")
                    }
                }
            }
        }
    }

    private fun multimediaDisplayTitle(note: NoteEntity, language: AppLanguage): String =
        note.title.ifBlank { L.t(language, S.UNTITLED) }

    private fun multimediaLabels(language: AppLanguage): MultimediaLabels = when (language) {
        AppLanguage.EN -> MultimediaLabels(
            audio = "Audio", image = "Image", openAudio = "Open audio file",
            audioFallback = "Your browser cannot play this audio.",
            missingImage = "Image file is missing.", missingAudio = "Audio file is missing.", empty = "No notes in this export."
        )
        AppLanguage.RU -> MultimediaLabels(
            audio = "Аудио", image = "Изображение", openAudio = "Открыть аудиофайл",
            audioFallback = "Браузер не может воспроизвести это аудио.",
            missingImage = "Файл изображения не найден.", missingAudio = "Аудиофайл не найден.", empty = "В этом экспорте нет записей."
        )
        AppLanguage.UZ -> MultimediaLabels(
            audio = "Audio", image = "Rasm", openAudio = "Audio faylni ochish",
            audioFallback = "Brauzer bu audioni ijro eta olmadi.",
            missingImage = "Rasm fayli topilmadi.", missingAudio = "Audio fayli topilmadi.", empty = "Bu eksportda yozuv yo‘q."
        )
        else -> MultimediaLabels(
            audio = "Audio", image = "Image", openAudio = "Open audio file",
            audioFallback = "Your browser cannot play this audio.",
            missingImage = "Image file is missing.", missingAudio = "Audio file is missing.", empty = "No notes in this export."
        )
    }

    private fun multimediaReadme(language: AppLanguage): String = when (language) {
        AppLanguage.EN -> """Memora multimedia export

1. Extract this ZIP completely. Do not open Memora.html while it is still inside the ZIP.
2. Keep Memora.html, Images and Audio in the same extracted folder.
3. Open Memora.html with Chrome or another modern browser.
4. Audio players work inside the page. If a browser blocks playback, use the “Open audio file” link below that player.
"""
        AppLanguage.RU -> """Мультимедийный экспорт Memora

1. Полностью распакуйте ZIP. Не открывайте Memora.html прямо внутри архива.
2. Оставьте Memora.html и папки Images и Audio в одной распакованной папке.
3. Откройте Memora.html через Chrome или другой современный браузер.
4. Аудио воспроизводится прямо на странице. Если браузер блокирует воспроизведение, нажмите «Открыть аудиофайл» под плеером.
"""
        AppLanguage.UZ -> """Memora multimedia eksporti

1. ZIP faylni to‘liq ochib (extract) oling. Memora.html faylini ZIP ichidan turib ochmang.
2. Memora.html, Images va Audio papkalarini bitta ochilgan papkada birga saqlang.
3. Memora.html faylini Chrome yoki boshqa zamonaviy brauzerda oching.
4. Audio sahifaning o‘zida player orqali eshitiladi. Brauzer ijroni bloklasa, player tagidagi “Audio faylni ochish” havolasini bosing.
"""
        else -> """Memora multimedia export

Open the HTML file in a modern browser. Images and audio are included in the export.
"""
    }

    private fun imageExtension(mimeType: String, file: File): String = when (mimeType.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic", "image/heif" -> "heic"
        else -> file.extension.lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif", "heic") } ?: "jpg"
    }

    private fun imageMimeForExtension(extension: String): String = when (extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic" -> "image/heic"
        else -> "image/jpeg"
    }

    private fun audioExtension(mimeType: String, file: File): String = when (mimeType.lowercase()) {
        "audio/mpeg" -> "mp3"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/ogg" -> "ogg"
        "audio/webm" -> "webm"
        else -> file.extension.lowercase().takeIf { it in setOf("m4a", "mp4", "mp3", "wav", "ogg", "webm", "aac") } ?: "m4a"
    }

    private fun formatMultimediaDuration(durationMs: Long): String {
        val total = durationMs.coerceAtLeast(0L) / 1000L
        return "%02d:%02d".format(total / 60, total % 60)
    }

    private fun wordPackageReadme(language: AppLanguage, docxName: String, hasAudio: Boolean): String = when (language) {
        AppLanguage.EN -> buildString {
            appendLine("Memora Word export")
            appendLine()
            appendLine("1. Extract this ZIP file first.")
            appendLine("2. Open $docxName from the extracted folder.")
            if (hasAudio) {
                appendLine("3. Keep the Audio folder next to the Word file.")
                appendLine("4. Tap an audio link in Word to open the matching .m4a file. If your Word app blocks local links, open the same file directly from the Audio folder.")
            }
        }
        AppLanguage.RU -> buildString {
            appendLine("Экспорт Memora в Word")
            appendLine()
            appendLine("1. Сначала распакуйте ZIP-файл.")
            appendLine("2. Откройте $docxName из распакованной папки.")
            if (hasAudio) {
                appendLine("3. Папка Audio должна оставаться рядом с Word-файлом.")
                appendLine("4. Нажмите ссылку аудио в Word, чтобы открыть соответствующий .m4a. Если Word блокирует локальные ссылки, откройте этот файл напрямую из папки Audio.")
            }
        }
        AppLanguage.UZ -> buildString {
            appendLine("Memora Word eksporti")
            appendLine()
            appendLine("1. Word faylini oching.")
            if (hasAudio) appendLine("2. Audio fayllar Audio papkasida; Worddagi A001/A002 belgisi bilan mosini toping.")
        }
        else -> buildString {
            appendLine("Memora Word export")
            appendLine()
            appendLine("1. Open $docxName.")
            if (hasAudio) appendLine("2. Audio files are in the Audio folder; match them using the A001/A002 marker shown in Word.")
        }
    }

    private suspend fun writeBackupZip(
        output: java.io.OutputStream,
        request: ExportRequest,
        notes: List<NoteEntity>
    ) {
        val topilmalarBackup = if (request.backupScope == BackupScope.BOTH) {
            topilmalarRepository.backupSnapshot()
        } else null
        val root = JSONObject().apply {
            put("format", "MEMORA_BACKUP")
            put("schemaVersion", 7)
            put("createdAt", System.currentTimeMillis())
            put("scope", request.backupScope.name)
            put("notes", JSONArray().apply {
                notes.forEach { n ->
                    put(JSONObject().apply {
                        put("id", n.id)
                        put("type", n.type)
                        put("title", n.title)
                        put("contentHtml", n.contentHtml)
                        put("plainText", n.plainText)
                        put("createdAt", n.createdAt)
                        put("updatedAt", n.updatedAt)
                        put("wordCount", n.wordCount)
                        put("blocksJson", n.blocksJson)
                        put("hasImages", n.hasImages)
                        put("hasAudio", n.hasAudio)
                        n.mood?.let { put("mood", it) }
                        put("hashtagsJson", n.hashtagsJson)
                    })
                }
            })
            if (request.backupScope == BackupScope.BOTH) {
                put("savedTitles", JSONArray().apply {
                    repository.allTitles().forEach { t ->
                        put(JSONObject().apply {
                            put("value", t.value)
                            put("createdAt", t.createdAt)
                        })
                    }
                })
                put("reminders", JSONArray().apply {
                    repository.allReminders().forEach { r ->
                        put(JSONObject().apply {
                            put("hour", r.hour)
                            put("minute", r.minute)
                            put("enabled", r.enabled)
                            put("createdAt", r.createdAt)
                        })
                    }
                })
                val settings = preferences.settings.first()
                put("settings", JSONObject().apply {
                    put("language", settings.language.name)
                    put("themeMode", settings.themeMode.name)
                    put("palette", settings.palette.name)
                    put("uiFont", settings.uiFont.name)
                    put("writingFont", settings.writingFont.name)
                    put("uiScale", settings.uiScale)
                    put("lockEnabled", settings.lockEnabled)
                    put("lockInterval", settings.lockInterval.name)
                    put("monthlySummaryEnabled", settings.monthlySummaryEnabled)
                    put("yearlySummaryEnabled", settings.yearlySummaryEnabled)
                    put("timeTraceReminderEnabled", settings.timeTraceReminderEnabled)
                    put("timeTraceReminderHour", settings.timeTraceReminderHour)
                    put("timeTraceReminderMinute", settings.timeTraceReminderMinute)
                    put("timeTraceMilestonesEnabled", settings.timeTraceMilestonesEnabled)
                    put("timeTraceSpeed", settings.timeTraceSpeed.name)
                    put("timeTraceDateOverlay", settings.timeTraceDateOverlay)
                    put("timeTraceIncludeInBackup", settings.timeTraceIncludeInBackup)
                    put("topilmalarDailyCount", settings.topilmalarDailyCount)
                    put("topilmalarReminderEnabled", settings.topilmalarReminderEnabled)
                    put("topilmalarReminderHour", settings.topilmalarReminderHour)
                    put("topilmalarReminderMinute", settings.topilmalarReminderMinute)
                    put("memoryEnabled", settings.memoryEnabled)
                    put("memoryDailyCount", settings.memoryDailyCount)
                    put("albumReminderEnabled", settings.albumReminderEnabled)
                    put("albumReminderDailyCount", settings.albumReminderDailyCount)
                    put("albumReminderRandom", settings.albumReminderRandom)
                    put("albumReminderHour", settings.albumReminderHour)
                    put("albumReminderMinute", settings.albumReminderMinute)
                })
                if (settings.timeTraceIncludeInBackup) {
                    put("timeTracePhotos", JSONArray().apply {
                        repository.allTimeTracePhotos().forEach { photo ->
                            put(JSONObject().apply {
                                put("dateKey", photo.dateKey)
                                put("relativePath", photo.relativePath)
                                put("capturedAt", photo.capturedAt)
                                put("imported", photo.imported)
                                put("createdAt", photo.createdAt)
                            })
                        }
                    })
                }
                if (request.backupScope == BackupScope.BOTH) {
                    put("memoryAlbumPhotos", JSONArray().apply {
                        repository.allMemoryAlbum().forEach { photo ->
                            put(JSONObject().apply {
                                put("relativePath", photo.relativePath)
                                put("capturedAt", photo.capturedAt)
                                put("caption", photo.caption)
                                put("effect", photo.effect)
                            })
                        }
                    })
                }
                topilmalarBackup?.let { put("topilmalar", TopilmalarBackupCodec.toJson(it)) }
            }
        }

        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(root.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val paths = notes.flatMap { note ->
                NoteBlocksCodec.decode(note.blocksJson, note.plainText).mapNotNull { block ->
                    when (block) {
                        is NoteBlock.Image -> block.relativePath
                        is NoteBlock.Audio -> block.relativePath
                        is NoteBlock.Text -> null
                    }
                }
            }.distinct()

            paths.forEach { relative ->
                val file = storage.resolve(relative)
                if (file.exists() && file.isFile) {
                    zip.putNextEntry(ZipEntry(relative.replace('\\', '/')))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            if (request.backupScope == BackupScope.BOTH && preferences.settings.first().timeTraceIncludeInBackup) {
                repository.allTimeTracePhotos().forEach { photo ->
                    val file = timeTraceStorage.resolve(photo.relativePath)
                    if (file.exists() && file.isFile) {
                        zip.putNextEntry(ZipEntry(photo.relativePath.replace('\\', '/')))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            if (request.backupScope == BackupScope.BOTH) {
                repository.allMemoryAlbum().forEach { photo ->
                    val file = memoryAlbumStorage.resolve(photo.relativePath)
                    if (file.isFile) {
                        zip.putNextEntry(ZipEntry(photo.relativePath))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            topilmalarBackup?.let { backup ->
                (backup.images.map { it.relativePath } + backup.sources.mapNotNull { it.coverPath } + backup.sources.mapNotNull { it.originAppIconPath })
                    .distinct()
            }?.forEach { relative ->
                val file = runCatching { topilmalarStorage.resolve(relative) }.getOrNull()
                if (file?.isFile == true) {
                    zip.putNextEntry(ZipEntry(relative.replace('\\', '/')))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    suspend fun previewBackup(uri: Uri): ImportPreview {
        val header = resolver.openInputStream(uri)?.use { input ->
            ByteArray(4).also { input.read(it) }
        } ?: error("Unable to open backup")
        val root = if (header.size >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()) {
            readManifest(uri)
        } else {
            val text = resolver.openInputStream(uri)?.use { input ->
                readLimitedBytes(input, MAX_LEGACY_BACKUP_BYTES).toString(Charsets.UTF_8)
            } ?: error("Unable to open backup")
            JSONObject(text)
        }
        require(root.optString("format") == "MEMORA_BACKUP") { "Not a Memora backup" }
        require(root.optInt("schemaVersion", 0) in 1..6) { "Unsupported Memora backup version" }
        val notes = root.optJSONArray("notes") ?: JSONArray()
        require(notes.length() <= MAX_BACKUP_NOTES) { "Backup contains too many notes" }
        var duplicates = 0
        var images = 0
        var audio = 0
        for (i in 0 until notes.length()) {
            val obj = notes.getJSONObject(i)
            val title = obj.optString("title", "")
            val plain = obj.optString("plainText", "")
            val createdAt = obj.getLong("createdAt")
            if (repository.isDuplicate(title, plain, createdAt)) duplicates++
            NoteBlocksCodec.decode(obj.optString("blocksJson", ""), plain).forEach { block ->
                when (block) {
                    is NoteBlock.Image -> images++
                    is NoteBlock.Audio -> audio++
                    is NoteBlock.Text -> Unit
                }
            }
        }
        return ImportPreview(
            notes = notes.length(),
            duplicateNotes = duplicates,
            images = images,
            audio = audio,
            savedTitles = root.optJSONArray("savedTitles")?.length() ?: 0,
            reminders = root.optJSONArray("reminders")?.length() ?: 0,
            timeTracePhotos = (root.optJSONArray("timeTracePhotos")?.length() ?: 0) +
                (root.optJSONArray("memoryAlbumPhotos")?.length() ?: 0),
            includesSettings = root.has("settings"),
            topilmaCards = root.optJSONObject("topilmalar")?.optJSONArray("cards")?.length() ?: 0,
            topilmaSources = root.optJSONObject("topilmalar")?.optJSONArray("sources")?.length() ?: 0
        )
    }

    suspend fun importBackup(uri: Uri): ImportResult {
        repository.requireWritable()
        val header = resolver.openInputStream(uri)?.use { input ->
            ByteArray(4).also { input.read(it) }
        } ?: error("Unable to open backup")
        return if (header.size >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()) {
            importZipBackup(uri)
        } else {
            val text = resolver.openInputStream(uri)?.use { input ->
                readLimitedBytes(input, MAX_LEGACY_BACKUP_BYTES).toString(Charsets.UTF_8)
            }
                ?: error("Unable to open backup")
            importLegacyJson(JSONObject(text))
        }
    }

    private suspend fun importZipBackup(uri: Uri): ImportResult {
        val manifest = readManifest(uri)
        require(manifest.optString("format") == "MEMORA_BACKUP") { "Not a Memora backup" }
        require(manifest.optInt("schemaVersion", 0) in 1..7) { "Unsupported Memora backup version" }

        data class Pending(val obj: JSONObject, val blocks: List<NoteBlock>)
        val pending = mutableListOf<Pending>()
        var duplicates = 0
        val notesArray = manifest.optJSONArray("notes") ?: JSONArray()
        require(notesArray.length() <= MAX_BACKUP_NOTES) { "Backup contains too many notes" }
        for (i in 0 until notesArray.length()) {
            val obj = notesArray.getJSONObject(i)
            val title = obj.optString("title", "")
            val importedTitle = backupNoteTitle(obj)
            val plain = obj.optString("plainText", "")
            val createdAt = obj.getLong("createdAt")
            if (repository.isDuplicate(importedTitle, plain, createdAt) ||
                (title.isBlank() && repository.isDuplicate(title, plain, createdAt))) {
                duplicates++
                continue
            }
            pending += Pending(
                obj,
                NoteBlocksCodec.decode(obj.optString("blocksJson", ""), plain)
            )
        }

        val neededPaths = pending.flatMap { p ->
            p.blocks.mapNotNull { block ->
                when (block) {
                    is NoteBlock.Image -> storage.normalizedMediaPath(block.relativePath)
                    is NoteBlock.Audio -> storage.normalizedMediaPath(block.relativePath)
                    is NoteBlock.Text -> null
                }
            }
        }.toSet()
        // Preflight the manifest before writing files or records. Everything below is
        // tracked for cleanup if an exception aborts the database transaction.
        val topilmalarBackup = manifest.optJSONObject("topilmalar")
            ?.let(TopilmalarBackupCodec::fromJson)
        val noteMediaCreated = mutableListOf<String>()
        val topilmalarMediaCreated = mutableListOf<String>()
        val timeTraceCreated = mutableListOf<String>()
        val albumCreated = mutableListOf<String>()
        val result = try {
            val pathMap = importMediaEntries(uri, neededPaths, noteMediaCreated)
            require(pathMap.keys.containsAll(neededPaths)) { "Backup is missing note media files" }
            if (topilmalarBackup != null) {
                val topilmaPaths = (
                    topilmalarBackup.images.map { it.relativePath } +
                    topilmalarBackup.sources.mapNotNull { it.coverPath } +
                    topilmalarBackup.sources.mapNotNull { it.originAppIconPath }
                ).toSet()
                importTopilmalarMediaEntries(uri, topilmaPaths, topilmalarMediaCreated)
            }
            var imported = 0
            repository.withImportTransaction {
                importTimeTraceEntries(uri, manifest, timeTraceCreated)
                importMemoryAlbumEntries(uri, manifest, albumCreated)
                topilmalarBackup?.let { topilmalarRepository.restoreBackup(it) }
                pending.forEach { item ->
                    val obj = item.obj
                    val remapped = item.blocks.mapNotNull { block ->
                        when (block) {
                            is NoteBlock.Image -> storage.normalizedMediaPath(block.relativePath)
                                ?.let(pathMap::get)
                                ?.let { block.copy(relativePath = it) }
                            is NoteBlock.Audio -> storage.normalizedMediaPath(block.relativePath)
                                ?.let(pathMap::get)
                                ?.let { block.copy(relativePath = it) }
                            is NoteBlock.Text -> block
                        }
                    }
                    val type = runCatching { NoteType.valueOf(obj.getString("type")) }.getOrDefault(NoteType.LONG)
                    val plain = obj.optString("plainText", NoteBlocksCodec.plainText(remapped))
                    val createdAt = obj.getLong("createdAt")
                    repository.saveNote(
                        id = UUID.randomUUID().toString(),
                        type = type,
                        title = backupNoteTitle(obj),
                        html = obj.optString("contentHtml", TextUtils.plainTextToHtml(plain)),
                        plainText = plain,
                        createdAt = createdAt,
                        updatedAt = obj.optLong("updatedAt", createdAt),
                        blocksJson = NoteBlocksCodec.encode(remapped),
                        hasImages = NoteBlocksCodec.hasImages(remapped),
                        hasAudio = NoteBlocksCodec.hasAudio(remapped),
                        mood = obj.optString("mood").takeIf { it.isNotBlank() },
                        hashtagsJson = obj.optString("hashtagsJson").takeIf { it.isNotBlank() },
                        notifyObsidian = false
                    ).requireImportSave()
                    imported++
                }
                restoreDatabaseExtras(manifest)
            }
            ImportResult(imported, duplicates)
        } catch (error: Throwable) {
            // The database transaction is rolled back by Room; only files *created by
            // this import* may be removed. Existing user files are never touched.
            noteMediaCreated.forEach(storage::delete)
            topilmalarMediaCreated.forEach(topilmalarStorage::delete)
            timeTraceCreated.forEach(timeTraceStorage::delete)
            albumCreated.forEach(memoryAlbumStorage::delete)
            throw error
        }
        // Preferences are a separate DataStore: apply them only after the DB commit.
        restoreSettings(manifest)
        return result
    }

    private fun readManifest(uri: Uri): JSONObject {
        var manifest: JSONObject? = null
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (manifest == null) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name == "manifest.json") {
                        manifest = JSONObject(
                            readLimitedBytes(zip, MAX_MANIFEST_BYTES).toString(Charsets.UTF_8)
                        )
                    }
                    zip.closeEntry()
                }
            }
        }
        return manifest ?: error("Backup manifest is missing")
    }

    private fun importMediaEntries(uri: Uri, neededPaths: Set<String>, created: MutableList<String>): Map<String, String> {
        if (neededPaths.isEmpty()) return emptyMap()
        val mapped = mutableMapOf<String, String>()
        var totalImportedBytes = 0L
        var importedEntries = 0
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val safe = storage.normalizedMediaPath(entry.name)
                    if (!entry.isDirectory && safe != null && safe in neededPaths && safe !in mapped) {
                        require(++importedEntries <= MAX_MEDIA_ENTRIES) { "Backup contains too many media files" }
                        val extension = safe.substringAfterLast('.', "bin")
                        val isImage = safe.startsWith("media/images/")
                        val folder = if (isImage) "images" else "audio"
                        val entryLimit = if (isImage) MAX_IMAGE_ENTRY_BYTES else MAX_AUDIO_ENTRY_BYTES
                        val remaining = MAX_TOTAL_MEDIA_BYTES - totalImportedBytes
                        require(remaining > 0) { "Backup media is too large" }
                        val stored = storage.storeImported(folder, extension, zip, minOf(entryLimit, remaining))
                        totalImportedBytes += stored.byteCount
                        mapped[safe] = stored.relativePath
                        created += stored.relativePath
                    }
                    zip.closeEntry()
                }
            }
        }
        return mapped
    }

    private suspend fun importTimeTraceEntries(uri: Uri, manifest: JSONObject, created: MutableList<String>) {
        val array = manifest.optJSONArray("timeTracePhotos") ?: return
        val metadata = mutableMapOf<String, JSONObject>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val dateKey = obj.optString("dateKey").takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) } ?: continue
            metadata["time_trace/photos/$dateKey.jpg"] = obj
        }
        if (metadata.isEmpty()) return
        val found = mutableSetOf<String>()
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val obj = metadata[entry.name]
                    if (!entry.isDirectory && obj != null) {
                        found += entry.name
                        val bytes = readLimitedBytes(zip, MAX_IMAGE_ENTRY_BYTES)
                        val dateKey = obj.getString("dateKey")
                        // Import must never clobber a photo already stored for this day.
                        if (repository.timeTracePhoto(dateKey) != null) {
                            zip.closeEntry()
                            continue
                        }
                        val alreadyOnDisk = timeTraceStorage.fileFor(dateKey).exists()
                        if (!alreadyOnDisk) created += dateKey
                        val stored = if (alreadyOnDisk) {
                            com.memora.app.timetrace.StoredTimeTraceImage(
                                timeTraceStorage.relativePath(dateKey), 1080, 1350
                            )
                        } else timeTraceStorage.importAndNormalize(bytes, dateKey)
                        repository.saveTimeTracePhoto(
                            TimeTracePhotoEntity(
                                dateKey = dateKey,
                                relativePath = stored.relativePath,
                                capturedAt = obj.optLong("capturedAt", System.currentTimeMillis()),
                                imported = obj.optBoolean("imported", true),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                    zip.closeEntry()
                }
            }
        }
        require(metadata.keys.all { it in found || timeTraceStorage.fileFor(it.substringAfterLast('/').removeSuffix(".jpg")).isFile }) {
            "Backup is missing Time Trace photos"
        }
    }

    /** Restores only album files explicitly listed in the manifest, with size and path checks. */
    private suspend fun importMemoryAlbumEntries(uri: Uri, manifest: JSONObject, created: MutableList<String>) {
        val array = manifest.optJSONArray("memoryAlbumPhotos") ?: return
        require(array.length() <= 5000) { "Too many album photos" }
        val metadata = mutableMapOf<String, JSONObject>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val path = obj.optString("relativePath")
            require(path.matches(Regex("memory_album/[a-zA-Z0-9-]{10,60}\\.jpg"))) { "Invalid album path" }
            require(path !in metadata) { "Duplicate album path" }
            metadata[path] = obj
        }
        if (metadata.isEmpty()) return
        val found = mutableSetOf<String>()
        var total = 0L
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val obj = metadata[entry.name]
                    if (!entry.isDirectory && obj != null) {
                        found += entry.name
                        val bytes = readLimitedBytes(zip, MAX_IMAGE_ENTRY_BYTES)
                        total += bytes.size
                        require(total <= MAX_TOTAL_MEDIA_BYTES) { "Album images exceed backup limit" }
                        val capturedAt = obj.optLong("capturedAt")
                        val caption = obj.optString("caption").take(500)
                        val effect = obj.optString("effect", "ORIGINAL").takeIf {
                            it in setOf("ORIGINAL", "WARM", "VINTAGE", "INSTANT", "MONO")
                        } ?: "ORIGINAL"
                        if (!repository.hasSameAlbumPhoto(capturedAt, caption, effect)) {
                            val tmp = File.createTempFile("album-backup-", ".jpg", context.cacheDir)
                            try {
                                tmp.writeBytes(bytes)
                                val path = memoryAlbumStorage.importPhoto(tmp)
                                created += path
                                repository.addMemoryAlbum(MemoryAlbumPhotoEntity(
                                    relativePath = path, capturedAt = capturedAt,
                                    caption = caption, effect = effect
                                ))
                            } finally { tmp.delete() }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        require(found.containsAll(metadata.keys)) { "Backup is missing album photos" }
    }

    private fun importTopilmalarMediaEntries(uri: Uri, neededPaths: Set<String>, created: MutableList<String>) {
        if (neededPaths.isEmpty()) return
        var imported = 0
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name in neededPaths) {
                        require(++imported <= MAX_MEDIA_ENTRIES) { "Backup contains too many Topilmalar images" }
                        if (topilmalarStorage.restoreBackupEntry(entry.name, zip)) created += entry.name
                    }
                    zip.closeEntry()
                }
            }
        }
        require(neededPaths.all { runCatching { topilmalarStorage.resolve(it).isFile }.getOrDefault(false) }) {
            "Backup is missing Topilmalar media files"
        }
    }

    private suspend fun importLegacyJson(root: JSONObject): ImportResult {
        require(root.optString("format") == "MEMORA_BACKUP") { "Not a Memora backup" }
        require(root.optInt("schemaVersion", 0) in 1..6) { "Unsupported Memora backup version" }
        val notes = root.optJSONArray("notes") ?: JSONArray()
        require(notes.length() <= MAX_BACKUP_NOTES) { "Backup contains too many notes" }
        val result = repository.withImportTransaction {
            var imported = 0
            var duplicates = 0
            for (i in 0 until notes.length()) {
                val obj = notes.getJSONObject(i)
                val originalTitle = obj.optString("title", "")
                val title = backupNoteTitle(obj)
                val plain = obj.optString("plainText", "")
                val createdAt = obj.getLong("createdAt")
                if (repository.isDuplicate(title, plain, createdAt) ||
                    (originalTitle.isBlank() && repository.isDuplicate(originalTitle, plain, createdAt))) {
                    duplicates++
                    continue
                }
                val type = runCatching { NoteType.valueOf(obj.getString("type")) }.getOrDefault(NoteType.LONG)
                // Legacy JSON contains no media bytes. Keep only media already present locally.
                val blocks = NoteBlocksCodec.decode(obj.optString("blocksJson", ""), plain).filter { block ->
                    when (block) {
                        is NoteBlock.Text -> true
                        is NoteBlock.Image -> storage.normalizedMediaPath(block.relativePath)?.let(storage::resolve)?.isFile == true
                        is NoteBlock.Audio -> storage.normalizedMediaPath(block.relativePath)?.let(storage::resolve)?.isFile == true
                    }
                }
                repository.saveNote(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    title = title,
                    html = obj.optString("contentHtml", TextUtils.plainTextToHtml(plain)),
                    plainText = plain,
                    createdAt = createdAt,
                    updatedAt = obj.optLong("updatedAt", createdAt),
                    blocksJson = NoteBlocksCodec.encode(blocks),
                    hasImages = NoteBlocksCodec.hasImages(blocks),
                    hasAudio = NoteBlocksCodec.hasAudio(blocks),
                    mood = obj.optString("mood").takeIf { it.isNotBlank() },
                    hashtagsJson = obj.optString("hashtagsJson").takeIf { it.isNotBlank() },
                    notifyObsidian = false
                ).requireImportSave()
                imported++
            }
            restoreDatabaseExtras(root)
            root.optJSONObject("topilmalar")?.let {
                topilmalarRepository.restoreBackup(TopilmalarBackupCodec.fromJson(it))
            }
            ImportResult(imported, duplicates)
        }
        restoreSettings(root)
        return result
    }

    /** Older backups may contain autosaved untitled notes; keep their content. */
    private fun backupNoteTitle(obj: JSONObject): String =
        obj.optString("title", "").trim().ifBlank { "Recovered note (${obj.optLong("createdAt", 0L)})" }

    private suspend fun restoreDatabaseExtras(root: JSONObject) {

        root.optJSONArray("savedTitles")?.let { titles ->
            for (i in 0 until titles.length()) {
                val obj = titles.getJSONObject(i)
                repository.addTitle(
                    value = obj.optString("value", ""),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            }
        }
        root.optJSONArray("reminders")?.let { reminders ->
            val existing = repository.allReminders().map { it.hour to it.minute }.toMutableSet()
            for (i in 0 until reminders.length()) {
                val obj = reminders.getJSONObject(i)
                val pair = obj.optInt("hour", -1) to obj.optInt("minute", -1)
                if (pair.first in 0..23 && pair.second in 0..59 && pair !in existing) {
                    repository.addReminder(
                        hour = pair.first,
                        minute = pair.second,
                        enabled = obj.optBoolean("enabled", true),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                    existing += pair
                }
            }
        }
    }

    private suspend fun restoreSettings(root: JSONObject) {
        root.optJSONObject("settings")?.let { settings ->
            runCatching { AppLanguage.valueOf(settings.optString("language")) }
                .getOrNull()?.let { preferences.setLanguage(it) }
            runCatching { ThemeMode.valueOf(settings.optString("themeMode")) }
                .getOrNull()?.let { preferences.setThemeMode(it) }
            runCatching { PaletteChoice.valueOf(settings.optString("palette")) }
                .getOrNull()?.let { preferences.setPalette(it) }
            runCatching { FontChoice.valueOf(settings.optString("uiFont")) }
                .getOrNull()?.let { preferences.setUiFont(it) }
            runCatching { FontChoice.valueOf(settings.optString("writingFont")) }
                .getOrNull()?.let { preferences.setWritingFont(it) }
            if (settings.has("uiScale")) preferences.setUiScale(settings.optDouble("uiScale", 1.0).toFloat())
            if (settings.has("lockEnabled")) preferences.setLockEnabled(settings.optBoolean("lockEnabled", true))
            runCatching { LockInterval.valueOf(settings.optString("lockInterval")) }
                .getOrNull()?.let { preferences.setLockInterval(it) }
            if (settings.has("monthlySummaryEnabled")) preferences.setMonthlySummaryEnabled(settings.optBoolean("monthlySummaryEnabled", true))
            if (settings.has("yearlySummaryEnabled")) preferences.setYearlySummaryEnabled(settings.optBoolean("yearlySummaryEnabled", true))
            if (settings.has("timeTraceReminderEnabled")) preferences.setTimeTraceReminderEnabled(settings.optBoolean("timeTraceReminderEnabled", false))
            if (settings.has("timeTraceReminderHour") || settings.has("timeTraceReminderMinute")) {
                preferences.setTimeTraceReminderTime(settings.optInt("timeTraceReminderHour", 20), settings.optInt("timeTraceReminderMinute", 0))
            }
            if (settings.has("timeTraceMilestonesEnabled")) preferences.setTimeTraceMilestonesEnabled(settings.optBoolean("timeTraceMilestonesEnabled", true))
            runCatching { TimeTraceSpeed.valueOf(settings.optString("timeTraceSpeed")) }.getOrNull()?.let { preferences.setTimeTraceSpeed(it) }
            if (settings.has("timeTraceDateOverlay")) preferences.setTimeTraceDateOverlay(settings.optBoolean("timeTraceDateOverlay", true))
            if (settings.has("timeTraceIncludeInBackup")) preferences.setTimeTraceIncludeInBackup(settings.optBoolean("timeTraceIncludeInBackup", true))
            if (settings.has("topilmalarDailyCount")) {
                val count = settings.optInt("topilmalarDailyCount", 5)
                if (count in setOf(3, 5, 10, 15, 20)) preferences.setTopilmalarDailyCount(count)
            }
            if (settings.has("memoryEnabled")) preferences.setMemoryEnabled(settings.optBoolean("memoryEnabled"))
            if (settings.has("memoryDailyCount")) preferences.setMemoryDailyCount(settings.optInt("memoryDailyCount", 3).takeIf { it in setOf(1,3,5) } ?: 3)
            if (settings.has("albumReminderEnabled")) preferences.setAlbumReminderEnabled(settings.optBoolean("albumReminderEnabled"))
            if (settings.has("albumReminderDailyCount")) preferences.setAlbumReminderDailyCount(settings.optInt("albumReminderDailyCount", 3).takeIf { it in setOf(1,3,5) } ?: 3)
            if (settings.has("albumReminderRandom")) preferences.setAlbumReminderRandom(settings.optBoolean("albumReminderRandom", true))
            if (settings.has("albumReminderHour") || settings.has("albumReminderMinute")) preferences.setAlbumReminderFirstTime(settings.optInt("albumReminderHour", 9), settings.optInt("albumReminderMinute", 0))
            if (settings.has("topilmalarReminderEnabled")) preferences.setTopilmalarReminderEnabled(settings.optBoolean("topilmalarReminderEnabled", false))
            if (settings.has("topilmalarReminderHour") || settings.has("topilmalarReminderMinute")) {
                preferences.setTopilmalarReminderTime(settings.optInt("topilmalarReminderHour", 20), settings.optInt("topilmalarReminderMinute", 0))
            }
        }
    }

    private fun readLimitedBytes(input: InputStream, maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024L).toInt())
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "Backup entry is too large" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun NoteSaveResult.requireImportSave() {
        when (this) {
            is NoteSaveResult.Success -> Unit
            NoteSaveResult.ReadOnly -> error("Memora is in read-only mode")
            NoteSaveResult.TitleRequired -> error("Backup note has no title")
            is NoteSaveResult.Failed -> throw cause
        }
    }

    private fun ExportRequest.scopeNoteType(): NoteType? = when (backupScope) {
        BackupScope.SHORT -> NoteType.SHORT
        BackupScope.LONG -> NoteType.LONG
        BackupScope.BOTH -> null
    }

    private fun ExportRequest.range(): TimeRange = when (period) {
        ExportPeriod.ALL -> TimeRange(null, null)
        ExportPeriod.YEAR -> TimeUtils.yearRange(requireNotNull(year))
        ExportPeriod.MONTH -> TimeUtils.monthRange(requireNotNull(year), requireNotNull(month))
    }

    companion object {
        private const val HTML_RAW_MEDIA_TARGET_BYTES = 75L * 1024L * 1024L
        private const val HTML_IMAGE_MAX_DIMENSION = 1600
        private const val HTML_IMAGE_DECODE_MAX_DIMENSION = 3200
        private const val MAX_MANIFEST_BYTES = 16L * 1024L * 1024L
        private const val MAX_LEGACY_BACKUP_BYTES = 32L * 1024L * 1024L
        private const val MAX_IMAGE_ENTRY_BYTES = 25L * 1024L * 1024L
        private const val MAX_AUDIO_ENTRY_BYTES = 250L * 1024L * 1024L
        private const val MAX_TOTAL_MEDIA_BYTES = 2L * 1024L * 1024L * 1024L
        private const val MAX_MEDIA_ENTRIES = 10_000
        private const val MAX_BACKUP_NOTES = 50_000
        private const val HTML_JPEG_QUALITY = 82

        fun suggestedFileName(request: ExportRequest): String {
            val stamp = java.time.LocalDate.now().toString()
            return when (request.format) {
                ExportFormat.WORD -> "Memora-${request.backupScope.name.lowercase()}-$stamp.docx"
                ExportFormat.MULTIMEDIA -> "Memora-${request.backupScope.name.lowercase()}-$stamp.html"
                ExportFormat.BACKUP -> "Memora-${request.backupScope.name.lowercase()}-$stamp.memora"
            }
        }

        fun wordDocumentFileName(request: ExportRequest): String {
            val stamp = java.time.LocalDate.now().toString()
            return "Memora-${request.backupScope.name.lowercase()}-$stamp.docx"
        }
    }
}

private data class HtmlPageInfo(
    val fileName: String,
    val monthKey: String,
    val label: String,
    val noteType: String,
    val noteCount: Int,
    val part: Int,
    val partCount: Int
)

private data class HtmlIndexLabels(
    val notes: String,
    val open: String,
    val chooseType: String,
    val short: String,
    val long: String,
    val folder: String,
    val folderReady: String,
    val folderHint: String,
    val part: String,
    val close: String,
    val back: String,
    val missing: String,
    val empty: String
)

private data class OptimizedImage(
    val bytes: ByteArray,
    val mimeType: String
)

private data class MultimediaAsset(
    val blockId: String,
    val file: File,
    val targetPath: String,
    val mimeType: String
)

private data class MultimediaLabels(
    val audio: String,
    val image: String,
    val openAudio: String,
    val audioFallback: String,
    val missingImage: String,
    val missingAudio: String,
    val empty: String
)

private data class DocxImage(
    val blockId: String,
    val file: File,
    val relationshipId: String,
    val targetName: String,
    val cx: Long,
    val cy: Long,
    val docPrId: Int
)

private data class DocxAudio(
    val blockId: String,
    val file: File,
    val relationshipId: String,
    val targetName: String,
    val marker: String,
    val createdAt: Long,
    val mimeType: String
)

private data class BuiltDocx(
    val docxBytes: ByteArray,
    val audios: List<DocxAudio>
)

private object DocxWriter {
    fun build(
        notes: List<NoteEntity>,
        language: AppLanguage,
        layout: WordLayout,
        storage: MediaStorage
    ): BuiltDocx {
        val images = mutableListOf<DocxImage>()
        val audios = mutableListOf<DocxAudio>()
        var relNumber = 1
        var imageNumber = 1
        var audioNumber = 1
        var docPr = 1

        notes.forEach { note ->
            NoteBlocksCodec.decode(note.blocksJson, note.plainText).forEach { block ->
                when (block) {
                    is NoteBlock.Image -> {
                        val file = storage.resolve(block.relativePath)
                        if (file.exists()) {
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(file.absolutePath, options)
                            val width = options.outWidth.coerceAtLeast(1)
                            val height = options.outHeight.coerceAtLeast(1)
                            val maxCx = 5_200_000L
                            val cy = (maxCx * height.toDouble() / width.toDouble()).toLong().coerceAtMost(7_200_000L)
                            val cx = (cy * width.toDouble() / height.toDouble()).toLong().coerceAtMost(maxCx)
                            images += DocxImage(
                                blockId = block.id,
                                file = file,
                                relationshipId = "rId${relNumber++}",
                                targetName = "image${imageNumber++}.jpeg",
                                cx = cx,
                                cy = cy,
                                docPrId = docPr++
                            )
                        }
                    }
                    is NoteBlock.Audio -> {
                        val file = storage.resolve(block.relativePath)
                        if (file.exists()) {
                            val marker = "A${audioNumber.toString().padStart(3, '0')}"
                            val dateStamp = Instant.ofEpochMilli(note.createdAt)
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                            val extension = file.extension.lowercase().takeIf { it.isNotBlank() } ?: "m4a"
                            audios += DocxAudio(
                                blockId = block.id,
                                file = file,
                                relationshipId = "rId${relNumber++}",
                                targetName = "${marker}_${dateStamp}.$extension",
                                marker = marker,
                                createdAt = note.createdAt,
                                mimeType = when (extension) {
                                    "mp3" -> "audio/mpeg"
                                    "wav" -> "audio/wav"
                                    "ogg" -> "audio/ogg"
                                    "webm" -> "audio/webm"
                                    "aac" -> "audio/aac"
                                    else -> "audio/mp4"
                                }
                            )
                            audioNumber++
                        }
                    }
                    is NoteBlock.Text -> Unit
                }
            }
        }

        val docxOutput = ByteArrayOutputStream()
        ZipOutputStream(BufferedOutputStream(docxOutput)).use { zip ->
            add(zip, "[Content_Types].xml", contentTypes())
            add(zip, "_rels/.rels", rootRels())
            add(zip, "docProps/core.xml", coreProps())
            add(zip, "docProps/app.xml", appProps())
            add(zip, "word/styles.xml", styles())
            add(zip, "word/_rels/document.xml.rels", documentRels(images, audios))
            add(zip, "word/document.xml", document(notes, language, layout, images, audios))
            images.forEach { image -> addFile(zip, "word/media/${image.targetName}", image.file) }
        }
        return BuiltDocx(docxBytes = docxOutput.toByteArray(), audios = audios)
    }

    private fun add(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun addFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="jpeg" ContentType="image/jpeg"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>"""

    private fun documentRels(images: List<DocxImage>, audios: List<DocxAudio>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        images.forEach { image ->
            append("<Relationship Id=\"").append(image.relationshipId)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/")
                .append(image.targetName).append("\"/>")
        }
        audios.forEach { audio ->
            append("<Relationship Id=\"").append(audio.relationshipId)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink\" Target=\"Audio/")
                .append(audio.targetName).append("\" TargetMode=\"External\"/>")
        }
        append("</Relationships>")
    }

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:rPr><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr>
    <w:pPr><w:jc w:val="both"/><w:spacing w:after="90" w:line="300" w:lineRule="auto"/></w:pPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="MemoraTitle">
    <w:name w:val="Memora Title"/><w:basedOn w:val="Normal"/>
    <w:rPr><w:b/><w:sz w:val="44"/><w:szCs w:val="44"/></w:rPr>
    <w:pPr><w:spacing w:before="260" w:after="70"/></w:pPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="MemoraDate">
    <w:name w:val="Memora Date"/><w:basedOn w:val="Normal"/>
    <w:rPr><w:b/><w:color w:val="777777"/><w:sz w:val="16"/><w:szCs w:val="16"/></w:rPr>
    <w:pPr><w:spacing w:after="120"/></w:pPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="MemoraDateHeading">
    <w:name w:val="Memora Date Heading"/><w:basedOn w:val="Normal"/>
    <w:rPr><w:b/><w:sz w:val="16"/><w:szCs w:val="16"/></w:rPr>
    <w:pPr><w:spacing w:before="180" w:after="90"/></w:pPr>
  </w:style>
</w:styles>"""

    private fun coreProps(): String {
        val now = Instant.now().toString()
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
 xmlns:dc="http://purl.org/dc/elements/1.1/"
 xmlns:dcterms="http://purl.org/dc/terms/"
 xmlns:dcmitype="http://purl.org/dc/dcmitype/"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:title>Memora export</dc:title><dc:creator>Memora</dc:creator><cp:lastModifiedBy>Memora</cp:lastModifiedBy>
  <dcterms:created xsi:type="dcterms:W3CDTF">$now</dcterms:created>
  <dcterms:modified xsi:type="dcterms:W3CDTF">$now</dcterms:modified>
</cp:coreProperties>"""
    }

    private fun appProps() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"
 xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes"><Application>Memora</Application></Properties>"""

    private fun document(
        notes: List<NoteEntity>,
        language: AppLanguage,
        layout: WordLayout,
        images: List<DocxImage>,
        audios: List<DocxAudio>
    ): String {
        val imageByBlock = images.associateBy { it.blockId }
        val audioByBlock = audios.associateBy { it.blockId }
        val body = buildString {
            if (layout == WordLayout.CHRONOLOGICAL) {
                notes.forEach { note ->
                    append(paragraph(displayTitle(note, language), "MemoraTitle"))
                    append(paragraph(TimeUtils.dateTime(note.createdAt, language), "MemoraDate"))
                    appendBlocks(note, language, imageByBlock, audioByBlock)
                    append(emptyParagraph())
                }
            } else {
                val grouped = notes.groupBy { displayTitle(it, language) }
                    .entries.sortedBy { entry -> entry.value.minOfOrNull { it.createdAt } ?: Long.MAX_VALUE }
                grouped.forEach { (title, groupedNotes) ->
                    append(paragraph(title, "MemoraTitle"))
                    groupedNotes.sortedBy { it.createdAt }.forEach { note ->
                        append(paragraph(TimeUtils.dateTime(note.createdAt, language), "MemoraDateHeading"))
                        appendBlocks(note, language, imageByBlock, audioByBlock)
                    }
                    append(emptyParagraph())
                }
            }
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
 xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
 xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
 xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
  <w:body>$body
    <w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr>
  </w:body>
</w:document>"""
    }

    private fun StringBuilder.appendBlocks(
        note: NoteEntity,
        language: AppLanguage,
        images: Map<String, DocxImage>,
        audios: Map<String, DocxAudio>
    ) {
        val blocks = NoteBlocksCodec.decode(note.blocksJson, note.plainText)
        blocks.forEach { block ->
            when (block) {
                is NoteBlock.Text -> {
                    val normalizedText = block.text.replace("\r\n", "\n")
                    val paragraphs = normalizedText.split("\n")
                    var paragraphStart = 0
                    if (paragraphs.isEmpty()) append(paragraph("", "Normal"))
                    else paragraphs.forEachIndexed { index, line ->
                        val lineEnd = paragraphStart + line.length
                        val lineStyles = block.styles.mapNotNull { span ->
                            val start = maxOf(span.start, paragraphStart)
                            val end = minOf(span.end, lineEnd)
                            if (end > start) span.copy(start = start - paragraphStart, end = end - paragraphStart) else null
                        }
                        append(richParagraph(line, "Normal", lineStyles))
                        paragraphStart = lineEnd + if (index < paragraphs.lastIndex) 1 else 0
                    }
                }
                is NoteBlock.Image -> images[block.id]?.let { append(imageParagraph(it)) }
                is NoteBlock.Audio -> {
                    val audio = audios[block.id]
                    if (audio != null) {
                        val date = TimeUtils.dateTime(audio.createdAt, language)
                        append(audioHyperlinkParagraph(
                            audio = audio,
                            text = "🎧 [${audio.marker}] · $date · ${formatDuration(block.durationMs)} → Audio/${audio.targetName}"
                        ))
                    } else {
                        append(paragraph("🎧 ${formatDuration(block.durationMs)}", "MemoraDate"))
                    }
                }
            }
        }
    }

    private fun displayTitle(note: NoteEntity, language: AppLanguage): String =
        note.title.ifBlank { com.memora.app.i18n.L.t(language, com.memora.app.i18n.S.UNTITLED) }

    private fun richParagraph(text: String, style: String, styles: List<TextInlineStyle>): String {
        if (styles.isEmpty() || text.isEmpty()) return paragraph(text, style)
        val boundaries = mutableSetOf(0, text.length)
        styles.forEach { span ->
            boundaries += span.start.coerceIn(0, text.length)
            boundaries += span.end.coerceIn(0, text.length)
        }
        val points = boundaries.sorted()
        val runs = buildString {
            for (i in 0 until points.lastIndex) {
                val start = points[i]
                val end = points[i + 1]
                if (end <= start) continue
                val active = styles.filter { it.start < end && it.end > start }
                val bold = active.any { it.bold }
                val italic = active.any { it.italic }
                append("<w:r>")
                if (bold || italic) {
                    append("<w:rPr>")
                    if (bold) append("<w:b/>")
                    if (italic) append("<w:i/>")
                    append("</w:rPr>")
                }
                append("<w:t xml:space=\"preserve\">")
                    .append(TextUtils.xmlEscape(text.substring(start, end)))
                    .append("</w:t></w:r>")
            }
        }
        return "<w:p><w:pPr><w:pStyle w:val=\"$style\"/></w:pPr>$runs</w:p>"
    }

    private fun paragraph(text: String, style: String): String =
        """<w:p><w:pPr><w:pStyle w:val="$style"/></w:pPr><w:r><w:t xml:space="preserve">${TextUtils.xmlEscape(text)}</w:t></w:r></w:p>"""

    private fun emptyParagraph() = "<w:p><w:r><w:t></w:t></w:r></w:p>"

    private fun imageParagraph(image: DocxImage): String = """
<w:p><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0">
  <wp:extent cx="${image.cx}" cy="${image.cy}"/><wp:docPr id="${image.docPrId}" name="Memora image ${image.docPrId}"/>
  <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
    <pic:pic><pic:nvPicPr><pic:cNvPr id="0" name="${image.targetName}"/><pic:cNvPicPr/></pic:nvPicPr>
      <pic:blipFill><a:blip r:embed="${image.relationshipId}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
      <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="${image.cx}" cy="${image.cy}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
    </pic:pic>
  </a:graphicData></a:graphic>
</wp:inline></w:drawing></w:r></w:p>"""

    private fun audioHyperlinkParagraph(audio: DocxAudio, text: String): String =
        """<w:p><w:pPr><w:pStyle w:val="MemoraDate"/></w:pPr><w:hyperlink r:id="${audio.relationshipId}" w:history="1"><w:r><w:rPr><w:color w:val="2F6FED"/><w:u w:val="single"/></w:rPr><w:t xml:space="preserve">${TextUtils.xmlEscape(text)}</w:t></w:r></w:hyperlink></w:p>"""

    private fun formatDuration(durationMs: Long): String {
        val total = durationMs.coerceAtLeast(0L) / 1000L
        return "%02d:%02d".format(total / 60, total % 60)
    }
}
