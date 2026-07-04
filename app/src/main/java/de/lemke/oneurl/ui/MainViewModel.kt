package de.lemke.oneurl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.oneurl.domain.DeleteURLUseCase
import de.lemke.oneurl.domain.ObserveURLsUseCase
import de.lemke.oneurl.domain.UpdateURLUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import de.lemke.oneurl.domain.model.URL
import dev.oneuiproject.oneui.layout.ToolbarLayout.AllSelectorState
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val updateUserSettings: UpdateUserSettingsUseCase,
    private val observeURLs: ObserveURLsUseCase,
    private val deleteURL: DeleteURLUseCase,
    private val updateURL: UpdateURLUseCase,
) : ViewModel() {
    val state: StateFlow<MainUiState>
        field = MutableStateFlow(MainUiState())

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events: Flow<MainEvent> = _events.receiveAsFlow()

    val search: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    val filterFavorite: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val allSelectorState: StateFlow<AllSelectorState>
        field = MutableStateFlow(AllSelectorState())

    init {
        viewModelScope.launch {
            observeURLs(search, filterFavorite).collectLatest { urls ->
                val previousSize = state.value.urls.size
                state.update { it.copy(urls = urls, isUIReady = true) }
                if (urls.size > previousSize) _events.send(MainEvent.NewItemAdded)
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
)

sealed class MainEvent {
    data object NewItemAdded : MainEvent()
}
