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
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import de.lemke.oneurl.domain.model.GenerateURLError
import de.lemke.oneurl.domain.model.GenerateURLResult
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.URL
import io.kotest.matchers.shouldBe
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class DefaultShortURLClientTest {
    private val queue = FakeVolleyQueue()
    private val client = DefaultShortURLClient(queue)

    /** Answers each create request through [answer] and each visit count through [visitCount]. */
    private class TestProvider(
        private val answer: (success: (String) -> Unit, error: (GenerateURLError) -> Unit) -> Unit = { _, _ -> },
        private val visitCount: (VolleyQueue, (Int?) -> Unit) -> Unit = { _, _ -> },
    ) : ShortURLProvider {
        override val name = "test"
        override val baseURL = "https://test.example"
        val requests = mutableListOf<Pair<String, String>>()

        override fun getCreateRequest(
            queue: VolleyQueue,
            longURL: String,
            alias: String,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit,
        ): Request<*> {
            requests += longURL to alias
            answer(successCallback, errorCallback)
            return StringRequest(Request.Method.GET, "$baseURL/create", {}, {})
        }

        override fun getURLClickCount(
            queue: VolleyQueue,
            url: URL,
            callback: (clicks: Int?) -> Unit,
        ) = visitCount(queue, callback)
    }

    private fun url(provider: ShortURLProvider) =
        URL(
            shortURL = "https://test.example/abc",
            longURL = "https://example.com",
            shortURLProvider = provider,
            favorite = false,
            title = "title",
            description = "description",
            added = ZonedDateTime.of(2024, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC),
        )

    @Test
    fun `create queues the provider's request for the long url and alias`() =
        runTest {
            val provider = TestProvider(answer = { success, _ -> success("https://test.example/abc") })

            client.create(provider, "https://example.com", "abc")

            provider.requests shouldBe listOf("https://example.com" to "abc")
            queue.added.single().url shouldBe "https://test.example/create"
        }

    @Test
    fun `create returns Success with the provider's short url`() =
        runTest {
            val provider = TestProvider(answer = { success, _ -> success("https://test.example/abc") })

            client.create(provider, "https://example.com", "") shouldBe GenerateURLResult.Success("https://test.example/abc")
        }

    @Test
    fun `create returns Failure with the provider's error`() =
        runTest {
            val provider = TestProvider(answer = { _, error -> error(GenerateURLError.RateLimitExceeded) })

            client.create(provider, "https://example.com", "") shouldBe GenerateURLResult.Failure(GenerateURLError.RateLimitExceeded)
        }

    @Test
    fun `create keeps the first answer of a provider that answers twice`() =
        runTest {
            val provider =
                TestProvider(answer = { success, error ->
                    success("https://test.example/first")
                    error(GenerateURLError.NoInternet)
                })

            client.create(provider, "https://example.com", "") shouldBe GenerateURLResult.Success("https://test.example/first")
        }

    @Test
    fun `create keeps the first failure of a provider that fails twice`() =
        runTest {
            val provider =
                TestProvider(answer = { _, error ->
                    error(GenerateURLError.RateLimitExceeded)
                    error(GenerateURLError.NoInternet)
                })

            client.create(provider, "https://example.com", "") shouldBe GenerateURLResult.Failure(GenerateURLError.RateLimitExceeded)
        }

    @Test
    fun `create keeps the first failure of a provider that succeeds afterwards`() =
        runTest {
            val provider =
                TestProvider(answer = { success, error ->
                    error(GenerateURLError.RateLimitExceeded)
                    success("https://test.example/late")
                })

            client.create(provider, "https://example.com", "") shouldBe GenerateURLResult.Failure(GenerateURLError.RateLimitExceeded)
        }

    @Test
    fun `cancelling create cancels the queued request`() =
        runTest {
            val job = launch { client.create(TestProvider(), "https://example.com", "") }
            runCurrent()

            job.cancel()
            runCurrent()

            queue.added.single().isCanceled shouldBe true
        }

    @Test
    fun `visitCount returns the count the provider reports`() =
        runTest {
            client.visitCount(url(TestProvider(visitCount = { _, report -> report(42) }))) shouldBe 42
        }

    @Test
    fun `visitCount returns null when the provider reports no count`() =
        runTest {
            client.visitCount(url(TestProvider(visitCount = { _, report -> report(null) }))) shouldBe null
        }

    @Test
    fun `visitCount keeps the first count of a provider that reports twice`() =
        runTest {
            val provider =
                TestProvider(visitCount = { _, report ->
                    report(1)
                    report(2)
                })

            client.visitCount(url(provider)) shouldBe 1
        }

    @Test
    fun `cancelling visitCount cancels the queued request`() =
        runTest {
            val request = StringRequest(Request.Method.GET, "https://test.example/stats", {}, {})
            val provider = TestProvider(visitCount = { queue, _ -> queue.add(request) })
            val job = launch { client.visitCount(url(provider)) }
            runCurrent()
            queue.added.single() shouldBe request
            request.isCanceled shouldBe false

            job.cancel()
            runCurrent()

            request.isCanceled shouldBe true
        }

    @Test
    fun `a request queued after visitCount was canceled is canceled at once`() =
        runTest {
            lateinit var providerQueue: VolleyQueue
            val provider = TestProvider(visitCount = { queue, _ -> providerQueue = queue })
            val job = launch { client.visitCount(url(provider)) }
            runCurrent()
            job.cancel()
            runCurrent()
            val late = StringRequest(Request.Method.GET, "https://test.example/late", {}, {})

            providerQueue.add(late)

            late.isCanceled shouldBe true
            queue.added.single() shouldBe late
        }
}
