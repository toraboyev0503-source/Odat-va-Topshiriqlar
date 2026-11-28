package com.memora.app.prefs

import android.content.Context
import java.security.MessageDigest

class TopilmaIntegrationPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabledPackages(): Set<String> = prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().toSet()

    fun isEnabled(packageName: String): Boolean = packageName in enabledPackages()

    fun setEnabled(packageName: String, enabled: Boolean) {
        val next = enabledPackages().toMutableSet()
        if (enabled) next += packageName else next -= packageName
        prefs.edit().putStringSet(KEY_PACKAGES, next).apply()
    }

    fun quickMonitorEnabled(): Boolean = prefs.getBoolean(KEY_QUICK_MONITOR, false)

    fun setQuickMonitorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QUICK_MONITOR, enabled).apply()
    }

    fun sourceForContext(contextKey: String): String? =
        prefs.getString(KEY_CONTEXT_PREFIX + digest(contextKey), null)

    fun rememberContextSource(contextKey: String, sourceId: String) {
        prefs.edit().putString(KEY_CONTEXT_PREFIX + digest(contextKey), sourceId).apply()
    }

    private fun digest(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }

    companion object {
        private const val PREFS = "topilma_integrations"
        private const val KEY_PACKAGES = "enabled_packages"
        private const val KEY_QUICK_MONITOR = "quick_monitor"
        private const val KEY_CONTEXT_PREFIX = "context_source_"
    }
}
