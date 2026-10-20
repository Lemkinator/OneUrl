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
import org.json.JSONObject
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import de.lemke.commonutils.R as commonutilsR

private fun testURL(shortURL: String) =
    URL(
        shortURL = shortURL,
        longURL = "https://example.com",
        shortURLProvider = Owovc.Owo,
        favorite = false,
        title = "title",
        description = "description",
        added = ZonedDateTime.now(),
    )

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class OwoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val queue = FakeVolleyQueue()
    private val longURL = "https://example.com"

    @Test
    fun `create request succeeds when the response has an id`() {
        var result: String? = null
        val req = Owovc.Owo.getCreateRequest(queue, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse(JSONObject().put("id", " uvu.owo.vc/uwU-uvU.uwU-uwU "))

        result shouldBe "uvu.owo.vc/uwU-uvU.uwU-uwU"
    }

    @Test
    fun `create request fails with Unknown when the response has no id`() {
        var error: GenerateURLError? = null
        val req = Owovc.Owo.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverResponse(JSONObject().put("destination", longURL))

        error shouldBe GenerateURLError.Unknown(200)
    }

    @Test
    fun `create request maps every known error condition to its GenerateURLError`() {
        val cases =
            listOf<Pair<VolleyError, GenerateURLError>>(
                NoConnectionError() to GenerateURLError.ServiceOffline,
                VolleyError("no network") to GenerateURLError.Unknown(),
                VolleyError(NetworkResponse(503, ByteArray(0), false, 0L, emptyList())) to
                    GenerateURLError.ServiceTemporarilyUnavailable(Owovc.Owo.baseURL),
                VolleyError(NetworkResponse(500, ByteArray(0), false, 0L, emptyList())) to GenerateURLError.Unknown(500),
                VolleyError(NetworkResponse(500, null, false, 0L, emptyList())) to GenerateURLError.Unknown(500),
                VolleyError(
                    NetworkResponse(
                        400,
                        """{"message":"body/link must match pattern \"https?://.+\\..+\""}""".toByteArray(),
                        false,
                        0L,
                        emptyList(),
                    ),
                ) to GenerateURLError.InvalidURL,
                VolleyError(NetworkResponse(400, "different problem".toByteArray(), false, 0L, emptyList())) to
                    GenerateURLError.Custom(400, "different problem"),
                VolleyError(NetworkResponse(500, "server exploded".toByteArray(), false, 0L, emptyList())) to
                    GenerateURLError.Custom(500, "server exploded"),
            )
        cases.forEach { (volleyError, expected) ->
            var error: GenerateURLError? = null
            val req = Owovc.Owo.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

            req.deliverError(volleyError)

            error shouldBe expected
        }
    }

    @Test
    fun `create error callback that throws once is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        var errorCallbackCount = 0
        val req =
            Owovc.Owo.getCreateRequest(queue, longURL, "", { fail("unexpected success") }) {
                errorCallbackCount++
                if (errorCallbackCount == 1) throw IllegalStateException("boom") else error = it
            }

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `getURLClickCount returns the visit count`() {
        var clicks: Int? = -1

        Owovc.Owo.getURLClickCount(queue, testURL("owo.vc/abc")) { clicks = it }
        queue.added.single().deliverResponse(JSONObject().put("visits", 5))

        clicks shouldBe 5
    }

    @Test
    fun `getURLClickCount returns null when the response has no visits field`() {
        var clicks: Int? = -1

        Owovc.Owo.getURLClickCount(queue, testURL("owo.vc/abc")) { clicks = it }
        queue.added.single().deliverResponse(JSONObject().put("id", "owo.vc/abc"))

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount returns null on a network error`() {
        var clicks: Int? = -1

        Owovc.Owo.getURLClickCount(queue, testURL("owo.vc/abc")) { clicks = it }
        queue.added.single().deliverError(VolleyError("no network"))

        clicks shouldBe null
    }

    @Test
    fun `getTipsCardTitleAndInfo returns owo specific info`() {
        val (title, info) = requireNotNull(Owovc.Owo.getTipsCardTitleAndInfo(context))

        title shouldBe context.getString(commonutilsR.string.commonutils_info)
        info shouldBe context.getString(R.string.owovc_fun_text)
    }

    @Test
    fun `getInfoContents returns the analytics info`() {
        val infoContents = Owovc.Owo.getInfoContents(context)

        infoContents.size shouldBe 1
        infoContents[0].title shouldBe context.getString(R.string.analytics)
        infoContents[0].linkOrDescription shouldBe context.getString(R.string.analytics_text)
    }

    @Test
    fun `sanitizeLongURL adds https when missing and trims trailing whitespace`() {
        Owovc.Owo.sanitizeLongURL("example.com") shouldBe "https://example.com"
        Owovc.Owo.sanitizeLongURL("https://example.com ") shouldBe "https://example.com"
    }

    @Test
    fun `create request with a JSON null id maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Owovc.Owo.getCreateRequest(queue, longURL, "", { fail("unexpected success: $it") }, { error = it })

        req.deliverResponse(JSONObject("""{"id":null}"""))

        error shouldBe GenerateURLError.Unknown(200)
    }
}
