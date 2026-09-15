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
import android.content.Context
import com.android.volley.NetworkResponse
import com.android.volley.NoConnectionError
import com.android.volley.Request
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.HttpStatusCode
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import de.lemke.oneurl.domain.testUrl
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Request#deliverResponse(T) is protected - only Volley's own RequestQueue can normally trigger
// it. Tests stand in for the queue, so they reach it via reflection instead of a real round-trip.
private fun Request<*>.deliverStringResponse(response: String) {
    val method = Request::class.java.getDeclaredMethod("deliverResponse", Any::class.java)
    method.isAccessible = true
    method.invoke(this, response)
}

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class MurlTest {
    private val context = mockk<Context>()
    private val requestQueue = mockk<RequestQueueSingleton>(relaxed = true)
    private val longURL = "https://example.com"

    @Before
    fun setup() {
        mockkObject(RequestQueueSingleton.Companion)
        every { RequestQueueSingleton.getInstance(context) } returns requestQueue
    }

    @After
    fun tearDown() {
        unmockkObject(RequestQueueSingleton.Companion)
    }

    @Test
    fun `create request succeeds when the response is a murl short url`() {
        var result: String? = null
        val req = Murl.getCreateRequest(context, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverStringResponse("https://murl.com/abc123")

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
            val req = Murl.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

            req.deliverStringResponse(response)

            error shouldBe expected
        }
    }

    @Test
    fun `create request falls back to Unknown for an unrecognized response`() {
        var error: GenerateURLError? = null
        val req = Murl.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverStringResponse("something unexpected")

        error shouldBe GenerateURLError.Unknown(HttpStatusCode.OK)
    }

    @Test
    fun `create request offline error maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Murl.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `create request with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Murl.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `create request maps service unavailable to ServiceTemporarilyUnavailable`() {
        var error: GenerateURLError? = null
        val req = Murl.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(503, "body".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.ServiceTemporarilyUnavailable(Murl.baseURL)
    }

    @Test
    fun `create request with a blank error body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Murl.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(400, ByteArray(0), false, 0L, emptyList())))

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
            val req = Murl.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

            req.deliverError(VolleyError(NetworkResponse(400, message.toByteArray(), false, 0L, emptyList())))

            error shouldBe expected
        }
    }

    @Test
    fun `create request falls back to Custom for an unrecognized error message`() {
        var error: GenerateURLError? = null
        val req = Murl.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(400, "server exploded".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(400, "server exploded")
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `create error callback that throws once is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        var errorCallbackCount = 0
        val req =
            Murl.getCreateRequest(context, longURL, "", { fail("unexpected success") }) {
                errorCallbackCount++
                if (errorCallbackCount == 1) throw RuntimeException("boom") else error = it
            }

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `getURLClickCount parses the view count out of the html response`() {
        var clicks: Int? = -1
        val innerReq = slot<StringRequest>()
        every { requestQueue.addToRequestQueue(capture(innerReq)) } returns Unit
        val url = testUrl(shortURL = "https://murl.com/abc123")

        Murl.getURLClickCount(context, url) { clicks = it }
        innerReq.captured.deliverStringResponse("""<span class="mu-mc count-480357" title="Views">7</span>""")

        clicks shouldBe 7
    }

    @Test
    fun `getURLClickCount returns null when the expected marker is missing`() {
        var clicks: Int? = -1
        val innerReq = slot<StringRequest>()
        every { requestQueue.addToRequestQueue(capture(innerReq)) } returns Unit
        val url = testUrl(shortURL = "https://murl.com/abc123")

        Murl.getURLClickCount(context, url) { clicks = it }
        innerReq.captured.deliverStringResponse("no marker here")

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount returns null on a network error`() {
        var clicks: Int? = -1
        val innerReq = slot<StringRequest>()
        every { requestQueue.addToRequestQueue(capture(innerReq)) } returns Unit
        val url = testUrl(shortURL = "https://murl.com/abc123")

        Murl.getURLClickCount(context, url) { clicks = it }
        innerReq.captured.deliverError(VolleyError("no network"))

        clicks shouldBe null
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `getURLClickCount callback that throws once is caught and resolves to null`() {
        val innerReq = slot<StringRequest>()
        every { requestQueue.addToRequestQueue(capture(innerReq)) } returns Unit
        var callbackCount = 0
        var clicks: Int? = -1
        val url = testUrl(shortURL = "https://murl.com/abc123")

        Murl.getURLClickCount(context, url) {
            callbackCount++
            if (callbackCount == 1) throw RuntimeException("boom") else clicks = it
        }
        innerReq.captured.deliverStringResponse("""<span class="mu-mc count-480357" title="Views">7</span>""")

        clicks shouldBe null
    }
}
