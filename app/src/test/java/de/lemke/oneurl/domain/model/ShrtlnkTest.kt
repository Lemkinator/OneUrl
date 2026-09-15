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
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Request#parseNetworkResponse/#parseNetworkError are protected - only Volley's own RequestQueue
// normally calls them. Tests stand in for the queue, so they reach them via reflection instead of
// a real round-trip.
private fun Request<*>.callParseNetworkResponse(response: NetworkResponse?): Response<*> {
    val method = Request::class.java.getDeclaredMethod("parseNetworkResponse", NetworkResponse::class.java)
    method.isAccessible = true
    return method.invoke(this, response) as Response<*>
}

private fun Request<*>.callParseNetworkError(volleyError: VolleyError?): VolleyError {
    val method = Request::class.java.getDeclaredMethod("parseNetworkError", VolleyError::class.java)
    method.isAccessible = true
    return method.invoke(this, volleyError) as VolleyError
}

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ShrtlnkTest {
    private val context = mockk<Context>()
    private val realContext = ApplicationProvider.getApplicationContext<Context>()
    private val longURL = "https://example.com"

    @Test
    fun `parseNetworkResponse succeeds when the redirect header contains a key`() {
        var result: String? = null
        val req = Shrtlnk.getCreateRequest(context, longURL, "", { result = it }, { fail("unexpected error: $it") })
        val networkResponse =
            NetworkResponse(204, ByteArray(0), false, 0L, listOf(Header("X-Remix-Redirect", "/new-link-added?key=h1aja4")))

        val response = req.callParseNetworkResponse(networkResponse)

        result shouldBe "https://www.shrtlnk.dev/h1aja4"
        response.isSuccess shouldBe true
        response.result shouldBe "https://www.shrtlnk.dev/h1aja4"
    }

    @Test
    fun `parseNetworkResponse fails with Unknown when the network response is null`() {
        var error: GenerateURLError? = null
        val req = Shrtlnk.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        val response = req.callParseNetworkResponse(null)

        error shouldBe GenerateURLError.Unknown()
        response.isSuccess shouldBe false
    }

    @Test
    fun `parseNetworkResponse fails with Unknown when the redirect header is missing`() {
        var error: GenerateURLError? = null
        val req = Shrtlnk.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })
        val networkResponse = NetworkResponse(204, ByteArray(0), false, 0L, listOf(Header("Server", "Vercel")))

        val response = req.callParseNetworkResponse(networkResponse)

        error shouldBe GenerateURLError.Unknown()
        response.isSuccess shouldBe false
    }

    @Test
    fun `parseNetworkError maps NoConnectionError to ServiceOffline`() {
        var error: GenerateURLError? = null
        val req = Shrtlnk.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })
        val volleyError = NoConnectionError()

        val returned = req.callParseNetworkError(volleyError)

        error shouldBe GenerateURLError.ServiceOffline
        returned shouldBe volleyError
    }

    @Test
    fun `parseNetworkError maps any other error to Unknown`() {
        var error: GenerateURLError? = null
        val req = Shrtlnk.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })
        val volleyError = VolleyError("no network")

        val returned = req.callParseNetworkError(volleyError)

        error shouldBe GenerateURLError.Unknown()
        returned shouldBe volleyError
    }

    @Test
    fun `parseNetworkError maps a null error to Unknown and returns a fallback error`() {
        var error: GenerateURLError? = null
        val req = Shrtlnk.getCreateRequest(context, longURL, "", { fail("unexpected success") }, { error = it })

        val returned = req.callParseNetworkError(null)

        error shouldBe GenerateURLError.Unknown()
        returned.message shouldBe "unknown error"
    }

    @Test
    fun `getInfoContents returns the redirect hint info`() {
        val infoContents = Shrtlnk.getInfoContents(realContext)

        infoContents.size shouldBe 1
        infoContents[0].title shouldBe realContext.getString(R.string.redirect_hint)
        infoContents[0].linkOrDescription shouldBe realContext.getString(R.string.redirect_hint_text)
    }
}
