package com.memora.app.ui.screens

/** A year becomes a collapsible archive only after it has ended. */
internal object NotesArchivePolicy {
    fun archived(year: Int, currentYear: Int): Boolean = year < currentYear
}
