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
    fun `succeeds with the trimmed short url when the response contains one`() {
        var result: String? = null
        val req = Spoome.Emoji.getCreateRequest(queue, longURL, "😀", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse(JSONObject().put("short_url", " https://spoo.me/emoji/abc "))

        result shouldBe "https://spoo.me/emoji/abc"
    }

    @Test
    fun `error body with an already-exists EmojiError maps to AliasAlreadyExists`() {
        var error: GenerateURLError? = null
        val req = Spoome.Emoji.getCreateRequest(queue, longURL, "😀", { fail("unexpected success") }, { error = it })
        val body = """{"EmojiError":"Emoji already exists"}"""

        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.AliasAlreadyExists
    }

    @Test
    fun `error body with an invalid EmojiError maps to InvalidAlias`() {
        var error: GenerateURLError? = null
        val req = Spoome.Emoji.getCreateRequest(queue, longURL, "😀", { fail("unexpected success") }, { error = it })
        val body = """{"EmojiError":"Invalid emoji"}"""

        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.InvalidAlias
    }

    @Test
    fun `error body with an unrecognized EmojiError falls back to Custom`() {
        var error: GenerateURLError? = null
        val req = Spoome.Emoji.getCreateRequest(queue, longURL, "😀", { fail("unexpected success") }, { error = it })
        val body = """{"EmojiError":"something else entirely"}"""

        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(400, "something else entirely")
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
