package com.memora.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Shared visual tokens for Memora's "Tiniq Xotira" design language.
 *
 * Keep chrome quiet and predictable: three corner radii, two elevation levels,
 * and a small spacing scale. Screen-specific art (for example the review
 * frame) may still use custom geometry when it is part of the product identity.
 */
object MemoraDesign {
    val radiusSmall = 14.dp
    val radiusMedium = 22.dp
    val radiusLarge = 30.dp

    val shadowSubtle = 0.75.dp
    val shadowRaised = 5.dp

    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 12.dp
    val spaceLg = 16.dp
    val spaceXl = 20.dp
    val spaceXxl = 24.dp

    val screenPadding = 20.dp
    val cardPadding = 18.dp
    val bottomBarHorizontal = 16.dp
}
