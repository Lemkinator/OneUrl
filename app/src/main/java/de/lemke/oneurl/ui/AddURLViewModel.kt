package de.lemke.oneurl.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.oneurl.domain.AddURLUseCase
import de.lemke.oneurl.domain.GenerateQRCodeUseCase
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.GetURLTitleUseCase
import de.lemke.oneurl.domain.GetUserSettingsUseCase
import de.lemke.oneurl.domain.ObserveUserSettingsUseCase
import de.lemke.oneurl.domain.UpdateUserSettingsUseCase
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.GenerateURLResult
import de.lemke.oneurl.domain.generateURL.GenerateURLUseCase
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.model.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class AddURLViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getUserSettings: GetUserSettingsUseCase,
    private val observeUserSettings: ObserveUserSettingsUseCase,
    private val updateUserSettings: UpdateUserSettingsUseCase,
    private val generateURL: GenerateURLUseCase,
    private val getURLTitle: GetURLTitleUseCase,
    private val generateQRCode: GenerateQRCodeUseCase,
    private val addURL: AddURLUseCase,
    private val getURL: GetURLUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(AddUrlUiState())
    val state: StateFlow<AddUrlUiState> = _state.asStateFlow()
    private val _events = Channel<AddUrlEvent>(Channel.BUFFERED)
    val events: Flow<AddUrlEvent> = _events.receiveAsFlow()

    private val intentUrl: String? = savedStateHandle.get<String>("url")

    init {
        viewModelScope.launch {
            val settings = getUserSettings()
            _state.update {
                it.copy(
                    selectedProvider = ShortURLProviderCompanion.getIfEnabledOrDefault(settings.selectedShortURLProvider),
                    initialURL = intentUrl ?: settings.lastURL,
                    initialAlias = settings.lastAlias,
                    initialDescription = settings.lastDescription,
                    isLoading = false,
                )
            }
            if (intentUrl != null) updateUserSettings { it.copy(lastURL = intentUrl) }
        }
        viewModelScope.launch {
            observeUserSettings().collectLatest { settings ->
                val newProvider = ShortURLProviderCompanion.getIfEnabledOrDefault(settings.selectedShortURLProvider)
                if (newProvider != _state.value.selectedProvider) {
                    _state.update { it.copy(selectedProvider = newProvider) }
                }
            }
        }
    }

    fun onLongURLChanged(text: String) {
        viewModelScope.launch { updateUserSettings { it.copy(lastURL = text) } }
    }

    fun onAliasChanged(text: String) {
        viewModelScope.launch { updateUserSettings { it.copy(lastAlias = text) } }
    }

    fun onDescriptionChanged(text: String) {
        viewModelScope.launch { updateUserSettings { it.copy(lastDescription = text) } }
    }

    fun submit(longURLRaw: String, alias: String, description: String) {
        viewModelScope.launch {
            val provider = _state.value.selectedProvider
            val longURL = provider.sanitizeLongURL(longURLRaw)
            _state.update { it.copy(isLoading = true, loadingMessageRes = de.lemke.oneurl.R.string.checking_duplicates) }

            val existingURLs = getURL(provider, longURL)
            if (existingURLs.isNotEmpty()) {
                if (alias.isBlank()) {
                    _state.update { it.copy(isLoading = false) }
                    _events.send(AddUrlEvent.AlreadyShortened(existingURLs.first().shortURL))
                    return@launch
                }
                val exactMatch = existingURLs.find { it.shortURL == provider.baseURL + alias }
                if (exactMatch != null) {
                    _state.update { it.copy(isLoading = false) }
                    _events.send(AddUrlEvent.AlreadyShortened(exactMatch.shortURL))
                    return@launch
                }
            }

            val title = getURLTitle(longURL) ?: ""

            val result = generateURL(provider, longURL, alias) { messageRes ->
                _state.update { it.copy(loadingMessageRes = messageRes) }
            }

            _state.update { it.copy(isLoading = false) }

            when (result) {
                is GenerateURLResult.Failure -> _events.send(AddUrlEvent.Error(result.error))
                is GenerateURLResult.Success -> {
                    val qr = withContext(Dispatchers.Default) { generateQRCode(result.shortURL) }
                    addURL(
                        URL(
                            shortURL = result.shortURL,
                            longURL = longURL,
                            shortURLProvider = provider,
                            qr = qr,
                            favorite = false,
                            title = title,
                            description = description,
                            added = ZonedDateTime.now(),
                        )
                    )
                    val settings = getUserSettings()
                    if (settings.autoCopyOnCreate) _events.send(AddUrlEvent.CopyAndFinish(result.shortURL, title))
                    else _events.send(AddUrlEvent.Saved)
                }
            }
        }
    }
}

data class AddUrlUiState(
    val selectedProvider: ShortURLProvider = ShortURLProviderCompanion.default,
    val initialURL: String = "",
    val initialAlias: String = "",
    val initialDescription: String = "",
    val isLoading: Boolean = true,
    val loadingMessageRes: Int = 0,
)

sealed class AddUrlEvent {
    data class AlreadyShortened(val shortURL: String) : AddUrlEvent()
    data class Error(val error: GenerateURLError) : AddUrlEvent()
    data class CopyAndFinish(val shortURL: String, val title: String) : AddUrlEvent()
    data object Saved : AddUrlEvent()
}
