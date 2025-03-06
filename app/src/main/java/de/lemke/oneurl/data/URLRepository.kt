package de.lemke.oneurl.data

import de.lemke.oneurl.data.database.URLDao
import de.lemke.oneurl.data.database.urlFromDb
import de.lemke.oneurl.data.database.urlToDb
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.URL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

class URLRepository @Inject constructor(
    private val urlDao: URLDao
) {
    //get reversed flow
    fun observeURLs(): Flow<List<URL>> = urlDao.observeAll().mapNotNull { it.asReversed().map(::urlFromDb) }

    suspend fun getURL(shortURL: String): URL? = urlDao.getURL(shortURL)?.let(::urlFromDb)

    suspend fun getURL(provider: ShortURLProvider, longURL: String): List<URL> = urlDao.getURL(provider.name, longURL).asReversed().map { urlFromDb(it) }

    suspend fun addURL(url: URL) = urlDao.insert(urlToDb(url))

    suspend fun updateURL(url: URL) = urlDao.update(urlToDb(url))

    suspend fun updateURLs(urls: List<URL>) = urlDao.updateMultiple(urls.map(::urlToDb))

    suspend fun deleteURL(url: URL) = urlDao.delete(url.shortURL)

    suspend fun deleteURLs(urls: List<URL>) = urlDao.delete(urls.map(::urlToDb))
}