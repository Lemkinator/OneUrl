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
import androidx.test.core.app.ApplicationProvider
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
class DagdTest {
    private val queue = FakeVolleyQueue()
    private val longURL = "https://example.com"

    @Test
    fun `blank alias succeeds when da-gd returns a short url`() {
        var result: String? = null
        val req = Dagd.getCreateRequest(queue, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse("https://da.gd/abc")

        result shouldBe "https://da.gd/abc"
    }

    @Test
    fun `blank alias fails with Unknown when the response is unrecognized`() {
        var error: GenerateURLError? = null
        val req = Dagd.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverResponse("not a da.gd url")

        error shouldBe GenerateURLError.Unknown(200)
    }

    @Test
    fun `alias coshorten check succeeds when the existing short url points to the same long url`() {
        var result: String? = null
        val req = Dagd.getCreateRequest(queue, longURL, "abc", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse(longURL)

        result shouldBe "https://da.gd/abc"
    }

    @Test
    fun `alias coshorten check fails with AliasAlreadyExists when it points elsewhere`() {
        var error: GenerateURLError? = null
        val req = Dagd.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverResponse("https://other.example.com")

        error shouldBe GenerateURLError.AliasAlreadyExists
    }

    @Test
    fun `alias coshorten check offline error maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Dagd.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `alias coshorten check with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Dagd.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `alias coshorten check 404 falls through to creating the alias`() {
        var result: String? = null
        val req = Dagd.getCreateRequest(queue, longURL, "abc", { result = it }, { fail("unexpected error: $it") })

        req.deliverError(VolleyError(NetworkResponse(404, ByteArray(0), false, 0L, emptyList())))
        queue.added.single().deliverResponse("https://da.gd/abc")

        result shouldBe "https://da.gd/abc"
    }

    @Test
    fun `alias coshorten check with a non-404 status still falls through to creating the alias`() {
        var result: String? = null
        val req = Dagd.getCreateRequest(queue, longURL, "abc", { result = it }, { fail("unexpected error: $it") })

        req.deliverError(VolleyError(NetworkResponse(500, ByteArray(0), false, 0L, emptyList())))
        queue.added.single().deliverResponse("https://da.gd/abc")

        result shouldBe "https://da.gd/abc"
    }

    @Test
    fun `cancel also cancels the inner create request once the coshorten check has queued it`() {
        val req = Dagd.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { fail("unexpected error") })

        req.deliverError(VolleyError(NetworkResponse(404, ByteArray(0), false, 0L, emptyList())))
        req.cancel()

        queue.added.single().isCanceled shouldBe true
    }

    @Test
    fun `cancel before the inner create request exists only cancels the check request`() {
        val req = Dagd.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { fail("unexpected error") })

        req.cancel()

        queue.added shouldBe emptyList()
    }

    @Test
    fun `alias coshorten check callback that throws once is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        var errorCallbackCount = 0
        val req =
            Dagd.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }) {
                errorCallbackCount++
                if (errorCallbackCount == 1) throw IllegalStateException("boom") else error = it
            }

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `create request maps every known error message to its GenerateURLError`() {
        val cases =
            mapOf(
                "Long URL cannot be empty" to GenerateURLError.InvalidURL,
                "Long URL must have http:// or https:// scheme." to GenerateURLError.InvalidURL,
                "Long URL is not a valid URL." to GenerateURLError.InvalidURL,
                "Short URL already taken. Pick a different one." to GenerateURLError.AliasAlreadyExists,
                "Custom short URL contained invalid characters." to GenerateURLError.InvalidAlias,
            )
        cases.forEach { (message, expected) ->
            var error: GenerateURLError? = null
            val req = Dagd.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

            req.deliverError(VolleyError(NetworkResponse(400, message.toByteArray(), false, 0L, emptyList())))

            error shouldBe expected
        }
    }

    @Test
    fun `create request falls back to Custom for an unrecognized error message`() {
        var error: GenerateURLError? = null
        val req = Dagd.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, "server exploded".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(500, "server exploded")
    }

    @Test
    fun `create request maps a blank error body to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Dagd.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, ByteArray(0), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Test
    fun `create request maps a null error body to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Dagd.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, null, false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Test
    fun `create error callback that throws once is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        var errorCallbackCount = 0
        val req =
            Dagd.getCreateRequest(queue, longURL, "", { fail("unexpected success") }) {
                errorCallbackCount++
                if (errorCallbackCount == 1) throw IllegalStateException("boom") else error = it
            }

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `create request offline error maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Dagd.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `create request with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Dagd.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `isAliasValid accepts alphanumerics and underscores, rejects other characters`() {
        Dagd.aliasConfig.isAliasValid("abc_DEF_123") shouldBe true
        Dagd.aliasConfig.isAliasValid("abc-123") shouldBe false
        Dagd.aliasConfig.isAliasValid("") shouldBe false
    }

    @Test
    fun `info contents lead with the preview page hint for new links`() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        val infoContents = Dagd.getInfoContents(context)

        infoContents.map { it.title } shouldBe listOf("Hint before redirecting", "Custom alias", "Analytics")
        infoContents[0].linkOrDescription shouldBe
            "Only new links show a preview page before redirecting. da.gd shows this page to HTML clients, " +
            "such as browsers, until a cooldown ends. Older links redirect directly."
    }
}
