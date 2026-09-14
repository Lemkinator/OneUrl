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

package de.lemke.oneurl.domain

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import de.lemke.oneurl.BuildConfig
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
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

// Request#getParams()/getHeaders() are protected - only Volley's own network dispatcher normally
// calls them. Tests reach them via reflection to assert what the request actually sends.
private fun Request<*>.paramsViaReflection(): Map<*, *>? {
    val method = Request::class.java.getDeclaredMethod("getParams")
    method.isAccessible = true
    return method.invoke(this) as Map<*, *>?
}

private fun Request<*>.headersViaReflection(): Map<*, *> {
    val method = Request::class.java.getDeclaredMethod("getHeaders")
    method.isAccessible = true
    return method.invoke(this) as Map<*, *>
}

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class CheckURLSafetyUseCaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val requestQueue = mockk<RequestQueueSingleton>()
    private val checkURLSafety = CheckURLSafetyUseCase(context)

    @Before
    fun setup() {
        mockkObject(RequestQueueSingleton.Companion)
        every { RequestQueueSingleton.getInstance(context) } returns requestQueue
    }

    @After
    fun tearDown() {
        unmockkObject(RequestQueueSingleton.Companion)
    }

    private fun respond(json: String) {
        every { requestQueue.addToRequestQueue(any<StringRequest>()) } answers {
            firstArg<StringRequest>().deliverStringResponse(json)
        }
    }

    @Test
    fun `returns Ok when query_status is not ok`() =
        runTest {
            respond("""{"query_status":"no_results"}""")

            checkURLSafety("example.com") shouldBe CheckURLSafetyUseCase.UrlhausResult.Ok
        }

    @Test
    fun `returns Ok when the response has no blacklist data`() =
        runTest {
            respond("""{"query_status":"ok"}""")

            checkURLSafety("example.com") shouldBe CheckURLSafetyUseCase.UrlhausResult.Ok
        }

    @Test
    fun `returns Ok on malformed json`() =
        runTest {
            respond("not json")

            checkURLSafety("example.com") shouldBe CheckURLSafetyUseCase.UrlhausResult.Ok
        }

    @Test
    fun `returns Ok on a network error`() =
        runTest {
            every { requestQueue.addToRequestQueue(any<StringRequest>()) } answers {
                firstArg<StringRequest>().deliverError(VolleyError(NetworkResponse(500, ByteArray(0), false, 0L, emptyList())))
            }

            checkURLSafety("example.com") shouldBe CheckURLSafetyUseCase.UrlhausResult.Ok
        }

    @Test
    fun `returns Blacklisted with surbl and spamhaus details and links when present`() =
        runTest {
            respond(
                """
                {
                  "query_status":"ok",
                  "urlhaus_reference":"https://urlhaus.abuse.ch/url/123/",
                  "blacklists":{"surbl":"listed","spamhaus_dbl":"phishing_domain"},
                  "payloads":[{"virustotal":{"link":"https://virustotal.com/report/1"}}]
                }
                """.trimIndent(),
            )

            val result = checkURLSafety("example.com")

            result.shouldBeInstanceOf<CheckURLSafetyUseCase.UrlhausResult.Blacklisted>()
            result.urlhausLink shouldBe "https://urlhaus.abuse.ch/url/123/"
            result.virustotalLink shouldBe "https://virustotal.com/report/1"
            result.message shouldBe
                context.getString(
                    R.string.error_urlhaus_blacklisted,
                    "URLhaus, SURBL, Spamhaus",
                    context.getString(R.string.error_urlhaus_phishing_domain),
                )
        }

    @Test
    fun `returns Blacklisted with null links and no surbl mention when absent`() =
        runTest {
            respond("""{"query_status":"ok","blacklists":{"spamhaus_dbl":"spammer_domain"}}""")

            val result = checkURLSafety("example.com")

            result.shouldBeInstanceOf<CheckURLSafetyUseCase.UrlhausResult.Blacklisted>()
            result.urlhausLink shouldBe null
            result.virustotalLink shouldBe null
            result.message shouldBe
                context.getString(
                    R.string.error_urlhaus_blacklisted,
                    "URLhaus, Spamhaus",
                    context.getString(R.string.error_urlhaus_spammer_domain),
                )
        }

    @Test
    fun `maps every known spamhaus status and falls back to the default reason otherwise`() =
        runTest {
            val statusToStringRes =
                mapOf(
                    "spammer_domain" to R.string.error_urlhaus_spammer_domain,
                    "phishing_domain" to R.string.error_urlhaus_phishing_domain,
                    "botnet_cc_domain" to R.string.error_urlhaus_botnet_cc_domain,
                    "abused_legit_spam" to R.string.error_urlhaus_abused_legit_spam,
                    "abused_legit_malware" to R.string.error_urlhaus_abused_legit_malware,
                    "abused_legit_phishing" to R.string.error_urlhaus_abused_legit_phishing,
                    "abused_legit_botnetcc" to R.string.error_urlhaus_abused_legit_botnetcc,
                    "abused_redirector" to R.string.error_urlhaus_abused_redirector,
                    "something_else" to R.string.error_urlhaus_default,
                )

            statusToStringRes.forEach { (status, expectedStringRes) ->
                respond("""{"query_status":"ok","blacklists":{"spamhaus_dbl":"$status"}}""")

                val result = checkURLSafety("example.com")

                result.shouldBeInstanceOf<CheckURLSafetyUseCase.UrlhausResult.Blacklisted>()
                result.message shouldBe
                    context.getString(R.string.error_urlhaus_blacklisted, "URLhaus, Spamhaus", context.getString(expectedStringRes))
            }
        }

    @Test
    fun `sends the url as a param and the auth key as a header`() =
        runTest {
            val requestSlot = slot<StringRequest>()
            every { requestQueue.addToRequestQueue(capture(requestSlot)) } answers {
                requestSlot.captured.deliverStringResponse("""{"query_status":"ok"}""")
            }

            checkURLSafety("example.com")

            requestSlot.captured.paramsViaReflection() shouldBe mapOf("url" to "example.com")
            requestSlot.captured.headersViaReflection() shouldBe mapOf("Auth-Key" to BuildConfig.URL_HAUS_AUTH_KEY)
        }

    @Test
    fun `ignores a response delivered after the continuation already resumed`() =
        runTest {
            every { requestQueue.addToRequestQueue(any<StringRequest>()) } answers {
                val request = firstArg<StringRequest>()
                request.deliverStringResponse("""{"query_status":"ok"}""")
                request.deliverStringResponse("""{"query_status":"ok"}""")
            }

            checkURLSafety("example.com") shouldBe CheckURLSafetyUseCase.UrlhausResult.Ok
        }

    @Test
    fun `ignores a network error delivered after the continuation already resumed`() =
        runTest {
            every { requestQueue.addToRequestQueue(any<StringRequest>()) } answers {
                val request = firstArg<StringRequest>()
                val error = VolleyError(NetworkResponse(500, ByteArray(0), false, 0L, emptyList()))
                request.deliverError(error)
                request.deliverError(error)
            }

            checkURLSafety("example.com") shouldBe CheckURLSafetyUseCase.UrlhausResult.Ok
        }

    @Test
    fun `cancelling the coroutine cancels the underlying request`() =
        runTest {
            val requestSlot = slot<StringRequest>()
            every { requestQueue.addToRequestQueue(capture(requestSlot)) } just Runs

            val job = launch { checkURLSafety("example.com") }
            runCurrent()
            job.cancel()
            runCurrent()

            requestSlot.captured.isCanceled shouldBe true
        }
}
