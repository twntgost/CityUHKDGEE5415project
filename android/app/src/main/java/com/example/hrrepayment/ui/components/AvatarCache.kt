package com.example.hrrepayment.ui.components

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Small in-memory bitmap cache for resume portraits.
 *
 * The dossier screen can show hundreds of faces and the workbench re-composes on
 * every command, so decoding on each composition both stutters and churns the heap.
 * An [LruCache] bounded by *bytes* (6 MB) keeps the working set small enough for a
 * low-end device while still avoiding repeat decodes for the visible pages.
 */
class AvatarCache(private val maxSizeKb: Int = DEFAULT_MAX_KB) {

    private val cache = object : LruCache<String, Bitmap>(maxSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun get(assetPath: String): Bitmap? = cache.get(assetPath)

    fun put(assetPath: String, bitmap: Bitmap) {
        if (cache.get(assetPath) == null) cache.put(assetPath, bitmap)
    }

    fun clear() = cache.evictAll()

    val sizeKb: Int get() = cache.size()

    companion object {
        const val DEFAULT_MAX_KB = 6 * 1024
    }
}
