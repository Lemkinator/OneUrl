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
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import java.time.ZonedDateTime
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Request#deliverResponse(T) is protected - only Volley's own RequestQueue can normally trigger
// it. Tests stand in for the queue, so they reach it via reflection instead of a real round-trip.
private fun Request<*>.deliverJsonResponse(response: JSONObject) {
    val method = Request::class.java.getDeclaredMethod("deliverResponse", Any::class.java)
    method.isAccessible = true
    method.invoke(this, response)
}

private fun Request<*>.parseResponse(response: NetworkResponse): Response<*> {
    val method = Request::class.java.getDeclaredMethod("parseNetworkResponse", NetworkResponse::class.java)
    method.isAccessible = true
    return method.invoke(this, response) as Response<*>
}

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SpoomeDefaultTest {
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
    fun `sanitizeLongURL adds https, encodes ampersands, and trims`() {
        Spoome.Default.sanitizeLongURL("example.com") shouldBe "https://example.com"
        Spoome.Default.sanitizeLongURL("https://example.com?a=1&b=2 ") shouldBe "https://example.com?a=1%26b=2"
    }

    @Test
    fun `create request sends the expected Accept header`() {
        val req = Spoome.Default.getCreateRequest(context, longURL, "abc", { }, { fail("unexpected error") })

        req.headers["Accept"] shouldBe "application/json"
    }

    @Test
    fun `succeeds with the trimmed short url when the response contains one`() {
        var result: String? = null
        val req = Spoome.Default.getCreateRequest(context, longURL, "abc", { result = it }, { fail("unexpected error: $it") })

        req.deliverJsonResponse(JSONObject().put("short_url", " https://spoo.me/NSPBXZ "))

        result shouldBe "https://spoo.me/NSPBXZ"
    }

    @Test
    fun `fails with Unknown when the response has no short_url`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverJsonResponse(JSONObject())

        error shouldBe GenerateURLError.Unknown(200)
    }

    @Test
    fun `offline error maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `reply that is not JSON maps to ServiceTemporarilyUnavailable`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })

        val parsed = req.parseResponse(NetworkResponse(200, "<html>maintenance</html>".toByteArray(), false, 0L, emptyList()))
        req.deliverError(parsed.error)

        error shouldBe GenerateURLError.ServiceTemporarilyUnavailable("https://spoo.me")
    }

    @Test
    fun `error with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `error with a null body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(400, null, false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(400)
    }

    @Test
    fun `error body with UrlError maps to InvalidURL`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })
        val body = """{"UrlError":"Invalid URL, URL must have a valid protocol"}"""

        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.InvalidURL
    }

    @Test
    fun `error body with an already-exists AliasError maps to AliasAlreadyExists`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })
        val body = """{"AliasError":"Alias already exists"}"""

        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.AliasAlreadyExists
    }

    @Test
    fun `error body with an invalid AliasError maps to InvalidAlias`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })
        val body = """{"AliasError":"Invalid Alias"}"""

        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.InvalidAlias
    }

    @Test
    fun `error body with an unrecognized AliasError falls back to Custom`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })
        val body = """{"AliasError":"something else entirely"}"""

        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(400, "something else entirely")
    }

    @Test
    fun `429 status with an unrecognized body maps to RateLimitExceeded`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })
        val body = """{"message":"rate limited"}"""

        req.deliverError(VolleyError(NetworkResponse(429, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.RateLimitExceeded
    }

    @Test
    fun `unrecognized error body falls back to Custom with the raw body`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })
        val body = """{"message":"server exploded"}"""

        req.deliverError(VolleyError(NetworkResponse(500, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(500, body)
    }

    @Test
    fun `unparseable error body maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(400, "not json".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `isAliasValid accepts alphanumerics and underscores, rejects other characters`() {
        Spoome.Default.aliasConfig.isAliasValid("abc_123") shouldBe true
        Spoome.Default.aliasConfig.isAliasValid("abc-123") shouldBe false
    }

    @Test
    fun `getURLClickCount resolves the click count from total-clicks`() {
        val slot = slot<JsonObjectRequest>()
        every { requestQueue.addToRequestQueue(capture(slot)) } returns Unit
        var clicks: Int? = -1
        val url = URL("https://spoo.me/abc", longURL, Spoome.Default, false, "", "", ZonedDateTime.now())

        Spoome.Default.getURLClickCount(context, url) { clicks = it }
        slot.captured.deliverJsonResponse(JSONObject().put("total-clicks", 42))

        clicks shouldBe 42
    }

    @Test
    fun `getURLClickCount resolves to null when total-clicks is missing`() {
        val slot = slot<JsonObjectRequest>()
        every { requestQueue.addToRequestQueue(capture(slot)) } returns Unit
        var clicks: Int? = -1
        val url = URL("https://spoo.me/abc", longURL, Spoome.Default, false, "", "", ZonedDateTime.now())

        Spoome.Default.getURLClickCount(context, url) { clicks = it }
        slot.captured.deliverJsonResponse(JSONObject())

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount resolves to null on error`() {
        val slot = slot<JsonObjectRequest>()
        every { requestQueue.addToRequestQueue(capture(slot)) } returns Unit
        var clicks: Int? = -1
        val url = URL("https://spoo.me/abc", longURL, Spoome.Default, false, "", "", ZonedDateTime.now())

        Spoome.Default.getURLClickCount(context, url) { clicks = it }
        slot.captured.deliverError(VolleyError("no network"))

        clicks shouldBe null
    }
}
