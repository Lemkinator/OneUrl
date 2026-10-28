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

import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.URL
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Keeps [urls] in memory, latest `added` first and the newer insert first among equals, with the replace-on-add and update-in-place rules of [DefaultURLRepository].
 * Like Room, [observeURLs] emits after every write that touches a row, even when the rows stay equal.
 */
internal class FakeURLRepository(
    urls: List<URL> = emptyList(),
) : URLRepository {
    private val rows =
        MutableSharedFlow<List<URL>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply {
            tryEmit(urls.newestAddedFirst())
        }
    val urls: List<URL> get() = rows.replayCache.single()

    override fun observeURLs(): Flow<List<URL>> = rows

    override suspend fun getURL(shortURL: String): URL? = urls.find { it.shortURL == shortURL }

    override suspend fun getURL(
        provider: ShortURLProvider,
        longURL: String,
    ): List<URL> = urls.filter { it.shortURLProvider.name == provider.name && it.longURL == longURL }

    override suspend fun addURL(url: URL) {
        rows.emit((listOf(url) + urls.filterNot { it.shortURL == url.shortURL }).newestAddedFirst())
    }

    override suspend fun updateURL(url: URL) = updateURLs(listOf(url))

    override suspend fun updateURLs(urls: List<URL>) {
        val updates = urls.associateBy { it.shortURL }
        val stored = this.urls
        if (stored.any { it.shortURL in updates }) rows.emit(stored.map { updates[it.shortURL] ?: it }.newestAddedFirst())
    }

    override suspend fun deleteURL(url: URL) = deleteURLs(listOf(url))

    override suspend fun deleteURLs(urls: List<URL>) {
        val deleted = urls.mapTo(HashSet()) { it.shortURL }
        val stored = this.urls
        if (stored.any { it.shortURL in deleted }) rows.emit(stored.filterNot { it.shortURL in deleted })
    }
}
