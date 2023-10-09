package de.lemke.oneurl.domain


import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.model.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AddURLUseCase @Inject constructor(
    private val urlRepository: URLRepository,
) {
    suspend operator fun invoke(url: URL): Unit = withContext(Dispatchers.Default) {
        urlRepository.addURL(url)
    }
}