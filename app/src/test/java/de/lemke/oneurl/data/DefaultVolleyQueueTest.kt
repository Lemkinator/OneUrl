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
import androidx.test.core.app.ApplicationProvider
import com.android.volley.Cache
import com.android.volley.CacheDispatcher
import com.android.volley.NetworkDispatcher
import com.android.volley.Request
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.StringRequest
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class DefaultVolleyQueueTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val url = "https://example.com/cached"
    private val queue = DefaultVolleyQueue(context)

    @After
    fun stopQueue() {
        queue.stop()
    }

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

    private fun dispatcherThreads() =
        Thread.getAllStackTraces().keys.filterTo(HashSet()) { it is CacheDispatcher || it is NetworkDispatcher }

    private fun deliverCachedResponse(): String? {
        var delivered: String? = null
        val deliveredLatch = CountDownLatch(1)

        queue.add(
            StringRequest(Request.Method.GET, url, {
                delivered = it
                deliveredLatch.countDown()
            }, {}),
        )

        deliveredLatch.await(DELIVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
        return delivered
    }

    @Test
    fun `add delivers the response Volley serves for the request`() {
        cacheResponse("cached body")

        deliverCachedResponse() shouldBe "cached body"
    }

    @Test
    fun `stop on an unused queue starts no dispatcher threads and a later add still delivers`() {
        cacheResponse("cached body")
        val dispatchersBefore = dispatcherThreads()

        queue.stop()

        (dispatcherThreads() - dispatchersBefore).size shouldBe 0
        deliverCachedResponse() shouldBe "cached body"
        (dispatcherThreads() - dispatchersBefore).size shouldBe 5
    }

    @Test
    fun `removeCacheEntry drops the cached response of the url`() {
        cacheResponse("cached body")

        queue.removeCacheEntry(url)

        volleyCache().get(url) shouldBe null
    }

    private companion object {
        const val DELIVERY_TIMEOUT_SECONDS = 5L
    }
}
