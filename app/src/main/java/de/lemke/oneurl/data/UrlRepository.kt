package de.lemke.oneurl.data

import de.lemke.oneurl.data.database.UrlDao
import de.lemke.oneurl.data.database.urlFromDb
import de.lemke.oneurl.data.database.urlToDb
import de.lemke.oneurl.domain.model.ShortUrlProvider
import de.lemke.oneurl.domain.model.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

class UrlRepository @Inject constructor(
    private val urlDao: UrlDao
) {

    suspend fun getUrls(): List<Url> = urlDao.getAll().asReversed().map { urlFromDb(it) }

    //get reversed flow
    fun observeUrls(): Flow<List<Url>> = urlDao.observeAll().mapNotNull { it.asReversed().map(::urlFromDb) }

    suspend fun getUrl(shortUrl: String): Url? = urlDao.getUrl(shortUrl)?.let(::urlFromDb)

    suspend fun getUrl(provider: ShortUrlProvider, longUrl: String): List<Url> = urlDao.getUrl(provider.toString(), longUrl).asReversed().map { urlFromDb(it) }

    suspend fun addUrl(url: Url) = urlDao.insert(urlToDb(url))

    suspend fun updateUrl(url: Url) = urlDao.update(urlToDb(url))

    suspend fun updateUrls(urls: List<Url>) = urlDao.updateMultiple(urls.map(::urlToDb))

    suspend fun deleteUrl(url: Url) = urlDao.delete(url.shortUrl)

    suspend fun deleteUrls(urls: List<Url>) = urls.forEach { deleteUrl(it) }

    suspend fun deleteAll() = urlDao.deleteAll()
}