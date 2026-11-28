package com.memora.app.billing

import android.content.Context
import com.memora.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal sealed interface VerificationResult {
    data class Verified(
        val active: Boolean,
        val expiryTimeMillis: Long,
        val serverTimeMillis: Long,
        val basePlanId: String?
    ) : VerificationResult

    data object Unavailable : VerificationResult
    data object Misconfigured : VerificationResult
}

/** Sends only package/product/purchase-token billing data. No journal content is accepted here. */
internal class BillingVerificationClient(private val context: Context) {
    suspend fun verify(purchaseToken: String): VerificationResult = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.BILLING_VERIFICATION_URL.trim()
        if (endpoint.isBlank()) return@withContext VerificationResult.Misconfigured
        val uri = runCatching { URI(endpoint) }.getOrNull()
            ?: return@withContext VerificationResult.Misconfigured
        if (uri.scheme != "https" && !(BuildConfig.DEBUG && uri.scheme == "http")) {
            return@withContext VerificationResult.Misconfigured
        }

        runCatching {
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.doOutput = true
                connection.useCaches = false
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("X-Memora-Protocol", "1")

                val request = JSONObject().apply {
                    put("packageName", context.packageName.removeSuffix(".debug"))
                    put("productId", SubscriptionCatalog.PRODUCT_ID)
                    put("purchaseToken", purchaseToken)
                }.toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(request.size)
                connection.outputStream.use { it.write(request) }

                if (connection.responseCode !in 200..299) {
                    return@runCatching if (connection.responseCode in 400..499) {
                        VerificationResult.Misconfigured
                    } else {
                        VerificationResult.Unavailable
                    }
                }
                val response = connection.inputStream.use { readLimited(it, MAX_RESPONSE_BYTES) }
                val root = JSONObject(response.toString(Charsets.UTF_8))
                VerificationResult.Verified(
                    active = root.getBoolean("active"),
                    expiryTimeMillis = root.optLong("expiryTimeMillis", 0L),
                    serverTimeMillis = root.getLong("serverTimeMillis"),
                    basePlanId = root.optString("basePlanId").takeIf { it.isNotBlank() }
                )
            } finally {
                connection.disconnect()
            }
        }.getOrElse { VerificationResult.Unavailable }
    }

    private fun readLimited(input: java.io.InputStream, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maximum) { "Billing response is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 15_000
        const val MAX_RESPONSE_BYTES = 64 * 1024
    }
}
