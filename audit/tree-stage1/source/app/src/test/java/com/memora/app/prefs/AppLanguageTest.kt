package com.memora.app.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun selectsFirstSupportedDeviceLanguage() {
        assertEquals(AppLanguage.UZ, AppLanguage.fromLanguageTags(listOf("it-IT", "uz-UZ", "en-US")))
    }

    @Test
    fun mapsBrazilianPortugueseAndFallsBackToEnglish() {
        assertEquals(AppLanguage.PT_BR, AppLanguage.fromLanguageTags(listOf("pt-BR")))
        assertEquals(AppLanguage.EN, AppLanguage.fromLanguageTags(listOf("it-IT")))
    }
}
