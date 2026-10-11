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

package de.lemke.oneurl.domain.generateURL

import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.commonutils.ui.utils.withHttps
import de.lemke.oneurl.R
import de.lemke.oneurl.data.NetworkMonitor
import de.lemke.oneurl.data.VolleyQueue
import de.lemke.oneurl.domain.CheckURLSafetyUseCase
import de.lemke.oneurl.domain.model.GenerateURLError
import de.lemke.oneurl.domain.model.GenerateURLResult
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.UrlhausResult
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class GenerateURLUseCase @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val checkURLSafety: CheckURLSafetyUseCase,
    private val queue: VolleyQueue,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    // Guard clauses are interleaved with onProgress() calls that must fire in order; collapsing
    // to a single return would reorder those side effects for no readability gain.
    @Suppress("ReturnCount")
    suspend operator fun invoke(
        provider: ShortURLProvider,
        longURL: String,
        alias: String,
        onProgress: (Int) -> Unit,
    ): GenerateURLResult {
        onProgress(R.string.checking_internet)
        if (!withContext(defaultDispatcher) { networkMonitor.hasValidatedInternet() }) {
            return GenerateURLResult.Failure(GenerateURLError.NoInternet)
        }
        onProgress(R.string.checking_url)
        val normalizedURL = longURL.withHttps()
        val safetyResult = checkURLSafety(normalizedURL)
        if (safetyResult is UrlhausResult.Blacklisted) {
            return GenerateURLResult.Failure(
                GenerateURLError.BlacklistedURL(safetyResult.message, safetyResult.urlhausLink, safetyResult.virustotalLink),
            )
        }
        onProgress(R.string.generating_url)
        return provider.createShortURL(queue, normalizedURL, alias)
    }
}

private suspend fun ShortURLProvider.createShortURL(
    queue: VolleyQueue,
    longURL: String,
    alias: String,
): GenerateURLResult =
    suspendCancellableCoroutine { cont ->
        val req =
            getCreateRequest(
                queue,
                longURL,
                alias,
                successCallback = { shortURL ->
                    if (cont.isActive) cont.resume(GenerateURLResult.Success(shortURL))
                },
                errorCallback = { error ->
                    if (cont.isActive) cont.resume(GenerateURLResult.Failure(error))
                },
            )
        queue.add(req)
        cont.invokeOnCancellation { req.cancel() }
    }
