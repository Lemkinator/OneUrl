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
import de.lemke.oneurl.data.FakeVolleyQueue
import de.lemke.oneurl.data.deliverResponse
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
class TlyIbitlyTest {
    private val queue = FakeVolleyQueue()
    private val longURL = "https://example.com"

    @Test
    fun `name and enabled reflect the ibit_ly provider`() {
        Tly.Ibitly.name shouldBe "t.ly (ibit.ly)"
        Tly.Ibitly.enabled shouldBe true
    }

    @Test
    fun `request body targets the ibit_ly provider`() {
        val req = Tly.Ibitly.getCreateRequest(queue, longURL, "", { }, { fail("unexpected error") })

        JSONObject(String(req.body!!, Charsets.UTF_8)).getString("provider") shouldBe "ibit.ly"
    }

    @Test
    fun `succeeds when the response contains a short_url`() {
        var result: String? = null
        val req = Tly.Ibitly.getCreateRequest(queue, longURL, "", { result = it }, { fail("unexpected error: $it") })

        req.deliverResponse(JSONObject("""{"short_url":"https://t.ly/abc"}"""))

        result shouldBe "https://t.ly/abc"
    }
}
