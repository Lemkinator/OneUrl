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

import de.lemke.oneurl.data.database.URLDao
import de.lemke.oneurl.data.database.urlFromDb
import de.lemke.oneurl.data.database.urlToDb
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.URL
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Stores the shortened URLs; lists return the URL with the latest `added` instant first, the newer insert first among equals. */
interface URLRepository {
    fun observeURLs(): Flow<List<URL>>

    suspend fun getURL(shortURL: String): URL?

    suspend fun getURL(
        provider: ShortURLProvider,
        longURL: String,
    ): List<URL>

    /** Stores [url]; a stored URL with the same short URL is replaced, and [url] becomes the newest. */
    suspend fun addURL(url: URL)

    /** Replaces the stored URL with the short URL of [url] in place; stores nothing for an unknown short URL. */
    suspend fun updateURL(url: URL)

    suspend fun updateURLs(urls: List<URL>)

    suspend fun deleteURL(url: URL)

    suspend fun deleteURLs(urls: List<URL>)
}

class DefaultURLRepository @Inject constructor(
    private val urlDao: URLDao,
) : URLRepository {
    override fun observeURLs(): Flow<List<URL>> = urlDao.observeAll().map { rows -> rows.map(::urlFromDb).newestAddedFirst() }

    override suspend fun getURL(shortURL: String): URL? = urlDao.getURL(shortURL)?.let(::urlFromDb)

    override suspend fun getURL(
        provider: ShortURLProvider,
        longURL: String,
    ): List<URL> = urlDao.getURL(provider.name, longURL).map(::urlFromDb)

    override suspend fun addURL(url: URL) = urlDao.insert(urlToDb(url))

    override suspend fun updateURL(url: URL) = urlDao.update(urlToDb(url))

    override suspend fun updateURLs(urls: List<URL>) = urlDao.updateMultiple(urls.map(::urlToDb))

    override suspend fun deleteURL(url: URL) = urlDao.delete(url.shortURL)

    override suspend fun deleteURLs(urls: List<URL>) = urlDao.delete(urls.map(::urlToDb))
}

internal fun List<URL>.newestAddedFirst(): List<URL> = sortedByDescending { it.added.toInstant() }
