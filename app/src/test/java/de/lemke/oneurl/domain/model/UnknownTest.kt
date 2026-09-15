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
import com.android.volley.Request
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import io.mockk.mockk
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
    private val context = mockk<Context>()
    private val unknown = Unknown()

    @Test
    fun `is not enabled`() {
        unknown.enabled.shouldBeFalse()
    }

    @Test
    fun `getCreateRequest always reports Unknown and never succeeds`() {
        var error: GenerateURLError? = null

        unknown.getCreateRequest(context, "https://example.com", "alias", { fail("unexpected success") }, { error = it })

        error shouldBe GenerateURLError.Unknown()
    }

    @Test
    fun `getCreateRequest returns a request whose network response parses to null`() {
        val req = unknown.getCreateRequest(context, "https://example.com", "alias", {}, {})

        val method = req.javaClass.getMethod("parseNetworkResponse", com.android.volley.NetworkResponse::class.java)
        method.isAccessible = true

        method.invoke(req, null) shouldBe null
    }

    @Test
    fun `getCreateRequest returns a request whose deliverResponse is a no-op`() {
        var errorCount = 0
        val req = unknown.getCreateRequest(context, "https://example.com", "alias", { fail("unexpected success") }, { errorCount++ })

        val method = Request::class.java.getDeclaredMethod("deliverResponse", Any::class.java)
        method.isAccessible = true
        method.invoke(req, "anything")

        errorCount shouldBe 1
    }
}
