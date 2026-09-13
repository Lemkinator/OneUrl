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
private fun Request<*>.deliverStringResponse(response: String) {
    val method = Request::class.java.getDeclaredMethod("deliverResponse", Any::class.java)
    method.isAccessible = true
    method.invoke(this, response)
}

private fun Request<*>.deliverJsonResponse(response: JSONObject) {
    val method = Request::class.java.getDeclaredMethod("deliverResponse", Any::class.java)
    method.isAccessible = true
    method.invoke(this, response)
}

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class TnyimTest {
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
    fun `succeeds when a blank alias gets a short url back`() {
        var result: String? = null
        val req = Tnyim.getCreateRequest(context, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverStringResponse("""{"shorturl":"http://tny.im/k1Ka"}""")

        result shouldBe "http://tny.im/k1Ka"
    }

    @Test
    fun `succeeds when the short url matches the requested alias`() {
        var result: String? = null
        val req = Tnyim.getCreateRequest(context, longURL, "abc12", { result = it }, { fail("unexpected error: $it") })

        req.deliverStringResponse("""{"shorturl":"https://tny.im/abc12"}""")

        result shouldBe "https://tny.im/abc12"
    }

    @Test
    fun `fails with URLExistsWithDifferentAlias when the short url does not match the requested alias`() {
        var error: GenerateURLError? = null
        val req = Tnyim.getCreateRequest(context, longURL, "abc12", { fail("unexpected success") }, { error = it })

        req.deliverStringResponse("""{"shorturl":"https://tny.im/k1Ka"}""")

        error shouldBe GenerateURLError.URLExistsWithDifferentAlias
    }

    @Test
    fun `fails with Unknown when the response has no shorturl`() {
        var error: GenerateURLError? = null
        val req = Tnyim.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverStringResponse("""{"status":"fail","code":"error:nourl"}""")

        error shouldBe GenerateURLError.Unknown(200)
    }

    @Test
    fun `fails with Unknown when the response is not valid json`() {
        var error: GenerateURLError? = null
        val req = Tnyim.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverStringResponse("not json")

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `offline error maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Tnyim.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `error with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Tnyim.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `error with a blank body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Tnyim.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(400, ByteArray(0), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(400)
    }

    @Test
    fun `500 status maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Tnyim.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, "server exploded".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Test
    fun `an unrecognized status code falls back to Custom`() {
        var error: GenerateURLError? = null
        val req = Tnyim.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(404, "not found".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(404, "not found")
    }

    @Test
    fun `isAliasValid accepts alphanumerics and hyphens, rejects other characters`() {
        Tnyim.aliasConfig.isAliasValid("abc-123") shouldBe true
        Tnyim.aliasConfig.isAliasValid("abc_123") shouldBe false
    }

    @Test
    fun `getURLClickCount resolves the click count from the link clicks field`() {
        val slot = slot<JsonObjectRequest>()
        every { requestQueue.addToRequestQueue(capture(slot)) } returns Unit
        var clicks: Int? = -1
        val url = URL("https://tny.im/abc12", longURL, Tnyim, false, "", "", ZonedDateTime.now())

        Tnyim.getURLClickCount(context, url) { clicks = it }
        slot.captured.deliverJsonResponse(JSONObject().put("link", JSONObject().put("clicks", "3")))

        clicks shouldBe 3
    }

    @Test
    fun `getURLClickCount resolves to null when the link field is missing`() {
        val slot = slot<JsonObjectRequest>()
        every { requestQueue.addToRequestQueue(capture(slot)) } returns Unit
        var clicks: Int? = -1
        val url = URL("https://tny.im/abc12", longURL, Tnyim, false, "", "", ZonedDateTime.now())

        Tnyim.getURLClickCount(context, url) { clicks = it }
        slot.captured.deliverJsonResponse(JSONObject().put("statusCode", 404))

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount resolves to null on error`() {
        val slot = slot<JsonObjectRequest>()
        every { requestQueue.addToRequestQueue(capture(slot)) } returns Unit
        var clicks: Int? = -1
        val url = URL("https://tny.im/abc12", longURL, Tnyim, false, "", "", ZonedDateTime.now())

        Tnyim.getURLClickCount(context, url) { clicks = it }
        slot.captured.deliverError(VolleyError("no network"))

        clicks shouldBe null
    }
}
