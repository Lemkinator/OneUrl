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
class OcnTest {
    private val queue = FakeVolleyQueue()
    private val longURL = "https://example.com"

    @Test
    fun `create request succeeds when the response contains a shorturl`() {
        var result: String? = null
        val req = Kurzelinks.Ocn.getCreateRequest(queue, longURL, "abc", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse("""{"status":"ok","shorturl":{"url":"https://0cn.de/014q"}}""")

        result shouldBe "https://0cn.de/014q"
    }

    @Test
    fun `create request fails with Unknown when shorturl is missing from the response`() {
        var error: GenerateURLError? = null
        val req = Kurzelinks.Ocn.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverResponse("""{"status":"ok"}""")

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `create request fails with ServiceTemporarilyUnavailable when the response is not valid json`() {
        var error: GenerateURLError? = null
        val req = Kurzelinks.Ocn.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverResponse("not json")

        error shouldBe GenerateURLError.ServiceTemporarilyUnavailable("https://0cn.de")
    }

    @Test
    fun `error offline maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Kurzelinks.Ocn.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `error with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Kurzelinks.Ocn.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `error with blank body maps to Unknown with status code`() {
        var error: GenerateURLError? = null
        val req = Kurzelinks.Ocn.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

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
                444 to GenerateURLError.ServiceTemporarilyUnavailable(Kurzelinks.Ocn.baseURL),
            )
        cases.forEach { (statusCode, expected) ->
            var error: GenerateURLError? = null
            val req = Kurzelinks.Ocn.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

            req.deliverError(VolleyError(NetworkResponse(statusCode, "body".toByteArray(), false, 0L, emptyList())))

            error shouldBe expected
        }
    }

    @Test
    fun `error falls back to Custom for an unrecognized status code`() {
        var error: GenerateURLError? = null
        val req = Kurzelinks.Ocn.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, "server exploded".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(500, "server exploded")
    }

    @Test
    fun `alias validity follows the allowed character set`() {
        Kurzelinks.Ocn.aliasConfig.isAliasValid("abc-DEF_123") shouldBe true
        Kurzelinks.Ocn.aliasConfig.isAliasValid("abc def") shouldBe false
    }
}
