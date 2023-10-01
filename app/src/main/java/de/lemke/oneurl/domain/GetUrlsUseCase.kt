package de.lemke.oneurl.domain


import de.lemke.oneurl.data.UrlRepository
import de.lemke.oneurl.domain.model.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetUrlsUseCase @Inject constructor(
    private val urlRepository: UrlRepository,
) {
    suspend operator fun invoke(): List<Url> = withContext(Dispatchers.Default) {
        urlRepository.getUrls()
    }
}