package com.memora.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.memora.app.data.model.NoteMood

private data class MoodPalette(val fill: Color, val ink: Color, val cheek: Color)

private fun palette(mood: NoteMood): MoodPalette = when (mood) {
    NoteMood.GREAT -> MoodPalette(Color(0xFFF2C978), Color(0xFF5A3A1A), Color(0xFFD99661))
    NoteMood.GOOD -> MoodPalette(Color(0xFFB8C7AA), Color(0xFF304237), Color(0xFF9FB18F))
    NoteMood.NEUTRAL -> MoodPalette(Color(0xFFAFC6DB), Color(0xFF324A5F), Color(0xFF93AEC8))
    NoteMood.BAD -> MoodPalette(Color(0xFFBEB5D7), Color(0xFF4A4563), Color(0xFFA59BC6))
    NoteMood.VERY_BAD -> MoodPalette(Color(0xFFD9A09A), Color(0xFF633B39), Color(0xFFC5847E))
}

@Composable
fun MoodSticker(
    mood: NoteMood,
    description: String,
    modifier: Modifier = Modifier
) {
    val p = palette(mood)
    Canvas(modifier.semantics { contentDescription = description }) {
        val w = size.width
        val h = size.height
        val blob = Path().apply {
            moveTo(w * .20f, h * .10f)
            cubicTo(w * .08f, h * .18f, w * .05f, h * .55f, w * .12f, h * .78f)
            cubicTo(w * .20f, h * .98f, w * .78f, h * .97f, w * .88f, h * .78f)
            cubicTo(w * .96f, h * .54f, w * .91f, h * .20f, w * .78f, h * .10f)
            cubicTo(w * .62f, h * .02f, w * .38f, h * .03f, w * .20f, h * .10f)
            close()
        }
        drawPath(blob, p.fill)
        val stroke = (w * .045f).coerceAtLeast(2.dp.toPx())
        val eyeY = h * .43f
        val mouthY = h * .62f
        when (mood) {
            NoteMood.GREAT -> {
                drawArc(p.ink, 195f, 150f, false, Offset(w*.24f,h*.34f), androidx.compose.ui.geometry.Size(w*.20f,h*.18f), style=Stroke(stroke, cap=StrokeCap.Round))
                drawArc(p.ink, 195f, 150f, false, Offset(w*.56f,h*.34f), androidx.compose.ui.geometry.Size(w*.20f,h*.18f), style=Stroke(stroke, cap=StrokeCap.Round))
                drawArc(p.ink, 10f, 160f, false, Offset(w*.32f,h*.51f), androidx.compose.ui.geometry.Size(w*.36f,h*.26f), style=Stroke(stroke*1.08f, cap=StrokeCap.Round))
                drawLine(p.ink, Offset(w*.31f,h*.19f), Offset(w*.27f,h*.10f), stroke*.72f, StrokeCap.Round)
                drawLine(p.ink, Offset(w*.50f,h*.17f), Offset(w*.50f,h*.07f), stroke*.72f, StrokeCap.Round)
                drawLine(p.ink, Offset(w*.69f,h*.19f), Offset(w*.73f,h*.10f), stroke*.72f, StrokeCap.Round)
            }
            NoteMood.GOOD -> {
                drawArc(p.ink, 195f, 150f, false, Offset(w*.24f,h*.35f), androidx.compose.ui.geometry.Size(w*.20f,h*.16f), style=Stroke(stroke, cap=StrokeCap.Round))
                drawArc(p.ink, 195f, 150f, false, Offset(w*.56f,h*.35f), androidx.compose.ui.geometry.Size(w*.20f,h*.16f), style=Stroke(stroke, cap=StrokeCap.Round))
                drawArc(p.ink, 15f, 150f, false, Offset(w*.35f,h*.52f), androidx.compose.ui.geometry.Size(w*.30f,h*.20f), style=Stroke(stroke, cap=StrokeCap.Round))
            }
            NoteMood.NEUTRAL -> {
                drawCircle(p.ink, w*.045f, Offset(w*.35f, eyeY))
                drawCircle(p.ink, w*.045f, Offset(w*.65f, eyeY))
                drawLine(p.ink, Offset(w*.42f,mouthY), Offset(w*.58f,mouthY), stroke, StrokeCap.Round)
            }
            NoteMood.BAD -> {
                drawArc(p.ink, 15f, 150f, false, Offset(w*.24f,h*.37f), androidx.compose.ui.geometry.Size(w*.20f,h*.14f), style=Stroke(stroke, cap=StrokeCap.Round))
                drawArc(p.ink, 15f, 150f, false, Offset(w*.56f,h*.37f), androidx.compose.ui.geometry.Size(w*.20f,h*.14f), style=Stroke(stroke, cap=StrokeCap.Round))
                drawArc(p.ink, 195f, 150f, false, Offset(w*.35f,h*.58f), androidx.compose.ui.geometry.Size(w*.30f,h*.20f), style=Stroke(stroke, cap=StrokeCap.Round))
            }
            NoteMood.VERY_BAD -> {
                drawLine(p.ink, Offset(w*.27f,h*.38f), Offset(w*.41f,h*.46f), stroke, StrokeCap.Round)
                drawLine(p.ink, Offset(w*.41f,h*.38f), Offset(w*.28f,h*.46f), stroke, StrokeCap.Round)
                drawLine(p.ink, Offset(w*.59f,h*.38f), Offset(w*.73f,h*.46f), stroke, StrokeCap.Round)
                drawLine(p.ink, Offset(w*.72f,h*.38f), Offset(w*.59f,h*.46f), stroke, StrokeCap.Round)
                drawArc(p.ink, 195f, 150f, false, Offset(w*.34f,h*.58f), androidx.compose.ui.geometry.Size(w*.32f,h*.20f), style=Stroke(stroke, cap=StrokeCap.Round))
            }
        }
        if (mood != NoteMood.NEUTRAL) {
            drawCircle(p.cheek.copy(alpha=.48f), w*.055f, Offset(w*.22f,h*.58f))
            drawCircle(p.cheek.copy(alpha=.48f), w*.055f, Offset(w*.78f,h*.58f))
        }
    }
}
