package com.memora.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Calm, readable marker colors. The stored code is independent of light/dark theme. */
fun highlightComposeColor(code: String?): Color? = when (code) {
    "YELLOW" -> Color(0x80F4D779)
    "GREEN" -> Color(0x806CBF9D)
    "ROSE" -> Color(0x80DFA7B9)
    else -> null
}
