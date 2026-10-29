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
import com.android.volley.Request
import com.android.volley.VolleyError
import de.lemke.oneurl.data.FakeVolleyQueue
import de.lemke.oneurl.data.deliverResponse
import de.lemke.oneurl.data.parseResponse
import io.kotest.matchers.shouldBe
import java.net.URLDecoder
import java.time.Duration
import java.time.Instant
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
class SpoomeDefaultTest {
    private val queue = FakeVolleyQueue()
    private val longURL = "https://example.com"

    @Test
    fun `sanitizeLongURL adds https and trims, keeping ampersands`() {
        Spoome.Default.sanitizeLongURL("example.com") shouldBe "https://example.com"
        Spoome.Default.sanitizeLongURL("https://example.com?a=1&b=2 ") shouldBe "https://example.com?a=1&b=2"
    }

    @Test
    fun `create request posts the url and alias as JSON to the v1 endpoint`() {
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc_1", { }, { fail("unexpected error") })

        req.method shouldBe Request.Method.POST
        req.url shouldBe "https://spoo.me/api/v1/shorten"
        val body = JSONObject(String(req.body, Charsets.UTF_8))
        body.getString("long_url") shouldBe "https://example.com"
        body.getString("alias") shouldBe "abc_1"
    }

    @Test
    fun `create request omits the alias when it is blank`() {
        val req = Spoome.Default.getCreateRequest(queue, "https://example.com?a=1&b=2", " ", { }, { fail("unexpected error") })

        val body = JSONObject(String(req.body, Charsets.UTF_8))
        body.getString("long_url") shouldBe "https://example.com?a=1&b=2"
        body.has("alias") shouldBe false
    }

    @Test
    fun `create request sends the expected Accept header`() {
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { }, { fail("unexpected error") })

