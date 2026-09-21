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
import com.android.volley.NetworkResponse
import com.android.volley.NoConnectionError
import com.android.volley.Request
import com.android.volley.VolleyError
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.HttpStatusCode
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
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

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class L4fTest {
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
    fun `blank alias succeeds when the response has no error`() {
        var result: String? = null
        val req = L4f.getCreateRequest(context, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverJSONResponse(
            JSONObject("""{"error":false,"message":"Link has been shortened","data":{"id":7498,"shorturl":"https://l4f.com/xyz"}}"""),
        )

        result shouldBe "https://l4f.com/xyz"
    }

    @Test
    fun `alias succeeds when the returned short url matches the requested alias`() {
        var result: String? = null
        val req = L4f.getCreateRequest(context, longURL, "asdf", { result = it }, { fail("unexpected error: $it") })

        req.deliverJSONResponse(
            JSONObject("""{"error":false,"message":"Link has been shortened","data":{"id":7498,"shorturl":"https://l4f.com/asdf"}}"""),
        )

        result shouldBe "https://l4f.com/asdf"
    }

    @Test
    fun `fails with URLExistsWithDifferentAlias when the returned short url does not match the requested alias`() {
        var error: GenerateURLError? = null
        val req = L4f.getCreateRequest(context, longURL, "asdf", { fail("unexpected success") }, { error = it })

        req.deliverJSONResponse(
            JSONObject("""{"error":false,"message":"Link has been shortened","data":{"id":7498,"shorturl":"https://l4f.com/other"}}"""),
        )

        error shouldBe GenerateURLError.URLExistsWithDifferentAlias
    }

    @Test
    fun `response maps every known error message to its GenerateURLError`() {
        val cases =
            mapOf(
                "That alias is taken. Please choose another one." to GenerateURLError.AliasAlreadyExists,
                "Inappropriate aliases are not allowed." to GenerateURLError.InvalidAlias,
                "Please enter a valid URL." to GenerateURLError.InvalidURL,
                "Too Many Requests. Please retry later." to GenerateURLError.RateLimitExceeded,
            )
        cases.forEach { (message, expected) ->
            var error: GenerateURLError? = null
            val req = L4f.getCreateRequest(context, longURL, "asdf", { fail("unexpected success") }, { error = it })

            req.deliverJSONResponse(JSONObject().put("error", true).put("message", message))

            error shouldBe expected
        }
    }

    @Test
    fun `response falls back to Custom for an unrecognized error message`() {
        var error: GenerateURLError? = null
        val req = L4f.getCreateRequest(context, longURL, "asdf", { fail("unexpected success") }, { error = it })

        req.deliverJSONResponse(JSONObject().put("error", true).put("message", "something unexpected happened"))

        error shouldBe GenerateURLError.Custom(HttpStatusCode.OK, "something unexpected happened")
    }

    @Test
    fun `network error offline maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = L4f.getCreateRequest(context, longURL, "asdf", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `network error with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = L4f.getCreateRequest(context, longURL, "asdf", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `network error with a status code maps to Unknown with that status code`() {
        var error: GenerateURLError? = null
        val req = L4f.getCreateRequest(context, longURL, "asdf", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, "server exploded".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Test
    fun `network error with a null response body maps to Unknown with that status code`() {
        var error: GenerateURLError? = null
        val req = L4f.getCreateRequest(context, longURL, "asdf", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, null, false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Test
    fun `response with no error and no shorturl falls back to Custom`() {
        var error: GenerateURLError? = null
        val req = L4f.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverJSONResponse(JSONObject("""{"error":false,"message":"unexpected shape"}"""))

        error shouldBe GenerateURLError.Custom(HttpStatusCode.OK, "unexpected shape")
    }

    @Test
    fun `sanitizeLongURL trims and url-encodes ampersands`() {
        L4f.sanitizeLongURL(" https://example.com?a=1&b=2 ") shouldBe "https://example.com?a=1%26b=2"
    }

    @Test
    fun `alias validity follows the allowed character set`() {
        L4f.aliasConfig.isAliasValid("abc123") shouldBe true
        L4f.aliasConfig.isAliasValid("abc-123") shouldBe false
    }

    @Test
    fun `getInfoContents returns the alias info`() {
        val infoContents = L4f.getInfoContents(realContext)

        infoContents.size shouldBe 1
        infoContents[0].title shouldBe realContext.getString(R.string.alias)
        infoContents[0].linkOrDescription shouldBe
            realContext.getString(
                R.string.alias_text,
                L4f.aliasConfig.minAliasLength,
                L4f.aliasConfig.maxAliasLength,
                L4f.aliasConfig.allowedAliasCharacters,
            )
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `response success callback that throws is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        val req = L4f.getCreateRequest(context, longURL, "", { throw RuntimeException("boom") }, { error = it })

        req.deliverJSONResponse(
            JSONObject("""{"error":false,"message":"Link has been shortened","data":{"id":7498,"shorturl":"https://l4f.com/xyz"}}"""),
        )

        error shouldBe GenerateURLError.Unknown(HttpStatusCode.OK)
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `network error callback that throws once is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        var errorCallbackCount = 0
        val req =
            L4f.getCreateRequest(context, longURL, "asdf", { fail("unexpected success") }) {
                errorCallbackCount++
                if (errorCallbackCount == 1) throw RuntimeException("boom") else error = it
            }

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.Unknown()
    }
}
