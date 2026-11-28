package com.memora.app.billing

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores only billing entitlement metadata. Journal text, titles, images and audio never enter
 * this cache. The value is encrypted with a non-exportable Android Keystore key.
 */
internal class EntitlementCache(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): CachedEntitlement? = runCatching {
        val encoded = preferences.getString(KEY_PAYLOAD, null) ?: return null
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > IV_BYTES)
        val iv = combined.copyOfRange(0, IV_BYTES)
        val encrypted = combined.copyOfRange(IV_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        val root = JSONObject(cipher.doFinal(encrypted).toString(Charsets.UTF_8))
        CachedEntitlement(
            verifiedAtDeviceMillis = root.getLong("verifiedAtDeviceMillis"),
            serverTimeMillis = root.getLong("serverTimeMillis"),
            entitlementExpiryMillis = root.getLong("entitlementExpiryMillis"),
            basePlanId = root.optString("basePlanId").takeIf { it.isNotBlank() }
        )
    }.getOrElse {
        clear()
        null
    }

    fun write(value: CachedEntitlement) {
        val bytes = JSONObject().apply {
            put("verifiedAtDeviceMillis", value.verifiedAtDeviceMillis)
            put("serverTimeMillis", value.serverTimeMillis)
            put("entitlementExpiryMillis", value.entitlementExpiryMillis)
            put("basePlanId", value.basePlanId ?: "")
        }.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(bytes)
        val combined = cipher.iv + encrypted
        preferences.edit()
            .putString(KEY_PAYLOAD, Base64.encodeToString(combined, Base64.NO_WRAP))
            .apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_PAYLOAD).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES = "memora_billing_entitlement"
        const val KEY_PAYLOAD = "encrypted_entitlement_v1"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "memora_billing_cache_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
