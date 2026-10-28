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
    fun `create request without an alias uses the legacy emoji endpoint`() {
        val req = Spoome.Emoji.getCreateRequest(queue, "https://example.com?a=1&b=2", "", { }, { fail("unexpected error") })

        req.method shouldBe Request.Method.POST
        req.url shouldBe "https://spoo.me/emoji?url=https%3A%2F%2Fexample.com%3Fa%3D1%26b%3D2"
        req.body shouldBe null
        req.headers["Accept"] shouldBe "application/json"
    }

    @Test
    fun `legacy emoji response with an http short url succeeds with the https one`() {
        var result: String? = null
        val req = Spoome.Emoji.getCreateRequest(queue, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse(JSONObject("""{"short_url":"http://spoo.me/🏹🕝🕙","domain":"spoo.me","original_url":"https://example.com"}"""))

        result shouldBe "https://spoo.me/🏹🕝🕙"
    }

    @Test
    fun `legacy emoji UrlError body maps to InvalidURL`() {
        var error: GenerateURLError? = null
        val req = Spoome.Emoji.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })
        val body = """{"UrlError":"Invalid URL, URL must have a valid protocol"}"""

        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.InvalidURL
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
        val body = """{"error":"emoji alias must be 1-15 emoji","code":"validation_error","field":"alias"}"""

        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.InvalidAlias
    }

    @Test
    fun `isAliasValid accepts emoji characters, rejects plain text`() {
        Spoome.Emoji.aliasConfig.isAliasValid("😀") shouldBe true
        Spoome.Emoji.aliasConfig.isAliasValid("abc") shouldBe false
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
