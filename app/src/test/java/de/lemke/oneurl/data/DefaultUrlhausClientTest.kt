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

package de.lemke.oneurl.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.android.volley.NetworkResponse
import com.android.volley.VolleyError
import de.lemke.oneurl.BuildConfig
import de.lemke.oneurl.domain.model.UrlhausResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class DefaultUrlhausClientTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val queue = FakeVolleyQueue()
    private val urlhausClient = DefaultUrlhausClient(context, queue)

    private fun blacklisted(
        lists: String,
        reason: String,
    ) = "This URL is blacklisted on: $lists.\n$reason\n" +
        "If you continue trying to shorten blacklisted URLs, you may be blocked from using this App."

    private fun respond(json: String) {
        queue.onAdd = {
            it.deliverResponse(json)
        }
    }

    @Test
    fun `returns Ok when query_status is not ok`() =
        runTest {
            respond("""{"query_status":"no_results"}""")

            urlhausClient.check("example.com") shouldBe UrlhausResult.Ok
        }

    @Test
    fun `returns Ok when the response has no blacklist data`() =
        runTest {
            respond("""{"query_status":"ok"}""")

            urlhausClient.check("example.com") shouldBe UrlhausResult.Ok
        }

    @Test
    fun `returns Ok on malformed json`() =
        runTest {
            respond("not json")

            urlhausClient.check("example.com") shouldBe UrlhausResult.Ok
        }

    @Test
    fun `returns Ok on a network error`() =
        runTest {
            queue.onAdd = {
                it.deliverError(VolleyError(NetworkResponse(500, ByteArray(0), false, 0L, emptyList())))
            }

            urlhausClient.check("example.com") shouldBe UrlhausResult.Ok
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

            val result = urlhausClient.check("example.com")

            result.shouldBeInstanceOf<UrlhausResult.Blacklisted>()
            result.urlhausLink shouldBe "https://urlhaus.abuse.ch/url/123/"
            result.virustotalLink shouldBe "https://virustotal.com/report/1"
            result.message shouldBe blacklisted("URLhaus, SURBL, Spamhaus", "It is a known phishing domain.")
        }

    @Test
    fun `returns Blacklisted with null links and no surbl mention when absent`() =
        runTest {
            respond("""{"query_status":"ok","blacklists":{"spamhaus_dbl":"spammer_domain"}}""")

            val result = urlhausClient.check("example.com")

            result.shouldBeInstanceOf<UrlhausResult.Blacklisted>()
            result.urlhausLink shouldBe null
            result.virustotalLink shouldBe null
            result.message shouldBe blacklisted("URLhaus, Spamhaus", "It is a known spammer domain.")
        }

    @Test
    fun `returns Blacklisted naming only SURBL with the default reason when spamhaus does not list the url`() =
        runTest {
            respond("""{"query_status":"ok","blacklists":{"surbl":"listed","spamhaus_dbl":"not listed"}}""")

            val result = urlhausClient.check("example.com")

            result.shouldBeInstanceOf<UrlhausResult.Blacklisted>()
            result.message shouldBe
                blacklisted(
                    "URLhaus, SURBL",
                    "It may have been used abusively in the past, or it may link to another URL redirection service.",
                )
        }

    @Test
    fun `returns a null virustotal link when the first payload carries no usable link`() =
        runTest {
            listOf(
                """[]""",
                """["not an object"]""",
                """[{}]""",
                """[{"virustotal":{"link":""}}]""",
            ).forEach { payloads ->
                respond("""{"query_status":"ok","blacklists":{"surbl":"listed"},"payloads":$payloads}""")

                val result = urlhausClient.check("example.com")

                result.shouldBeInstanceOf<UrlhausResult.Blacklisted>()
                result.virustotalLink shouldBe null
            }
        }

    @Test
    fun `returns null links when the links are json null`() =
        runTest {
            respond(
                """
                {
                  "query_status":"ok",
                  "urlhaus_reference":null,
                  "blacklists":{"spamhaus_dbl":"spammer_domain"},
                  "payloads":[{"virustotal":{"link":null}}]
                }
                """.trimIndent(),
            )

            val result = urlhausClient.check("example.com")

            result.shouldBeInstanceOf<UrlhausResult.Blacklisted>()
            result.urlhausLink shouldBe null
            result.virustotalLink shouldBe null
        }

    @Test
    fun `returns null links when the link keys are missing`() =
        runTest {
            respond(
                """{"query_status":"ok","blacklists":{"spamhaus_dbl":"spammer_domain"},"payloads":[{"virustotal":{}}]}""",
            )

            val result = urlhausClient.check("example.com")

            result.shouldBeInstanceOf<UrlhausResult.Blacklisted>()
            result.urlhausLink shouldBe null
            result.virustotalLink shouldBe null
        }

    @Test
    fun `returns null links when the links are blank`() =
        runTest {
            respond(
                """
                {
                  "query_status":"ok",
                  "urlhaus_reference":"  ",
                  "blacklists":{"spamhaus_dbl":"spammer_domain"},
                  "payloads":[{"virustotal":{"link":""}}]
                }
                """.trimIndent(),
            )

            val result = urlhausClient.check("example.com")

            result.shouldBeInstanceOf<UrlhausResult.Blacklisted>()
            result.urlhausLink shouldBe null
            result.virustotalLink shouldBe null
        }

    @Test
    fun `returns a null virustotal link when the payloads are empty`() =
        runTest {
            respond("""{"query_status":"ok","blacklists":{"spamhaus_dbl":"spammer_domain"},"payloads":[]}""")

            val result = urlhausClient.check("example.com")

            result.shouldBeInstanceOf<UrlhausResult.Blacklisted>()
            result.virustotalLink shouldBe null
        }

    @Test
    fun `returns a null virustotal link when the payload has a json null virustotal entry`() =
        runTest {
            respond("""{"query_status":"ok","blacklists":{"spamhaus_dbl":"spammer_domain"},"payloads":[{"virustotal":null}]}""")

            val result = urlhausClient.check("example.com")

            result.shouldBeInstanceOf<UrlhausResult.Blacklisted>()
            result.virustotalLink shouldBe null
        }

    @Test
    fun `treats json null blacklist entries as not listed`() =
        runTest {
            respond("""{"query_status":"ok","blacklists":{"surbl":null,"spamhaus_dbl":null}}""")

            val result = urlhausClient.check("example.com")

            result.shouldBeInstanceOf<UrlhausResult.Blacklisted>()
            result.message shouldBe
                "This URL is blacklisted on: URLhaus.\n" +
                "It may have been used abusively in the past, or it may link to another URL redirection service.\n" +
                "If you continue trying to shorten blacklisted URLs, you may be blocked from using this App."
        }

    @Test
    fun `treats blank blacklist entries as not listed`() =
        runTest {
            respond("""{"query_status":"ok","blacklists":{"surbl":"","spamhaus_dbl":""}}""")

            val result = urlhausClient.check("example.com")

            result.shouldBeInstanceOf<UrlhausResult.Blacklisted>()
            result.message shouldBe
                "This URL is blacklisted on: URLhaus.\n" +
                "It may have been used abusively in the past, or it may link to another URL redirection service.\n" +
                "If you continue trying to shorten blacklisted URLs, you may be blocked from using this App."
        }

    @Test
    fun `returns Ok when query_status is json null`() =
        runTest {
            respond("""{"query_status":null,"blacklists":{"surbl":"listed"}}""")

            urlhausClient.check("example.com") shouldBe UrlhausResult.Ok
        }

    @Test
    fun `maps every known spamhaus status and falls back to the default reason otherwise`() =
        runTest {
            val statusToReason =
                mapOf(
                    "spammer_domain" to "It is a known spammer domain.",
                    "phishing_domain" to "It is a known phishing domain.",
                    "botnet_cc_domain" to "It is a known botnet C&C domain.",
                    "abused_legit_spam" to "It is a known compromised website used for spammer hosting.",
                    "abused_legit_malware" to "It is a known compromised website used for malware distribution.",
                    "abused_legit_phishing" to "It is a known compromised website used for phishing hosting.",
                    "abused_legit_botnetcc" to "It is a known compromised website used for botnet C&C hosting.",
                    "abused_redirector" to "It is a known abused redirector or URL shortener.",
                    "something_else" to
                        "It may have been used abusively in the past, or it may link to another URL redirection service.",
                )

            statusToReason.forEach { (status, reason) ->
                respond("""{"query_status":"ok","blacklists":{"spamhaus_dbl":"$status"}}""")

                val result = urlhausClient.check("example.com")

                result.shouldBeInstanceOf<UrlhausResult.Blacklisted>()
                result.message shouldBe blacklisted("URLhaus, Spamhaus", reason)
            }
        }

    @Test
    fun `sends the url as a param and the auth key as a header`() =
        runTest {
            queue.onAdd = {
                it.deliverResponse("""{"query_status":"ok"}""")
            }

            urlhausClient.check("example.com")

            queue.added.single().params() shouldBe mapOf("url" to "example.com")
            queue.added.single().headers() shouldBe mapOf("Auth-Key" to BuildConfig.URL_HAUS_AUTH_KEY)
        }

    @Test
    fun `ignores a response delivered after the continuation already resumed`() =
        runTest {
            queue.onAdd = { request ->
                request.deliverResponse("""{"query_status":"ok"}""")
                request.deliverResponse("""{"query_status":"ok"}""")
            }

            urlhausClient.check("example.com") shouldBe UrlhausResult.Ok
        }

    @Test
    fun `ignores a network error delivered after the continuation already resumed`() =
        runTest {
            queue.onAdd = { request ->
                val error = VolleyError(NetworkResponse(500, ByteArray(0), false, 0L, emptyList()))
                request.deliverError(error)
                request.deliverError(error)
            }

            urlhausClient.check("example.com") shouldBe UrlhausResult.Ok
        }

    @Test
    fun `cancelling the coroutine cancels the underlying request`() =
        runTest {
            val job = launch { urlhausClient.check("example.com") }
            runCurrent()
            job.cancel()
            runCurrent()

            queue.added.single().isCanceled shouldBe true
        }
}
