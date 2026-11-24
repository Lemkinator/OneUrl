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

import android.os.Handler
import android.util.Log
import com.android.volley.ExecutorDelivery
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.ResponseDelivery
import com.android.volley.VolleyError
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Sends Volley requests; each request delivers its own response or error. */
interface VolleyQueue {
    fun add(request: Request<*>)

    /** Drops the cached response of [url], so the next request for it reaches the network. */
    fun removeCacheEntry(url: String)
}

@Singleton
class DefaultVolleyQueue @Inject constructor(
    private val requestQueue: Provider<RequestQueue>,
) : VolleyQueue {
    override fun add(request: Request<*>) {
        requestQueue.get().add(request)
    }

    override fun removeCacheEntry(url: String) {
        requestQueue.get().cache.remove(url)
    }
}

/**
 * Delivers on the main thread like Volley's own delivery. An exception from a request's response or error callback
 * becomes an error result for that request, so a failing provider callback shows the error dialog instead of crashing the app.
 * If the error callback fails as well, the failure is logged and the request is canceled and finished.
 */
internal class GuardedDelivery(
    private val handler: Handler,
) : ResponseDelivery {
    override fun postResponse(
        request: Request<*>,
        response: Response<*>,
    ) = postResponse(request, response, null)

    override fun postResponse(
        request: Request<*>,
        response: Response<*>,
        runnable: Runnable?,
    ) = deliveryFor(request, retryAsError = true).postResponse(request, response, runnable)

    override fun postError(
        request: Request<*>,
        error: VolleyError,
    ) = deliveryFor(request, retryAsError = true).postError(request, error)

    @Suppress("TooGenericExceptionCaught")
    private fun deliveryFor(
        request: Request<*>,
        retryAsError: Boolean,
    ): ResponseDelivery =
        ExecutorDelivery(
            Executor { delivery ->
                handler.post {
                    try {
                        delivery.run()
                    } catch (e: Exception) {
                        Log.e(TAG, "callback of ${request.url} threw", e)
                        // Request.finish is package-private; delivering to a canceled request finishes it without calling a listener.
                        if (!retryAsError) request.cancel()
                        deliveryFor(request, retryAsError = false).postError(request, VolleyError(e))
                    }
                }
            },
        )

    private companion object {
        const val TAG = "VolleyQueue"
    }
}
