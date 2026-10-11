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
import com.android.volley.NetworkResponse
import com.android.volley.NoConnectionError
import com.android.volley.VolleyError
import de.lemke.oneurl.data.FakeVolleyQueue
import de.lemke.oneurl.data.deliverResponse
import io.kotest.matchers.shouldBe
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class TinyurlTest {
    private val queue = FakeVolleyQueue()
    private val longURL = "https://example.com"

    @Test
    fun `sanitizeLongURL encodes ampersands and trims`() {
        Tinyurl.sanitizeLongURL(" https://example.com?a=1&b=2 ") shouldBe "https://example.com?a=1%26b=2"
    }

    @Test
    fun `succeeds with the trimmed short url when the response starts with https`() {
        var result: String? = null
        val req = Tinyurl.getCreateRequest(queue, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse("https://tinyurl.com/abc ")

        result shouldBe "https://tinyurl.com/abc"
    }

    @Test
    fun `succeeds with the trimmed short url when the response starts with http`() {
        var result: String? = null
        val req = Tinyurl.getCreateRequest(queue, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse("http://tinyurl.com/abc ")

        result shouldBe "http://tinyurl.com/abc"
    }

    @Test
    fun `fails with Custom when the response does not look like a tinyurl link`() {
        var error: GenerateURLError? = null
        val req = Tinyurl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverResponse("Error")

        error shouldBe GenerateURLError.Custom(200, "Error")
    }

    @Test
    fun `offline error maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Tinyurl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `error with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Tinyurl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `error with a blank body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Tinyurl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(400, ByteArray(0), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(400)
    }

    @Test
    fun `error with a null body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Tinyurl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(503, null, false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(503)
    }

    @Test
    fun `422 with a blank alias maps to InvalidURL`() {
        var error: GenerateURLError? = null
        val req = Tinyurl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(422, "bad url".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.InvalidURL
    }

    @Test
    fun `400 with a non-blank alias maps to InvalidURLOrAlias`() {
        var error: GenerateURLError? = null
        val req = Tinyurl.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(400, "bad request".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.InvalidURLOrAlias
    }

    @Test
    fun `an unrecognized status code falls back to Custom`() {
        var error: GenerateURLError? = null
        val req = Tinyurl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, "server exploded".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(500, "server exploded")
    }

    @Test
    fun `isAliasValid accepts alphanumerics and underscores, rejects other characters`() {
        Tinyurl.aliasConfig.isAliasValid("abc_123") shouldBe true
        Tinyurl.aliasConfig.isAliasValid("abc-123") shouldBe false
    }

    @Test
    fun `error callback that throws once is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        var errorCallbackCount = 0
        val req =
            Tinyurl.getCreateRequest(queue, longURL, "", { fail("unexpected success") }) {
                errorCallbackCount++
                if (errorCallbackCount == 1) throw IllegalStateException("boom") else error = it
            }

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.Unknown()
    }
}
