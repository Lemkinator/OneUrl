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
import com.android.volley.Header
import com.android.volley.NetworkResponse
import com.android.volley.NoConnectionError
import com.android.volley.VolleyError
import de.lemke.oneurl.R
import de.lemke.oneurl.data.FakeVolleyQueue
import de.lemke.oneurl.data.answerThroughQueue
import de.lemke.oneurl.data.failThroughQueue
import io.kotest.matchers.shouldBe
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ShrtlnkTest {
    private val queue = FakeVolleyQueue()
    private val realContext = ApplicationProvider.getApplicationContext<Context>()
    private val longURL = "https://example.com"
    private val redirectResponse =
        NetworkResponse(204, ByteArray(0), false, 0L, listOf(Header("X-Remix-Redirect", "/new-link-added?key=h1aja4")))

    @Test
    fun `parseNetworkResponse succeeds when the redirect header contains a key`() {
        var result: String? = null
        val req = Shrtlnk.getCreateRequest(queue, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.answerThroughQueue(redirectResponse)

        result shouldBe "https://www.shrtlnk.dev/h1aja4"
    }

    @Test
    fun `parseNetworkResponse fails with Unknown when the redirect header is missing`() {
        var error: GenerateURLError? = null
        val req = Shrtlnk.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.answerThroughQueue(NetworkResponse(204, ByteArray(0), false, 0L, listOf(Header("Server", "Vercel"))))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `parseNetworkResponse fails with Unknown when the response has no headers`() {
        var error: GenerateURLError? = null
        val req = Shrtlnk.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.answerThroughQueue(NetworkResponse(204, ByteArray(0), false, 0L, null as List<Header>?))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `parseNetworkError maps NoConnectionError to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Shrtlnk.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.failThroughQueue(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `parseNetworkError maps any other error to Unknown`() {
        var error: GenerateURLError? = null
        val req = Shrtlnk.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.failThroughQueue(VolleyError("no network"))

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `getParams sends the long url as the request body`() {
        val req = Shrtlnk.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { fail("unexpected error: $it") })

        req.body.decodeToString() shouldBe "url=https%3A%2F%2Fexample.com&"
    }

    @Test
    fun `parseNetworkResponse fails with Unknown when the success callback throws`() {
        var error: GenerateURLError? = null
        val req = Shrtlnk.getCreateRequest(queue, longURL, "", { error("boom") }, { error = it })

        req.answerThroughQueue(redirectResponse)

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `getInfoContents returns the redirect hint info`() {
        val infoContents = Shrtlnk.getInfoContents(realContext)

        infoContents.size shouldBe 1
        infoContents[0].title shouldBe realContext.getString(R.string.redirect_hint)
        infoContents[0].linkOrDescription shouldBe realContext.getString(R.string.redirect_hint_text)
    }
}
