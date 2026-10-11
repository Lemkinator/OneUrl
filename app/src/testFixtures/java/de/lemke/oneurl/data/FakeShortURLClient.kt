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
import kotlinx.coroutines.CompletableDeferred

/**
 * Answers [create] with the result set in [results] for the provider and [visitCount] with the count set in [visitCounts]
 * for the short URL, or throws [visitCountFailure] once set. Each call waits for [gate] while one is set and is recorded.
 */
class FakeShortURLClient : ShortURLClient {
    val results = mutableMapOf<ShortURLProvider, GenerateURLResult>()
    val visitCounts = mutableMapOf<String, Int>()
    var visitCountFailure: Exception? = null
    var gate: CompletableDeferred<Unit>? = null
    val created = mutableListOf<Create>()
    val visitCountRequests = mutableListOf<URL>()

    override suspend fun create(
        provider: ShortURLProvider,
        longURL: String,
        alias: String,
    ): GenerateURLResult {
        created += Create(provider, longURL, alias)
        gate?.await()
        return checkNotNull(results[provider]) { "no result set for ${provider.name}" }
    }

    override suspend fun visitCount(url: URL): Int? {
        visitCountRequests += url
        gate?.await()
        visitCountFailure?.let { throw it }
        return visitCounts[url.shortURL]
    }

    data class Create(
        val provider: ShortURLProvider,
        val longURL: String,
        val alias: String,
    )
}
