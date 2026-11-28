package com.memora.app.data.repository

/** Shared persistence rule: an untitled draft lives only in EditorRecoveryStore. */
internal object NoteTitlePolicy {
    fun canPersist(title: String): Boolean = title.isNotBlank()
}
