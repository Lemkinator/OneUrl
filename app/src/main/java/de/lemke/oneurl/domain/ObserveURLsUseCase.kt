/*
 * Copyright 2023-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.oneurl.domain

import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.model.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ObserveURLsUseCase @Inject constructor(
    private val urlRepository: URLRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(
        searchQuery: Flow<String?>,
        filterFavorite: Flow<Boolean>,
    ): Flow<List<URL>> =
        searchQuery
            .flatMapLatest { query ->
                // observe search query
                if (query != null) {
                    if (query.isBlank()) {
                        urlRepository.observeURLs().map { emptyList() }
                    } else {
                        urlRepository.observeURLs().map { urls ->
                            urls.filter { url -> url.contains(query) }
                        }
                    }
                } else {
                    filterFavorite.flatMapLatest { filterFavorite ->
                        // observe favorite filter
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
