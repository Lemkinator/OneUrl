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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderViewModelTest : ShouldSpec(
    {
        lateinit var settingsScope: CoroutineScope
        lateinit var userSettings: UserSettings

        fun handleOf(mode: ProviderScreenMode) = SavedStateHandle(mapOf(ProviderActivity.KEY_MODE to mode.name))

        fun newViewModel(
            savedStateHandle: SavedStateHandle = handleOf(ProviderScreenMode.Info),
            providers: List<ShortURLProvider> = ShortURLProviderCompanion.enabled,
        ) = ProviderViewModel(savedStateHandle, userSettings, providers)

        beforeEach {
            settingsScope = CoroutineScope(Job() + UnconfinedTestDispatcher())
            userSettings = UserSettings(FakeSharedPreferences(), settingsScope)
        }

        afterEach {
            settingsScope.cancel()
        }

        should("init reads the mode from saved state") {
            newViewModel(handleOf(ProviderScreenMode.Select)).state.value.mode shouldBe ProviderScreenMode.Select
            newViewModel(handleOf(ProviderScreenMode.Info)).state.value.mode shouldBe ProviderScreenMode.Info
        }

        should("init without a mode fails") {
            shouldThrow<IllegalStateException> { newViewModel(SavedStateHandle()) }
                .message shouldBe "ProviderActivity needs a mode"
        }

        should("init with an unknown mode name fails") {
            shouldThrow<IllegalStateException> { newViewModel(SavedStateHandle(mapOf(ProviderActivity.KEY_MODE to "Browse"))) }
                .message shouldBe "ProviderActivity needs a mode"
        }

        should("init reflects the currently selected provider") {
            val provider = ShortURLProviderCompanion.enabled.first { it != ShortURLProviderCompanion.default }
            userSettings.selectedShortURLProvider = provider

            newViewModel().state.value.currentSelected shouldBe provider
        }

        should("items mark the current provider in select mode only") {
            userSettings.selectedShortURLProvider = Dagd

            newViewModel(handleOf(ProviderScreenMode.Select))
                .state.value.items
                .filter { it.marked }
                .map { it.provider } shouldBe listOf(Dagd)
            newViewModel(handleOf(ProviderScreenMode.Info))
                .state.value.items
                .filter { it.marked } shouldBe emptyList()
        }

        should("items hold every enabled provider in order") {
            newViewModel()
                .state.value.items
                .map { it.provider } shouldBe ShortURLProviderCompanion.enabled
        }

        should("init in select mode holds the selected provider's index in the enabled list as the scroll target") {
            userSettings.selectedShortURLProvider = Dagd

            val viewModel = newViewModel(handleOf(ProviderScreenMode.Select))

            viewModel.state.value.scrollToPosition shouldBe 2
        }

        should("init in info mode holds no scroll target") {
            userSettings.selectedShortURLProvider = ShortURLProviderCompanion.enabled.last()

            val viewModel = newViewModel()

            viewModel.state.value.scrollToPosition shouldBe null
        }

        should("init holds no scroll target when the selected provider is not in the enabled list") {
            userSettings.selectedShortURLProvider = Dagd

            val viewModel =
                newViewModel(handleOf(ProviderScreenMode.Select), listOf(VgdIsgd.Isgd, VgdIsgd.Vgd))

            viewModel.state.value.scrollToPosition shouldBe null
        }

        should("onScrolledToSelected clears the scroll target") {
            userSettings.selectedShortURLProvider = Dagd
            val viewModel = newViewModel(handleOf(ProviderScreenMode.Select))
            viewModel.state.value.scrollToPosition shouldBe 2

            viewModel.onScrolledToSelected()

            viewModel.state.value.scrollToPosition shouldBe null
        }

        should("navigation starts as None") {
            newViewModel().navigation.value shouldBe ProviderNavigation.None
        }

        should("onProviderClick in select mode selects the provider and holds Finish") {
            val provider = ShortURLProviderCompanion.enabled.first { it != ShortURLProviderCompanion.default }
            val viewModel = newViewModel(handleOf(ProviderScreenMode.Select))

            viewModel.onProviderClick(provider)

            viewModel.navigation.value shouldBe ProviderNavigation.Finish
            userSettings.selectedShortURLProvider shouldBe provider
        }

        should("onProviderClick in info mode leaves selection unchanged and holds ShowInfo") {
            val previouslySelected = ShortURLProviderCompanion.default
            val provider = ShortURLProviderCompanion.enabled.first { it != ShortURLProviderCompanion.default }
            val viewModel = newViewModel()

            viewModel.onProviderClick(provider)

            viewModel.navigation.value shouldBe ProviderNavigation.ShowInfo(provider)
            userSettings.selectedShortURLProvider shouldBe previouslySelected
        }

        should("onProviderInfoClick always holds ShowInfo in either mode") {
            val provider = ShortURLProviderCompanion.enabled.first { it != ShortURLProviderCompanion.default }
            val viewModel = newViewModel(handleOf(ProviderScreenMode.Select))

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
