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
import com.android.volley.Response
import com.android.volley.VolleyError
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.HttpStatusCode
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import de.lemke.commonutils.R as commonutilsR

// JsonRequest#deliverResponse(T) is protected - only Volley's own RequestQueue can normally
// trigger it. Tests stand in for the queue, so they reach it via reflection instead of a real
// round-trip.
private fun Request<*>.deliverJsonResponse(response: JSONObject) {
    val method = Request::class.java.getDeclaredMethod("deliverResponse", Any::class.java)
    method.isAccessible = true
    method.invoke(this, response)
}

// Request#parseNetworkResponse is protected - tests reach it via reflection to run the request's
// real parsing on a raw reply body.
private fun Request<*>.parseResponse(response: NetworkResponse): Response<*> {
    val method = Request::class.java.getDeclaredMethod("parseNetworkResponse", NetworkResponse::class.java)
    method.isAccessible = true
    return method.invoke(this, response) as Response<*>
}

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ZwsimTest {
    private val context = mockk<Context>()
    private val realContext = ApplicationProvider.getApplicationContext<Context>()
    private val longURL = "https://example.com"

    @Test
    fun `sanitizeLongURL adds https when the scheme is missing`() {
        Zwsim.sanitizeLongURL("example.com") shouldBe "https://example.com"
    }

    @Test
    fun `getTipsCardTitleAndInfo returns the info title and zws text`() {
        val (title, info) = Zwsim.getTipsCardTitleAndInfo(realContext)

        title shouldBe realContext.getString(commonutilsR.string.commonutils_info)
        info shouldBe realContext.getString(R.string.zwsim_zws)
    }

    @Test
    fun `sanitizeLongURL trims surrounding whitespace when a scheme is already present`() {
        Zwsim.sanitizeLongURL("https://example.com  ") shouldBe "https://example.com"
    }

    @Test
    fun `create request succeeds using the short field`() {
        var result: String? = null
        val req = Zwsim.getCreateRequest(context, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverJsonResponse(JSONObject(mapOf("short" to "abc123")))

        result shouldBe "https://zws.im/abc123"
    }

    @Test
    fun `create request succeeds using the url field when short is absent`() {
        var result: String? = null
        val req = Zwsim.getCreateRequest(context, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverJsonResponse(JSONObject(mapOf("url" to "https://zws.im/abc123")))

        result shouldBe "https://zws.im/abc123"
    }

    @Test
    fun `create request fails with Unknown when the response has neither short nor url`() {
        var error: GenerateURLError? = null
        val req = Zwsim.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverJsonResponse(JSONObject(mapOf("foo" to "bar")))

        error shouldBe GenerateURLError.Unknown(HttpStatusCode.OK)
    }

    @Test
    fun `create request offline error maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Zwsim.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `create request reply that is not JSON maps to ServiceTemporarilyUnavailable`() {
        var error: GenerateURLError? = null
        val req = Zwsim.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        val parsed = req.parseResponse(NetworkResponse(200, "<html>maintenance</html>".toByteArray(), false, 0L, emptyList()))
        req.deliverError(parsed.error)

        error shouldBe GenerateURLError.ServiceTemporarilyUnavailable("https://zws.im")
    }

    @Test
    fun `create request with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Zwsim.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `create request with a blank error body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Zwsim.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, ByteArray(0), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Test
    fun `create request with a null error body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Zwsim.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, null, false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Test
    fun `create request maps 422 invalid url to InvalidURL`() {
        var error: GenerateURLError? = null
        val req = Zwsim.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })
        val body = """{"message":["url: Invalid url"],"error":"Unprocessable Entity","statusCode":422}"""

        req.deliverError(
            VolleyError(NetworkResponse(HttpStatusCode.UNPROCESSABLE_ENTITY, body.toByteArray(), false, 0L, emptyList())),
        )

        error shouldBe GenerateURLError.InvalidURL
    }

    @Test
    fun `create request maps 422 without an invalid url message to Custom`() {
        var error: GenerateURLError? = null
        val req = Zwsim.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })
        val body = """{"message":["something else"]}"""

        req.deliverError(
            VolleyError(NetworkResponse(HttpStatusCode.UNPROCESSABLE_ENTITY, body.toByteArray(), false, 0L, emptyList())),
        )

        error shouldBe GenerateURLError.Custom(HttpStatusCode.UNPROCESSABLE_ENTITY, body)
    }

    @Test
    fun `create request maps 503 to ServiceTemporarilyUnavailable`() {
        var error: GenerateURLError? = null
        val req = Zwsim.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(
            VolleyError(NetworkResponse(HttpStatusCode.SERVICE_UNAVAILABLE, "down".toByteArray(), false, 0L, emptyList())),
        )

        error shouldBe GenerateURLError.ServiceTemporarilyUnavailable(Zwsim.baseURL)
    }

    @Test
    fun `create request maps any other status code to Custom`() {
        var error: GenerateURLError? = null
        val req = Zwsim.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })
        val body = "server exploded"

        req.deliverError(VolleyError(NetworkResponse(500, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(500, body)
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `create error callback that throws once is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        var errorCallbackCount = 0
        val req =
            Zwsim.getCreateRequest(context, longURL, "", { fail("unexpected success") }) {
                errorCallbackCount++
                if (errorCallbackCount == 1) throw RuntimeException("boom") else error = it
            }

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.Unknown()
    }
}
