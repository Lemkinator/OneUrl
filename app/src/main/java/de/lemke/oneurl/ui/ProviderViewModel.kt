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
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.di.EnabledProviders
import de.lemke.oneurl.domain.model.ShortURLProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class ProviderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userSettings: UserSettings,
    @EnabledProviders private val enabledProviders: @JvmSuppressWildcards List<ShortURLProvider>,
) : ViewModel() {
    val state: StateFlow<ProviderUiState>
        field = MutableStateFlow(ProviderUiState())

    val navigation: StateFlow<ProviderNavigation>
        field = MutableStateFlow<ProviderNavigation>(ProviderNavigation.None)

    init {
        val selectMode = savedStateHandle.get<Boolean>(ProviderActivity.KEY_SELECT_PROVIDER) == true
        val currentSelected = userSettings.selectedShortURLProvider
        val position = enabledProviders.indexOf(currentSelected).takeIf { it >= 0 }
        state.update {
            it.copy(providers = enabledProviders, selectMode = selectMode, currentSelected = currentSelected, scrollToPosition = position)
        }
    }

    fun onProviderClick(provider: ShortURLProvider) {
        if (state.value.selectMode) {
            userSettings.selectedShortURLProvider = provider
            navigation.value = ProviderNavigation.Finish
        } else {
            navigation.value = ProviderNavigation.ShowInfo(provider)
        }
    }

    fun onProviderInfoClick(provider: ShortURLProvider) {
        navigation.value = ProviderNavigation.ShowInfo(provider)
    }

    fun onScrolledToSelected() {
        state.update { it.copy(scrollToPosition = null) }
    }

    fun onNavigationHandled(request: ProviderNavigation.Request) {
        navigation.update { if (it == request) ProviderNavigation.None else it }
    }
}

data class ProviderUiState(
    val providers: List<ShortURLProvider> = emptyList(),
    val selectMode: Boolean = false,
    val currentSelected: ShortURLProvider? = null,
    val scrollToPosition: Int? = null,
)

/** Where a provider tap leads. The activity acts on a [Request] and then reports it handled. */
sealed interface ProviderNavigation {
    sealed interface Request : ProviderNavigation

    data object None : ProviderNavigation

    data class ShowInfo(val provider: ShortURLProvider) : Request

    data object Finish : Request
}
