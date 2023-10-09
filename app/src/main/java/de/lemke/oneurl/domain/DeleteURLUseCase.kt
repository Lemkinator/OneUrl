package de.lemke.oneurl.domain


import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.model.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DeleteURLUseCase @Inject constructor(
    private val urlRepository: URLRepository,
) {
    suspend operator fun invoke(url: URL) = withContext(Dispatchers.Default) {
        urlRepository.deleteURL(url)
    }
    suspend operator fun invoke(urls: List<URL>) = withContext(Dispatchers.Default) {
        urlRepository.deleteURLs(urls)
    }
}