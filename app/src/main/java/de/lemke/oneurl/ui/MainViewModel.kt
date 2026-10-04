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

package de.lemke.oneurl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.oneurl.domain.DeleteURLUseCase
import de.lemke.oneurl.domain.ObserveURLsUseCase
import de.lemke.oneurl.domain.UpdateURLUseCase
import de.lemke.oneurl.domain.model.URL
import dev.oneuiproject.oneui.layout.ToolbarLayout.AllSelectorState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val observeURLs: ObserveURLsUseCase,
    private val deleteURL: DeleteURLUseCase,
    private val updateURL: UpdateURLUseCase,
) : ViewModel() {
    val state: StateFlow<MainUiState>
        field = MutableStateFlow(MainUiState())

    val search: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    val filterFavorite: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val allSelectorState: StateFlow<AllSelectorState>
        field = MutableStateFlow(AllSelectorState())

    private var previousSnapshot: ScrollSnapshot? = null

    init {
        viewModelScope.launch {
            observeURLs(search, filterFavorite).collectLatest { urls ->
                val snapshot = ScrollSnapshot(urls.mapTo(mutableSetOf()) { it.shortURL }, search.value, filterFavorite.value)
                val previous = previousSnapshot
                val added =
                    if (previous != null && previous.search == snapshot.search && previous.filterFavorite == snapshot.filterFavorite) {
                        urls.firstOrNull { it.shortURL !in previous.ids }?.shortURL
                    } else {
                        null
                    }
                previousSnapshot = snapshot
                state.update { it.copy(urls = urls, isUIReady = true, reveal = added ?: it.reveal) }
            }
        }
    }

    fun setSearch(query: String?) {
        search.value = query
    }

    fun setFilterFavorite(enabled: Boolean) {
        filterFavorite.value = enabled
    }

    fun setFavorite(
        url: URL,
        favorite: Boolean,
    ) {
        viewModelScope.launch { updateURL(url.copy(favorite = favorite)) }
    }

    fun setFavorites(
        urls: List<URL>,
        favorite: Boolean,
    ) {
        viewModelScope.launch { updateURL(urls.map { it.copy(favorite = favorite) }) }
    }

    fun delete(urls: List<URL>) {
        viewModelScope.launch { deleteURL(urls) }
    }

    fun setAllSelectorState(state: AllSelectorState) {
        allSelectorState.value = state
    }

    fun onRevealHandled(shortURL: String) {
        state.update { if (it.reveal == shortURL) it.copy(reveal = null) else it }
    }
}

/** [reveal] is the short URL of the newest added URL, which the list scrolls to and then reports handled. */
data class MainUiState(
    val urls: List<URL> = emptyList(),
    val isUIReady: Boolean = false,
    val reveal: String? = null,
)

private data class ScrollSnapshot(
    val ids: Set<String>,
    val search: String?,
    val filterFavorite: Boolean,
)
