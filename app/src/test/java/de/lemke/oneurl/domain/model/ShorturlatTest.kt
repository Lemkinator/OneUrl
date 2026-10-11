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
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import de.lemke.commonutils.R as commonutilsR

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ShorturlatTest {
    private val realContext = ApplicationProvider.getApplicationContext<Context>()
    private val queue = FakeVolleyQueue()
    private val longURL = "https://example.com"
    private val url =
        URL(
            shortURL = "https://shorturl.at/2ssVp",
            longURL = longURL,
            shortURLProvider = Shorturlat,
            favorite = false,
            title = "",
            description = "",
            added = ZonedDateTime.now(),
        )

    @Test
    fun `create request body is the form-urlencoded long url`() {
        val req = Shorturlat.getCreateRequest(queue, longURL, "", { }, { fail("unexpected error") })

        String(req.body!!, Charsets.UTF_8) shouldBe "u=$longURL"
    }

    @Test
    fun `create request content type is form-urlencoded`() {
        val req = Shorturlat.getCreateRequest(queue, longURL, "", { }, { fail("unexpected error") })

        req.bodyContentType shouldBe "application/x-www-form-urlencoded; charset=UTF-8"
    }

    @Test
    fun `create request succeeds when the response contains the shortened url`() {
        var result: String? = null
        val req = Shorturlat.getCreateRequest(queue, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse(
            """<input id="shortenurl" type="text" value="https://shorturl.at/R8dPc" onClick="this.select();">""",
        )

        result shouldBe "https://shorturl.at/R8dPc"
    }

    @Test
    fun `create request succeeds with the remainder of the response when no onClick delimiter follows the value`() {
        var result: String? = null
        val req = Shorturlat.getCreateRequest(queue, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse("""<input id="shortenurl" type="text" value="https://shorturl.at/R8dPc">""")

        result shouldBe "https://shorturl.at/R8dPc\">"
    }

    @Test
    fun `create request fails with Unknown when the response has no recognizable short url`() {
        var error: GenerateURLError? = null
        val req = Shorturlat.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverResponse("<h1>An error occurred creating the short URL</h1>")

        error shouldBe GenerateURLError.Unknown(HttpStatusCode.OK)
    }

    @Test
    fun `create request offline error maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Shorturlat.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `create request with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Shorturlat.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `create request with a status code maps to Unknown with that code`() {
        var error: GenerateURLError? = null
        val req = Shorturlat.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, ByteArray(0), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Test
    fun `getURLClickCount reports the parsed click count`() {
        var clicks: Int? = -1
        Shorturlat.getURLClickCount(queue, url) { clicks = it }

        queue.added.single().deliverResponse("""<div class="squarebox"><div class="squareboxtext">42</div></div>""")

        clicks shouldBe 42
    }

    @Test
    fun `getURLClickCount reports null when the click count cannot be parsed`() {
        var clicks: Int? = -1
        Shorturlat.getURLClickCount(queue, url) { clicks = it }

        queue.added.single().deliverResponse("<h1>Total URL Clicks</h1>")

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount reports null when the click count marker is present but not numeric`() {
        var clicks: Int? = -1
        Shorturlat.getURLClickCount(queue, url) { clicks = it }

        queue.added.single().deliverResponse("""<div class="squarebox"><div class="squareboxtext">many</div></div>""")

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount reports null on a network error`() {
        var clicks: Int? = -1
        Shorturlat.getURLClickCount(queue, url) { clicks = it }

        queue.added.single().deliverError(VolleyError("no network"))

        clicks shouldBe null
    }

    @Test
    fun `getInfoContents returns the experimental notice and analytics info`() {
        val infoContents = Shorturlat.getInfoContents(realContext)

        infoContents.size shouldBe 2
        infoContents[0].title shouldBe realContext.getString(commonutilsR.string.commonutils_experimental)
        infoContents[0].linkOrDescription shouldBe realContext.getString(R.string.shorturlat_info)
        infoContents[1].title shouldBe realContext.getString(R.string.analytics)
        infoContents[1].linkOrDescription shouldBe realContext.getString(R.string.analytics_text)
    }

    @Test
    fun `getTipsCardTitleAndInfo pairs the info title with the shorturlat info text`() {
        val tipsCardTitleAndInfo = Shorturlat.getTipsCardTitleAndInfo(realContext)
        val expected = Pair(realContext.getString(commonutilsR.string.commonutils_info), realContext.getString(R.string.shorturlat_info))

        tipsCardTitleAndInfo shouldBe expected
    }

    @Test
    fun `create request error callback that throws once is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        var errorCallbackCount = 0
        val req =
            Shorturlat.getCreateRequest(queue, longURL, "", { fail("unexpected success") }) {
                errorCallbackCount++
                if (errorCallbackCount == 1) throw IllegalStateException("boom") else error = it
            }

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }
}
