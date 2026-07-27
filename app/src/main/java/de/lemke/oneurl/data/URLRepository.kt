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

class URLRepository @Inject constructor(
    private val urlDao: URLDao,
) {
    // get reversed flow
    fun observeURLs(): Flow<List<URL>> = urlDao.observeAll().map { it.asReversed().map(::urlFromDb) }

    suspend fun getURL(shortURL: String): URL? = urlDao.getURL(shortURL)?.let(::urlFromDb)

    suspend fun getURL(
        provider: ShortURLProvider,
        longURL: String,
    ): List<URL> = urlDao.getURL(provider.name, longURL).asReversed().map { urlFromDb(it) }

    suspend fun addURL(url: URL) = urlDao.insert(urlToDb(url))

    suspend fun updateURL(url: URL) = urlDao.update(urlToDb(url))

    suspend fun updateURLs(urls: List<URL>) = urlDao.updateMultiple(urls.map(::urlToDb))

    suspend fun deleteURL(url: URL) = urlDao.delete(url.shortURL)

    suspend fun deleteURLs(urls: List<URL>) = urlDao.delete(urls.map(::urlToDb))
}
