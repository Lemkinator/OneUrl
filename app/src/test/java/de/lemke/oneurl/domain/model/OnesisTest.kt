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
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.Assert.fail
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
class OnesisTest {
    private val context = mockk<Context>()
    private val longURL = "https://example.com"

    @Test
    fun `isAliasValid accepts lowercase letters and digits only`() {
        Onesis.aliasConfig.isAliasValid("abc123").shouldBeTrue()
    }

    @Test
    fun `isAliasValid rejects uppercase, hyphens, and blank`() {
        Onesis.aliasConfig.isAliasValid("Abc123").shouldBeFalse()
        Onesis.aliasConfig.isAliasValid("abc-123").shouldBeFalse()
        Onesis.aliasConfig.isAliasValid("").shouldBeFalse()
    }

    @Test
    fun `blank alias succeeds when the response contains the short url span`() {
        var result: String? = null
        val req = Onesis.getCreateRequest(context, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverStringResponse(
            """<span id="shortlink-url" style="color: #007bff; font-weight: bold;">https://1s.is/K5F8WO</span>""",
        )

        result shouldBe "https://1s.is/K5F8WO"
    }

    @Test
    fun `matching custom alias succeeds`() {
        var result: String? = null
        val req = Onesis.getCreateRequest(context, longURL, "K5F8WO", { result = it }, { fail("unexpected error: $it") })

        req.deliverStringResponse(
            """<span id="shortlink-url" style="color: #007bff; font-weight: bold;">https://1s.is/K5F8WO</span>""",
        )

        result shouldBe "https://1s.is/K5F8WO"
    }

    @Test
    fun `differing custom alias fails with URLExistsWithDifferentAlias`() {
        var error: GenerateURLError? = null
        val req = Onesis.getCreateRequest(context, longURL, "custom", { fail("unexpected success") }, { error = it })

        req.deliverStringResponse(
            """<span id="shortlink-url" style="color: #007bff; font-weight: bold;">https://1s.is/K5F8WO</span>""",
        )

        error shouldBe GenerateURLError.URLExistsWithDifferentAlias
    }

    @Test
    fun `alias already taken maps to AliasAlreadyExists`() {
        var error: GenerateURLError? = null
        val req = Onesis.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverStringResponse("Short URL already exists. Please choose another one.")

        error shouldBe GenerateURLError.AliasAlreadyExists
    }

    @Test
    fun `invalid alias format maps to InvalidAlias`() {
        var error: GenerateURLError? = null
        val req = Onesis.getCreateRequest(context, longURL, "a b", { fail("unexpected success") }, { error = it })

        req.deliverStringResponse("The custom short URL must follow the correct format: no spaces, no accents.")

        error shouldBe GenerateURLError.InvalidAlias
    }

    @Test
    fun `unrecognized response body maps to Unknown 200`() {
        var error: GenerateURLError? = null
        val req = Onesis.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverStringResponse("something unexpected")

        error shouldBe GenerateURLError.Unknown(200)
    }

    @Test
    fun `create request maps every known network error condition to its GenerateURLError`() {
        val cases =
            listOf<Pair<VolleyError, GenerateURLError>>(
                NoConnectionError() to GenerateURLError.ServiceOffline,
                VolleyError("no network") to GenerateURLError.Unknown(),
                VolleyError(NetworkResponse(503, ByteArray(0), false, 0L, emptyList())) to
                    GenerateURLError.ServiceTemporarilyUnavailable(Onesis.baseURL),
                VolleyError(NetworkResponse(500, ByteArray(0), false, 0L, emptyList())) to GenerateURLError.Unknown(500),
            )
        cases.forEach { (volleyError, expected) ->
            var error: GenerateURLError? = null
            val req = Onesis.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

            req.deliverError(volleyError)

            error shouldBe expected
        }
    }
}
