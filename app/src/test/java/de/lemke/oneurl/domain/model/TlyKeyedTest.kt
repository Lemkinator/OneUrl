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
import com.android.volley.NetworkResponse
import com.android.volley.NoConnectionError
import com.android.volley.VolleyError
import de.lemke.oneurl.data.FakeVolleyQueue
import de.lemke.oneurl.data.deliverResponse
import io.kotest.matchers.shouldBe
import org.json.JSONObject
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class TlyKeyedTest {
    private val apiKey = checkNotNull(TlyApiKey.ofOrNull("secret"))
    private val longURL = "https://example.com"

    private fun keyed(
        provider: Tly = Tly.Default,
        onSuccess: (String) -> Unit = { fail("unexpected success") },
        onError: (GenerateURLError) -> Unit = { fail("unexpected error: $it") },
    ) = provider.getTlyCreateRequest(apiKey, longURL, onSuccess, onError)

    private fun errorOf(
        statusCode: Int,
        body: String,
        apiKey: TlyApiKey? = this.apiKey,
    ): GenerateURLError {
        var error: GenerateURLError? = null
        val req = Tly.Default.getTlyCreateRequest(apiKey, longURL, { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(statusCode, body.toByteArray(), false, 0L, emptyList())))

        return checkNotNull(error)
    }

    @Test
    fun `a key posts to the documented API`() {
        keyed().url shouldBe "https://api.t.ly/api/v1/link/shorten"
    }

    @Test
    fun `a key sends the long url and the full domain of the provider`() {
        val expectedDomains =
            mapOf(
                Tly.Default to "https://t.ly/",
                Tly.Ibitly to "https://ibit.ly/",
                Tly.Jpegly to "https://jpeg.ly/",
            )

        expectedDomains.forEach { (provider, domain) ->
            val body = JSONObject(String(keyed(provider).body!!, Charsets.UTF_8))

            body.getString("long_url") shouldBe longURL
            body.getString("domain") shouldBe domain
            body.has("provider") shouldBe false
        }
    }

    @Test
    fun `a key sends a bearer token and no origin`() {
        keyed().headers shouldBe
            mapOf(
                "accept" to "application/json",
                "content-type" to "application/json;charset=UTF-8",
                "authorization" to "Bearer secret",
            )
    }

    @Test
    fun `without a key the request still posts to the keyless endpoint`() {
        val req = Tly.Default.getTlyCreateRequest(null, longURL, { }, { })

        req.url shouldBe "https://t.ly/api/v1/link/shorten"
        JSONObject(String(req.body!!, Charsets.UTF_8)).getString("provider") shouldBe "t.ly"
    }

    @Test
    fun `a key request succeeds when the response contains a short_url`() {
        var result: String? = null
        val req = keyed(onSuccess = { result = it })

        req.deliverResponse(JSONObject("""{"short_url":"https://t.ly/abc"}"""))

        result shouldBe "https://t.ly/abc"
    }

    @Test
    fun `a keyed 401 is a rejected key`() {
        errorOf(401, """{"message":"Unauthenticated."}""") shouldBe GenerateURLError.TlyKeyRejected
    }

    @Test
    fun `a keyed 402 and 403 show the message of T_LY`() {
        errorOf(403, """{"message":"Invalid request please contact support@t.ly"}""") shouldBe
            GenerateURLError.Custom(403, "Invalid request please contact support@t.ly")
        errorOf(402, """{"message":"The current team needs an active subscription."}""") shouldBe
            GenerateURLError.Custom(402, "The current team needs an active subscription.")
    }

    @Test
    fun `a rejected key is reported when the body is empty`() {
        errorOf(401, "") shouldBe GenerateURLError.TlyKeyRejected
    }

    @Test
    fun `without a key a 401 shows the provider message`() {
        errorOf(401, """{"message":"Unauthenticated."}""", apiKey = null) shouldBe GenerateURLError.Custom(401, "Unauthenticated.")
    }

    @Test
    fun `a keyed 429 shows the message of T_LY`() {
        val message = "Your account has reached its monthly short link limit. Upgrade your plan to create more short links."

        errorOf(429, """{"message":"$message"}""") shouldBe GenerateURLError.Custom(429, message)
    }

    @Test
    fun `a keyed 429 without a message is Unknown with its status`() {
        errorOf(429, "") shouldBe GenerateURLError.Unknown(429)
    }

    @Test
    fun `a keyed message about an account and API key is shown as it is`() {
        val message = "T.LY account and API key required to create additional short links."

        errorOf(422, """{"message":"$message"}""") shouldBe GenerateURLError.Custom(422, message)
    }

    @Test
    fun `a keyed 422 for a missing long url is InvalidURL`() {
        errorOf(422, """{"message":"The long url field is required."}""") shouldBe GenerateURLError.InvalidURL
    }

    @Test
    fun `a keyed 503 is ServiceTemporarilyUnavailable`() {
        errorOf(503, "") shouldBe GenerateURLError.ServiceTemporarilyUnavailable("https://t.ly")
    }

    @Test
    fun `a keyed request without a connection is ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = keyed(onError = { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }
}
