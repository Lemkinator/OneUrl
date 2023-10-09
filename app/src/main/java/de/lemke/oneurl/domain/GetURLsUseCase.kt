package de.lemke.oneurl.domain


import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.model.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetURLsUseCase @Inject constructor(
    private val urlRepository: URLRepository,
) {
    suspend operator fun invoke(): List<URL> = withContext(Dispatchers.Default) {
        urlRepository.getURLs()
    }
}