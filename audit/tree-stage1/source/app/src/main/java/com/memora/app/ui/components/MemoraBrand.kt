package com.memora.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Memora's small, reusable brand system.
 *
 * The mark intentionally reads in three ways at once: M / open pages / saved bookmark.
 * Keeping it vector-drawn makes the launch animation crisp on every density and avoids
 * loading a large bitmap while the app is starting.
 */
object MemoraBrand {
    val Ivory = Color(0xFFFBF7EE)
    val Slate = Color(0xFF304E5E)
    val Sage = Color(0xFFAFC0AF)
    val SoftStone = Color(0xFFE8E2D7)
}

@Composable
fun MemoraBrandMark(
    modifier: Modifier = Modifier,
    pageColor: Color = MemoraBrand.Slate,
    bookmarkColor: Color = MemoraBrand.Sage,
    pageProgress: Float = 1f,
    bookmarkProgress: Float = 1f
) {
    Canvas(modifier = modifier) {
        drawMemoraMark(
            pageColor = pageColor,
            bookmarkColor = bookmarkColor,
            pageProgress = pageProgress.coerceIn(0f, 1f),
            bookmarkProgress = bookmarkProgress.coerceIn(0f, 1f)
        )
    }
}

private fun DrawScope.drawMemoraMark(
    pageColor: Color,
    bookmarkColor: Color,
    pageProgress: Float,
    bookmarkProgress: Float
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val spread = (1f - pageProgress) * w * 0.22f
    val alpha = pageProgress

    val top = h * 0.16f
    val bottom = h * 0.84f
    val mid = w * 0.50f
    val outerLeft = w * 0.12f - spread
    val outerRight = w * 0.88f + spread
    val innerLeft = w * 0.47f - spread
    val innerRight = w * 0.53f + spread

    val left = Path().apply {
        moveTo(outerLeft, top + h * 0.08f)
        cubicTo(
            outerLeft,
            top + h * 0.015f,
            outerLeft + w * 0.055f,
            top - h * 0.005f,
            outerLeft + w * 0.11f,
            top + h * 0.015f
        )
        cubicTo(
            outerLeft + w * 0.20f,
            top + h * 0.055f,
            innerLeft - w * 0.035f,
            top + h * 0.13f,
            innerLeft,
            top + h * 0.19f
        )
        lineTo(innerLeft, bottom)
        cubicTo(
            innerLeft - w * 0.095f,
            bottom - h * 0.075f,
            outerLeft + w * 0.13f,
            bottom - h * 0.095f,
            outerLeft + w * 0.055f,
            bottom - h * 0.07f
        )
        cubicTo(
            outerLeft + w * 0.018f,
            bottom - h * 0.055f,
            outerLeft,
            bottom - h * 0.03f,
            outerLeft,
            bottom - h * 0.005f
        )
        close()
    }

    val right = Path().apply {
        moveTo(outerRight, top + h * 0.08f)
        cubicTo(
            outerRight,
            top + h * 0.015f,
            outerRight - w * 0.055f,
            top - h * 0.005f,
            outerRight - w * 0.11f,
            top + h * 0.015f
        )
        cubicTo(
            outerRight - w * 0.20f,
            top + h * 0.055f,
            innerRight + w * 0.035f,
            top + h * 0.13f,
            innerRight,
            top + h * 0.19f
        )
        lineTo(innerRight, bottom)
        cubicTo(
            innerRight + w * 0.095f,
            bottom - h * 0.075f,
            outerRight - w * 0.13f,
            bottom - h * 0.095f,
            outerRight - w * 0.055f,
            bottom - h * 0.07f
        )
        cubicTo(
            outerRight - w * 0.018f,
            bottom - h * 0.055f,
            outerRight,
            bottom - h * 0.03f,
            outerRight,
            bottom - h * 0.005f
        )
        close()
    }

    drawPath(left, pageColor.copy(alpha = alpha))
    drawPath(right, pageColor.copy(alpha = alpha))

    if (bookmarkProgress > 0f) {
        val reveal = bookmarkProgress
        val bookmarkTop = top + h * 0.12f + (1f - reveal) * h * 0.12f
        val bookmarkBottom = bottom - h * 0.06f
        val halfWidth = w * 0.055f
        val notch = h * 0.065f
        val bookmark = Path().apply {
            moveTo(mid - halfWidth, bookmarkTop)
            lineTo(mid + halfWidth, bookmarkTop - h * 0.04f)
            lineTo(mid + halfWidth, bookmarkBottom)
            lineTo(mid, bookmarkBottom - notch)
            lineTo(mid - halfWidth, bookmarkBottom)
            close()
        }
        drawPath(bookmark, bookmarkColor.copy(alpha = reveal))
    }
}

/**
 * Two quiet, overlapping curves used at the end of the launch animation and behind Home.
 * Reusing the same visual primitive is what makes splash -> Home feel connected rather than
 * like two separate screens.
 */
@Composable
fun MemoraBrandBackdrop(
    modifier: Modifier = Modifier,
    primaryColor: Color,
    secondaryColor: Color,
    strength: Float = 1f
) {
    Canvas(modifier = modifier) {
        val a = strength.coerceIn(0f, 1f)
        val radius = size.width * 0.62f
        drawCircle(
            color = primaryColor.copy(alpha = 0.16f * a),
            radius = radius,
            center = Offset(size.width * 0.10f, size.height * 1.02f)
        )
        drawCircle(
            color = secondaryColor.copy(alpha = 0.28f * a),
            radius = radius * 0.92f,
            center = Offset(size.width * 0.86f, size.height * 1.07f)
        )
    }
}
