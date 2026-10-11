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

import de.lemke.oneurl.domain.model.GenerateURLResult
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.URL
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
) : ShortURLClient {
    override suspend fun create(
        provider: ShortURLProvider,
        longURL: String,
        alias: String,
    ): GenerateURLResult =
        suspendCancellableCoroutine { continuation ->
            val request =
                provider.getCreateRequest(
                    queue,
                    longURL,
                    alias,
                    successCallback = { shortURL ->
                        if (continuation.isActive) continuation.resume(GenerateURLResult.Success(shortURL))
                    },
                    errorCallback = { error ->
                        if (continuation.isActive) continuation.resume(GenerateURLResult.Failure(error))
                    },
                )
            queue.add(request)
            continuation.invokeOnCancellation { request.cancel() }
        }

    override suspend fun visitCount(url: URL): Int? =
        suspendCancellableCoroutine { continuation ->
            url.shortURLProvider.getURLClickCount(queue, url) { count ->
                if (continuation.isActive) continuation.resume(count)
            }
        }
}
