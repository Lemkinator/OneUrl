package de.lemke.oneurl.domain


import android.util.Log
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.model.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveURLsUseCase @Inject constructor(
    private val urlRepository: URLRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(searchQuery: Flow<String?>, filterFavorite: Flow<Boolean>): Flow<List<URL>> =
        searchQuery.flatMapLatest { query -> // observe search query
            if (query != null) {
                if (query.isBlank()) {
                    urlRepository.observeURLs().map { emptyList() }
                } else {
                    urlRepository.observeURLs().map { urls ->
                        urls.filter { url -> url.contains(query) }
                    }
                }
            } else {
                filterFavorite.flatMapLatest { filterFavorite -> // observe favorite filter
                    if (filterFavorite) {
                        urlRepository.observeURLs().map { urls ->
                            urls.filter { url -> url.favorite }
                        }
                    } else {
                        urlRepository.observeURLs()
                    }
                }
            }
        }.flowOn(Dispatchers.Default)
}