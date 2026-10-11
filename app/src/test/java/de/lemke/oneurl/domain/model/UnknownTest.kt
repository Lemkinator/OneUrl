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
import com.android.volley.Request
import de.lemke.oneurl.data.FakeVolleyQueue
import de.lemke.oneurl.data.deliverResponse
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// android.util.Log touches a static native binding under the default unit-test "not mocked" stub
// jar, hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class UnknownTest {
    private val queue = FakeVolleyQueue()
    private val unknown = Unknown()

    @Test
    fun `is not enabled`() {
        unknown.enabled.shouldBeFalse()
    }

    @Test
    fun `getCreateRequest always reports Unknown and never succeeds`() {
        var error: GenerateURLError? = null

        unknown.getCreateRequest(queue, "https://example.com", "alias", { fail("unexpected success") }, { error = it })

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `getCreateRequest returns a request whose network response parses to null`() {
        val req = unknown.getCreateRequest(queue, "https://example.com", "alias", {}, {})

        val method = Request::class.java.getDeclaredMethod("parseNetworkResponse", NetworkResponse::class.java)
        method.isAccessible = true

        method.invoke(req, null) shouldBe null
    }

    @Test
    fun `getCreateRequest returns a request whose deliverResponse is a no-op`() {
        var errorCount = 0
        val req = unknown.getCreateRequest(queue, "https://example.com", "alias", { fail("unexpected success") }, { errorCount++ })

        req.deliverResponse("anything")

        errorCount shouldBe 1
    }
}
