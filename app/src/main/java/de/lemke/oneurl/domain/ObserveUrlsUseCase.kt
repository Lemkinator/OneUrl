package de.lemke.oneurl.domain


import de.lemke.oneurl.data.UrlRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ObserveUrlsUseCase @Inject constructor(
    private val urlRepository: UrlRepository,
) {
    operator fun invoke() = urlRepository.observeUrls().flowOn(Dispatchers.Default)
}