        req.headers["Accept"] shouldBe "application/json"
    }

    @Test
    fun `succeeds with the trimmed short url when the response contains one`() {
        var result: String? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse(JSONObject().put("short_url", " https://spoo.me/NSPBXZ "))

        result shouldBe "https://spoo.me/NSPBXZ"
    }

    @Test
    fun `succeeds with an https short url when the response contains an http one`() {
        var result: String? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse(JSONObject().put("short_url", "http://spoo.me/NSPBXZ"))

        result shouldBe "https://spoo.me/NSPBXZ"
    }

    @Test
    fun `fails with Unknown when the response has no short_url`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverResponse(JSONObject())

        error shouldBe GenerateURLError.Unknown(200)
    }

    @Test
    fun `offline error maps to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `reply that is not JSON maps to ServiceTemporarilyUnavailable`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        val parsed = req.parseResponse(NetworkResponse(200, "<html>maintenance</html>".toByteArray(), false, 0L, emptyList()))
        req.deliverError(parsed.error)

        error shouldBe GenerateURLError.ServiceTemporarilyUnavailable("https://spoo.me")
    }

    @Test
    fun `error with no status code maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `error with a null body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(400, null, false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(400)
    }

    @Test
    fun `error body for field long_url maps to InvalidURL`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })
        val body = """{"error":"URL is not allowed or invalid","code":"validation_error","field":"long_url"}"""

        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.InvalidURL
    }

    @Test
    fun `error body for field alias maps to InvalidAlias`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "ab", { fail("unexpected success") }, { error = it })
        val body =
            """{"error":"alias: Value error, alphanumeric alias must be 3-16 characters","code":"validation_error","field":"alias"}"""

        req.deliverError(VolleyError(NetworkResponse(422, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.InvalidAlias
    }

    @Test
    fun `409 status maps to AliasAlreadyExists`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })
        val body = """{"error":"Alias is already in use","code":"conflict"}"""

        req.deliverError(VolleyError(NetworkResponse(409, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.AliasAlreadyExists
    }

    @Test
    fun `422 detail with an alias location maps to InvalidAlias`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "ab", { fail("unexpected success") }, { error = it })
        val body = """{"detail":[{"type":"string_too_short","loc":["body","alias"],"msg":"String should have at least 3 characters"}]}"""

        req.deliverError(VolleyError(NetworkResponse(422, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.InvalidAlias
    }

    @Test
    fun `422 detail with a long_url location maps to InvalidURL`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, "notaurl", "", { fail("unexpected success") }, { error = it })
        val body = """{"detail":[{"type":"url_parsing","loc":["body","long_url"],"msg":"Input should be a valid URL"}]}"""

        req.deliverError(VolleyError(NetworkResponse(422, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.InvalidURL
    }

    @Test
    fun `422 detail without a known location falls back to Custom with the raw body`() {
        listOf(
            """{"detail":[{"loc":["body","password"],"msg":"bad"}]}""",
            """{"detail":[{"msg":"no location"}]}""",
            """{"detail":["text"]}""",
            """{"detail":"Not found"}""",
        ).forEach { body ->
            var error: GenerateURLError? = null
            val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

            req.deliverError(VolleyError(NetworkResponse(422, body.toByteArray(), false, 0L, emptyList())))

            error shouldBe GenerateURLError.Custom(422, body)
        }
    }

    @Test
    fun `error body for another field falls back to Custom with the server message`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })
        val body = """{"error":"Bad body","code":"validation_error","field":"password"}"""

        req.deliverError(VolleyError(NetworkResponse(422, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(422, "Bad body")
    }

    @Test
    fun `429 status with an unrecognized body maps to RateLimitExceeded`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })
        val body = """{"message":"rate limited"}"""

        req.deliverError(VolleyError(NetworkResponse(429, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.RateLimitExceeded
    }

    @Test
    fun `unrecognized error body falls back to Custom with the raw body`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })
        val body = """{"message":"server exploded"}"""

        req.deliverError(VolleyError(NetworkResponse(500, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(500, body)
    }

    @Test
    fun `plain-text 503 error body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(503, "Service Unavailable".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(503)
    }

    @Test
    fun `HTML 404 error body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })
        val body = "<html><body><h1>404 Not Found</h1></body></html>"

        req.deliverError(VolleyError(NetworkResponse(404, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(404)
    }

    @Test
    fun `error body that is not a JSON object maps to Unknown with the status code`() {
        listOf("""["UrlError"]""", "null", """{"UrlError":"Invalid URL"""").forEach { body ->
            var error: GenerateURLError? = null
            val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

            req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

            error shouldBe GenerateURLError.Unknown(400)
        }
    }

    @Test
    fun `429 status with a plain-text body maps to RateLimitExceeded`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(429, "Too Many Requests".toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.RateLimitExceeded
    }

    @Test
    fun `429 status with an empty body maps to RateLimitExceeded`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(429, ByteArray(0), false, 0L, emptyList())))

        error shouldBe GenerateURLError.RateLimitExceeded
    }

    @Test
    fun `429 status with a null body maps to RateLimitExceeded`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(429, null, false, 0L, emptyList())))

        error shouldBe GenerateURLError.RateLimitExceeded
    }

    @Test
    fun `error with an empty body maps to Unknown with the status code`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })

        req.deliverError(VolleyError(NetworkResponse(400, ByteArray(0), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Unknown(400)
    }

    @Test
    fun `alias config follows the v1 rules`() {
        val config = Spoome.Default.aliasConfig

        config.minAliasLength shouldBe 3
        config.maxAliasLength shouldBe 16
        config.allowedAliasCharacters shouldBe "a-z, A-Z, 0-9, _, -"
        config.isAliasValid("abc_1-Z") shouldBe true
        config.isAliasValid("abc.123") shouldBe false
        config.isAliasValid("äbc") shouldBe false
        config.isAliasValid("") shouldBe false
        config.aliasLength("abc_1-Z") shouldBe 7
    }

    @Test
    fun `429 status with the v1 rate limit body maps to RateLimitExceeded`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success") }, { error = it })
        val body = """{"error":"Too many requests","code":"rate_limit_exceeded"}"""

        req.deliverError(VolleyError(NetworkResponse(429, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.RateLimitExceeded
    }

    @Test
    fun `getURLClickCount reads total_clicks from the public stats since the link was added`() {
        var clicks: Int? = -1
        val added = ZonedDateTime.now().minusDays(1)
        val url = URL("https://spoo.me/abc", longURL, Spoome.Default, false, "", "", added)

        Spoome.Default.getURLClickCount(queue, url) { clicks = it }
        val request = queue.added.single()
        request.method shouldBe Request.Method.GET
        request.url.substringBefore("?") shouldBe "https://spoo.me/api/v1/public/stats/abc"
        request.query()["start_date"] shouldBe added.toInstant().toString()
        request.deliverResponse(JSONObject("""{"generation":"v2","stats":{"summary":{"total_clicks":42,"unique_clicks":40}}}"""))

        clicks shouldBe 42
        queue.added.size shouldBe 1
    }

    @Test
    fun `getURLClickCount sums consecutive windows of at most 89 days back to the creation`() {
        var clicks: Int? = -1
        val added = ZonedDateTime.now().minusDays(200)
        val url = URL("https://spoo.me/abc", longURL, Spoome.Default, false, "", "", added)

        Spoome.Default.getURLClickCount(queue, url) { clicks = it }
        queue.added[0].deliverResponse(JSONObject("""{"stats":{"summary":{"total_clicks":10}}}"""))
        clicks shouldBe -1
        queue.added[1].deliverResponse(JSONObject("""{"stats":{"summary":{"total_clicks":5}}}"""))
        clicks shouldBe -1
        queue.added[2].deliverResponse(JSONObject("""{"stats":{"summary":{"total_clicks":1}}}"""))

        clicks shouldBe 16
        queue.added.size shouldBe 3
        val windows = queue.added.map { it.query().let { q -> Instant.parse(q["start_date"]!!) to Instant.parse(q["end_date"]!!) } }
        Duration.between(windows[0].first, windows[0].second) shouldBe Duration.ofDays(89)
        Duration.between(windows[1].first, windows[1].second) shouldBe Duration.ofDays(89)
        windows[1].second shouldBe windows[0].first.minusMillis(1)
        windows[2].second shouldBe windows[1].first.minusMillis(1)
        windows[2].first shouldBe added.toInstant()
    }

    @Test
    fun `getURLClickCount asks for an empty window when the link was added in the future`() {
        val url = URL("https://spoo.me/abc", longURL, Spoome.Default, false, "", "", ZonedDateTime.now().plusDays(1))

        Spoome.Default.getURLClickCount(queue, url) { }

        val query = queue.added.single().query()
        query["start_date"] shouldBe query["end_date"]
    }

    @Test
    fun `getURLClickCount resolves to null when the response has no stats`() {
        var clicks: Int? = -1
        val url = URL("https://spoo.me/abc", longURL, Spoome.Default, false, "", "", ZonedDateTime.now())

        Spoome.Default.getURLClickCount(queue, url) { clicks = it }
        queue.added.single().deliverResponse(JSONObject("""{"generation":"v2","link":{}}"""))

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount resolves to null when the summary has no total_clicks`() {
        var clicks: Int? = -1
        val url = URL("https://spoo.me/abc", longURL, Spoome.Default, false, "", "", ZonedDateTime.now())

        Spoome.Default.getURLClickCount(queue, url) { clicks = it }
        queue.added.single().deliverResponse(JSONObject("""{"stats":{"summary":{}}}"""))

        clicks shouldBe null
    }

    @Test
    fun `getURLClickCount resolves to null on error`() {
        var clicks: Int? = -1
        val url = URL("https://spoo.me/abc", longURL, Spoome.Default, false, "", "", ZonedDateTime.now())

        Spoome.Default.getURLClickCount(queue, url) { clicks = it }
        queue.added.single().deliverError(VolleyError("no network"))

        clicks shouldBe null
    }

    @Test
    fun `create request with a JSON null short_url maps to Unknown`() {
        var error: GenerateURLError? = null
        val req = Spoome.Default.getCreateRequest(queue, longURL, "abc", { fail("unexpected success: $it") }, { error = it })

        req.deliverResponse(JSONObject("""{"short_url":null}"""))

        error shouldBe GenerateURLError.Unknown(200)
    }
}

private fun Request<*>.query(): Map<String, String> =
    url.substringAfter("?").split("&").associate { pair ->
        pair.substringBefore("=") to URLDecoder.decode(pair.substringAfter("="), "UTF-8")
    }
