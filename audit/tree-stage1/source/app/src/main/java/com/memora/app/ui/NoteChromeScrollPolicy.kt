package com.memora.app.ui

/** Pure directional scroll policy; no Compose or Android dependencies. */
class NoteChromeScrollPolicy(private val thresholdPx: Float) {
    private var previous: Int? = null
    private var accumulated = 0f

    /** Returns true to reveal controls, false to hide, or null to keep current state. */
    fun update(scrollValue: Int): Boolean? {
        val last = previous
        previous = scrollValue
        if (scrollValue <= 0) {
            accumulated = 0f
            return true
        }
        if (last == null) return null
        val step = (scrollValue - last).toFloat()
        return when {
            step > 0f -> {
                accumulated = (accumulated.coerceAtLeast(0f) + step).coerceAtMost(thresholdPx)
                if (accumulated >= thresholdPx) false else null
            }
            step < 0f -> {
                accumulated = (accumulated.coerceAtMost(0f) + step).coerceAtLeast(-thresholdPx)
                if (accumulated <= -thresholdPx) true else null
            }
            else -> null
        }
    }
}
