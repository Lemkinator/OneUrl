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
private fun Request<*>.deliverJsonResponse(response: JSONObject) {
    val method = Request::class.java.getDeclaredMethod("deliverResponse", Any::class.java)
    method.isAccessible = true
    method.invoke(this, response)
}

// VolleyError has no constructor taking both a NetworkResponse and a message/cause - real Volley
// (e.g. a ParseError from malformed JSON) can still surface both, so tests reach it via reflection.
private fun volleyErrorWithMessage(
    response: NetworkResponse,
    message: String,
): VolleyError {
    val error = VolleyError(response)
    val field = Throwable::class.java.getDeclaredField("detailMessage")
    field.isAccessible = true
    field.set(error, message)
    return error
}

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class IsgdTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
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
    fun `succeeds when the response contains shorturl`() {
        var result: String? = null
        val req = VgdIsgd.Isgd.getCreateRequest(context, longURL, "abc", { result = it }, { fail("unexpected error: $it") })

        req.deliverJsonResponse(JSONObject("""{"shorturl":"https://is.gd/abc"}"""))

        result shouldBe "https://is.gd/abc"
    }

    @Test
    fun `request url omits the shorturl param when alias is blank`() {
        val req = VgdIsgd.Isgd.getCreateRequest(context, longURL, "", { }, { fail("unexpected error") })

        req.url.contains("&shorturl=") shouldBe false
    }

    @Test
    fun `request url includes shorturl and logstats params when alias is given`() {
        val req = VgdIsgd.Isgd.getCreateRequest(context, longURL, "abc", { }, { fail("unexpected error") })

        req.url.contains("&shorturl=abc&logstats=1") shouldBe true
    }

    @Test
    fun `fails with Unknown when the response has neither errorcode nor shorturl`() {
        var error: GenerateURLError? = null
        val req = VgdIsgd.Isgd.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverJsonResponse(JSONObject("""{"foo":"bar"}"""))

        error shouldBe GenerateURLError.Unknown(HttpStatusCode.OK)
    }

    @Test
    fun `maps every known errorcode to its GenerateURLError`() {
        val cases =
            listOf(
                """{"errorcode":"1","errormessage":"URL is on our internal blacklist"}""" to GenerateURLError.BlacklistedURL(),
                """{"errorcode":"1","errormessage":"Please enter a valid URL to shorten."}""" to GenerateURLError.InvalidURL,
                """{"errorcode":"2","errormessage":"already exists"}""" to GenerateURLError.AliasAlreadyExists,
                """{"errorcode":"3","errormessage":"rate limit"}""" to GenerateURLError.RateLimitExceeded,
                """{"errorcode":"4","errormessage":"maintenance"}""" to
                    GenerateURLError.ServiceTemporarilyUnavailable(
                        VgdIsgd.Isgd.baseURL,
                    ),
                """{"errorcode":"9","errormessage":"weird error"}""" to GenerateURLError.Custom(HttpStatusCode.OK, "weird error (9)"),
            )
        cases.forEach { (json, expected) ->
            var error: GenerateURLError? = null
            val req = VgdIsgd.Isgd.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

            req.deliverJsonResponse(JSONObject(json))

            error shouldBe expected
        }
    }

    @Test
    fun `error ServiceOffline on NoConnectionError`() {
        var error: GenerateURLError? = null
        val req = VgdIsgd.Isgd.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `error Unknown when there is no status code`() {
        var error: GenerateURLError? = null
        val req = VgdIsgd.Isgd.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `error Unknown with status code when the error body is blank`() {
        var error: GenerateURLError? = null
        val req = VgdIsgd.Isgd.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, ByteArray(0), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Test
    fun `error Unknown with status code when the error body is null`() {
        var error: GenerateURLError? = null
        val req = VgdIsgd.Isgd.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(404, null, false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(404)
    }

    @Test
    fun `error Custom with a localized message on JSONException`() {
        var error: GenerateURLError? = null
        val req = VgdIsgd.Isgd.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(
            volleyErrorWithMessage(
                NetworkResponse(500, "some data".toByteArray(), false, 0L, emptyList()),
                "org.json.JSONException: Unterminated object",
            ),
        )

        error shouldBe GenerateURLError.Custom(500, context.getString(R.string.error_vgd_isgd))
    }

    @Test
    fun `error Custom with the raw body otherwise`() {
        var error: GenerateURLError? = null
        val req = VgdIsgd.Isgd.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, "server exploded".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(500, "server exploded")
    }

    @Test
    fun `error Custom with the raw body when the message does not mention JSONException`() {
        var error: GenerateURLError? = null
        val req = VgdIsgd.Isgd.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(
            volleyErrorWithMessage(
                NetworkResponse(500, "server exploded".toByteArray(), false, 0L, emptyList()),
                "org.json.JSONArray: some other message",
            ),
        )

        error shouldBe GenerateURLError.Custom(500, "server exploded")
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `error callback that throws once is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        var errorCallbackCount = 0
        val req =
            VgdIsgd.Isgd.getCreateRequest(context, longURL, "", { fail("unexpected success") }) {
                errorCallbackCount++
                if (errorCallbackCount == 1) throw RuntimeException("boom") else error = it
            }

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `isAliasValid accepts letters digits and underscore and rejects other characters`() {
        VgdIsgd.Isgd.aliasConfig.isAliasValid("abc_123") shouldBe true
        VgdIsgd.Isgd.aliasConfig.isAliasValid("abc-123") shouldBe false
        VgdIsgd.Isgd.aliasConfig.isAliasValid("abc 123") shouldBe false
    }

    @Test
    fun `sanitizeLongURL trims whitespace and encodes ampersands`() {
        VgdIsgd.Isgd.sanitizeLongURL(" https://example.com?a=1&b=2 ") shouldBe "https://example.com?a=1%26b=2"
    }

    @Test
    fun `getAnalyticsURL builds a stats url on the is_gd domain`() {
        VgdIsgd.Isgd.getAnalyticsURL("abc") shouldBe "https://is.gd/stats.php?url=abc"
    }
}
