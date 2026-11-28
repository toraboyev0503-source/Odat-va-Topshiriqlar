package com.memora.app.ui.components

import com.memora.app.data.local.TopilmaCardRow
import com.memora.app.util.HashtagUtils

/**
 * Canonical Topilma card content used by both the in-app card and bitmap export.
 * Keeping the presentation derivation here prevents card/share text drift.
 */
data class TopilmaCardPresentation(
    val title: String,
    val author: String,
    val body: String,
    val tags: List<String>
)

fun TopilmaCardRow.presentation(): TopilmaCardPresentation = TopilmaCardPresentation(
    title = sourceTitle?.takeIf { it.isNotBlank() } ?: "Manbasi aniqlanmagan",
    author = sourceAuthor.orEmpty(),
    body = HashtagUtils.withoutTags(text).ifBlank { if (text.isBlank()) "Rasmli Topilma" else text },
    tags = tagNames()
)
