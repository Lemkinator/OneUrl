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
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.json.JSONObject
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
class OneptcoTest {
    private val context = mockk<Context>()
    private val realContext = ApplicationProvider.getApplicationContext<Context>()
    private val longURL = "https://example.com"

    @Test
    fun `alias validity follows the allowed character set`() {
        Oneptco.aliasConfig.isAliasValid("abc_DEF_123") shouldBe true
        Oneptco.aliasConfig.isAliasValid("abc-def") shouldBe false
        Oneptco.aliasConfig.isAliasValid("") shouldBe false
    }

    @Test
    fun `sanitizeLongURL encodes ampersands and trims`() {
        Oneptco.sanitizeLongURL("https://example.com?a=1&b=2 ") shouldBe "https://example.com?a=1%26b=2"
    }

    @Test
    fun `getInfoContents returns the alias info`() {
        val infoContents = Oneptco.getInfoContents(realContext)

        infoContents.size shouldBe 1
        infoContents[0].title shouldBe realContext.getString(R.string.alias)
        infoContents[0].linkOrDescription shouldBe
            realContext.resources.getQuantityString(
                R.plurals.alias_text,
                Oneptco.aliasConfig.maxAliasLength,
                Oneptco.aliasConfig.minAliasLength,
                Oneptco.aliasConfig.maxAliasLength,
                Oneptco.aliasConfig.allowedAliasCharacters,
            )
    }

    @Test
    fun `create request succeeds when short is present and receivedRequestedShort is absent`() {
        var result: String? = null
        val req = Oneptco.getCreateRequest(context, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverJsonResponse(JSONObject("""{"message":"Added!","short":"ajodd","long":"t"}"""))

        result shouldBe "${Oneptco.baseURL}/ajodd"
    }

    @Test
    fun `create request succeeds when receivedRequestedShort is true`() {
        var result: String? = null
        val req = Oneptco.getCreateRequest(context, longURL, "ajodd", { result = it }, { fail("unexpected error: $it") })

        req.deliverJsonResponse(
            JSONObject("""{"message":"Added!","short":"ajodd","long":"t","receivedRequestedShort":true}"""),
        )

        result shouldBe "${Oneptco.baseURL}/ajodd"
    }

    @Test
    fun `create request fails with AliasAlreadyExists when receivedRequestedShort is false`() {
        var error: GenerateURLError? = null
        val req = Oneptco.getCreateRequest(context, longURL, "test", { fail("unexpected success") }, { error = it })

        req.deliverJsonResponse(
            JSONObject("""{"message":"Added!","short":"asdfg","long":"asdfasdf","receivedRequestedShort":false}"""),
        )

        error shouldBe GenerateURLError.AliasAlreadyExists
    }

    @Test
    fun `create request fails with Unknown when the response has no message`() {
        var error: GenerateURLError? = null
        val req = Oneptco.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverJsonResponse(JSONObject("""{"short":"ajodd"}"""))

        error shouldBe GenerateURLError.Unknown(HttpStatusCode.OK)
    }

    @Test
    fun `create request fails with Custom when the message is not Added`() {
        var error: GenerateURLError? = null
        val req = Oneptco.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverJsonResponse(JSONObject("""{"message":"Bad request"}"""))

        error shouldBe GenerateURLError.Custom(HttpStatusCode.OK, "Bad request")
    }

    @Test
    fun `create request fails with Unknown when the response has no short`() {
        var error: GenerateURLError? = null
        val req = Oneptco.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverJsonResponse(JSONObject("""{"message":"Added!"}"""))

        error shouldBe GenerateURLError.Unknown(HttpStatusCode.OK)
    }

    @Test
    fun `create request fails with Unknown when receivedRequestedShort is not a boolean`() {
        var error: GenerateURLError? = null
        val req = Oneptco.getCreateRequest(context, longURL, "test", { fail("unexpected success") }, { error = it })

        req.deliverJsonResponse(JSONObject("""{"message":"Added!","short":"ajodd","receivedRequestedShort":42}"""))

        error shouldBe GenerateURLError.Unknown(HttpStatusCode.OK)
    }

    @Test
    fun `create request offline error maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Oneptco.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `create request with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Oneptco.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `create request with a blank error body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Oneptco.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(400, ByteArray(0), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(400)
    }

    @Test
    fun `create request maps 404 with a body to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Oneptco.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(HttpStatusCode.NOT_FOUND, "body".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(HttpStatusCode.NOT_FOUND)
    }

    @Test
    fun `create request maps 500 with a body to InternalServerError`() {
        var error: GenerateURLError? = null
        val req = Oneptco.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(HttpStatusCode.INTERNAL_SERVER_ERROR, "body".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.InternalServerError
    }

    @Test
    fun `create request maps 503 with a body to ServiceTemporarilyUnavailable`() {
        var error: GenerateURLError? = null
        val req = Oneptco.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(HttpStatusCode.SERVICE_UNAVAILABLE, "body".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.ServiceTemporarilyUnavailable(Oneptco.baseURL)
    }

    @Test
    fun `create request falls back to Custom for an unrecognized status code`() {
        var error: GenerateURLError? = null
        val req = Oneptco.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(400, "server exploded".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(400, "server exploded")
    }

    @Test
    fun `create request with a null error body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Oneptco.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(400, null, false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(400)
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun `create request error callback that throws once is caught and reported as unknown`() {
        var error: GenerateURLError? = null
        var errorCallbackCount = 0
        val req =
            Oneptco.getCreateRequest(context, longURL, "", { fail("unexpected success") }) {
                errorCallbackCount++
                if (errorCallbackCount == 1) throw RuntimeException("boom") else error = it
            }

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }
}
