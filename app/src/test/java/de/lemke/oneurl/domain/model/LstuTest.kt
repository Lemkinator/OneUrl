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
import androidx.test.core.app.ApplicationProvider
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.NoConnectionError
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import de.lemke.oneurl.R
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

private fun Request<*>.parseResponse(response: NetworkResponse): Response<*> {
    val method = Request::class.java.getDeclaredMethod("parseNetworkResponse", NetworkResponse::class.java)
    method.isAccessible = true
    return method.invoke(this, response) as Response<*>
}

// Request#getParams() is protected - only Volley's own network dispatcher normally calls it.
// Tests reach it via reflection to assert what the request actually sends.
private fun Request<*>.paramsViaReflection(): Map<*, *>? {
    val method = Request::class.java.getDeclaredMethod("getParams")
    method.isAccessible = true
    return method.invoke(this) as Map<*, *>?
}

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class LstuTest {
    private val context = mockk<Context>()
    private val realContext = ApplicationProvider.getApplicationContext<Context>()
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
    fun `alias validity follows the allowed character set`() {
        Lstu.aliasConfig.isAliasValid("abc-DEF_123") shouldBe true
        Lstu.aliasConfig.isAliasValid("abc def") shouldBe false
        Lstu.aliasConfig.isAliasValid("") shouldBe false
    }

    @Test
    fun `create request succeeds when the short url ends with the requested alias`() {
        var result: String? = null
        val req = Lstu.getCreateRequest(context, longURL, "test", { result = it }, { fail("unexpected error: $it") })

        req.deliverStringResponse("""{"success":true,"short":"https://lstu.fr/test"}""")

        result shouldBe "https://lstu.fr/test"
    }

    @Test
    fun `create request succeeds for a blank alias regardless of the returned short url`() {
        var result: String? = null
        val req = Lstu.getCreateRequest(context, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverStringResponse("""{"success":true,"short":"https://lstu.fr/random123"}""")

        result shouldBe "https://lstu.fr/random123"
    }

    @Test
    fun `create request fails with AliasAlreadyExists when the short url does not end with the alias`() {
        var error: GenerateURLError? = null
        val req = Lstu.getCreateRequest(context, longURL, "test", { fail("unexpected success") }, { error = it })

        req.deliverStringResponse("""{"success":true,"short":"https://lstu.fr/other"}""")

        error shouldBe GenerateURLError.AliasAlreadyExists
    }

    @Test
    fun `create request fails with Custom when the response reports a msg`() {
        var error: GenerateURLError? = null
        val req = Lstu.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverStringResponse("""{"msg":"example.com is not a valid URL.","success":false}""")

        error shouldBe GenerateURLError.Custom(HttpStatusCode.OK, "example.com is not a valid URL.")
    }

    @Test
    fun `create request fails with Unknown when the response has neither success nor msg`() {
        var error: GenerateURLError? = null
        val req = Lstu.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverStringResponse("""{"foo":"bar"}""")

        error shouldBe GenerateURLError.Unknown(HttpStatusCode.OK)
    }

    @Test
    fun `reply that is not JSON maps to ServiceTemporarilyUnavailable`() {
        var error: GenerateURLError? = null
        val req = Lstu.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        val parsed = req.parseResponse(NetworkResponse(200, "<html>maintenance</html>".toByteArray(), false, 0L, emptyList()))
        req.deliverStringResponse(parsed.result as String)

        error shouldBe GenerateURLError.ServiceTemporarilyUnavailable("https://lstu.fr")
    }

    @Test
    fun `create request offline error maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Lstu.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `create request with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Lstu.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `create request with a status code maps to Unknown with that code`() {
        var error: GenerateURLError? = null
        val req = Lstu.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, ByteArray(0), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Test
    fun `getURLClickCount returns the counter reported by the stats endpoint`() {
        var clicks: Int? = -1
        val innerReq = slot<StringRequest>()
        every { requestQueue.addToRequestQueue(capture(innerReq)) } returns Unit
        val url = testUrl(shortURL = "https://lstu.fr/test")

        Lstu.getURLClickCount(context, url) { clicks = it }
        innerReq.captured.deliverStringResponse("""{"counter":702,"short":"https://lstu.fr/test","success":true}""")

        clicks shouldBe 702
    }

    @Test
    fun `getURLClickCount returns null when the response is not valid json`() {
        var clicks: Int? = -1
        val innerReq = slot<StringRequest>()
        every { requestQueue.addToRequestQueue(capture(innerReq)) } returns Unit
        val url = testUrl(shortURL = "https://lstu.fr/test")

        Lstu.getURLClickCount(context, url) { clicks = it }
        innerReq.captured.deliverStringResponse("not json")

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount returns null on a network error`() {
        var clicks: Int? = -1
        val innerReq = slot<StringRequest>()
        every { requestQueue.addToRequestQueue(capture(innerReq)) } returns Unit
        val url = testUrl(shortURL = "https://lstu.fr/test")

        Lstu.getURLClickCount(context, url) { clicks = it }
        innerReq.captured.deliverError(VolleyError("no network"))

        clicks shouldBe null
    }

    @Test
    fun `sanitizeLongURL adds https and trims`() {
        Lstu.sanitizeLongURL("example.com") shouldBe "https://example.com"
        Lstu.sanitizeLongURL("https://example.com ") shouldBe "https://example.com"
    }

    @Test
    fun `getInfoContents returns the alias and analytics info`() {
        val infoContents = Lstu.getInfoContents(realContext)

        infoContents.size shouldBe 2
        infoContents[0].title shouldBe realContext.getString(R.string.alias)
        infoContents[0].linkOrDescription shouldBe
            realContext.getString(
                R.string.alias_text,
                Lstu.aliasConfig.minAliasLength,
                Lstu.aliasConfig.maxAliasLength,
                Lstu.aliasConfig.allowedAliasCharacters,
            )
        infoContents[1].title shouldBe realContext.getString(R.string.analytics)
        infoContents[1].linkOrDescription shouldBe realContext.getString(R.string.analytics_text)
    }

    @Test
    fun `create request falls through to Unknown when success is true but short is missing`() {
        var error: GenerateURLError? = null
        val req = Lstu.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverStringResponse("""{"success":true}""")

        error shouldBe GenerateURLError.Unknown(HttpStatusCode.OK)
    }

    @Test
    fun `create request with a status code and a null response body maps to Unknown with that status`() {
        var error: GenerateURLError? = null
        val req = Lstu.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, null, false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `error callback that throws once is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        var errorCallbackCount = 0
        val req =
            Lstu.getCreateRequest(context, longURL, "", { fail("unexpected success") }) {
                errorCallbackCount++
                if (errorCallbackCount == 1) throw RuntimeException("boom") else error = it
            }

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `getParams returns the lsturl, alias, and format params`() {
        val req = Lstu.getCreateRequest(context, longURL, "test", { fail("unexpected success") }, { fail("unexpected error: $it") })

        req.paramsViaReflection() shouldBe mapOf("lsturl" to longURL, "lsturl-custom" to "test", "format" to "json")
    }

    @Test
    fun `getRetryPolicy returns a policy with the request timeout and default retry settings`() {
        val req = Lstu.getCreateRequest(context, longURL, "test", { fail("unexpected success") }, { fail("unexpected error: $it") })

        val retryPolicy = req.retryPolicy

        retryPolicy.currentTimeout shouldBe 10000
        retryPolicy.currentRetryCount shouldBe 0
        (retryPolicy as DefaultRetryPolicy).backoffMultiplier shouldBe DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
    }
}
