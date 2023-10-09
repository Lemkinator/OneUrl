package de.lemke.oneurl.domain


import de.lemke.oneurl.data.URLRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class ObserveURLsUseCase @Inject constructor(
    private val urlRepository: URLRepository,
) {
    operator fun invoke() = urlRepository.observeURLs().flowOn(Dispatchers.Default)
}