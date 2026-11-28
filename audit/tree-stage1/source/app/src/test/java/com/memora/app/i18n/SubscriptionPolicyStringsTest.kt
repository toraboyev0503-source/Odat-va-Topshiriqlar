package com.memora.app.i18n

import com.memora.app.prefs.AppLanguage
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionPolicyStringsTest {
    @Test
    fun renewalTermsExistForEverySupportedLanguage() {
        AppLanguage.entries.forEach { language ->
            listOf(S.AUTO_RENEW_TERMS, S.TRIAL_RENEW_TERMS).forEach { key ->
                val value = L.t(language, key)
                assertTrue(
                    "Missing localized subscription policy text for ${language.name}: ${key.name}",
                    value.isNotBlank() && value != key.name
                )
            }
        }
    }
}
