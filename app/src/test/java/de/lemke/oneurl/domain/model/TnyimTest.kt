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
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.NoConnectionError
import com.android.volley.VolleyError
import de.lemke.oneurl.R
import de.lemke.oneurl.data.FakeVolleyQueue
import de.lemke.oneurl.data.deliverResponse
import de.lemke.oneurl.data.parseResponse
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime
import org.json.JSONObject
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class TnyimTest {
    private val queue = FakeVolleyQueue()
    private val realContext = ApplicationProvider.getApplicationContext<Context>()
    private val longURL = "https://example.com"

    @Test
    fun `succeeds when a blank alias gets a short url back`() {
        var result: String? = null
        val req = Tnyim.getCreateRequest(queue, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse("""{"shorturl":"http://tny.im/k1Ka"}""")

        result shouldBe "http://tny.im/k1Ka"
    }

    @Test
    fun `succeeds when the short url matches the requested alias`() {
        var result: String? = null
        val req = Tnyim.getCreateRequest(queue, longURL, "abc12", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse("""{"shorturl":"https://tny.im/abc12"}""")

        result shouldBe "https://tny.im/abc12"
    }

    @Test
    fun `fails with URLExistsWithDifferentAlias when the short url does not match the requested alias`() {
        var error: GenerateURLError? = null
        val req = Tnyim.getCreateRequest(queue, longURL, "abc12", { fail("unexpected success") }, { error = it })

        req.deliverResponse("""{"shorturl":"https://tny.im/k1Ka"}""")

        error shouldBe GenerateURLError.URLExistsWithDifferentAlias
    }

    @Test
    fun `fails with Unknown when the response has no shorturl`() {
        var error: GenerateURLError? = null
        val req = Tnyim.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverResponse("""{"status":"fail","code":"error:nourl"}""")

        error shouldBe GenerateURLError.Unknown(200)
    }

    @Test
    fun `reply that is not JSON maps to ServiceTemporarilyUnavailable`() {
        var error: GenerateURLError? = null
        val req = Tnyim.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        val parsed = req.parseResponse(NetworkResponse(200, "<html>maintenance</html>".toByteArray(), false, 0L, emptyList()))
        req.deliverResponse(parsed.result as String)

        error shouldBe GenerateURLError.ServiceTemporarilyUnavailable("https://tny.im")
    }

    @Test
    fun `offline error maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Tnyim.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `error with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Tnyim.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `error with a blank body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Tnyim.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(400, ByteArray(0), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(400)
    }

    @Test
    fun `500 status maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Tnyim.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, "server exploded".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Test
    fun `an unrecognized status code falls back to Custom`() {
        var error: GenerateURLError? = null
        val req = Tnyim.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(404, "not found".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(404, "not found")
    }

    @Test
    fun `error with a null body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Tnyim.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(404, null, false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(404)
    }

    @Test
    fun `sanitizeLongURL encodes ampersands and trims`() {
        Tnyim.sanitizeLongURL("https://example.com?a=1&b=2 ") shouldBe "https://example.com?a=1%26b=2"
    }

    @Test
    fun `getInfoContents returns the alias and analytics info`() {
        val infoContents = Tnyim.getInfoContents(realContext)

        infoContents.size shouldBe 2
        infoContents[0].title shouldBe realContext.getString(R.string.alias)
        infoContents[0].linkOrDescription shouldBe
            realContext.getString(
                R.string.alias_text,
                Tnyim.aliasConfig.minAliasLength,
                Tnyim.aliasConfig.maxAliasLength,
                Tnyim.aliasConfig.allowedAliasCharacters,
            )
        infoContents[1].title shouldBe realContext.getString(R.string.analytics)
        infoContents[1].linkOrDescription shouldBe realContext.getString(R.string.analytics_text)
    }

    @Test
    fun `isAliasValid accepts alphanumerics and hyphens, rejects other characters`() {
        Tnyim.aliasConfig.isAliasValid("abc-123") shouldBe true
        Tnyim.aliasConfig.isAliasValid("abc_123") shouldBe false
    }

    @Test
    fun `getURLClickCount resolves the click count from the link clicks field`() {
        var clicks: Int? = -1
        val url = URL("https://tny.im/abc12", longURL, Tnyim, false, "", "", ZonedDateTime.now())

        Tnyim.getURLClickCount(queue, url) { clicks = it }
        queue.added.single().deliverResponse(JSONObject().put("link", JSONObject().put("clicks", "3")))

        clicks shouldBe 3
    }

    @Test
    fun `getURLClickCount resolves to null when the clicks field is not numeric`() {
        var clicks: Int? = -1
        val url = URL("https://tny.im/abc12", longURL, Tnyim, false, "", "", ZonedDateTime.now())

        Tnyim.getURLClickCount(queue, url) { clicks = it }
        queue.added.single().deliverResponse(JSONObject().put("link", JSONObject().put("clicks", "not-a-number")))

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount resolves to null when the clicks field is JSON null`() {
        var clicks: Int? = -1
        val url = URL("https://tny.im/abc12", longURL, Tnyim, false, "", "", ZonedDateTime.now())

        Tnyim.getURLClickCount(queue, url) { clicks = it }
        queue.added.single().deliverResponse(JSONObject("""{"link":{"clicks":null}}"""))

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount resolves to null when the link field is missing`() {
        var clicks: Int? = -1
        val url = URL("https://tny.im/abc12", longURL, Tnyim, false, "", "", ZonedDateTime.now())

        Tnyim.getURLClickCount(queue, url) { clicks = it }
        queue.added.single().deliverResponse(JSONObject().put("statusCode", 404))

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount resolves to null on error`() {
        var clicks: Int? = -1
        val url = URL("https://tny.im/abc12", longURL, Tnyim, false, "", "", ZonedDateTime.now())

        Tnyim.getURLClickCount(queue, url) { clicks = it }
        queue.added.single().deliverError(VolleyError("no network"))

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount callback that throws once is caught and resolves to null`() {
        var callbackCount = 0
        var clicks: Int? = -1
        val url = URL("https://tny.im/abc12", longURL, Tnyim, false, "", "", ZonedDateTime.now())

        Tnyim.getURLClickCount(queue, url) {
            callbackCount++
            if (callbackCount == 1) throw IllegalStateException("boom") else clicks = it
        }
        queue.added.single().deliverResponse(JSONObject().put("link", JSONObject().put("clicks", "3")))

        clicks shouldBe null
    }

    @Test
    fun `create error callback that throws once is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        var errorCallbackCount = 0
        val req =
            Tnyim.getCreateRequest(queue, longURL, "", { fail("unexpected success") }) {
                errorCallbackCount++
                if (errorCallbackCount == 1) throw IllegalStateException("boom") else error = it
            }

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `getRetryPolicy returns a policy with the request timeout and default retry settings`() {
        val req = Tnyim.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { fail("unexpected error: $it") })

        val retryPolicy = req.retryPolicy as DefaultRetryPolicy

        retryPolicy.currentTimeout shouldBe 10000
        retryPolicy.currentRetryCount shouldBe 0
        retryPolicy.backoffMultiplier shouldBe DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
    }
}
