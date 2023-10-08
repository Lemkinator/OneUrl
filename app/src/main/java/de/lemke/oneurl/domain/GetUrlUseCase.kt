package de.lemke.oneurl.domain


import de.lemke.oneurl.data.UrlRepository
import de.lemke.oneurl.domain.model.ShortUrlProvider
import de.lemke.oneurl.domain.model.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetUrlUseCase @Inject constructor(
    private val urlRepository: UrlRepository,
) {
    suspend operator fun invoke(shortUrl: String): Url? = withContext(Dispatchers.Default) {
        urlRepository.getUrl(shortUrl)
    }

    suspend operator fun invoke(shortUrlProvider: ShortUrlProvider, longUrl: String): List<Url> = withContext(Dispatchers.Default) {
        urlRepository.getUrl(shortUrlProvider, longUrl)
    }
}