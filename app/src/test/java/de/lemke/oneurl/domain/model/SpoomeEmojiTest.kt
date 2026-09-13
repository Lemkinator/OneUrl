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
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.VolleyError
import de.lemke.oneurl.domain.generateURL.GenerateURLError
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
class SpoomeEmojiTest {
    private val context = mockk<Context>()
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
    fun `succeeds with the trimmed short url when the response contains one`() {
        var result: String? = null
        val req = Spoome.Emoji.getCreateRequest(context, longURL, "😀", { result = it }, { fail("unexpected error: $it") })

        req.deliverJsonResponse(JSONObject().put("short_url", " https://spoo.me/emoji/abc "))

        result shouldBe "https://spoo.me/emoji/abc"
    }

    @Test
    fun `error body with an already-exists EmojiError maps to AliasAlreadyExists`() {
        var error: GenerateURLError? = null
        val req = Spoome.Emoji.getCreateRequest(context, longURL, "😀", { fail("unexpected success") }, { error = it })
        val body = """{"EmojiError":"Emoji already exists"}"""

        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.AliasAlreadyExists
    }

    @Test
    fun `error body with an invalid EmojiError maps to InvalidAlias`() {
        var error: GenerateURLError? = null
        val req = Spoome.Emoji.getCreateRequest(context, longURL, "😀", { fail("unexpected success") }, { error = it })
        val body = """{"EmojiError":"Invalid emoji"}"""

        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.InvalidAlias
    }

    @Test
    fun `error body with an unrecognized EmojiError falls back to Custom`() {
        var error: GenerateURLError? = null
        val req = Spoome.Emoji.getCreateRequest(context, longURL, "😀", { fail("unexpected success") }, { error = it })
        val body = """{"EmojiError":"something else entirely"}"""

        req.deliverError(VolleyError(NetworkResponse(400, body.toByteArray(), false, 0L, emptyList())))

        error shouldBe GenerateURLError.Custom(400, "something else entirely")
    }

    @Test
    fun `isAliasValid accepts emoji characters, rejects plain text`() {
        Spoome.Emoji.aliasConfig.isAliasValid("😀") shouldBe true
        Spoome.Emoji.aliasConfig.isAliasValid("abc") shouldBe false
    }
}
