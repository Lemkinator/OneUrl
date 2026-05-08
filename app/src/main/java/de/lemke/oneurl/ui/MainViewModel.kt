package de.lemke.oneurl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.oneurl.domain.DeleteURLUseCase
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.ObserveURLsUseCase
import de.lemke.oneurl.domain.UpdateURLUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import de.lemke.oneurl.domain.model.URL
import dev.oneuiproject.oneui.layout.ToolbarLayout.AllSelectorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getUserSettings: GetUserSettingsUseCase,
    private val updateUserSettings: UpdateUserSettingsUseCase,
    private val observeURLs: ObserveURLsUseCase,
    private val deleteURL: DeleteURLUseCase,
    private val updateURL: UpdateURLUseCase,
) : ViewModel() {
    private val _search = MutableStateFlow<String?>(null)
    private val _filterFavorite = MutableStateFlow(false)

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    val search: MutableStateFlow<String?> get() = _search
    val filterFavorite: MutableStateFlow<Boolean> get() = _filterFavorite
    val allSelectorState = MutableStateFlow(AllSelectorState())

    init {
        viewModelScope.launch {
            observeURLs(_search, _filterFavorite).collectLatest { urls ->
                val previousSize = _state.value.urls.size
                _state.update { it.copy(urls = urls, isUIReady = true, newItemAdded = urls.size > previousSize) }
            }
        }
    }

    fun setSearch(query: String?) {
        _search.value = query
    }

    fun setFilterFavorite(enabled: Boolean) {
        _filterFavorite.value = enabled
    }

    fun setFavorite(url: URL, favorite: Boolean) {
        viewModelScope.launch { updateURL(url.copy(favorite = favorite)) }
    }

    fun delete(urls: List<URL>) {
        viewModelScope.launch { deleteURL(urls) }
    }

    fun setAllSelectorState(state: AllSelectorState) {
        allSelectorState.value = state
    }

    fun updateAutoCopy(enabled: Boolean) {
        viewModelScope.launch { updateUserSettings { it.copy(autoCopyOnCreate = enabled) } }
    }
}

data class MainUiState(
    val urls: List<URL> = emptyList(),
    val isUIReady: Boolean = false,
    val newItemAdded: Boolean = false,
)
