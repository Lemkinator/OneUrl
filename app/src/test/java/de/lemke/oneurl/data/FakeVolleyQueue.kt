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

import com.android.volley.ExecutorDelivery
import com.android.volley.Network
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.NoCache
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Records every added request and passes it to [onAdd], which can answer it; records every removed cache entry. */
internal class FakeVolleyQueue : VolleyQueue {
    val added = mutableListOf<Request<*>>()
    val removedCacheEntries = mutableListOf<String>()
    var onAdd: (Request<*>) -> Unit = {}

    override fun add(request: Request<*>) {
        added += request
        onAdd(request)
    }

    override fun removeCacheEntry(url: String) {
        removedCacheEntries += url
    }
}

/** Delivers [response] to the request's listener as the queue would after a successful parse. */
internal fun Request<*>.deliverResponse(response: Any?) {
    ExecutorDelivery(Runnable::run).postResponse(this, Response.success(response, null))
}

/** Runs the request through a real [RequestQueue] whose network answers with [response]. */
internal fun Request<*>.answerThroughQueue(response: NetworkResponse) = runThroughQueue(Network { response })

/** Runs the request through a real [RequestQueue] whose network fails with [error]. */
internal fun Request<*>.failThroughQueue(error: VolleyError) = runThroughQueue(Network { throw error })

private fun Request<*>.runThroughQueue(network: Network) {
    val finished = CountDownLatch(1)
    val thrown = AtomicReference<Throwable?>(null)
    val delivery =
        ExecutorDelivery { delivered ->
            try {
                delivered.run()
            } catch (t: Throwable) {
                thrown.compareAndSet(null, t)
                finished.countDown()
            }
        }
    val queue = RequestQueue(NoCache(), network, 1, delivery)
    queue.addRequestEventListener { _, event -> if (event == RequestQueue.RequestEvent.REQUEST_FINISHED) finished.countDown() }
    queue.start()
    try {
        queue.add(this)
        val completed = finished.await(5, TimeUnit.SECONDS)
        thrown.get()?.let { throw it }
        completed shouldBe true
    } finally {
        queue.stop()
    }
}
