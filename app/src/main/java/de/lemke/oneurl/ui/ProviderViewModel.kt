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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProviderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getUserSettings: GetUserSettingsUseCase,
    private val updateUserSettings: UpdateUserSettingsUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(ProviderUiState())
    val state: StateFlow<ProviderUiState> = _state.asStateFlow()
    val events = Channel<ProviderEvent>(Channel.BUFFERED)

    init {
        val selectMode = savedStateHandle.get<Boolean>(ProviderActivity.KEY_SELECT_PROVIDER) == true
        viewModelScope.launch {
            val settings = getUserSettings()
            _state.update {
                it.copy(
                    selectMode = selectMode,
                    currentSelected = settings.selectedShortURLProvider,
                    initialScrollPosition = ShortURLProviderCompanion.enabled.indexOf(settings.selectedShortURLProvider),
                )
            }
        }
    }

    fun onProviderClick(provider: ShortURLProvider) {
        viewModelScope.launch {
            if (_state.value.selectMode) {
                updateUserSettings { it.copy(selectedShortURLProvider = provider) }
                events.send(ProviderEvent.Finish)
            } else {
                events.send(ProviderEvent.ShowInfo(provider))
            }
        }
    }

    fun onProviderInfoClick(provider: ShortURLProvider) {
        viewModelScope.launch { events.send(ProviderEvent.ShowInfo(provider)) }
    }
}

data class ProviderUiState(
    val providers: List<ShortURLProvider> = ShortURLProviderCompanion.enabled,
    val selectMode: Boolean = false,
    val currentSelected: ShortURLProvider? = null,
    val initialScrollPosition: Int = 0,
)

sealed class ProviderEvent {
    data object Finish : ProviderEvent()
    data class ShowInfo(val provider: ShortURLProvider) : ProviderEvent()
}
