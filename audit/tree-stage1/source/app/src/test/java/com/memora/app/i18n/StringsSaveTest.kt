package com.memora.app.i18n

import com.memora.app.prefs.AppLanguage
import org.junit.Assert.assertTrue
import org.junit.Test

class StringsSaveTest {
    @Test
    fun everySupportedLanguageHasARealSaveLabel() {
        AppLanguage.entries.forEach { language ->
            val value = L.t(language, S.SAVE)
            assertTrue("Missing save label for ${language.name}", value.isNotBlank() && value != S.SAVE.name)
        }
    }
}
