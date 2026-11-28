package com.memora.app.ui.components

import android.text.Html
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.memora.app.prefs.FontChoice

@Composable
fun HtmlText(
    html: String,
    fontChoice: FontChoice,
    color: Color,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                textSize = 18f
                setLineSpacing(0f, 1.35f)
                setTextColor(color.toArgb())
                typeface = when (fontChoice) {
                    FontChoice.SANS -> android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                    FontChoice.SERIF -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
                    FontChoice.MONO -> android.graphics.Typeface.MONOSPACE
                    FontChoice.CURSIVE -> android.graphics.Typeface.create("cursive", android.graphics.Typeface.NORMAL)
                }
                setTextIsSelectable(true)
            }
        },
        update = { view ->
            view.setTextColor(color.toArgb())
            view.typeface = when (fontChoice) {
                FontChoice.SANS -> android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                FontChoice.SERIF -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
                FontChoice.MONO -> android.graphics.Typeface.MONOSPACE
                FontChoice.CURSIVE -> android.graphics.Typeface.create("cursive", android.graphics.Typeface.NORMAL)
            }
            view.text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        }
    )
}
