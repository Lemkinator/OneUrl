package de.lemke.oneurl.domain

import de.lemke.oneurl.domain.model.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetSearchListUseCase @Inject constructor(
    private val getURLs: GetURLsUseCase,
) {
    suspend operator fun invoke(search: String?, urls: List<URL>? = null): List<URL> = withContext(Dispatchers.Default) {
        if (search.isNullOrBlank()) emptyList()
        else (urls ?: getURLs()).filter { it.contains(search) }
    }
}
