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
import com.android.volley.toolbox.JsonObjectRequest
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
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
private fun Request<*>.deliverJSONResponse(response: JSONObject) {
    val method = Request::class.java.getDeclaredMethod("deliverResponse", Any::class.java)
    method.isAccessible = true
    method.invoke(this, response)
}

private fun testURL(shortURL: String) =
    URL(
        shortURL = shortURL,
        longURL = "https://example.com",
        shortURLProvider = Ulvis,
        favorite = false,
        title = "title",
        description = "description",
        added = ZonedDateTime.now(),
    )

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class UlvisTest {
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
    fun `isAliasValid accepts letters and digits and rejects everything else`() {
        Ulvis.aliasConfig.isAliasValid("Example12").shouldBeTrue()
        Ulvis.aliasConfig.isAliasValid("example-12").shouldBeFalse()
        Ulvis.aliasConfig.isAliasValid("").shouldBeFalse()
    }

    @Test
    fun `create request succeeds with the short url from the response`() {
        var result: String? = null
        val req = Ulvis.getCreateRequest(context, longURL, "example12", { result = it }, { fail("unexpected error: $it") })

        req.deliverJSONResponse(JSONObject().put("data", JSONObject().put("url", " https://ulvis.net/example12 ")))

        result shouldBe "https://ulvis.net/example12"
    }

    @Test
    fun `create request custom-taken status maps to AliasAlreadyExists`() {
        var error: GenerateURLError? = null
        val req = Ulvis.getCreateRequest(context, longURL, "example12", { fail("unexpected success") }, { error = it })

        req.deliverJSONResponse(JSONObject().put("data", JSONObject().put("status", "custom-taken")))

        error shouldBe GenerateURLError.AliasAlreadyExists
    }

    @Test
    fun `create request maps every known error code to its GenerateURLError`() {
        val cases =
            mapOf(
                0 to GenerateURLError.DomainNotAllowed,
                1 to GenerateURLError.InvalidURL,
                2 to GenerateURLError.InvalidAlias,
            )
        cases.forEach { (code, expected) ->
            var error: GenerateURLError? = null
            val req = Ulvis.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

            req.deliverJSONResponse(JSONObject().put("error", JSONObject().put("code", code).put("msg", "some message")))

            error shouldBe expected
        }
    }

    @Test
    fun `create request falls back to Custom when the error code is unrecognized but a message is present`() {
        var error: GenerateURLError? = null
        val req = Ulvis.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverJSONResponse(JSONObject().put("error", JSONObject().put("code", 5).put("msg", "custom name too long")))

        error shouldBe GenerateURLError.Custom(5, "custom name too long")
    }

    @Test
    fun `create request falls back to Unknown when the error code is unrecognized and no message is present`() {
        var error: GenerateURLError? = null
        val req = Ulvis.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverJSONResponse(JSONObject().put("error", JSONObject().put("code", 5)))

        error shouldBe GenerateURLError.Unknown(200)
    }

    @Test
    fun `create request falls back to Unknown when the response has no data and no error`() {
        var error: GenerateURLError? = null
        val req = Ulvis.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverJSONResponse(JSONObject())

        error shouldBe GenerateURLError.Unknown(200)
    }

    @Test
    fun `create request falls back to Unknown when the error object has no code`() {
        var error: GenerateURLError? = null
        val req = Ulvis.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverJSONResponse(JSONObject().put("error", JSONObject().put("msg", "something went wrong")))

        error shouldBe GenerateURLError.Unknown(200)
    }

    @Test
    fun `create request falls back to Unknown when the error code cannot be parsed as a number`() {
        var error: GenerateURLError? = null
        val req = Ulvis.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverJSONResponse(JSONObject().put("error", JSONObject().put("code", "not-a-number")))

        error shouldBe GenerateURLError.Unknown(200)
    }

    @Test
    fun `create request maps every known network error condition to its GenerateURLError`() {
        val cases =
            listOf<Pair<VolleyError, GenerateURLError>>(
                NoConnectionError() to GenerateURLError.ServiceOffline,
                VolleyError("no network") to GenerateURLError.Unknown(),
                VolleyError(NetworkResponse(403, ByteArray(0), false, 0L, emptyList())) to
                    GenerateURLError.ServiceTemporarilyUnavailable(Ulvis.baseURL),
                VolleyError(NetworkResponse(500, ByteArray(0), false, 0L, emptyList())) to GenerateURLError.Unknown(500),
                VolleyError(NetworkResponse(400, "custom name must be less than 60 chars".toByteArray(), false, 0L, emptyList())) to
                    GenerateURLError.Custom(400, "custom name must be less than 60 chars"),
            )
        cases.forEach { (volleyError, expected) ->
            var error: GenerateURLError? = null
            val req = Ulvis.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

            req.deliverError(volleyError)

            error shouldBe expected
        }
    }

    @Test
    fun `getURLClickCount returns the hit count`() {
        var clicks: Int? = -1
        val slot = slot<JsonObjectRequest>()
        every { requestQueue.addToRequestQueue(capture(slot)) } returns Unit

        Ulvis.getURLClickCount(context, testURL("https://ulvis.net/example1")) { clicks = it }
        slot.captured.deliverJSONResponse(JSONObject().put("data", JSONObject().put("hits", "5")))

        clicks shouldBe 5
    }

    @Test
    fun `getURLClickCount returns null when the response has no data object`() {
        var clicks: Int? = -1
        val slot = slot<JsonObjectRequest>()
        every { requestQueue.addToRequestQueue(capture(slot)) } returns Unit

        Ulvis.getURLClickCount(context, testURL("https://ulvis.net/example1")) { clicks = it }
        slot.captured.deliverJSONResponse(JSONObject())

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount returns null on a network error`() {
        var clicks: Int? = -1
        val slot = slot<JsonObjectRequest>()
        every { requestQueue.addToRequestQueue(capture(slot)) } returns Unit

        Ulvis.getURLClickCount(context, testURL("https://ulvis.net/example1")) { clicks = it }
        slot.captured.deliverError(VolleyError("no network"))

        clicks shouldBe null
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `create request error callback that throws once is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        var errorCallbackCount = 0
        val req =
            Ulvis.getCreateRequest(context, longURL, "", { fail("unexpected success") }) {
                errorCallbackCount++
                if (errorCallbackCount == 1) throw RuntimeException("boom") else error = it
            }

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }
}
