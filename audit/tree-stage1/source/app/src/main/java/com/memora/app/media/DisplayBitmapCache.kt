package com.memora.app.media

import android.graphics.Bitmap
import android.util.LruCache

/** Approx. 24 MiB bounded cache; entries are keyed by path + file metadata. */
internal object DisplayBitmapCache {
    private const val MAX_BYTES = 24 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
    }

    fun get(key: String): Bitmap? = synchronized(cache) { cache.get(key) }
    fun put(key: String, value: Bitmap) = synchronized(cache) {
        if (value.allocationByteCount <= MAX_BYTES) cache.put(key, value)
        Unit
    }
    fun clear() = synchronized(cache) { cache.evictAll() }
}
