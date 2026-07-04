package de.lemke.oneurl.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProviderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getUserSettings: GetUserSettingsUseCase,
    private val updateUserSettings: UpdateUserSettingsUseCase,
) : ViewModel() {
    val state: StateFlow<ProviderUiState>
        field = MutableStateFlow(ProviderUiState())

    private val _events = Channel<ProviderEvent>(Channel.BUFFERED)
    val events: Flow<ProviderEvent> = _events.receiveAsFlow()

    init {
        val selectMode = savedStateHandle.get<Boolean>(ProviderActivity.KEY_SELECT_PROVIDER) == true
        viewModelScope.launch {
            val settings = getUserSettings()
            state.update { it.copy(selectMode = selectMode, currentSelected = settings.selectedShortURLProvider) }
            val position = ShortURLProviderCompanion.enabled.indexOf(settings.selectedShortURLProvider)
            if (position >= 0) _events.send(ProviderEvent.ScrollToSelected(position))
        }
    }

    fun onProviderClick(provider: ShortURLProvider) {
        viewModelScope.launch {
            if (state.value.selectMode) {
                updateUserSettings { it.copy(selectedShortURLProvider = provider) }
                _events.send(ProviderEvent.Finish)
            } else {
                _events.send(ProviderEvent.ShowInfo(provider))
            }
        }
    }

    fun onProviderInfoClick(provider: ShortURLProvider) {
        viewModelScope.launch { _events.send(ProviderEvent.ShowInfo(provider)) }
    }
}

data class ProviderUiState(
    val providers: List<ShortURLProvider> = ShortURLProviderCompanion.enabled,
    val selectMode: Boolean = false,
    val currentSelected: ShortURLProvider? = null,
)

sealed class ProviderEvent {
    data object Finish : ProviderEvent()
    data class ShowInfo(val provider: ShortURLProvider) : ProviderEvent()
    data class ScrollToSelected(val position: Int) : ProviderEvent()
}
