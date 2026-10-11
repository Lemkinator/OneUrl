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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Keeps [urls] in memory, newest first, with the replace-on-add and update-in-place rules of [DefaultURLRepository]. */
internal class FakeURLRepository(
    urls: List<URL> = emptyList(),
) : URLRepository {
    private val rows = MutableStateFlow(urls)
    val urls: List<URL> get() = rows.value

    override fun observeURLs(): Flow<List<URL>> = rows

    override suspend fun getURL(shortURL: String): URL? = rows.value.find { it.shortURL == shortURL }

    override suspend fun getURL(
        provider: ShortURLProvider,
        longURL: String,
    ): List<URL> = rows.value.filter { it.shortURLProvider.name == provider.name && it.longURL == longURL }

    override suspend fun addURL(url: URL) {
        rows.update { stored -> listOf(url) + stored.filterNot { it.shortURL == url.shortURL } }
    }

    override suspend fun updateURL(url: URL) = updateURLs(listOf(url))

    override suspend fun updateURLs(urls: List<URL>) {
        val updates = urls.associateBy { it.shortURL }
        rows.update { stored -> stored.map { updates[it.shortURL] ?: it } }
    }

    override suspend fun deleteURL(url: URL) = deleteURLs(listOf(url))

    override suspend fun deleteURLs(urls: List<URL>) {
        val deleted = urls.mapTo(HashSet()) { it.shortURL }
        rows.update { stored -> stored.filterNot { it.shortURL in deleted } }
    }
}
