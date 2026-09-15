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
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// JsonRequest#deliverResponse(T) is protected - only Volley's own RequestQueue can normally
// trigger it. Tests stand in for the queue, so they reach it via reflection instead of a real
// round-trip.
private fun Request<*>.deliverJsonResponse(response: JSONObject) {
    val method = Request::class.java.getDeclaredMethod("deliverResponse", Any::class.java)
    method.isAccessible = true
    method.invoke(this, response)
}

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ShareaholicTest {
    private val context = mockk<Context>()
    private val longURL = "https://example.com"

    @Test
    fun `sanitizeLongURL percent-encodes ampersands and trims`() {
        Shareaholic.sanitizeLongURL(" https://example.com?a=1&b=2 ") shouldBe "https://example.com?a=1%26b=2"
    }

    @Test
    fun `create request succeeds when the response has a data field`() {
        var result: String? = null
        val req = Shareaholic.getCreateRequest(context, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverJsonResponse(JSONObject(mapOf("status_code" to "200", "data" to " https://go.shr.lc/abc ")))

        result shouldBe "https://go.shr.lc/abc"
    }

    @Test
    fun `create request fails with Unknown when the response has no data field`() {
        var error: GenerateURLError? = null
        val req = Shareaholic.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverJsonResponse(JSONObject(mapOf("status_code" to "200")))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `create request offline error maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Shareaholic.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `create request with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Shareaholic.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `create request with a null error body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Shareaholic.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, null, false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Test
    fun `create request with unparsable error body maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Shareaholic.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, "not json".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `create request with a json error body but no errors field maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Shareaholic.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(400, """{"foo":"bar"}""".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(400)
    }

    @Test
    fun `create request maps every known api error code to its GenerateURLError`() {
        val cases =
            mapOf(
                "100" to GenerateURLError.Unknown(1100),
                "101" to GenerateURLError.Unknown(1101),
                "140" to GenerateURLError.Unknown(1140),
                "141" to GenerateURLError.InvalidURL,
                "145" to GenerateURLError.InvalidURL,
                "429" to GenerateURLError.RateLimitExceeded,
            )
        cases.forEach { (code, expected) ->
            var error: GenerateURLError? = null
            val req = Shareaholic.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })
            val body = """{"errors":[{"code":"$code"}]}"""

            req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

            error shouldBe expected
        }
    }

    @Test
    fun `create request with an unrecognized api error code and a detail maps to Custom`() {
        var error: GenerateURLError? = null
        val req = Shareaholic.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })
        val body = """{"errors":[{"code":"999","detail":"something broke"}]}"""

        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(400, "something broke")
    }

    @Test
    fun `create request with an unrecognized api error code and no detail maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Shareaholic.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })
        val body = """{"errors":[{"code":"999"}]}"""

        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(400)
    }

    @Test
    fun `create request with an empty errors array maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Shareaholic.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })
        val body = """{"errors":[]}"""

        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(400)
    }

    @Test
    fun `create request with an errors field that is not a json array maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Shareaholic.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })
        val body = """{"errors":"not an array"}"""

        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(400)
    }
}
