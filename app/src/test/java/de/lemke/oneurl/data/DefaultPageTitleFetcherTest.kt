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
import com.android.volley.NetworkResponse
import com.android.volley.VolleyError
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class DefaultPageTitleFetcherTest {
    private val queue = FakeVolleyQueue()
    private val pageTitleFetcher = DefaultPageTitleFetcher(queue)

    @Test
    fun `extracts the title from the response html`() =
        runTest {
            queue.onAdd = {
                it.deliverResponse("<html><head><title> My Page </title></head></html>")
            }

            pageTitleFetcher.title("https://example.com") shouldBe "My Page"
        }

    @Test
    fun `requests the given url`() =
        runTest {
            queue.onAdd = { it.deliverResponse("<title>Page</title>") }

            pageTitleFetcher.title("https://example.com/page")

            queue.added.single().url shouldBe "https://example.com/page"
        }

    @Test
    fun `returns null when the response has no title tag`() =
        runTest {
            queue.onAdd = {
                it.deliverResponse("<html><body>no title here</body></html>")
            }

            pageTitleFetcher.title("https://example.com") shouldBe null
        }

    @Test
    fun `returns null on a network error`() =
        runTest {
            queue.onAdd = {
                it.deliverError(VolleyError(NetworkResponse(404, ByteArray(0), false, 0L, emptyList())))
            }

            pageTitleFetcher.title("https://example.com") shouldBe null
        }

    @Test
    fun `returns null and logs when parsing the response throws`() =
        runTest {
            // The compiled listener null-checks its parameter, so a null response reaches the parse failure branch.
            queue.onAdd = {
                it.deliverResponse(null)
            }

            pageTitleFetcher.title("https://example.com") shouldBe null
        }

    @Test
    fun `ignores a response delivered after the continuation already resumed`() =
        runTest {
            queue.onAdd = { request ->
                request.deliverResponse("<html><head><title>First</title></head></html>")
                request.deliverResponse("<html><head><title>Second</title></head></html>")
            }

            pageTitleFetcher.title("https://example.com") shouldBe "First"
        }

    @Test
    fun `ignores a network error delivered after the continuation already resumed`() =
        runTest {
            queue.onAdd = { request ->
                val error = VolleyError(NetworkResponse(404, ByteArray(0), false, 0L, emptyList()))
                request.deliverError(error)
                request.deliverError(error)
            }

            pageTitleFetcher.title("https://example.com") shouldBe null
        }

    @Test
    fun `cancelling the coroutine cancels the underlying request`() =
        runTest {
            val job = launch { pageTitleFetcher.title("https://example.com") }
            runCurrent()
            job.cancel()
            runCurrent()

            queue.added.single().isCanceled shouldBe true
        }
}
