package de.lemke.oneurl.data

import de.lemke.oneurl.data.database.UrlDao
import de.lemke.oneurl.data.database.urlFromDb
import de.lemke.oneurl.data.database.urlToDb
import de.lemke.oneurl.domain.model.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

class UrlRepository @Inject constructor(
    private val urlDao: UrlDao
) {

    suspend fun getUrls(): List<Url> = urlDao.getAll().map { urlFromDb(it) }

    fun observeUrls(): Flow<List<Url>> = urlDao.observeAll().mapNotNull { it.map(::urlFromDb) }

    suspend fun addUrl(url: Url) = urlDao.insert(urlToDb(url))

    suspend fun deleteUrl(url: Url) = urlDao.delete(url.shortUrl)

    suspend fun deleteAll() = urlDao.deleteAll()
}