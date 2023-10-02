package de.lemke.oneurl.domain


import de.lemke.oneurl.data.UrlRepository
import de.lemke.oneurl.domain.model.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DeleteUrlUseCase @Inject constructor(
    private val urlRepository: UrlRepository,
) {
    suspend operator fun invoke(url: Url) = withContext(Dispatchers.Default) {
        urlRepository.deleteUrl(url)
    }
    suspend operator fun invoke(urls: List<Url>) = withContext(Dispatchers.Default) {
        urlRepository.deleteUrls(urls)
    }
}