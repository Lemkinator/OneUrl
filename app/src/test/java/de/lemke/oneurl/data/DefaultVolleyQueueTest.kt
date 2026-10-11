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

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.volley.Cache
import com.android.volley.Request
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.StringRequest
import io.kotest.matchers.shouldBe
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class DefaultVolleyQueueTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val url = "https://example.com/cached"

    private fun volleyCache() = DiskBasedCache(File(context.cacheDir, "volley")).apply { initialize() }

    private fun cacheResponse(body: String) {
        val entry =
            Cache.Entry().apply {
                data = body.toByteArray()
                ttl = Long.MAX_VALUE
                softTtl = Long.MAX_VALUE
                responseHeaders = emptyMap()
            }
        volleyCache().put(url, entry)
    }

    @Test
    fun `add delivers the response Volley serves for the request`() {
        cacheResponse("cached body")
        var delivered: String? = null
        val queue = DefaultVolleyQueue(context)

        queue.add(StringRequest(Request.Method.GET, url, { delivered = it }, {}))
        repeat(AWAIT_ATTEMPTS) {
            if (delivered == null) {
                Thread.sleep(AWAIT_STEP_MS)
                shadowOf(Looper.getMainLooper()).idle()
            }
        }

        delivered shouldBe "cached body"
    }

    @Test
    fun `removeCacheEntry drops the cached response of the url`() {
        cacheResponse("cached body")

        DefaultVolleyQueue(context).removeCacheEntry(url)

        volleyCache().get(url) shouldBe null
    }

    private companion object {
        const val AWAIT_ATTEMPTS = 200
        const val AWAIT_STEP_MS = 10L
    }
}
