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

import com.android.volley.Request
import de.lemke.oneurl.domain.model.GenerateURLError
import de.lemke.oneurl.domain.model.GenerateURLResult
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.Tly
import de.lemke.oneurl.domain.model.URL
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

interface ShortURLClient {
    /** Asks [provider] to shorten [longURL], under [alias] unless it is blank. */
    suspend fun create(
        provider: ShortURLProvider,
        longURL: String,
        alias: String,
    ): GenerateURLResult

    /** Asks the provider of [url] how often its short URL was visited, or null when the provider cannot tell. */
    suspend fun visitCount(url: URL): Int?
}

class DefaultShortURLClient @Inject constructor(
    private val queue: VolleyQueue,
    private val tlyKeys: TlyKeyStore,
) : ShortURLClient {
    override suspend fun create(
        provider: ShortURLProvider,
        longURL: String,
        alias: String,
    ): GenerateURLResult {
        val tlyKey =
            when (val stored = if (provider is Tly) tlyKeys.get() else StoredTlyKey.None) {
                is StoredTlyKey.Key -> stored.apiKey
                StoredTlyKey.None -> null
                StoredTlyKey.Unavailable -> return GenerateURLResult.Failure(GenerateURLError.TlyKeyRejected)
            }
        return suspendCancellableCoroutine { continuation ->
            val onSuccess = { shortURL: String ->
                if (continuation.isActive) continuation.resume(GenerateURLResult.Success(shortURL))
            }
            val onError = { error: GenerateURLError ->
                if (continuation.isActive) continuation.resume(GenerateURLResult.Failure(error))
            }
            val request =
                when (provider) {
                    is Tly -> provider.getTlyCreateRequest(tlyKey, longURL, onSuccess, onError)
                    else -> provider.getCreateRequest(queue, longURL, alias, onSuccess, onError)
                }
            queue.add(request)
            continuation.invokeOnCancellation { request.cancel() }
        }
    }

    override suspend fun visitCount(url: URL): Int? =
        suspendCancellableCoroutine { continuation ->
            val requests = CancelableQueue(queue)
            continuation.invokeOnCancellation { requests.cancelAll() }
            url.shortURLProvider.getURLClickCount(requests, url) { count ->
                if (continuation.isActive) continuation.resume(count)
            }
        }
}

/** Forwards to [delegate] and remembers the requests, because a provider hands back no handle to cancel its count request. */
private class CancelableQueue(
    private val delegate: VolleyQueue,
) : VolleyQueue by delegate {
    private val requests = ConcurrentLinkedQueue<Request<*>>()

    @Volatile
    private var canceled = false

    override fun add(request: Request<*>) {
        requests += request
        if (canceled) request.cancel()
        delegate.add(request)
    }

    fun cancelAll() {
        canceled = true
        requests.forEach { it.cancel() }
    }
}
