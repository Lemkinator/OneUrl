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

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.lemke.oneurl.R
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
        field = MutableStateFlow(initialState(savedStateHandle))

    val navigation: StateFlow<ProviderNavigation>
        field = MutableStateFlow<ProviderNavigation>(ProviderNavigation.None)

    fun onProviderClick(provider: ShortURLProvider) {
        when (state.value.mode) {
            ProviderScreenMode.Select -> {
                userSettings.selectedShortURLProvider = provider
                navigation.value = ProviderNavigation.Finish
            }

            ProviderScreenMode.Info -> {
                navigation.value = ProviderNavigation.ShowInfo(provider)
            }
        }
    }

    fun onProviderInfoClick(provider: ShortURLProvider) {
        navigation.value = ProviderNavigation.ShowInfo(provider)
    }

    fun onScrolledToSelected() {
        state.update { it.copy(scrollToPosition = null) }
    }

    fun onNavigationHandled(request: ProviderNavigation.Request) {
        navigation.compareAndSet(request, ProviderNavigation.None)
    }

    private fun initialState(savedStateHandle: SavedStateHandle): ProviderUiState {
        val mode = checkNotNull(savedStateHandle.get<ProviderScreenMode>(ProviderActivity.KEY_MODE)) { "ProviderActivity needs a mode" }
        val currentSelected = userSettings.selectedShortURLProvider
        val position = enabledProviders.indexOf(currentSelected).takeIf { mode == ProviderScreenMode.Select && it >= 0 }
        return ProviderUiState(providers = enabledProviders, mode = mode, currentSelected = currentSelected, scrollToPosition = position)
    }
}

/** What the provider screen is for: picking the default provider, or browsing provider info. */
enum class ProviderScreenMode(
    @StringRes val title: Int,
) {
    Select(R.string.select_provider),
    Info(R.string.provider_info),
}

/** One list row; [marked] shows the checkmark of the current provider. */
data class ProviderItem(val provider: ShortURLProvider, val marked: Boolean)

data class ProviderUiState(
    val providers: List<ShortURLProvider>,
    val mode: ProviderScreenMode,
    val currentSelected: ShortURLProvider?,
    val scrollToPosition: Int?,
) {
    val items: List<ProviderItem>
        get() = providers.map { ProviderItem(it, marked = mode == ProviderScreenMode.Select && it == currentSelected) }
}

/** Where a provider tap leads. The activity acts on a [Request] and then reports it handled. */
sealed interface ProviderNavigation {
    sealed interface Request : ProviderNavigation

    data object None : ProviderNavigation

    data class ShowInfo(val provider: ShortURLProvider) : Request

    data object Finish : Request
}
