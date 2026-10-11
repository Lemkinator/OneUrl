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
import de.lemke.oneurl.data.FakeVolleyQueue
import de.lemke.oneurl.data.deliverResponse
import de.lemke.oneurl.data.parseResponse
import io.kotest.matchers.shouldBe
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
class VgdTest {
    private val queue = FakeVolleyQueue()
    private val longURL = "https://example.com"

    @Test
    fun `succeeds when the response contains shorturl`() {
        var result: String? = null
        val req = VgdIsgd.Vgd.getCreateRequest(queue, longURL, "abc", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse(JSONObject("""{"shorturl":"https://v.gd/abc"}"""))

        result shouldBe "https://v.gd/abc"
    }

    @Test
    fun `request url includes shorturl and logstats params when alias is given`() {
        val req = VgdIsgd.Vgd.getCreateRequest(queue, longURL, "abc", { }, { fail("unexpected error") })

        req.url.contains("&shorturl=abc&logstats=1") shouldBe true
    }

    @Test
    fun `errorcode 4 maps to ServiceTemporarilyUnavailable with the v_gd base url`() {
        var error: GenerateURLError? = null
        val req = VgdIsgd.Vgd.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverResponse(JSONObject("""{"errorcode":"4","errormessage":"maintenance"}"""))

        error shouldBe GenerateURLError.ServiceTemporarilyUnavailable(VgdIsgd.Vgd.baseURL)
    }

    @Test
    fun `error ServiceOffline on NoConnectionError`() {
        var error: GenerateURLError? = null
        val req = VgdIsgd.Vgd.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        req.deliverError(NoConnectionError())

        error shouldBe GenerateURLError.ServiceOffline
    }

    @Test
    fun `error ServiceTemporarilyUnavailable when the reply is not JSON`() {
        var error: GenerateURLError? = null
        val req = VgdIsgd.Vgd.getCreateRequest(queue, longURL, "", { fail("unexpected success") }, { error = it })

        val parsed =
            req.parseResponse(
                NetworkResponse(200, "Error, database insert failed".toByteArray(), false, 0L, emptyList()),
            )
        req.deliverError(parsed.error)

        error shouldBe GenerateURLError.ServiceTemporarilyUnavailable("https://v.gd")
    }

    @Test
    fun `sanitizeLongURL trims whitespace and encodes ampersands`() {
        VgdIsgd.Vgd.sanitizeLongURL(" https://example.com?a=1&b=2 ") shouldBe "https://example.com?a=1%26b=2"
    }

    @Test
    fun `getAnalyticsURL builds a stats url on the v_gd domain`() {
        VgdIsgd.Vgd.getAnalyticsURL("abc") shouldBe "https://v.gd/stats.php?url=abc"
    }
}
