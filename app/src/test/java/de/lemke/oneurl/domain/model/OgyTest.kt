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
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
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
class OgyTest {
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
    fun `create request succeeds when the response contains a shorturl`() {
        var result: String? = null
        val req = Kurzelinks.Ogy.getCreateRequest(context, longURL, "abc", { result = it }, { fail("unexpected error: $it") })

        req.deliverStringResponse("""{"status":"ok","shorturl":{"url":"https://ogy.de/014q"}}""")

        result shouldBe "https://ogy.de/014q"
    }

    @Test
    fun `create request fails with Unknown when shorturl is missing from the response`() {
        var error: GenerateURLError? = null
        val req = Kurzelinks.Ogy.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverStringResponse("""{"status":"ok"}""")

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `create request fails with Unknown when the response is not valid json`() {
        var error: GenerateURLError? = null
        val req = Kurzelinks.Ogy.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverStringResponse("not json")

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `error offline maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Kurzelinks.Ogy.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `error with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Kurzelinks.Ogy.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `error with blank body maps to Unknown with status code`() {
        var error: GenerateURLError? = null
        val req = Kurzelinks.Ogy.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, ByteArray(0), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Test
    fun `error maps every known status code to its GenerateURLError`() {
        val cases =
            mapOf(
                400 to GenerateURLError.Unknown(400),
                403 to GenerateURLError.Unknown(403),
                423 to GenerateURLError.AliasAlreadyExists,
                429 to GenerateURLError.RateLimitExceeded,
                444 to GenerateURLError.ServiceTemporarilyUnavailable(Kurzelinks.Ogy.baseURL),
            )
        cases.forEach { (statusCode, expected) ->
            var error: GenerateURLError? = null
            val req = Kurzelinks.Ogy.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })

            req.deliverError(VolleyError(NetworkResponse(statusCode, "body".toByteArray(), false, 0L, emptyList())))

            error shouldBe expected
        }
    }

    @Test
    fun `error falls back to Custom for an unrecognized status code`() {
        var error: GenerateURLError? = null
        val req = Kurzelinks.Ogy.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, "server exploded".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(500, "server exploded")
    }

    @Test
    fun `alias validity follows the allowed character set`() {
        Kurzelinks.Ogy.aliasConfig.isAliasValid("abc-DEF_123") shouldBe true
        Kurzelinks.Ogy.aliasConfig.isAliasValid("abc def") shouldBe false
    }
}
