package com.memora.app.ui

/** Pure scroll policy to avoid flicker around the system-bar boundary. */
class NoteStatusBarPolicy(private val hideAtPx: Int, private val restoreAtPx: Int) {
    init {
        require(restoreAtPx < hideAtPx)
        require(restoreAtPx >= 0)
    }

    var hidden: Boolean = false
        private set

    fun update(scrollPx: Int): Boolean {
        if (!hidden && scrollPx >= hideAtPx) hidden = true
        else if (hidden && scrollPx <= restoreAtPx) hidden = false
        return hidden
    }
}
