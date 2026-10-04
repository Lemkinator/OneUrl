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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.data.selectedShortURLProvider
import de.lemke.oneurl.domain.AddURLUseCase
import de.lemke.oneurl.domain.GetURLTitleUseCase
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.GenerateURLResult
import de.lemke.oneurl.domain.generateURL.GenerateURLUseCase
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.model.URL
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AddURLViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userSettings: UserSettings,
    private val generateURL: GenerateURLUseCase,
    private val getURLTitle: GetURLTitleUseCase,
    private val addURL: AddURLUseCase,
    private val getURL: GetURLUseCase,
) : ViewModel() {
    val state: StateFlow<AddUrlUiState>
        field = MutableStateFlow(AddUrlUiState())

    val outcome: StateFlow<AddUrlOutcome>
        field = MutableStateFlow<AddUrlOutcome>(AddUrlOutcome.None)

    private val intentUrl: String? = savedStateHandle.get<String>("url")

    init {
        state.update {
            it.copy(
                selectedProvider = userSettings.selectedShortURLProvider,
                initialURL = intentUrl ?: userSettings.lastURL,
                initialAlias = userSettings.lastAlias,
                initialDescription = userSettings.lastDescription,
                isLoading = false,
            )
        }
        if (intentUrl != null) userSettings.lastURL = intentUrl
        viewModelScope.launch {
            userSettings.flow.selectedShortURLProvider.collectLatest { provider ->
                if (provider != state.value.selectedProvider) {
                    state.update { it.copy(selectedProvider = provider) }
                }
            }
        }
    }

    fun onLongURLChanged(text: String) {
        userSettings.lastURL = text
    }

    fun onAliasChanged(text: String) {
        userSettings.lastAlias = text
    }

    fun onDescriptionChanged(text: String) {
        userSettings.lastDescription = text
    }

    fun submit(
        longURLRaw: String,
        alias: String,
        description: String,
    ) {
        if (state.value.isLoading) return
        viewModelScope.launch {
            val provider = state.value.selectedProvider
            val longURL = provider.sanitizeLongURL(longURLRaw)
            state.update { it.copy(isLoading = true, loadingMessageRes = de.lemke.oneurl.R.string.checking_duplicates) }

            val existingURLs = getURL(provider, longURL)
            if (existingURLs.isNotEmpty()) {
                if (alias.isBlank()) {
                    complete(AddUrlOutcome.AlreadyShortened(existingURLs.first().shortURL))
                    return@launch
                }
                val exactMatch = existingURLs.find { it.shortURL == "${provider.baseURL}/$alias" }
                if (exactMatch != null) {
                    complete(AddUrlOutcome.AlreadyShortened(exactMatch.shortURL))
                    return@launch
                }
            }

            state.update { it.copy(loadingMessageRes = de.lemke.oneurl.R.string.fetching_title) }
            val title = getURLTitle(longURL) ?: ""

            val result =
                generateURL(provider, longURL, alias) { messageRes ->
                    state.update { it.copy(loadingMessageRes = messageRes) }
                }

            when (result) {
                is GenerateURLResult.Failure -> {
                    complete(AddUrlOutcome.Failed(result.error))
                }

                is GenerateURLResult.Success -> {
                    addURL(
                        URL(
                            shortURL = result.shortURL,
                            longURL = longURL,
                            shortURLProvider = provider,
                            favorite = false,
                            title = title,
                            description = description,
                            added = ZonedDateTime.now(),
                        ),
                    )
                    complete(if (userSettings.autoCopyOnCreate) AddUrlOutcome.Copy(result.shortURL, title) else AddUrlOutcome.Saved)
                }
            }
        }
    }

    fun onOutcomeHandled(result: AddUrlOutcome.Result) {
        outcome.update { if (it == result) AddUrlOutcome.None else it }
    }

    private fun complete(result: AddUrlOutcome.Result) {
        outcome.value = result
        state.update { it.copy(isLoading = false) }
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

/** The outcome of a [AddURLViewModel.submit]. The activity acts on a [Result] and then reports it handled. */
sealed interface AddUrlOutcome {
    sealed interface Result : AddUrlOutcome

    data object None : AddUrlOutcome

    data class AlreadyShortened(val shortURL: String) : Result

    data class Failed(val error: GenerateURLError) : Result

    data class Copy(val shortURL: String, val title: String) : Result

    data object Saved : Result
}
