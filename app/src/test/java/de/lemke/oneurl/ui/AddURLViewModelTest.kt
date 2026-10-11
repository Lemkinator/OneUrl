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
import de.lemke.oneurl.R
import de.lemke.oneurl.data.FakeNetworkMonitor
import de.lemke.oneurl.data.FakePageTitleFetcher
import de.lemke.oneurl.data.FakeShortURLClient
import de.lemke.oneurl.data.FakeShortURLClient.Create
import de.lemke.oneurl.data.FakeURLRepository
import de.lemke.oneurl.data.FakeUrlhausClient
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.domain.AddURLUseCase
import de.lemke.oneurl.domain.CheckURLSafetyUseCase
import de.lemke.oneurl.domain.GetURLTitleUseCase
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.generateURL.GenerateURLUseCase
import de.lemke.oneurl.domain.model.GenerateURLError
import de.lemke.oneurl.domain.model.GenerateURLResult
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.model.UrlhausResult
import de.lemke.oneurl.domain.testUrl
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class AddURLViewModelTest : ShouldSpec(
    {
        val provider = ShortURLProviderCompanion.default
        lateinit var userSettings: UserSettings
        lateinit var repository: FakeURLRepository
        lateinit var networkMonitor: FakeNetworkMonitor
        lateinit var urlhausClient: FakeUrlhausClient
        lateinit var pageTitleFetcher: FakePageTitleFetcher
        lateinit var shortURLClient: FakeShortURLClient

        fun newViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
            AddURLViewModel(
                savedStateHandle,
                userSettings,
                GenerateURLUseCase(networkMonitor, CheckURLSafetyUseCase(urlhausClient), shortURLClient, Dispatchers.Unconfined),
                GetURLTitleUseCase(pageTitleFetcher),
                AddURLUseCase(repository, Dispatchers.Unconfined),
                GetURLUseCase(repository, Dispatchers.Unconfined),
            )

        beforeEach {
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            repository = FakeURLRepository()
            networkMonitor = FakeNetworkMonitor()
            urlhausClient = FakeUrlhausClient()
            pageTitleFetcher = FakePageTitleFetcher().apply { titles["https://example.com"] = "title" }
            shortURLClient = FakeShortURLClient().apply { results[provider] = GenerateURLResult.Success("https://short.url/abc") }
        }

        should("init state reflects userSettings when there is no intent URL") {
            userSettings.lastURL = "https://last.example.com"
            userSettings.lastAlias = "alias"
            userSettings.lastDescription = "a description"
            val other = ShortURLProviderCompanion.enabled.first { it != ShortURLProviderCompanion.default }
            userSettings.selectedShortURLProvider = other

            val viewModel = newViewModel()

            viewModel.state.value.initialURL shouldBe "https://last.example.com"
            viewModel.state.value.initialAlias shouldBe "alias"
            viewModel.state.value.initialDescription shouldBe "a description"
            viewModel.state.value.selectedProvider shouldBe other
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
            shortURLClient.gate = CompletableDeferred()
            val viewModel = newViewModel()

            viewModel.submit("https://example.com", "", "")
            viewModel.state.value.isLoading
                .shouldBeTrue()
            viewModel.submit("https://example.com", "", "")

            shortURLClient.created shouldBe listOf(Create(provider, "https://example.com", ""))
            viewModel.state.value.loadingMessageRes shouldBe R.string.generating_url
        }

        should("submit holds AlreadyShortened with blank alias when an existing URL is found") {
            val existing = testUrl(shortURL = "${provider.baseURL}/existing", provider = provider)
            repository = FakeURLRepository(listOf(existing))
            val viewModel = newViewModel()

            viewModel.submit("https://example.com", "", "")

            viewModel.outcome.value shouldBe AddUrlOutcome.AlreadyShortened(existing.shortURL)
            shortURLClient.created shouldBe emptyList()
        }

        should("submit holds AlreadyShortened when the alias matches an existing entry's shortURL exactly") {
            val other = testUrl(shortURL = "${provider.baseURL}/other", provider = provider)
            val exact = testUrl(shortURL = "${provider.baseURL}/my-alias", provider = provider)
            repository = FakeURLRepository(listOf(other, exact))
            val viewModel = newViewModel()

            viewModel.submit("https://example.com", "my-alias", "")

            viewModel.outcome.value shouldBe AddUrlOutcome.AlreadyShortened(exact.shortURL)
            shortURLClient.created shouldBe emptyList()
        }

        should("submit generates a URL when the alias does not match an existing entry") {
            val other = testUrl(shortURL = "${provider.baseURL}/other", provider = provider)
            repository = FakeURLRepository(listOf(other))
            val viewModel = newViewModel()

            viewModel.submit("https://example.com", "not-taken", "")

            viewModel.outcome.value shouldBe AddUrlOutcome.Saved
            pageTitleFetcher.requested shouldBe listOf("https://example.com")
            shortURLClient.created shouldBe listOf(Create(provider, "https://example.com", "not-taken"))
        }

        should("submit fetches the title, checks and generates the URL with the sanitized long URL") {
            val viewModel = newViewModel()

            viewModel.submit("example.com", "", "")

            viewModel.outcome.value shouldBe AddUrlOutcome.Saved
            pageTitleFetcher.requested shouldBe listOf("https://example.com")
            urlhausClient.checked shouldBe listOf("https://example.com")
            shortURLClient.created shouldBe listOf(Create(provider, "https://example.com", ""))
        }

        should("submit holds Failed and stores nothing without internet") {
            networkMonitor.online = false
            val viewModel = newViewModel()

            viewModel.submit("https://example.com", "", "")

            viewModel.outcome.value shouldBe AddUrlOutcome.Failed(GenerateURLError.NoInternet)
            viewModel.state.value.isLoading
                .shouldBeFalse()
            repository.urls shouldBe emptyList()
        }

        should("submit holds Failed with the URLhaus details for a blacklisted URL") {
            urlhausClient.results["https://example.com"] = UrlhausResult.Blacklisted("listed", "urlhaus-link", null)
            val viewModel = newViewModel()

            viewModel.submit("https://example.com", "", "")

            viewModel.outcome.value shouldBe AddUrlOutcome.Failed(GenerateURLError.BlacklistedURL("listed", "urlhaus-link", null))
            shortURLClient.created shouldBe emptyList()
        }

        should("submit saves and holds Saved on Success when autoCopyOnCreate is false") {
            userSettings.autoCopyOnCreate = false
            val viewModel = newViewModel()

            viewModel.submit("https://example.com", "", "my description")

            viewModel.outcome.value shouldBe AddUrlOutcome.Saved
            val saved = repository.urls.single()
            saved.shortURL shouldBe "https://short.url/abc"
            saved.longURL shouldBe "https://example.com"
            saved.shortURLProvider shouldBe provider
            saved.title shouldBe "title"
            saved.description shouldBe "my description"
            saved.favorite.shouldBeFalse()
        }

        should("submit falls back to an empty title when the page has none") {
            pageTitleFetcher.titles.clear()
            val viewModel = newViewModel()

            viewModel.submit("https://example.com", "", "")

            viewModel.outcome.value shouldBe AddUrlOutcome.Saved
            repository.urls.single().title shouldBe ""
        }

        should("submit ends on the last progress message of generateURL") {
            val viewModel = newViewModel()

            viewModel.submit("https://example.com", "", "")

            viewModel.outcome.value shouldBe AddUrlOutcome.Saved
            viewModel.state.value.loadingMessageRes shouldBe R.string.generating_url
        }

        should("submit holds Copy on Success when autoCopyOnCreate is true") {
            userSettings.autoCopyOnCreate = true
            pageTitleFetcher.titles["https://example.com"] = "my title"
            val viewModel = newViewModel()

            viewModel.submit("https://example.com", "", "")

            viewModel.outcome.value shouldBe AddUrlOutcome.Copy("https://short.url/abc", "my title")
            viewModel.state.value.isLoading
                .shouldBeFalse()
        }

        should("outcome starts as None") {
            newViewModel().outcome.value shouldBe AddUrlOutcome.None
        }

        should("onOutcomeHandled returns to None and admits the next submit") {
            userSettings.autoCopyOnCreate = false
            val viewModel = newViewModel()
            viewModel.submit("https://example.com", "", "")
            viewModel.onOutcomeHandled(AddUrlOutcome.Saved)

            viewModel.outcome.value shouldBe AddUrlOutcome.None
            viewModel.submit("https://example.org", "", "")
            viewModel.outcome.value shouldBe AddUrlOutcome.Saved
            shortURLClient.created shouldBe
                listOf(Create(provider, "https://example.com", ""), Create(provider, "https://example.org", ""))
        }

        should("onOutcomeHandled keeps an outcome other than the handled one") {
            networkMonitor.online = false
            val viewModel = newViewModel()
            viewModel.submit("https://example.com", "", "")
            viewModel.onOutcomeHandled(AddUrlOutcome.Saved)

            viewModel.outcome.value shouldBe AddUrlOutcome.Failed(GenerateURLError.NoInternet)
        }
    },
)
