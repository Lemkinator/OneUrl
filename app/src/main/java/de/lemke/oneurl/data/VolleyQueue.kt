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

import android.content.Context
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.HurlStack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Sends Volley requests; each request delivers its own response or error. */
interface VolleyQueue {
    fun add(request: Request<*>)

    /** Drops the cached response of [url], so the next request for it reaches the network. */
    fun removeCacheEntry(url: String)
}

@Singleton
class DefaultVolleyQueue @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : VolleyQueue {
    private val queue: RequestQueue by lazy {
        RequestQueue(DiskBasedCache(File(context.cacheDir, CACHE_DIRECTORY)), BasicNetwork(HurlStack())).apply { start() }
    }

    override fun add(request: Request<*>) {
        queue.add(request)
    }

    override fun removeCacheEntry(url: String) {
        queue.cache.remove(url)
    }

    /** Stops the queue's cache and network dispatcher threads. */
    fun stop() {
        queue.stop()
    }

    private companion object {
        const val CACHE_DIRECTORY = "volley"
    }
}
