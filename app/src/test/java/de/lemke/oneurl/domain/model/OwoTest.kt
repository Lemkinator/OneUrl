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
import com.android.volley.toolbox.JsonObjectRequest
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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import de.lemke.commonutils.R as commonutilsR

// Request#deliverResponse(T) is protected - only Volley's own RequestQueue can normally trigger
// it. Tests stand in for the queue, so they reach it via reflection instead of a real round-trip.
private fun Request<*>.deliverJSONResponse(response: JSONObject) {
    val method = Request::class.java.getDeclaredMethod("deliverResponse", Any::class.java)
    method.isAccessible = true
    method.invoke(this, response)
}

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
    fun `create request succeeds when the response has an id`() {
        var result: String? = null
        val req = Owovc.Owo.getCreateRequest(context, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverJSONResponse(JSONObject().put("id", " uvu.owo.vc/uwU-uvU.uwU-uwU "))

        result shouldBe "uvu.owo.vc/uwU-uvU.uwU-uwU"
    }

    @Test
    fun `create request fails with Unknown when the response has no id`() {
        var error: GenerateURLError? = null
        val req = Owovc.Owo.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverJSONResponse(JSONObject().put("destination", longURL))

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
                VolleyError(
                    NetworkResponse(
                        400,
                        """{"message":"body/link must match pattern \"https?://.+\\..+\""}""".toByteArray(),
                        false,
                        0L,
                        emptyList(),
                    ),
                ) to GenerateURLError.InvalidURL,
                VolleyError(NetworkResponse(500, "server exploded".toByteArray(), false, 0L, emptyList())) to
                    GenerateURLError.Custom(500, "server exploded"),
            )
        cases.forEach { (volleyError, expected) ->
            var error: GenerateURLError? = null
            val req = Owovc.Owo.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

            req.deliverError(volleyError)

            error shouldBe expected
        }
    }

    @Test
    fun `getURLClickCount returns the visit count`() {
        var clicks: Int? = -1
        val slot = slot<JsonObjectRequest>()
        every { requestQueue.addToRequestQueue(capture(slot)) } returns Unit

        Owovc.Owo.getURLClickCount(context, testURL("owo.vc/abc")) { clicks = it }
        slot.captured.deliverJSONResponse(JSONObject().put("visits", 5))

        clicks shouldBe 5
    }

    @Test
    fun `getURLClickCount returns null when the response has no visits field`() {
        var clicks: Int? = -1
        val slot = slot<JsonObjectRequest>()
        every { requestQueue.addToRequestQueue(capture(slot)) } returns Unit

        Owovc.Owo.getURLClickCount(context, testURL("owo.vc/abc")) { clicks = it }
        slot.captured.deliverJSONResponse(JSONObject().put("id", "owo.vc/abc"))

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount returns null on a network error`() {
        var clicks: Int? = -1
        val slot = slot<JsonObjectRequest>()
        every { requestQueue.addToRequestQueue(capture(slot)) } returns Unit

        Owovc.Owo.getURLClickCount(context, testURL("owo.vc/abc")) { clicks = it }
        slot.captured.deliverError(VolleyError("no network"))

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
}
