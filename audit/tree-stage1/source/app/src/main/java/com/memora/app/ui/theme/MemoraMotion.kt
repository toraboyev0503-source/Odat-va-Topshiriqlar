package com.memora.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Central motion tokens for Memora.
 *
 * Interactive motion is intentionally quick so taps feel immediate, while atmospheric
 * motion (Home backdrop, Time Trace slideshow crossfade, branded splash) stays
 * calm. Keeping the timings here prevents individual screens from drifting apart.
 */
object MemoraMotion {
    const val Press = 70
    const val Fast = 100
    const val FastExit = 90
    const val Standard = 150
    const val StandardExit = 120
    const val Dialog = 110
    const val Snackbar = 120
    const val Ambient = 520

    // Quick response with a soft landing; no bounce/overshoot.
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.00f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.30f, 0.00f, 0.80f, 0.15f)
}
