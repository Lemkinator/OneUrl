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

package de.lemke.oneurl.domain.model

import android.app.Application
import com.android.volley.NetworkResponse
import com.android.volley.NoConnectionError
import com.android.volley.VolleyError
import de.lemke.oneurl.data.FakeVolleyQueue
import de.lemke.oneurl.data.deliverResponse
import de.lemke.oneurl.domain.testUrl
import io.kotest.matchers.shouldBe
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class MurlTest {
    private val queue = FakeVolleyQueue()
    private val longURL = "https://example.com"

    @Test
    fun `create request succeeds when the response is a murl short url`() {
        var result: String? = null
        val req = Murl.getCreateRequest(queue, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse("https://murl.com/abc123")

        result shouldBe "https://murl.com/abc123"
    }

    @Test
    fun `create request maps every known error phrase to its GenerateURLError`() {
        val cases =
            mapOf(
                "Invalid URL." to GenerateURLError.InvalidURL,
                "URL is too long." to GenerateURLError.InvalidURL,
                "You are adding URLs too fast." to GenerateURLError.RateLimitExceeded,
                "please slow down." to GenerateURLError.RateLimitExceeded,
            )
        cases.forEach { (response, expected) ->
            var error: GenerateURLError? = null
            val req = Murl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

            req.deliverResponse(response)

            error shouldBe expected
        }
    }

    @Test
    fun `create request maps too many requests to RateLimitExceeded`() {
        var error: GenerateURLError? = null
        val req = Murl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(429, "<html>".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.RateLimitExceeded
    }

    @Test
    fun `create request passes murl's not-responding text on as a Custom error`() {
        var error: GenerateURLError? = null
        val req = Murl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverResponse("URL is not responding")

        error shouldBe GenerateURLError.Custom(HttpStatusCode.OK, "URL is not responding")
    }

    @Test
    fun `create request passes an unrecognized response on as a Custom error`() {
        var error: GenerateURLError? = null
        val req = Murl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverResponse("something unexpected")

        error shouldBe GenerateURLError.Custom(HttpStatusCode.OK, "something unexpected")
    }

    @Test
    fun `create request passes an unrecognized HTML response on unchanged`() {
        var error: GenerateURLError? = null
        val req = Murl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverResponse("<html><body>Maintenance</body></html>")

        error shouldBe GenerateURLError.Custom(HttpStatusCode.OK, "<html><body>Maintenance</body></html>")
    }

    @Test
    fun `create request passes an empty response on as a Custom error`() {
        var error: GenerateURLError? = null
        val req = Murl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverResponse("")

        error shouldBe GenerateURLError.Custom(HttpStatusCode.OK, "")
    }

    @Test
    fun `create request offline error maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Murl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `create request with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Murl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `create request maps service unavailable to ServiceTemporarilyUnavailable`() {
        var error: GenerateURLError? = null
        val req = Murl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(503, "body".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.ServiceTemporarilyUnavailable(Murl.baseURL)
    }

    @Test
    fun `create request with a blank error body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Murl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(400, ByteArray(0), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(400)
    }

    @Test
    fun `create request with a null error body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Murl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(400, null, false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(400)
    }

    @Test
    fun `create request maps every known error message to its GenerateURLError`() {
        val cases =
            mapOf(
                "Long URL cannot be empty" to GenerateURLError.InvalidURL,
                "Long URL must have http:// or https:// scheme." to GenerateURLError.InvalidURL,
                "Long URL is not a valid URL." to GenerateURLError.InvalidURL,
                "Short URL already taken. Pick a different one." to GenerateURLError.AliasAlreadyExists,
            )
        cases.forEach { (message, expected) ->
            var error: GenerateURLError? = null
            val req = Murl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

            req.deliverError(VolleyError(NetworkResponse(400, message.toByteArray(), false, 0L, emptyList())))

            error shouldBe expected
        }
    }

    @Test
    fun `create request falls back to Custom for an unrecognized error message`() {
        var error: GenerateURLError? = null
        val req = Murl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(400, "server exploded".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(400, "server exploded")
    }

    @Test
    fun `create error callback that throws once is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        var errorCallbackCount = 0
        val req =
            Murl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }) {
                errorCallbackCount++
                if (errorCallbackCount == 1) throw IllegalStateException("boom") else error = it
            }

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `getURLClickCount parses the view count out of the html response`() {
        var clicks: Int? = -1
        val url = testUrl(shortURL = "https://murl.com/abc123")

        Murl.getURLClickCount(queue, url) { clicks = it }
        queue.added.single().deliverResponse("""<span class="mu-mc count-480357" title="Views">7</span>""")

        clicks shouldBe 7
    }

    @Test
    fun `getURLClickCount returns null when the expected marker is missing`() {
        var clicks: Int? = -1
        val url = testUrl(shortURL = "https://murl.com/abc123")

        Murl.getURLClickCount(queue, url) { clicks = it }
        queue.added.single().deliverResponse("no marker here")

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount returns null on a network error`() {
        var clicks: Int? = -1
        val url = testUrl(shortURL = "https://murl.com/abc123")

        Murl.getURLClickCount(queue, url) { clicks = it }
        queue.added.single().deliverError(VolleyError("no network"))

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount callback that throws once is caught and resolves to null`() {
        var callbackCount = 0
        var clicks: Int? = -1
        val url = testUrl(shortURL = "https://murl.com/abc123")

        Murl.getURLClickCount(queue, url) {
            callbackCount++
            if (callbackCount == 1) throw IllegalStateException("boom") else clicks = it
        }
        queue.added.single().deliverResponse("""<span class="mu-mc count-480357" title="Views">7</span>""")

        clicks shouldBe null
    }
}
