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

import de.lemke.oneurl.data.FakeURLRepository
import de.lemke.oneurl.domain.DeleteURLUseCase
import de.lemke.oneurl.domain.ObserveURLsUseCase
import de.lemke.oneurl.domain.UpdateURLUseCase
import de.lemke.oneurl.domain.testUrl
import dev.oneuiproject.oneui.layout.ToolbarLayout.AllSelectorState
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest : ShouldSpec(
    {
        val url1 = testUrl(shortURL = "https://short.url/1")
        val url2 = testUrl(shortURL = "https://short.url/2")
        val url3 = testUrl(shortURL = "https://short.url/3")
        lateinit var repository: FakeURLRepository

        fun newViewModel(observeDispatcher: CoroutineDispatcher = Dispatchers.Unconfined) =
            MainViewModel(
                ObserveURLsUseCase(repository, observeDispatcher),
                DeleteURLUseCase(repository, Dispatchers.Unconfined),
                UpdateURLUseCase(repository, Dispatchers.Unconfined),
            )

        beforeEach {
            repository = FakeURLRepository()
        }

        should("state.urls and isUIReady reflect the stored urls") {
            repository = FakeURLRepository(listOf(url1))
            val viewModel = newViewModel()

            viewModel.state.value shouldBe MainUiState(urls = listOf(url1), isUIReady = true, reveal = null)
        }

        should("state.reveal holds the added short URL when a url is added under the same search/filterFavorite") {
            repository = FakeURLRepository(listOf(url1))
            val viewModel = newViewModel()

            viewModel.state.value.reveal shouldBe null
            repository.addURL(url2)

            viewModel.state.value shouldBe MainUiState(urls = listOf(url2, url1), isUIReady = true, reveal = "https://short.url/2")
        }

        should("state.reveal holds the newest added short URL when one emission adds two") {
            repository = FakeURLRepository(listOf(url1))
            val observeDispatcher = StandardTestDispatcher()
            val viewModel = newViewModel(observeDispatcher)
            observeDispatcher.scheduler.advanceUntilIdle()

            repository.addURL(url2)
            repository.addURL(url3)
            observeDispatcher.scheduler.advanceUntilIdle()

            viewModel.state.value shouldBe MainUiState(urls = listOf(url3, url2, url1), isUIReady = true, reveal = "https://short.url/3")
        }

        should("state.reveal stays null when a write only changes or removes stored urls") {
            repository = FakeURLRepository(listOf(url2, url1))
            val viewModel = newViewModel()

            viewModel.setFavorite(url1, true)
            viewModel.delete(listOf(url2))

            viewModel.state.value shouldBe MainUiState(urls = listOf(url1.copy(favorite = true)), isUIReady = true, reveal = null)
        }

        should("state.reveal stays null when a search/filterFavorite change brings urls into view") {
            val favorite = url1.copy(favorite = true)
            repository = FakeURLRepository(listOf(url2, favorite))
            val viewModel = newViewModel()
            viewModel.setFilterFavorite(true)

            viewModel.setFilterFavorite(false)

            viewModel.state.value shouldBe MainUiState(urls = listOf(url2, favorite), isUIReady = true, reveal = null)
        }

        should("an unhandled reveal survives a later write that adds nothing") {
            repository = FakeURLRepository(listOf(url1))
            val viewModel = newViewModel()

            repository.addURL(url2)
            viewModel.setFavorite(url1, true)

            viewModel.state.value.reveal shouldBe "https://short.url/2"
        }

        should("onRevealHandled clears the handled reveal") {
            repository = FakeURLRepository(listOf(url1))
            val viewModel = newViewModel()
            repository.addURL(url2)

            viewModel.onRevealHandled("https://short.url/2")

            viewModel.state.value shouldBe MainUiState(urls = listOf(url2, url1), isUIReady = true, reveal = null)
        }

        should("onRevealHandled keeps a newer reveal") {
            repository = FakeURLRepository(listOf(url1))
            val viewModel = newViewModel()
            repository.addURL(url2)
            repository.addURL(url3)

            viewModel.onRevealHandled("https://short.url/2")

            viewModel.state.value.reveal shouldBe "https://short.url/3"
        }

        should("setSearch updates search") {
            val viewModel = newViewModel()
            viewModel.setSearch("query")
            viewModel.search.value shouldBe "query"
        }

        should("setFilterFavorite updates filterFavorite and filters the urls") {
            repository = FakeURLRepository(listOf(url2, url1.copy(favorite = true)))
            val viewModel = newViewModel()

            viewModel.setFilterFavorite(true)

            viewModel.filterFavorite.value shouldBe true
            viewModel.state.value.urls shouldBe listOf(url1.copy(favorite = true))
        }

        should("setFavorite stores the url's new favorite flag") {
            repository = FakeURLRepository(listOf(url1))
            val viewModel = newViewModel()

            viewModel.setFavorite(url1, true)

            repository.urls shouldBe listOf(url1.copy(favorite = true))
        }

        should("setFavorites stores the new favorite flag of every url") {
            repository = FakeURLRepository(listOf(url2, url1))
            val viewModel = newViewModel()

            viewModel.setFavorites(listOf(url2, url1), true)

            repository.urls shouldBe listOf(url2.copy(favorite = true), url1.copy(favorite = true))
        }

        should("delete removes the given urls") {
            repository = FakeURLRepository(listOf(url3, url2, url1))
            val viewModel = newViewModel()

            viewModel.delete(listOf(url3, url1))

            repository.urls shouldBe listOf(url2)
            viewModel.state.value.urls shouldBe listOf(url2)
        }

        should("setAllSelectorState updates allSelectorState") {
            val viewModel = newViewModel()
            val state = AllSelectorState(totalSelected = 3, isChecked = true, isEnabled = false)
            viewModel.setAllSelectorState(state)
            viewModel.allSelectorState.value shouldBe state
        }
    },
)
