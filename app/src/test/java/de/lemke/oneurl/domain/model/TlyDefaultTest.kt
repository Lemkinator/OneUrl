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
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.HttpStatusCode
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
private fun Request<*>.deliverJsonResponse(response: JSONObject) {
    val method = Request::class.java.getDeclaredMethod("deliverResponse", Any::class.java)
    method.isAccessible = true
    method.invoke(this, response)
}

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class TlyDefaultTest {
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
    fun `name group and enabled reflect the default t_ly provider`() {
        Tly.Default.name shouldBe "t.ly"
        Tly.Default.group shouldBe "t.ly [experimental]"
        Tly.Default.enabled shouldBe true
    }

    @Test
    fun `request body targets the t_ly provider`() {
        val req = Tly.Default.getCreateRequest(context, longURL, "", { }, { fail("unexpected error") })

        JSONObject(String(req.body!!, Charsets.UTF_8)).getString("provider") shouldBe "t.ly"
    }

    @Test
    fun `succeeds when the response contains a short_url`() {
        var result: String? = null
        val req = Tly.Default.getCreateRequest(context, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverJsonResponse(JSONObject("""{"short_url":"https://t.ly/abc"}"""))

        result shouldBe "https://t.ly/abc"
    }

    @Test
    fun `fails with Unknown when the response has no short_url`() {
        var error: GenerateURLError? = null
        val req = Tly.Default.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverJsonResponse(JSONObject("""{"provider_success":true}"""))

        error shouldBe GenerateURLError.Unknown(HttpStatusCode.OK)
    }

    @Test
    fun `error ServiceOffline on NoConnectionError`() {
        var error: GenerateURLError? = null
        val req = Tly.Default.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `error Unknown when there is no status code`() {
        var error: GenerateURLError? = null
        val req = Tly.Default.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `error ServiceTemporarilyUnavailable on 503`() {
        var error: GenerateURLError? = null
        val req = Tly.Default.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(503, ByteArray(0), false, 0L, emptyList())))

        error shouldBe GenerateURLError.ServiceTemporarilyUnavailable(Tly.Default.baseURL)
    }

    @Test
    fun `error Unknown with status code when the error body is blank`() {
        var error: GenerateURLError? = null
        val req = Tly.Default.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, ByteArray(0), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Test
    fun `error Unknown with status code when the error body has no message field`() {
        var error: GenerateURLError? = null
        val req = Tly.Default.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(422, """{"errors":{}}""".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(422)
    }

    @Test
    fun `error InvalidURL when message mentions the long url field is required`() {
        var error: GenerateURLError? = null
        val req = Tly.Default.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        val body = """{"message":"The long url field is required."}"""
        req.deliverError(VolleyError(NetworkResponse(422, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.InvalidURL
    }

    @Test
    fun `error RateLimitExceeded when message mentions the T_LY account and API key`() {
        var error: GenerateURLError? = null
        val req = Tly.Default.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        val body = """{"message":"T.LY account and API key required to create additional short links."}"""
        req.deliverError(VolleyError(NetworkResponse(422, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.RateLimitExceeded
    }

    @Test
    fun `error Custom for an unrecognized message`() {
        var error: GenerateURLError? = null
        val req = Tly.Default.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        val body = """{"message":"something went wrong"}"""
        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(400, "something went wrong")
    }

    @Test
    fun `error Unknown with status code when the error body is not valid JSON`() {
        var error: GenerateURLError? = null
        val req = Tly.Default.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(500, "not json".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(500)
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `error callback that throws once is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        var errorCallbackCount = 0
        val req =
            Tly.Default.getCreateRequest(context, longURL, "", { fail("unexpected success") }) {
                errorCallbackCount++
                if (errorCallbackCount == 1) throw RuntimeException("boom") else error = it
            }

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `getInfoContents returns the experimental info`() {
        val infoContents = Tly.Default.getInfoContents(realContext)

        infoContents.size shouldBe 1
        infoContents[0].title shouldBe realContext.getString(commonutilsR.string.commonutils_experimental)
        infoContents[0].linkOrDescription shouldBe realContext.getString(R.string.tly_info)
    }

    @Test
    fun `getTipsCardTitleAndInfo returns the info title and experimental text`() {
        val (title, info) = Tly.Default.getTipsCardTitleAndInfo(realContext)!!

        title shouldBe realContext.getString(commonutilsR.string.commonutils_info)
        info shouldBe realContext.getString(R.string.tly_info)
    }
}
