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
import android.os.Handler
import android.os.Looper
import com.android.volley.CacheDispatcher
import com.android.volley.Network
import com.android.volley.NetworkDispatcher
import com.android.volley.NetworkResponse
import com.android.volley.NoConnectionError
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.VolleyError
import com.android.volley.toolbox.NoCache
import com.android.volley.toolbox.StringRequest
import de.lemke.oneurl.di.NetworkModule
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Provider
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class DefaultVolleyQueueTest {
    private val url = "https://example.com/page"
    private val createdQueues = mutableListOf<RequestQueue>()

    @After
    fun stopRequestQueues() = createdQueues.forEach(RequestQueue::stop)

    private fun dispatcherThreads() =
        Thread.getAllStackTraces().keys.filterTo(HashSet()) { it is CacheDispatcher || it is NetworkDispatcher }

    private fun requestQueueAnswering(network: Network): RequestQueue =
        RequestQueue(NoCache(), network, 1, GuardedDelivery(Handler(Looper.getMainLooper()))).apply { start() }.also(createdQueues::add)

    private fun queueAnsweringBody(): VolleyQueue {
        val answering =
            requestQueueAnswering { if (it.url == MALFORMED_URL) throw NoConnectionError() else NetworkResponse("body".toByteArray()) }
        return DefaultVolleyQueue(Provider { answering })
    }

    private fun deliverResponse(queue: VolleyQueue): String? {
        val delivered = LinkedBlockingQueue<String>()

        queue.add(StringRequest(Request.Method.GET, url, delivered::add, {}))

        return delivered.poll(DELIVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    @Test
    fun `add delivers the network response`() {
        deliverResponse(queueAnsweringBody()) shouldBe "body"
    }

    @Test
    fun `add turns the Volley cache off for the request`() {
        val request = StringRequest(Request.Method.GET, url, {}, {})

        queueAnsweringBody().add(request)

        request.shouldCache() shouldBe false
    }

    @Test
    fun `the request queue starts a cache dispatcher and four network dispatchers`() {
        val dispatchersBefore = dispatcherThreads()

        NetworkModule.provideRequestQueue().also(createdQueues::add)

        (dispatcherThreads() - dispatchersBefore).size shouldBe 5
    }

    @Test
    fun `the request queue is resolved with the first add`() {
        val resolved = AtomicInteger()
        val lazyQueue =
            DefaultVolleyQueue(
                Provider {
                    resolved.incrementAndGet()
                    requestQueueAnswering { NetworkResponse(ByteArray(0)) }
                },
            )

        resolved.get() shouldBe 0
        lazyQueue.add(StringRequest(Request.Method.GET, url, {}, {}))
        resolved.get() shouldBe 1
    }

    @Test
    fun `a response callback that throws delivers the failure to the error callback`() {
        val queue = queueAnsweringBody()
        val errors = LinkedBlockingQueue<VolleyError>()

        queue.add(StringRequest(Request.Method.GET, url, { error("response boom") }, { errors.add(it) }))

        errors.poll(DELIVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)?.cause?.message shouldBe "response boom"
        deliverResponse(queue) shouldBe "body"
    }

    @Test
    fun `a request for the url of a request whose response callback threw still gets its response`() {
        val queue = queueAnsweringBody()
        val delivered = LinkedBlockingQueue<String>()

        queue.add(StringRequest(Request.Method.GET, url, { error("response boom") }, {}))
        queue.add(StringRequest(Request.Method.GET, url, delivered::add, {}))

        delivered.poll(DELIVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe "body"
    }

    @Test
    fun `an error callback that throws is called once more with the failure`() {
        val queue = queueAnsweringBody()
        val errors = LinkedBlockingQueue<String?>()

        queue.add(
            StringRequest(Request.Method.GET, MALFORMED_URL, { error("unexpected response") }) {
                if (it.cause !is IllegalStateException) {
                    errors.add("first")
                    error("error boom")
                }
                errors.add(it.cause?.message)
            },
        )

        errors.poll(DELIVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe "first"
        errors.poll(DELIVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe "error boom"
        deliverResponse(queue) shouldBe "body"
    }

    @Test
    fun `an error callback that keeps throwing cancels and finishes its request and the queue keeps delivering`() {
        val queue = queueAnsweringBody()
        val calls = AtomicInteger()
        val finished = CountDownLatch(1)
        createdQueues.last().addRequestEventListener { _, event ->
            if (event == RequestQueue.RequestEvent.REQUEST_FINISHED) finished.countDown()
        }
        val request =
            StringRequest(Request.Method.GET, MALFORMED_URL, { error("unexpected response") }) {
                calls.incrementAndGet()
                error("error boom")
            }

        queue.add(request)

        finished.await(DELIVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
        calls.get() shouldBe 2
        request.isCanceled shouldBe true
        deliverResponse(queue) shouldBe "body"
    }

    private companion object {
        const val DELIVERY_TIMEOUT_SECONDS = 5L
        const val MALFORMED_URL = "not a url"
    }
}
