package de.lemke.oneurl.domain


import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetURLUseCase @Inject constructor(
    private val urlRepository: URLRepository,
) {
    suspend operator fun invoke(shortURL: String): URL? = withContext(Dispatchers.Default) {
        urlRepository.getURL(shortURL)
    }

    suspend operator fun invoke(shortURLProvider: ShortURLProvider, longURL: String): List<URL> = withContext(Dispatchers.Default) {
        urlRepository.getURL(shortURLProvider, longURL)
    }
}