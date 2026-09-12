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

import app.cash.turbine.test
import de.lemke.oneurl.domain.DeleteURLUseCase
import de.lemke.oneurl.domain.ObserveURLsUseCase
import de.lemke.oneurl.domain.UpdateURLUseCase
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.model.URL
import dev.oneuiproject.oneui.layout.ToolbarLayout.AllSelectorState
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow

private fun testUrl(
    shortURL: String = "https://short.url/x",
    longURL: String = "https://example.com",
    provider: ShortURLProvider = ShortURLProviderCompanion.default,
    favorite: Boolean = false,
    title: String = "title",
    description: String = "description",
    added: ZonedDateTime = ZonedDateTime.now(),
) = URL(
    shortURL = shortURL,
    longURL = longURL,
    shortURLProvider = provider,
    favorite = favorite,
    title = title,
    description = description,
    added = added,
)

class MainViewModelTest : ShouldSpec(
    {
        val observeURLs = mockk<ObserveURLsUseCase>()
        val deleteURL = mockk<DeleteURLUseCase>()
        val updateURL = mockk<UpdateURLUseCase>()
        lateinit var viewModel: MainViewModel

        beforeEach {
            clearMocks(observeURLs, deleteURL, updateURL)
            every { observeURLs(any(), any()) } returns MutableStateFlow(emptyList())
            coEvery { updateURL(any<URL>()) } returns Unit
            coEvery { updateURL(any<List<URL>>()) } returns Unit
            coEvery { deleteURL(any<List<URL>>()) } returns Unit
            viewModel = MainViewModel(observeURLs, deleteURL, updateURL)
        }

        should("state.urls and isUIReady reflect what observeURLs emits") {
            val url = testUrl()
            every { observeURLs(any(), any()) } returns MutableStateFlow(listOf(url))
            viewModel = MainViewModel(observeURLs, deleteURL, updateURL)

            viewModel.state.value.urls shouldBe listOf(url)
            viewModel.state.value.isUIReady shouldBe true
        }

        should("emit NewItemAdded when a second emission adds an id under the same search/filterFavorite") {
            val url1 = testUrl(shortURL = "https://short.url/1")
            val url2 = testUrl(shortURL = "https://short.url/2")
            val urlsFlow = MutableStateFlow(listOf(url1))
            every { observeURLs(any(), any()) } returns urlsFlow
            viewModel = MainViewModel(observeURLs, deleteURL, updateURL)

            viewModel.events.test {
                urlsFlow.value = listOf(url1, url2)
                awaitItem() shouldBe MainEvent.NewItemAdded
            }
        }

        should("emit no event when a second emission only reorders/removes ids") {
            val url1 = testUrl(shortURL = "https://short.url/1")
            val url2 = testUrl(shortURL = "https://short.url/2")
            val urlsFlow = MutableStateFlow(listOf(url1, url2))
            every { observeURLs(any(), any()) } returns urlsFlow
            viewModel = MainViewModel(observeURLs, deleteURL, updateURL)

            viewModel.events.test {
                urlsFlow.value = listOf(url2, url1)
                expectNoEvents()
            }
        }

        should("emit no NewItemAdded when search/filterFavorite changed even though ids grew") {
            val url1 = testUrl(shortURL = "https://short.url/1")
            val url2 = testUrl(shortURL = "https://short.url/2")
            val urlsFlow = MutableStateFlow(listOf(url1))
            every { observeURLs(any(), any()) } returns urlsFlow
            viewModel = MainViewModel(observeURLs, deleteURL, updateURL)

            viewModel.events.test {
                viewModel.setSearch("changed")
                urlsFlow.value = listOf(url1, url2)
                expectNoEvents()
            }
        }

        should("setSearch updates search") {
            viewModel.setSearch("query")
            viewModel.search.value shouldBe "query"
        }

        should("setFilterFavorite updates filterFavorite") {
            viewModel.setFilterFavorite(true)
            viewModel.filterFavorite.value shouldBe true
        }

        should("setFavorite updates the url's favorite flag via updateURL") {
            val url = testUrl(favorite = false)
            viewModel.setFavorite(url, true)
            coVerify { updateURL(url.copy(favorite = true)) }
        }

        should("setFavorites updates all urls' favorite flag via updateURL") {
            val urls = listOf(testUrl(shortURL = "https://short.url/1"), testUrl(shortURL = "https://short.url/2"))
            viewModel.setFavorites(urls, true)
            coVerify { updateURL(urls.map { it.copy(favorite = true) }) }
        }

        should("delete calls deleteURL with the given urls") {
            val urls = listOf(testUrl(shortURL = "https://short.url/1"), testUrl(shortURL = "https://short.url/2"))
            viewModel.delete(urls)
            coVerify { deleteURL(urls) }
        }

        should("setAllSelectorState updates allSelectorState") {
            val state = AllSelectorState(totalSelected = 3, isChecked = true, isEnabled = false)
            viewModel.setAllSelectorState(state)
            viewModel.allSelectorState.value shouldBe state
        }
    },
)
