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

package de.lemke.oneurl.domain

import android.app.Application
import android.content.Context
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Request#deliverResponse(T) (and StringRequest's erasure bridge deliverResponse(Object)) is
// protected - only Volley's own RequestQueue can normally trigger it. Tests stand in for the
// queue, so they reach it via reflection instead of a real network round-trip.
private fun Request<*>.deliverStringResponse(response: String) {
    val method = Request::class.java.getDeclaredMethod("deliverResponse", Any::class.java)
    method.isAccessible = true
    method.invoke(this, response)
}

// The compiled response listener null-checks its parameter before use; invoking it through the
// erasure bridge with a null argument reaches that check without a real malformed network reply.
private fun Request<*>.deliverNullResponse() {
    val method = Request::class.java.getDeclaredMethod("deliverResponse", Any::class.java)
    method.isAccessible = true
    method.invoke(this, null)
}

// Volley's Request/VolleyLog touch android.util.Log/SystemClock in static initializers, which
// crash under the default unit-test "not mocked" stub jar, hence Robolectric here.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class GetURLTitleUseCaseTest {
    private val context = mockk<Context>()
    private val requestQueue = mockk<RequestQueueSingleton>()
    private val getURLTitle = GetURLTitleUseCase(context)

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
    fun `extracts the title from the response html`() =
        runTest {
            every { requestQueue.addToRequestQueue(any<StringRequest>()) } answers {
                firstArg<StringRequest>().deliverStringResponse("<html><head><title> My Page </title></head></html>")
            }

            getURLTitle("example.com") shouldBe "My Page"
        }

    @Test
    fun `returns null when the response has no title tag`() =
        runTest {
            every { requestQueue.addToRequestQueue(any<StringRequest>()) } answers {
                firstArg<StringRequest>().deliverStringResponse("<html><body>no title here</body></html>")
            }

            getURLTitle("example.com") shouldBe null
        }

    @Test
    fun `returns null on a network error`() =
        runTest {
            every { requestQueue.addToRequestQueue(any<StringRequest>()) } answers {
                firstArg<StringRequest>().deliverError(VolleyError(NetworkResponse(404, ByteArray(0), false, 0L, emptyList())))
            }

            getURLTitle("example.com") shouldBe null
        }

    @Test
    fun `returns null and logs when parsing the response throws`() =
        runTest {
            every { requestQueue.addToRequestQueue(any<StringRequest>()) } answers {
                firstArg<StringRequest>().deliverNullResponse()
            }

            getURLTitle("example.com") shouldBe null
        }

    @Test
    fun `ignores a response delivered after the continuation already resumed`() =
        runTest {
            every { requestQueue.addToRequestQueue(any<StringRequest>()) } answers {
                val request = firstArg<StringRequest>()
                request.deliverStringResponse("<html><head><title>First</title></head></html>")
                request.deliverStringResponse("<html><head><title>Second</title></head></html>")
            }

            getURLTitle("example.com") shouldBe "First"
        }

    @Test
    fun `ignores a network error delivered after the continuation already resumed`() =
        runTest {
            every { requestQueue.addToRequestQueue(any<StringRequest>()) } answers {
                val request = firstArg<StringRequest>()
                val error = VolleyError(NetworkResponse(404, ByteArray(0), false, 0L, emptyList()))
                request.deliverError(error)
                request.deliverError(error)
            }

            getURLTitle("example.com") shouldBe null
        }

    @Test
    fun `cancelling the coroutine cancels the underlying request`() =
        runTest {
            val requestSlot = slot<StringRequest>()
            every { requestQueue.addToRequestQueue(capture(requestSlot)) } just Runs

            val job = launch { getURLTitle("example.com") }
            runCurrent()
            job.cancel()
            runCurrent()

            requestSlot.captured.isCanceled shouldBe true
        }
}
