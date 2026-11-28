package com.memora.app.share

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.toArgb
import com.memora.app.R
import com.memora.app.data.local.TopilmaCardRow
import com.memora.app.media.TopilmalarMediaStorage
import com.memora.app.prefs.ThemeMode
import com.memora.app.prefs.UserSettings
import com.memora.app.ui.components.TopilmaCardVisualSpec
import com.memora.app.ui.components.presentation
import com.memora.app.ui.theme.palette
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The canonical Topilma card renderer.
 *
 * Android Share, Obsidian and the live feed card all display this exact bitmap.
 * The width is fixed to the approved reference; height grows downward when the
 * complete quote needs more room. No pagination and no text-size compression.
 */
object TopilmaShareManager {
    fun shareText(context: Context, card: TopilmaCardRow, includeSource: Boolean) {
        val presentation = card.presentation()
        val text = buildString {
            append(presentation.body)
            if (includeSource && presentation.title.isNotBlank()) {
                append("\n\n— ").append(presentation.title)
                if (presentation.author.isNotBlank()) append(", ").append(presentation.author)
            }
            if (presentation.tags.isNotEmpty()) {
                append("\n\n").append(presentation.tags.joinToString(" ") { "#$it" })
            }
        }
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Topilmani ulashish"
            )
        )
    }

    /** Export always renders the complete quote. */
    suspend fun shareCardImage(context: Context, card: TopilmaCardRow, outerBackgroundArgb: Int) {
        val uri = withContext(Dispatchers.IO) {
            val bitmap = renderCardBitmap(context, card, outerBackgroundArgb)
            val directory = File(context.cacheDir, "shares").apply { mkdirs() }
            val file = File(directory, humanReadableShareName(card))
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        withContext(Dispatchers.Main) {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Kartochka rasmini ulashish"
                )
            )
        }
    }

    /** This exact bitmap is displayed in-app and written to Share/Obsidian. */
    fun renderCardBitmap(
        context: Context,
        card: TopilmaCardRow,
        outerBackgroundArgb: Int,
        targetWidth: Int = TopilmaCardVisualSpec.CANVAS_WIDTH
    ): Bitmap {
        val s = TopilmaCardVisualSpec
        require(targetWidth > 0) { "targetWidth must be positive" }
        val p = card.presentation()
        val bodyPaint = bodyPaint(s.BODY_TEXT_SIZE)
        val bodyLayout = staticLayout(
            p.body,
            bodyPaint,
            (s.BODY_RIGHT - s.BODY_LEFT).toInt(),
            s.BODY_LINE_HEIGHT,
            Int.MAX_VALUE
        )
        val bodyBottom = s.BODY_TOP + bodyLayout.height
        val tagLayout = if (p.tags.isEmpty()) null else layoutTags(p.tags, bodyBottom + s.BODY_TO_TAGS_GAP)
        val desiredActionCenter = if (tagLayout == null) {
            bodyBottom + s.BODY_TO_FOOTER_GAP
        } else {
            tagLayout.bottom + s.TAGS_TO_FOOTER_GAP
        }
        val actionCenterY = max(s.BASE_ACTION_CENTER_Y, desiredActionCenter)
        val cardBottom = actionCenterY + s.ACTION_TO_CARD_BOTTOM
        val canvasHeight = max(
            s.MIN_CANVAS_HEIGHT,
            ceil(cardBottom + s.CARD_TO_CANVAS_BOTTOM).toInt()
        )

        val renderScale = targetWidth.toFloat() / s.CANVAS_WIDTH
        val targetHeight = ceil(canvasHeight * renderScale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // The outer canvas follows the active Memora palette and stays opaque,
        // preventing black/transparent receiver edges without hard-coding pink.
        canvas.drawColor(outerBackgroundArgb)
        canvas.save()
        canvas.scale(renderScale, renderScale)

        // Reference card + soft shadow.
        val cardRect = RectF(s.CARD_LEFT, s.CARD_TOP, s.CARD_RIGHT, cardBottom)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(18, 60, 42, 34)
            setShadowLayer(24f, 0f, 12f, Color.argb(38, 70, 45, 35))
        }
        canvas.drawRoundRect(cardRect, s.CARD_RADIUS, s.CARD_RADIUS, shadowPaint)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.CARD_BACKGROUND_ARGB.toInt() }
        canvas.drawRoundRect(cardRect, s.CARD_RADIUS, s.CARD_RADIUS, cardPaint)

        drawCover(context, canvas, card)
        drawHeader(canvas, p.title, p.author)
        drawMenu(canvas)

        canvas.save()
        canvas.translate(s.BODY_LEFT, s.BODY_TOP)
        bodyLayout.draw(canvas)
        canvas.restore()

        tagLayout?.let { drawTags(canvas, it) }
        drawActions(context, canvas, card, actionCenterY)
        canvas.restore()
        return bitmap
    }

    private fun bodyPaint(size: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = Color.rgb(31, 31, 30)
        textSize = size
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    }

    private fun drawHeader(canvas: Canvas, title: String, author: String) {
        val s = TopilmaCardVisualSpec
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.rgb(31, 31, 31)
            textSize = s.TITLE_TEXT_SIZE
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.rgb(111, 111, 111)
            textSize = s.AUTHOR_TEXT_SIZE
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val headerWidth = (s.TITLE_RIGHT - s.TITLE_LEFT).toInt()
        val titleLayout = staticLayout(title, titlePaint, headerWidth, s.TITLE_LINE_HEIGHT, 2)
        canvas.save()
        canvas.translate(s.TITLE_LEFT, s.TITLE_TOP)
        titleLayout.draw(canvas)
        canvas.restore()

        if (author.isNotBlank()) {
            val authorLayout = staticLayout(author, authorPaint, headerWidth, s.AUTHOR_LINE_HEIGHT, 1)
            canvas.save()
            canvas.translate(s.TITLE_LEFT, s.TITLE_TOP + titleLayout.height + s.AUTHOR_TOP_GAP)
            authorLayout.draw(canvas)
            canvas.restore()
        }
    }

    private fun drawMenu(canvas: Canvas) {
        val s = TopilmaCardVisualSpec
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(108, 108, 108) }
        canvas.drawCircle(s.MENU_CENTER_X - s.MENU_DOT_GAP, s.MENU_CENTER_Y, s.MENU_DOT_RADIUS, paint)
        canvas.drawCircle(s.MENU_CENTER_X, s.MENU_CENTER_Y, s.MENU_DOT_RADIUS, paint)
        canvas.drawCircle(s.MENU_CENTER_X + s.MENU_DOT_GAP, s.MENU_CENTER_Y, s.MENU_DOT_RADIUS, paint)
    }

    private fun staticLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        lineHeight: Float,
        maxLines: Int
    ): StaticLayout {
        val natural = paint.fontMetrics.let { it.descent - it.ascent }
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(lineHeight - natural, 1f)
            .setMaxLines(maxLines)
        if (maxLines != Int.MAX_VALUE) builder.setEllipsize(android.text.TextUtils.TruncateAt.END)
        return builder.build()
    }

    private fun drawCover(context: Context, canvas: Canvas, card: TopilmaCardRow) {
        val s = TopilmaCardVisualSpec
        val rect = RectF(s.COVER_LEFT, s.COVER_TOP, s.COVER_LEFT + s.COVER_WIDTH, s.COVER_TOP + s.COVER_HEIGHT)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(7f, 0f, 3f, Color.argb(36, 0, 0, 0))
        }
        canvas.drawRoundRect(rect, s.COVER_RADIUS, s.COVER_RADIUS, shadow)
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(rect, s.COVER_RADIUS, s.COVER_RADIUS, frame)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 220, 220)
            style = Paint.Style.STROKE
            strokeWidth = s.COVER_BORDER
        }
        canvas.drawRoundRect(rect, s.COVER_RADIUS, s.COVER_RADIUS, border)

        val thumbnail = card.sourceCoverPath?.let { relativePath ->
            runCatching { decodeThumbnail(TopilmalarMediaStorage(context).resolve(relativePath), 640) }.getOrNull()
        }
        val available = RectF(
            rect.left + s.COVER_INSET,
            rect.top + s.COVER_INSET,
            rect.right - s.COVER_INSET,
            rect.bottom - s.COVER_INSET
        )
        if (thumbnail != null) {
            // Fit, never crop or stretch: preserves the original book-cover ratio.
            val scale = minOf(available.width() / thumbnail.width, available.height() / thumbnail.height)
            val drawW = thumbnail.width * scale
            val drawH = thumbnail.height * scale
            val dst = RectF(
                available.centerX() - drawW / 2f,
                available.centerY() - drawH / 2f,
                available.centerX() + drawW / 2f,
                available.centerY() + drawH / 2f
            )
            canvas.save()
            val clip = Path().apply {
                addRoundRect(available, s.COVER_RADIUS - s.COVER_INSET, s.COVER_RADIUS - s.COVER_INSET, Path.Direction.CW)
            }
            canvas.clipPath(clip)
            canvas.drawBitmap(thumbnail, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
            thumbnail.recycle()
        } else {
            val fallback = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(241, 243, 241) }
            canvas.drawRoundRect(available, s.COVER_RADIUS - s.COVER_INSET, s.COVER_RADIUS - s.COVER_INSET, fallback)
            val letter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(78, 99, 89)
                textSize = 52f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("M", available.centerX(), available.centerY() - (letter.fontMetrics.ascent + letter.fontMetrics.descent) / 2f, letter)
        }
    }

    private data class TagChip(val label: String, val rect: RectF)
    private data class TagLayout(val chips: List<TagChip>, val bottom: Float)

    private fun tagTextPaint() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = Color.rgb(89, 102, 157)
        textSize = TopilmaCardVisualSpec.TAG_TEXT_SIZE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun layoutTags(tags: List<String>, top: Float): TagLayout {
        val s = TopilmaCardVisualSpec
        val textPaint = tagTextPaint()
        val chipHeight = s.TAG_TEXT_SIZE + s.TAG_VERTICAL_PADDING * 2f + 8f
        val chips = mutableListOf<TagChip>()
        var x = s.BODY_LEFT
        var y = top
        for (tag in tags) {
            val label = "#$tag"
            val width = textPaint.measureText(label) + s.TAG_HORIZONTAL_PADDING * 2f
            if (x > s.BODY_LEFT && x + width > s.BODY_RIGHT) {
                x = s.BODY_LEFT
                y += chipHeight + s.TAG_GAP_Y
            }
            chips += TagChip(label, RectF(x, y, x + width, y + chipHeight))
            x += width + s.TAG_GAP_X
        }
        return TagLayout(chips, chips.maxOfOrNull { it.rect.bottom } ?: top)
    }

    private fun drawTags(canvas: Canvas, layout: TagLayout) {
        val s = TopilmaCardVisualSpec
        val textPaint = tagTextPaint()
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(244, 243, 250) }
        layout.chips.forEach { chip ->
            val radius = chip.rect.height() / 2f
            canvas.drawRoundRect(chip.rect, radius, radius, chipPaint)
            val fm = textPaint.fontMetrics
            val baseline = chip.rect.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText(chip.label, chip.rect.left + s.TAG_HORIZONTAL_PADDING, baseline, textPaint)
        }
    }

    private fun drawActions(context: Context, canvas: Canvas, card: TopilmaCardRow, actionCenterY: Float) {
        val s = TopilmaCardVisualSpec
        val countPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.rgb(91, 92, 91)
            textSize = s.COUNT_TEXT_SIZE
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        drawApprovedIcon(context, canvas, R.drawable.ic_topilma_share, s.SHARE_CENTER_X, actionCenterY)
        drawApprovedIcon(
            context,
            canvas,
            if (card.likeCount > 0) R.drawable.ic_topilma_heart_filled else R.drawable.ic_topilma_heart,
            s.LIKE_CENTER_X,
            actionCenterY
        )
        drawApprovedIcon(context, canvas, R.drawable.ic_topilma_note, s.COMMENT_CENTER_X, actionCenterY)

        if (card.likeCount > 0) {
            canvas.drawText(card.likeCount.toString(), s.LIKE_CENTER_X + 60f, actionCenterY + 12f, countPaint)
        }
        if (card.commentCount > 0) {
            canvas.drawText(card.commentCount.toString(), s.COMMENT_CENTER_X + 58f, actionCenterY + 12f, countPaint)
        }
    }

    /** One resource is rendered by the app, Android Share and Obsidian. */
    private fun drawApprovedIcon(context: Context, canvas: Canvas, drawableId: Int, cx: Float, cy: Float) {
        val size = TopilmaCardVisualSpec.ACTION_ICON_BOX_SIZE
        val half = size / 2f
        val drawable = requireNotNull(ContextCompat.getDrawable(context, drawableId)).mutate()
        drawable.setBounds(
            (cx - half).roundToInt(),
            (cy - half).roundToInt(),
            (cx + half).roundToInt(),
            (cy + half).roundToInt()
        )
        drawable.draw(canvas)
    }

    fun activeBackgroundArgb(context: Context, settings: UserSettings): Int {
        val dark = when (settings.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> {
                val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                mode == Configuration.UI_MODE_NIGHT_YES
            }
        }
        return palette(settings.palette, dark).background.toArgb()
    }

    private fun humanReadableShareName(card: TopilmaCardRow): String {
        val p = card.presentation()
        val raw = buildString {
            append("Topilma - ").append(p.title)
            if (p.author.isNotBlank()) append(" - ").append(p.author)
        }
        val safe = raw.replace(Regex("[\\/:*?\"<>|]"), "-").replace(Regex("\\s+"), " ").trim().take(110)
        return "$safe.png"
    }

    private fun decodeThumbnail(file: File, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > target * 2 || bounds.outHeight / sample > target * 2) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
