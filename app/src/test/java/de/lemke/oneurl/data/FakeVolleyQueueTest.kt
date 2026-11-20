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

package de.lemke.oneurl.data

import android.app.Application
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class FakeVolleyQueueTest {
    @Test
    fun answerThroughQueue_rethrowsAnExceptionFromTheSuccessListener() {
        val request =
            StringRequest(
                Request.Method.GET,
                "https://example.com/",
                Response.Listener { throw IllegalStateException("listener crashed") },
                Response.ErrorListener {},
            )

        val thrown = shouldThrow<IllegalStateException> { request.answerThroughQueue(NetworkResponse("ok".toByteArray())) }

        thrown.message shouldBe "listener crashed"
    }
}
