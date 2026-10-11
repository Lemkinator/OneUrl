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
import com.android.volley.VolleyError
import de.lemke.oneurl.R
import de.lemke.oneurl.data.FakeVolleyQueue
import de.lemke.oneurl.data.deliverResponse
import de.lemke.oneurl.data.params
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
class GgTest {
    private val realContext = ApplicationProvider.getApplicationContext<Context>()
    private val queue = FakeVolleyQueue()
    private val longURL = "https://example.com"

    @Test
    fun `alias validity follows the allowed character set`() {
        Gg.aliasConfig.isAliasValid("abc-DEF_123") shouldBe true
        Gg.aliasConfig.isAliasValid("abc def") shouldBe false
        Gg.aliasConfig.isAliasValid("") shouldBe false
    }

    @Test
    fun `check response reporting a duplicate path fails with AliasAlreadyExists`() {
        var error: GenerateURLError? = null
        val req = Gg.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverResponse("Link with this path already exists. Choose another path.")

        error shouldBe GenerateURLError.AliasAlreadyExists
    }

    @Test
    fun `check response without a duplicate falls through and creates the alias`() {
        var result: String? = null
        val req = Gg.getCreateRequest(queue, longURL, "abc", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse("ok")
        queue.added.single().deliverResponse("https://gg.gg/abc")

        result shouldBe "https://gg.gg/abc"
    }

    @Test
    fun `check error falls through and creates the alias`() {
        var result: String? = null
        val req = Gg.getCreateRequest(queue, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverError(VolleyError("no network"))
        queue.added.single().deliverResponse("https://gg.gg/random")

        result shouldBe "https://gg.gg/random"
    }

    @Test
    fun `create response equal to the root url fails with Unknown redirect code`() {
        var error: GenerateURLError? = null
        val req = Gg.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })
        req.deliverResponse("ok")

        queue.added.single().deliverResponse("https://gg.gg/")

        error shouldBe GenerateURLError.Unknown(2001)
    }

    @Test
    fun `create response not ending with a non-blank alias fails with Unknown mismatch code`() {
        var error: GenerateURLError? = null
        val req = Gg.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })
        req.deliverResponse("ok")

        queue.added.single().deliverResponse("https://gg.gg/other")

        error shouldBe GenerateURLError.Unknown(2002)
    }

    @Test
    fun `create response succeeds for a blank alias regardless of the returned path`() {
        var result: String? = null
        val req = Gg.getCreateRequest(queue, longURL, "", { result = it }, { fail("unexpected error: $it") })
        req.deliverResponse("ok")

        queue.added.single().deliverResponse("https://gg.gg/random")

        result shouldBe "https://gg.gg/random"
    }

    @Test
    fun `create error offline maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Gg.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })
        req.deliverResponse("ok")

        queue.added.single().deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `create error with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Gg.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })
        req.deliverResponse("ok")

        queue.added.single().deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `create error with a status code maps to Unknown with that code`() {
        var error: GenerateURLError? = null
        val req = Gg.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })
        req.deliverResponse("ok")

        queue.added.single().deliverError(VolleyError(NetworkResponse(500, ByteArray(0), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Test
    fun `create error with a null response body maps to Unknown with that status code`() {
        var error: GenerateURLError? = null
        val req = Gg.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })
        req.deliverResponse("ok")

        queue.added.single().deliverError(VolleyError(NetworkResponse(500, null, false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Test
    fun `check response handling that throws still falls back to creating the alias`() {
        var result: String? = null
        var enqueueCount = 0
        queue.onAdd = {
            enqueueCount++
            if (enqueueCount == 1) throw IllegalStateException("boom")
        }
        val req = Gg.getCreateRequest(queue, longURL, "abc", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse("ok")
        queue.added.last().deliverResponse("https://gg.gg/abc")

        result shouldBe "https://gg.gg/abc"
    }

    @Test
    fun `check error handling that throws still falls back to creating the alias`() {
        var result: String? = null
        var enqueueCount = 0
        queue.onAdd = {
            enqueueCount++
            if (enqueueCount == 1) throw IllegalStateException("boom")
        }
        val req = Gg.getCreateRequest(queue, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverError(VolleyError("no network"))
        queue.added.last().deliverResponse("https://gg.gg/random")

        result shouldBe "https://gg.gg/random"
    }

    @Test
    fun `create response success callback that throws is caught and reported as unexpected response`() {
        var error: GenerateURLError? = null
        val req = Gg.getCreateRequest(queue, longURL, "", { throw IllegalStateException("boom") }, { error = it })
        req.deliverResponse("ok")

        queue.added.single().deliverResponse("https://gg.gg/random")

        error shouldBe GenerateURLError.Unknown(2009)
    }

    @Test
    fun `create error callback that throws once is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        var errorCallbackCount = 0
        val req =
            Gg.getCreateRequest(queue, longURL, "", { fail("unexpected success") }) {
                errorCallbackCount++
                if (errorCallbackCount == 1) throw IllegalStateException("boom") else error = it
            }
        req.deliverResponse("ok")

        queue.added.single().deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `getInfoContents returns the alias info`() {
        val infoContents = Gg.getInfoContents(realContext)

        infoContents.size shouldBe 1
        infoContents[0].title shouldBe realContext.getString(R.string.alias)
        infoContents[0].linkOrDescription shouldBe
            realContext.getString(
                R.string.alias_text,
                Gg.aliasConfig.minAliasLength,
                Gg.aliasConfig.maxAliasLength,
                Gg.aliasConfig.allowedAliasCharacters,
            )
    }

    @Test
    fun `sanitizeLongURL adds https and trims trailing whitespace`() {
        Gg.sanitizeLongURL("example.com") shouldBe "https://example.com"
        Gg.sanitizeLongURL("https://example.com ") shouldBe "https://example.com"
    }

    @Test
    fun `check error with a status code still falls through and creates the alias`() {
        var result: String? = null
        val req = Gg.getCreateRequest(queue, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverError(VolleyError(NetworkResponse(500, "body".toByteArray(), false, 0L, emptyList())))
        queue.added.single().deliverResponse("https://gg.gg/random")

        result shouldBe "https://gg.gg/random"
    }

    @Test
    fun `check error with a null response body still falls through and creates the alias`() {
        var result: String? = null
        val req = Gg.getCreateRequest(queue, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverError(VolleyError(NetworkResponse(500, null, false, 0L, emptyList())))
        queue.added.single().deliverResponse("https://gg.gg/random")

        result shouldBe "https://gg.gg/random"
    }

    @Test
    fun `check request getParams returns the long url and custom path params`() {
        val req = Gg.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { fail("unexpected error: $it") })

        req.params() shouldBe mapOf("long_url" to longURL, "custom_path" to "abc")
    }

    @Test
    fun `create request getParams returns the long url and custom path params`() {
        val req = Gg.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { fail("unexpected error: $it") })

        req.deliverResponse("ok")

        queue.added.single().params() shouldBe mapOf("long_url" to longURL, "custom_path" to "abc")
    }
}
