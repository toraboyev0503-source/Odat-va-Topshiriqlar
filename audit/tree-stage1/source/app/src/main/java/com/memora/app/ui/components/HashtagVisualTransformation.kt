package com.memora.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.memora.app.util.HashtagUtils

class HashtagVisualTransformation(
    private val color: Color,
    private val background: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val transformed = buildAnnotatedString {
            append(text.text)
            HashtagUtils.ranges(text.text).forEach { range ->
                addStyle(
                    SpanStyle(color = color, background = background, fontWeight = FontWeight.Medium),
                    range.first,
                    range.last + 1
                )
            }
        }
        return TransformedText(transformed, OffsetMapping.Identity)
    }
}
