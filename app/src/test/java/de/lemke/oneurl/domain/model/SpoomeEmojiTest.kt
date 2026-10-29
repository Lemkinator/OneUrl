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
import com.android.volley.Request
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

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SpoomeEmojiTest {
    private val queue = FakeVolleyQueue()
    private val realContext = ApplicationProvider.getApplicationContext<Context>()
    private val longURL = "https://example.com"

    @Test
    fun `create request with an alias posts it as JSON to the v1 endpoint`() {
        val req = Spoome.Emoji.getCreateRequest(queue, longURL, "😀😁", { }, { fail("unexpected error") })

        req.method shouldBe Request.Method.POST
        req.url shouldBe "https://spoo.me/api/v1/shorten"
        val body = JSONObject(String(req.body, Charsets.UTF_8))
        body.getString("long_url") shouldBe longURL
        body.getString("alias") shouldBe "😀😁"
    }

    @Test
    fun `create request without an alias asks the v1 endpoint for a generated emoji alias`() {
        val req = Spoome.Emoji.getCreateRequest(queue, "https://example.com?a=1&b=2", "", { }, { fail("unexpected error") })

        req.method shouldBe Request.Method.POST
        req.url shouldBe "https://spoo.me/api/v1/shorten"
        String(req.body, Charsets.UTF_8) shouldBe """{"long_url":"https:\/\/example.com?a=1&b=2","alias_type":"emoji"}"""
        req.headers["Accept"] shouldBe "application/json"
    }

    @Test
    fun `create request with an alias does not send alias_type`() {
        val req = Spoome.Emoji.getCreateRequest(queue, longURL, "😀", { }, { fail("unexpected error") })

        JSONObject(String(req.body, Charsets.UTF_8)).has("alias_type") shouldBe false
    }

    @Test
    fun `generated emoji response succeeds with the short url`() {
        var result: String? = null
        val req = Spoome.Emoji.getCreateRequest(queue, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse(
            JSONObject(
                """{"id":"6ae26c6084c578b80eba000c","alias":"🌮🧗🥌","short_url":"https://spoo.me/🌮🧗🥌"}""",
            ),
        )

        result shouldBe "https://spoo.me/🌮🧗🥌"
    }

    @Test
    fun `429 status on the generated emoji request maps to RateLimitExceeded`() {
        var error: GenerateURLError? = null
        val req = Spoome.Emoji.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })
        val body = """{"error":"Too many requests","code":"rate_limit_exceeded"}"""

        req.deliverError(VolleyError(NetworkResponse(429, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.RateLimitExceeded
    }

    @Test
    fun `succeeds with the short url when the response contains one`() {
        var result: String? = null
        val req = Spoome.Emoji.getCreateRequest(queue, longURL, "😀", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse(JSONObject().put("short_url", " https://spoo.me/😀 "))

        result shouldBe "https://spoo.me/😀"
    }

    @Test
    fun `409 status maps to AliasAlreadyExists`() {
        var error: GenerateURLError? = null
        val req = Spoome.Emoji.getCreateRequest(queue, longURL, "😀", { fail("unexpected success") }, { error = it })
        val body = """{"error":"Alias is already in use","code":"conflict"}"""

        req.deliverError(VolleyError(NetworkResponse(409, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.AliasAlreadyExists
    }

    @Test
    fun `error body for field alias maps to InvalidAlias`() {
        var error: GenerateURLError? = null
        val req = Spoome.Emoji.getCreateRequest(queue, longURL, "a😀", { fail("unexpected success") }, { error = it })
        val body =
            """{"error":"alias: Value error, alias must be alphanumeric (a-z, A-Z, 0-9, _, -) or emoji-only","code":"validation_error","field":"alias"}"""

        req.deliverError(VolleyError(NetworkResponse(422, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.InvalidAlias
    }

    @Test
    fun `alias config follows the v1 emoji rules`() {
        val config = Spoome.Emoji.aliasConfig

        config.minAliasLength shouldBe 1
        config.maxAliasLength shouldBe 15
        config.allowedAliasCharacters shouldBe "Emojis"
    }

    @Test
    fun `isAliasValid accepts single emoji and rejects everything else`() {
        val config = Spoome.Emoji.aliasConfig

        config.isAliasValid("😀") shouldBe true
        config.isAliasValid("🌮🧗🥌") shouldBe true
        config.isAliasValid("abc") shouldBe false
        config.isAliasValid("a😀") shouldBe false
        config.isAliasValid("") shouldBe false
        config.isAliasValid("🇩🇪") shouldBe false
        config.isAliasValid("👨\u200d👩\u200d👧") shouldBe false
        config.isAliasValid("👍🏽") shouldBe false
        config.isAliasValid("❤\uFE0F") shouldBe false
    }

    @Test
    fun `aliasLength counts emoji instead of UTF-16 characters`() {
        val config = Spoome.Emoji.aliasConfig

        config.aliasLength("😀") shouldBe 1
        config.aliasLength("🌮🧗🥌") shouldBe 3
        config.aliasLength("🇩🇪") shouldBe 2
        config.aliasLength("👨\u200d👩\u200d👧") shouldBe 5
        config.aliasLength("😀".repeat(15)) shouldBe 15
    }

    @Test
    fun `getURLClickCount reads the public stats of the percent-encoded emoji alias`() {
        var clicks: Int? = -1
        val url = URL("https://spoo.me/😀", longURL, Spoome.Emoji, false, "", "", ZonedDateTime.now())

        Spoome.Emoji.getURLClickCount(queue, url) { clicks = it }
        val request = queue.added.single()
        request.url.substringBefore("?") shouldBe "https://spoo.me/api/v1/public/stats/%F0%9F%98%80"
        request.url.substringAfter("?").startsWith("start_date=") shouldBe true
        request.deliverResponse(JSONObject("""{"generation":"v2","stats":{"summary":{"total_clicks":7}}}"""))

        clicks shouldBe 7
    }

    @Test
    fun `getTipsCardTitleAndInfo returns the info title and emoji text`() {
        val (title, info) = Spoome.Emoji.getTipsCardTitleAndInfo(realContext)

        title shouldBe realContext.getString(commonutilsR.string.commonutils_info)
        info shouldBe realContext.getString(R.string.emoji_text)
    }

    @Test
    fun `getInfoContents returns the emoji, alias and analytics info`() {
        val infoContents = Spoome.Emoji.getInfoContents(realContext)

        infoContents.size shouldBe 3
        infoContents[0].title shouldBe realContext.getString(R.string.emoji)
        infoContents[0].linkOrDescription shouldBe realContext.getString(R.string.emoji_text)
        infoContents[1].title shouldBe realContext.getString(R.string.alias)
        infoContents[1].linkOrDescription shouldBe
            realContext.getString(
                R.string.alias_text,
                Spoome.Emoji.aliasConfig.minAliasLength,
                Spoome.Emoji.aliasConfig.maxAliasLength,
                Spoome.Emoji.aliasConfig.allowedAliasCharacters,
            )
        infoContents[2].title shouldBe realContext.getString(R.string.analytics)
        infoContents[2].linkOrDescription shouldBe realContext.getString(R.string.analytics_text)
    }
}
