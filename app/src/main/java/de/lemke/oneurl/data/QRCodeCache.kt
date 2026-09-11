/*
 * Copyright 2023-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.oneurl.data

import android.graphics.Bitmap
import android.util.LruCache
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache of generated QR bitmaps, keyed by URL and pixel size. QR generation is a pure
 * function of (url, sizePx) — see [de.lemke.oneurl.domain.GenerateQRCodeUseCase] — so a cache hit
 * never goes stale. Evicted bitmaps are never recycled here: an evicted entry may still be
 * attached to a visible ImageView, and recycling it would crash that view's next draw.
 */
@Singleton
class QRCodeCache @Inject constructor() {
    private val cache =
        object : LruCache<String, Bitmap>(CACHE_BYTE_BUDGET_KB) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.byteCount / BYTES_PER_KB
        }

    operator fun get(url: String): Bitmap? = cache.get(url)

    operator fun set(
        url: String,
        bitmap: Bitmap,
    ) {
        cache.put(url, bitmap)
    }

    operator fun get(
        url: String,
        sizePx: Int,
    ): Bitmap? = cache.get(key(url, sizePx))

    operator fun set(
        url: String,
        sizePx: Int,
        bitmap: Bitmap,
    ) {
        cache.put(key(url, sizePx), bitmap)
    }

    private fun key(
        url: String,
        sizePx: Int,
    ): String = "$url@$sizePx"

    companion object {
        private const val BYTES_PER_KB = 1024
        private val CACHE_BYTE_BUDGET_KB = (Runtime.getRuntime().maxMemory() / BYTES_PER_KB / CACHE_MEMORY_FRACTION).toInt()
        private const val CACHE_MEMORY_FRACTION = 8
    }
}
