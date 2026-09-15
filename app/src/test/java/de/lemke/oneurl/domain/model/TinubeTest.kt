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
import com.android.volley.toolbox.StringRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import java.time.ZonedDateTime
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
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
class TinubeTest {
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
    fun `succeeds with the short url when status is ok and a urlCode is present`() {
        var result: String? = null
        val req = Tinube.getCreateRequest(context, longURL, "", { result = it }, { fail("unexpected error: $it") })
        val response = """1:{"status":200,"data":{"urlCode":"Nx1ByyelU","longUrl":"$longURL"}}"""

        req.deliverStringResponse(response)

        result shouldBe "https://tinu.be/Nx1ByyelU"
    }

    @Test
    fun `status 208 maps to AliasAlreadyExists`() {
        var error: GenerateURLError? = null
        val req = Tinube.getCreateRequest(context, longURL, "abc", { fail("unexpected success") }, { error = it })
        val response = """1:{"status":208,"data":"The suffix is already in use"}"""

        req.deliverStringResponse(response)

        error shouldBe GenerateURLError.AliasAlreadyExists
    }

    @Test
    fun `an unrecognized status maps to Unknown with that status`() {
        var error: GenerateURLError? = null
        val req = Tinube.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })
        val response = """1:{"status":400,"data":"bad request"}"""

        req.deliverStringResponse(response)

        error shouldBe GenerateURLError.Unknown(400)
    }

    @Test
    fun `an unparseable response maps to Unknown with status 200`() {
        var error: GenerateURLError? = null
        val req = Tinube.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverStringResponse("this response has no recognizable status marker")

        error shouldBe GenerateURLError.Unknown(200)
    }

    @Test
    fun `offline error maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Tinube.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `error with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Tinube.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `error with a status code maps to Unknown with that status`() {
        var error: GenerateURLError? = null
        val req = Tinube.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, ByteArray(0), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Test
    fun `status ok without a urlCode maps to Unknown 200`() {
        var error: GenerateURLError? = null
        val req = Tinube.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })
        val response = """1:{"status":200,"data":{"longUrl":"$longURL"}}"""

        req.deliverStringResponse(response)

        error shouldBe GenerateURLError.Unknown(200)
    }

    @Test
    fun `error with a status code and a null response body maps to Unknown with that status`() {
        var error: GenerateURLError? = null
        val req = Tinube.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(404, null, false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(404)
    }

    @Test
    fun `isAliasValid accepts alphanumerics, underscores and hyphens, rejects other characters`() {
        Tinube.aliasConfig.isAliasValid("abc_123-xyz") shouldBe true
        Tinube.aliasConfig.isAliasValid("abc.123") shouldBe false
    }

    @Test
    fun `getURLClickCount resolves the click count from the clicks field`() {
        val slot = slot<StringRequest>()
        every { requestQueue.addToRequestQueue(capture(slot)) } returns Unit
        var clicks: Int? = -1
        val url = URL("https://tinu.be/abc", longURL, Tinube, false, "", "", ZonedDateTime.now())

        Tinube.getURLClickCount(context, url) { clicks = it }
        slot.captured.deliverStringResponse("""{"clicks":7}""")

        clicks shouldBe 7
    }

    @Test
    fun `getURLClickCount resolves to null on an unparseable response`() {
        val slot = slot<StringRequest>()
        every { requestQueue.addToRequestQueue(capture(slot)) } returns Unit
        var clicks: Int? = -1
        val url = URL("https://tinu.be/abc", longURL, Tinube, false, "", "", ZonedDateTime.now())

        Tinube.getURLClickCount(context, url) { clicks = it }
        slot.captured.deliverStringResponse("not json")

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount resolves to null on error`() {
        val slot = slot<StringRequest>()
        every { requestQueue.addToRequestQueue(capture(slot)) } returns Unit
        var clicks: Int? = -1
        val url = URL("https://tinu.be/abc", longURL, Tinube, false, "", "", ZonedDateTime.now())

        Tinube.getURLClickCount(context, url) { clicks = it }
        slot.captured.deliverError(VolleyError("no network"))

        clicks shouldBe null
    }

    @Test
    fun `sanitizeLongURL adds https, encodes ampersands, and trims`() {
        Tinube.sanitizeLongURL("example.com") shouldBe "https://example.com"
        Tinube.sanitizeLongURL("https://example.com?a=1&b=2 ") shouldBe "https://example.com?a=1%26b=2"
    }

    @Test
    fun `getInfoContents returns the alias and analytics info`() {
        val infoContents = Tinube.getInfoContents(realContext)

        infoContents.size shouldBe 2
        infoContents[0].title shouldBe realContext.getString(R.string.alias)
        infoContents[0].linkOrDescription shouldBe
            realContext.resources.getQuantityString(
                R.plurals.alias_text,
                Tinube.aliasConfig.maxAliasLength,
                Tinube.aliasConfig.minAliasLength,
                Tinube.aliasConfig.maxAliasLength,
                Tinube.aliasConfig.allowedAliasCharacters,
            )
        infoContents[1].title shouldBe realContext.getString(R.string.analytics)
        infoContents[1].linkOrDescription shouldBe realContext.getString(R.string.analytics_text)
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `error callback that throws once is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        var errorCallbackCount = 0
        val req =
            Tinube.getCreateRequest(context, longURL, "", { fail("unexpected success") }) {
                errorCallbackCount++
                if (errorCallbackCount == 1) throw RuntimeException("boom") else error = it
            }

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.Unknown()
    }
}
