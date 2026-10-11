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

import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError

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

// Volley's RequestQueue is the only caller of these protected Request methods, so a test standing
// in for the queue reaches them through reflection.
private fun Request<*>.invokeProtected(
    name: String,
    vararg parameters: Pair<Class<*>, Any?>,
): Any? =
    Request::class.java
        .getDeclaredMethod(name, *parameters.map { it.first }.toTypedArray())
        .apply { isAccessible = true }
        .invoke(this, *parameters.map { it.second }.toTypedArray())

/** Delivers [response] to the request's listener as the queue would after a successful parse. */
internal fun Request<*>.deliverResponse(response: Any?) {
    invokeProtected("deliverResponse", Any::class.java to response)
}

internal fun Request<*>.parseResponse(response: NetworkResponse?): Response<*> =
    invokeProtected("parseNetworkResponse", NetworkResponse::class.java to response) as Response<*>

internal fun Request<*>.parseError(error: VolleyError?): VolleyError =
    invokeProtected("parseNetworkError", VolleyError::class.java to error) as VolleyError

internal fun Request<*>.params(): Map<*, *>? = invokeProtected("getParams") as Map<*, *>?

internal fun Request<*>.headers(): Map<*, *> = invokeProtected("getHeaders") as Map<*, *>
