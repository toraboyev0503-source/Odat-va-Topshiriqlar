package com.memora.app.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import com.memora.app.data.local.NoteEntity
import com.memora.app.data.model.NoteBlock
import com.memora.app.data.model.NoteBlocksCodec
import com.memora.app.media.MediaStorage
import com.memora.app.prefs.AppLanguage
import com.memora.app.util.TimeUtils
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

/** The PDF and PNG formats use the very same paged Memora Paper renderer.
 * Never screenshots system bars, floating controls, keyboard or other private UI.
 */
internal class PaperShareRenderer(context: Context) {
    private val storage = MediaStorage(context.applicationContext)
    private val appIcon: Drawable? = runCatching { context.applicationInfo.loadIcon(context.packageManager) }.getOrNull()
    private val cache = File(context.cacheDir, "shares").apply { mkdirs() }
    private val width = 1080
    private val height = 1528
    private val ink = Color.rgb(36, 40, 42)
    private val secondary = Color.rgb(112, 119, 122)
    private val paper = Color.rgb(255, 253, 250)
    private val canvasBackground = Color.rgb(239, 242, 243)
    private val left = 116f
    private val bodyWidth = 848
    private val paperBottom = 1392f
    private val bodyTop = 145f
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 39f
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    }
    private val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = 84f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondary
        textSize = 25f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    fun render(note: NoteEntity, language: AppLanguage, includeDates: Boolean = true): List<File> {
        val result = mutableListOf<File>()
        val requestId = UUID.randomUUID().toString().take(12)
        var bitmap = newPage()
        var canvas = Canvas(bitmap)
        var y = bodyTop

        fun nextPage() {
            finishPage(canvas, result.size + 1)
            val path = File(cache, "paper-$requestId-${result.size + 1}.png")
            FileOutputStream(path).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            result += path
            bitmap.recycle()
            bitmap = newPage()
            canvas = Canvas(bitmap)
            y = bodyTop
        }
        fun ensure(needed: Float) { if (y + needed > paperBottom && y > bodyTop) nextPage() }

        fun drawParagraph(source: CharSequence, paint: TextPaint, spacing: Float = 1.28f, gap: Float = 25f) {
            if (source.isEmpty()) return
            var start = 0
            while (start < source.length) {
                if (paperBottom - y < paint.textSize * 1.65f) nextPage()
                val part = source.subSequence(start, source.length)
                val layout = makeLayout(part, paint, spacing)
                val available = (paperBottom - y).toInt()
                if (layout.height <= available) {
                    canvas.save(); canvas.translate(left, y); layout.draw(canvas); canvas.restore()
                    y += layout.height + gap
                    start = source.length
                } else {
                    var line = 0
                    while (line < layout.lineCount && layout.getLineBottom(line) <= available) line++
                    if (line == 0) { nextPage(); continue }
                    val fit = layout.getLineEnd(line - 1).coerceIn(1, part.length)
                    val clipped = makeLayout(part.subSequence(0, fit), paint, spacing)
                    canvas.save(); canvas.translate(left, y); clipped.draw(canvas); canvas.restore()
                    start += fit
                    nextPage()
                }
            }
        }

        val title = note.title.ifBlank { "Memora" }
        drawParagraph(title, headingPaint, 1.06f, 39f)
        val blocks = NoteBlocksCodec.decode(note.blocksJson, note.plainText)
        for (block in blocks) {
            when (block) {
                is NoteBlock.Text -> if (block.text.isNotBlank()) {
                    val styled = SpannableStringBuilder(block.text)
                    for (style in block.styles) {
                        val start = style.start.coerceIn(0, block.text.length)
                        val end = style.end.coerceIn(start, block.text.length)
                        if (end <= start) continue
                        if (style.bold || style.italic) {
                            val type = when {
                                style.bold && style.italic -> Typeface.BOLD_ITALIC
                                style.bold -> Typeface.BOLD
                                else -> Typeface.ITALIC
                            }
                            styled.setSpan(StyleSpan(type), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        val color = when (style.highlight) {
                            "YELLOW" -> Color.rgb(247, 228, 163)
                            "GREEN" -> Color.rgb(214, 236, 209)
                            "ROSE" -> Color.rgb(240, 210, 218)
                            else -> null
                        }
                        if (color != null) styled.setSpan(BackgroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    drawParagraph(styled, textPaint)
                }
                is NoteBlock.Image -> {
                    val file = runCatching { storage.resolve(block.relativePath) }.getOrNull()
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    if (file?.isFile == true) BitmapFactory.decodeFile(file.absolutePath, options)
                    if (options.outWidth > 0 && options.outHeight > 0 && file != null) {
                        val scale = minOf(bodyWidth / options.outWidth.toFloat(), 430f / options.outHeight)
                        val pictureWidth = options.outWidth * scale
                        val h = options.outHeight * scale
                        ensure(h + 35f)
                        val sample = BitmapFactory.Options().apply { inSampleSize = maxOf(1, options.outWidth / 950) }
                        val picture = BitmapFactory.decodeFile(file.absolutePath, sample)
                        if (picture != null) {
                            val x = left + (bodyWidth - pictureWidth) / 2f
                            canvas.drawBitmap(picture, null, RectF(x, y, x + pictureWidth, y + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                            picture.recycle()
                        }
                        y += h + 31f
                    } else drawParagraph("[Image]", metaPaint)
                }
                is NoteBlock.Audio -> drawParagraph("♫  Audio", metaPaint)
            }
        }
        if (includeDates) {
            y += 20f
            val createdLabel = when (language) { AppLanguage.UZ -> "Yaratilgan"; AppLanguage.RU -> "Создано"; else -> "Created" }
            val editedLabel = when (language) { AppLanguage.UZ -> "Tahrirlangan"; AppLanguage.RU -> "Изменено"; else -> "Edited" }
            ensure(95f)
            val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(223, 226, 226); strokeWidth = 2f }
            canvas.drawLine(left, y, left + bodyWidth, y, divider)
            y += 17f
            drawParagraph("$createdLabel: ${TimeUtils.dateTime(note.createdAt, language)}", metaPaint, 1f, 2f)
            if (note.updatedAt > note.createdAt + 1000) {
                drawParagraph("$editedLabel: ${TimeUtils.dateTime(note.updatedAt, language)}", metaPaint, 1f, 2f)
            }
        }
        nextPage()
        bitmap.recycle() // nextPage initialized a blank new page, which is not exported.
        return result
    }

    private fun makeLayout(text: CharSequence, paint: TextPaint, spacing: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, bodyWidth)
            .setIncludePad(false).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spacing).build()

    private fun newPage(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
        val canvas = Canvas(bmp)
        canvas.drawColor(canvasBackground)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(221, 224, 226) }
        canvas.drawRoundRect(RectF(67f, 61f, 1021f, 1459f), 26f, 26f, shadow)
        val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paper }
        canvas.drawRoundRect(RectF(61f, 54f, 1017f, 1450f), 26f, 26f, paperPaint)
    }

    /**
     * Selection-only PNGs deliberately have no fixed A4/card height. The height comes from the
     * Android StaticLayout of the *actual* selected text, plus symmetrical paper margins and the
     * signature footer. Very long selections are divided into several naturally sized cards.
     */
    fun renderSelection(selection: String): List<File> {
        val text = selection.trim()
        require(text.isNotEmpty()) { "Nothing selected" }
        val pageLimit = 2300
        val requestId = UUID.randomUUID().toString().take(12)
        val chunks = mutableListOf<CharSequence>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            val layout = makeLayout(remaining, textPaint, 1.28f)
            if (layout.height <= pageLimit) {
                chunks += remaining
                break
            }
            var fitted = 0
            while (fitted < layout.lineCount && layout.getLineBottom(fitted) <= pageLimit) fitted++
            val cut = layout.getLineEnd((fitted - 1).coerceAtLeast(0)).coerceIn(1, remaining.length)
            chunks += remaining.substring(0, cut).trimEnd('\n')
            remaining = remaining.substring(cut).trimStart('\n')
        }
        return chunks.mapIndexed { index, part ->
            val layout = makeLayout(part, textPaint, 1.28f)
            val pageHeight = SelectionPaperDimensions.heightForText(layout.height)
            val bmp = Bitmap.createBitmap(width, pageHeight, Bitmap.Config.ARGB_8888)
            try {
                val c = Canvas(bmp)
                c.drawColor(canvasBackground)
                val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(221, 224, 226) }
                c.drawRoundRect(RectF(48f, 45f, 1037f, pageHeight - 27f), 27f, 27f, shadow)
                val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paper }
                c.drawRoundRect(RectF(41f, 38f, 1030f, pageHeight - 35f), 27f, 27f, paperPaint)
                c.save()
                c.translate(left, SelectionPaperDimensions.TEXT_TOP.toFloat())
                layout.draw(c)
                c.restore()
                drawSignature(c, pageHeight - 77f, if (chunks.size > 1) index + 1 else null)
                val file = File(cache, "paper-selection-$requestId-${index + 1}.png")
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                file
            } finally {
                bmp.recycle()
            }
        }
    }

    /** Draws the *installed app's actual launcher icon*, not a substitute or emoji. */
    private fun drawSignature(canvas: Canvas, baseline: Float, page: Int?) {
        val size = 36
        appIcon?.let { icon ->
            canvas.save()
            val x = left.toInt()
            val y = (baseline - 29f).roundToInt()
            icon.setBounds(x, y, x + size, y + size)
            icon.draw(canvas)
            canvas.restore()
        }
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondary
            textSize = 23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Memora", left + (if (appIcon != null) 49f else 0f), baseline, brand)
        if (page != null) canvas.drawText(page.toString(), 930f, baseline, brand)
    }

    private fun finishPage(canvas: Canvas, number: Int) {
        drawSignature(canvas, 1430f, number)
    }
}
