package de.lemke.oneurl.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.oneurl.domain.DeleteURLUseCase
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.GetVisitCountUseCase
import de.lemke.oneurl.domain.UpdateURLUseCase
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_SHORTURL
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class URLViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getURL: GetURLUseCase,
    private val updateURL: UpdateURLUseCase,
    private val deleteURL: DeleteURLUseCase,
    private val getVisitCount: GetVisitCountUseCase,
) : ViewModel() {
    val state: StateFlow<UrlDetailUiState>
        field = MutableStateFlow(UrlDetailUiState())

    private val _events = Channel<UrlDetailEvent>(Channel.BUFFERED)
    val events: Flow<UrlDetailEvent> = _events.receiveAsFlow()

    companion object {
        private const val TAG = "URLViewModel"
    }

    init {
        val shortURL = savedStateHandle.get<String>(KEY_SHORTURL) ?: ""
        viewModelScope.launch {
            val url = getURL(shortURL)
            if (url == null) {
                _events.send(UrlDetailEvent.NotFound)
                return@launch
            }
            state.update { it.copy(url = url, isLoading = false) }
            refreshVisitCount()
        }
    }

    fun toggleFavorite() {
        val url = state.value.url ?: return
        val updated = url.copy(favorite = !url.favorite)
        state.update { it.copy(url = updated) }
        viewModelScope.launch { updateURL(updated) }
    }

    fun refreshVisitCount() {
        val url = state.value.url ?: return
        if (state.value.isRefreshingVisits) return
        state.update { it.copy(isRefreshingVisits = true) }
        viewModelScope.launch {
            try {
                val count = getVisitCount(url)
                state.update { it.copy(visitCount = count, isRefreshingVisits = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh visit count", e)
                state.update { it.copy(isRefreshingVisits = false) }
            }
        }
    }

    fun delete() {
        val url = state.value.url ?: return
        viewModelScope.launch {
            deleteURL(url)
            _events.send(UrlDetailEvent.Deleted)
        }
    }
}

data class UrlDetailUiState(
    val url: URL? = null,
    val isLoading: Boolean = true,
    val visitCount: Int? = null,
    val isRefreshingVisits: Boolean = false,
)

sealed class UrlDetailEvent {
    data object NotFound : UrlDetailEvent()

    data object Deleted : UrlDetailEvent()
}
