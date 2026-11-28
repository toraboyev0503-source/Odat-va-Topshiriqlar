package com.memora.app.data.model

enum class NoteMood(val emoji: String) {
    GREAT("😄"),
    GOOD("🙂"),
    NEUTRAL("😐"),
    BAD("🙁"),
    VERY_BAD("😞");

    companion object {
        fun fromStored(value: String?): NoteMood? =
            value?.let { raw -> entries.firstOrNull { it.name == raw } }
    }
}
