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
import app.cash.turbine.test
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.domain.AddURLUseCase
import de.lemke.oneurl.domain.GetURLTitleUseCase
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.GenerateURLResult
import de.lemke.oneurl.domain.generateURL.GenerateURLUseCase
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.domain.testUrl
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class AddURLViewModelTest : ShouldSpec(
    {
        val generateURL = mockk<GenerateURLUseCase>()
        val getURLTitle = mockk<GetURLTitleUseCase>()
        val addURL = mockk<AddURLUseCase>()
        val getURL = mockk<GetURLUseCase>()
        lateinit var userSettings: UserSettings

        fun newViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
            AddURLViewModel(
                savedStateHandle,
                userSettings,
                generateURL,
                getURLTitle,
                addURL,
                getURL,
            )

        beforeEach {
            clearMocks(generateURL, getURLTitle, addURL, getURL)
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            coEvery { getURL(any<ShortURLProvider>(), any()) } returns emptyList()
            coEvery { getURLTitle(any()) } returns "title"
            coEvery { addURL(any()) } returns Unit
            coEvery { generateURL(any(), any(), any(), any()) } returns GenerateURLResult.Success("https://short.url/abc")
        }

        should("init state reflects userSettings when there is no intent URL") {
            userSettings.lastURL = "https://last.example.com"
            userSettings.lastAlias = "alias"
            userSettings.lastDescription = "a description"
            val provider = ShortURLProviderCompanion.enabled.first { it != ShortURLProviderCompanion.default }
            userSettings.selectedShortURLProvider = provider

            val viewModel = newViewModel()

            viewModel.state.value.initialURL shouldBe "https://last.example.com"
            viewModel.state.value.initialAlias shouldBe "alias"
            viewModel.state.value.initialDescription shouldBe "a description"
            viewModel.state.value.selectedProvider shouldBe provider
        }

        should("init state uses the intent URL and overwrites lastURL when launched with one") {
            userSettings.lastURL = "https://previous.example.com"

            val viewModel = newViewModel(SavedStateHandle(mapOf("url" to "https://intent.example.com")))

            viewModel.state.value.initialURL shouldBe "https://intent.example.com"
            userSettings.lastURL shouldBe "https://intent.example.com"
        }

        should("selectedProvider updates reactively when selectedShortURLProvider changes") {
            val viewModel = newViewModel()
            val newProvider = ShortURLProviderCompanion.enabled.first { it != ShortURLProviderCompanion.default }

            userSettings.selectedShortURLProvider = newProvider

            viewModel.state.value.selectedProvider shouldBe newProvider
        }

        should("onLongURLChanged writes lastURL") {
            val viewModel = newViewModel()
            viewModel.onLongURLChanged("https://new.example.com")
            userSettings.lastURL shouldBe "https://new.example.com"
        }

        should("onAliasChanged writes lastAlias") {
            val viewModel = newViewModel()
            viewModel.onAliasChanged("new-alias")
            userSettings.lastAlias shouldBe "new-alias"
        }

        should("onDescriptionChanged writes lastDescription") {
            val viewModel = newViewModel()
            viewModel.onDescriptionChanged("new description")
            userSettings.lastDescription shouldBe "new description"
        }

        should("submit is a no-op while already loading") {
            val stuck = CompletableDeferred<List<URL>>()
            coEvery { getURL(any<ShortURLProvider>(), any()) } coAnswers { stuck.await() }
            val viewModel = newViewModel()

            viewModel.submit("https://example.com", "", "")
            viewModel.state.value.isLoading
                .shouldBeTrue()
            viewModel.submit("https://example.com", "", "")

            coVerify(exactly = 1) { getURL(any<ShortURLProvider>(), any()) }
        }

        should("submit emits AlreadyShortened with blank alias when an existing URL is found") {
            val provider = ShortURLProviderCompanion.default
            val existing = testUrl(shortURL = "${provider.baseURL}/existing", provider = provider)
            coEvery { getURL(provider, any()) } returns listOf(existing)
            val viewModel = newViewModel()

            viewModel.events.test {
                viewModel.submit("https://example.com", "", "")
                awaitItem() shouldBe AddUrlEvent.AlreadyShortened(existing.shortURL)
            }
            coVerify(exactly = 0) { generateURL(any(), any(), any(), any()) }
        }

        should("submit emits AlreadyShortened when the alias matches an existing entry's shortURL exactly") {
            val provider = ShortURLProviderCompanion.default
            val other = testUrl(shortURL = "${provider.baseURL}/other", provider = provider)
            val exact = testUrl(shortURL = "${provider.baseURL}/my-alias", provider = provider)
            coEvery { getURL(provider, any()) } returns listOf(other, exact)
            val viewModel = newViewModel()

            viewModel.events.test {
                viewModel.submit("https://example.com", "my-alias", "")
                awaitItem() shouldBe AddUrlEvent.AlreadyShortened(exact.shortURL)
            }
            coVerify(exactly = 0) { generateURL(any(), any(), any(), any()) }
        }

        should("submit falls through to generateURL when alias is given but does not match an existing entry") {
            val provider = ShortURLProviderCompanion.default
            val other = testUrl(shortURL = "${provider.baseURL}/other", provider = provider)
            coEvery { getURL(provider, any()) } returns listOf(other)
            val viewModel = newViewModel()

            viewModel.events.test {
                viewModel.submit("https://example.com", "not-taken", "")
                awaitItem() shouldBe AddUrlEvent.Saved
            }
            coVerify(exactly = 1) { getURLTitle(any()) }
            coVerify(exactly = 1) { generateURL(provider, any(), "not-taken", any()) }
        }

        should("submit calls getURLTitle and generateURL with the sanitized long URL when no existing URL is found") {
            val provider = ShortURLProviderCompanion.default
            val viewModel = newViewModel()
            val sanitized = provider.sanitizeLongURL("  https://example.com  ")

            viewModel.events.test {
                viewModel.submit("  https://example.com  ", "", "")
                awaitItem() shouldBe AddUrlEvent.Saved
            }

            coVerify(exactly = 1) { getURLTitle(sanitized) }
            coVerify(exactly = 1) { generateURL(provider, sanitized, "", any()) }
        }

        should("submit emits Error and stops loading without calling addURL on Failure") {
            coEvery { generateURL(any(), any(), any(), any()) } returns GenerateURLResult.Failure(GenerateURLError.NoInternet)
            val viewModel = newViewModel()

            viewModel.events.test {
                viewModel.submit("https://example.com", "", "")
                awaitItem() shouldBe AddUrlEvent.Error(GenerateURLError.NoInternet)
            }
            viewModel.state.value.isLoading
                .shouldBeFalse()
            coVerify(exactly = 0) { addURL(any()) }
        }

        should("submit saves and emits Saved on Success when autoCopyOnCreate is false") {
            userSettings.autoCopyOnCreate = false
            val slot = slot<URL>()
            coEvery { addURL(capture(slot)) } returns Unit
            val provider = ShortURLProviderCompanion.default
            val viewModel = newViewModel()

            viewModel.events.test {
                viewModel.submit("https://example.com", "", "my description")
                awaitItem() shouldBe AddUrlEvent.Saved
            }

            coVerify(exactly = 1) { addURL(any()) }
            slot.captured.shortURL shouldBe "https://short.url/abc"
            slot.captured.longURL shouldBe provider.sanitizeLongURL("https://example.com")
            slot.captured.shortURLProvider shouldBe provider
            slot.captured.title shouldBe "title"
            slot.captured.description shouldBe "my description"
            slot.captured.favorite.shouldBeFalse()
        }

        should("submit emits CopyAndFinish on Success when autoCopyOnCreate is true") {
            userSettings.autoCopyOnCreate = true
            coEvery { getURLTitle(any()) } returns "my title"
            val viewModel = newViewModel()

            viewModel.events.test {
                viewModel.submit("https://example.com", "", "")
                awaitItem() shouldBe AddUrlEvent.CopyAndFinish("https://short.url/abc", "my title")
            }
        }
    },
)
