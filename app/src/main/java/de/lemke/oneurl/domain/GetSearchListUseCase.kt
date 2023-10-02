package de.lemke.oneurl.domain

import de.lemke.oneurl.domain.model.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetSearchListUseCase @Inject constructor(
    private val getUrls: GetUrlsUseCase,
) {
    suspend operator fun invoke(search: String?, urls: List<Url>? = null): List<Url> = withContext(Dispatchers.Default) {
        val result = urls ?: getUrls()
        when {
            search.isNullOrBlank() -> return@withContext emptyList()
            else -> return@withContext result.filter {
                it.containsKeywords(
                    if (search.startsWith("\"") && search.endsWith("\"") && search.length > 2) {
                        search.substring(1, search.length - 1).trim().split(" ").toSet()
                    } else setOf(search)
                )
            }
        }
    }
}
