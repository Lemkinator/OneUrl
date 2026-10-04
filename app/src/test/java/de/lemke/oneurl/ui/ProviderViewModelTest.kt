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
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.domain.model.Dagd
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.model.VgdIsgd
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderViewModelTest : ShouldSpec(
    {
        lateinit var userSettings: UserSettings

        fun newViewModel(
            savedStateHandle: SavedStateHandle = SavedStateHandle(),
            providers: List<ShortURLProvider> = ShortURLProviderCompanion.enabled,
        ) = ProviderViewModel(savedStateHandle, userSettings, providers)

        beforeEach {
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
        }

        should("init defaults to select mode false and reflects the currently selected provider") {
            val provider = ShortURLProviderCompanion.enabled.first { it != ShortURLProviderCompanion.default }
            userSettings.selectedShortURLProvider = provider

            val viewModel = newViewModel()

            viewModel.state.value.selectMode
                .shouldBeFalse()
            viewModel.state.value.currentSelected shouldBe provider
        }

        should("init reads select mode true from saved state") {
            val viewModel = newViewModel(SavedStateHandle(mapOf(ProviderActivity.KEY_SELECT_PROVIDER to true)))

            viewModel.state.value.selectMode
                .shouldBeTrue()
        }

        should("init holds the selected provider's real index in the enabled list as the scroll target") {
            val provider = ShortURLProviderCompanion.enabled.last()
            userSettings.selectedShortURLProvider = provider
            val expectedIndex = ShortURLProviderCompanion.enabled.indexOf(provider)

            val viewModel = newViewModel()

            viewModel.state.value.scrollToPosition shouldBe expectedIndex
        }

        should("init holds no scroll target when the selected provider is not in the enabled list") {
            userSettings.selectedShortURLProvider = Dagd

            val viewModel = newViewModel(providers = listOf(VgdIsgd.Isgd, VgdIsgd.Vgd))

            viewModel.state.value.scrollToPosition shouldBe null
        }

        should("onScrolledToSelected clears the scroll target") {
            val viewModel = newViewModel()

            viewModel.onScrolledToSelected()

            viewModel.state.value.scrollToPosition shouldBe null
        }

        should("navigation starts as None") {
            newViewModel().navigation.value shouldBe ProviderNavigation.None
        }

        should("onProviderClick in select mode selects the provider and holds Finish") {
            val provider = ShortURLProviderCompanion.enabled.first { it != ShortURLProviderCompanion.default }
            val viewModel = newViewModel(SavedStateHandle(mapOf(ProviderActivity.KEY_SELECT_PROVIDER to true)))

            viewModel.onProviderClick(provider)

            viewModel.navigation.value shouldBe ProviderNavigation.Finish
            userSettings.selectedShortURLProvider shouldBe provider
        }

        should("onProviderClick outside select mode leaves selection unchanged and holds ShowInfo") {
            val previouslySelected = ShortURLProviderCompanion.default
            val provider = ShortURLProviderCompanion.enabled.first { it != ShortURLProviderCompanion.default }
            val viewModel = newViewModel()

            viewModel.onProviderClick(provider)

            viewModel.navigation.value shouldBe ProviderNavigation.ShowInfo(provider)
            userSettings.selectedShortURLProvider shouldBe previouslySelected
        }

        should("onProviderInfoClick always holds ShowInfo regardless of select mode") {
            val provider = ShortURLProviderCompanion.enabled.first { it != ShortURLProviderCompanion.default }
            val viewModel = newViewModel(SavedStateHandle(mapOf(ProviderActivity.KEY_SELECT_PROVIDER to true)))

            viewModel.onProviderInfoClick(provider)

            viewModel.navigation.value shouldBe ProviderNavigation.ShowInfo(provider)
        }

        should("onNavigationHandled returns to None") {
            val provider = ShortURLProviderCompanion.enabled.first()
            val viewModel = newViewModel()
            viewModel.onProviderInfoClick(provider)

            viewModel.onNavigationHandled(ProviderNavigation.ShowInfo(provider))

            viewModel.navigation.value shouldBe ProviderNavigation.None
        }

        should("onNavigationHandled keeps a request other than the handled one") {
            val provider = ShortURLProviderCompanion.enabled.first()
            val viewModel = newViewModel()
            viewModel.onProviderInfoClick(provider)

            viewModel.onNavigationHandled(ProviderNavigation.Finish)

            viewModel.navigation.value shouldBe ProviderNavigation.ShowInfo(provider)
        }
    },
)
