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

package de.lemke.oneurl.domain

import app.cash.turbine.test
import de.lemke.oneurl.data.FakeURLRepository
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class ObserveURLsUseCaseTest : ShouldSpec(
    {
        val favoriteUrl = testUrl(shortURL = "https://short.url/fav", favorite = true, title = "favorite title")
        val plainUrl = testUrl(shortURL = "https://short.url/plain", favorite = false, title = "plain title")
        val allUrls = listOf(favoriteUrl, plainUrl)
        lateinit var urlRepository: FakeURLRepository
        lateinit var observeURLs: ObserveURLsUseCase

        beforeEach {
            urlRepository = FakeURLRepository(allUrls)
            observeURLs = ObserveURLsUseCase(urlRepository, Dispatchers.Unconfined)
        }

        should("emits emptyList when searchQuery is non-null and blank, regardless of repository contents") {

            observeURLs(searchQuery = flowOf("   "), filterFavorite = flowOf(false)).test {
                awaitItem() shouldBe emptyList()
                cancelAndIgnoreRemainingEvents()
            }
        }

        should("emits repository urls filtered by url.contains(query) when searchQuery is non-null and non-blank") {

            observeURLs(searchQuery = flowOf("favorite"), filterFavorite = flowOf(false)).test {
                awaitItem() shouldBe listOf(favoriteUrl)
                cancelAndIgnoreRemainingEvents()
            }
        }

        should("emits repository urls filtered to favorite == true when searchQuery is null and filterFavorite is true") {

            observeURLs(searchQuery = flowOf(null), filterFavorite = flowOf(true)).test {
                awaitItem() shouldBe listOf(favoriteUrl)
                cancelAndIgnoreRemainingEvents()
            }
        }

        should("emits all repository urls unfiltered when searchQuery is null and filterFavorite is false") {

            observeURLs(searchQuery = flowOf(null), filterFavorite = flowOf(false)).test {
                awaitItem() shouldBe allUrls
                cancelAndIgnoreRemainingEvents()
            }
        }

        should("emits again when the repository stores a new url") {
            val added = testUrl(shortURL = "https://short.url/added", favorite = true, title = "added title")

            observeURLs(searchQuery = flowOf(null), filterFavorite = flowOf(true)).test {
                awaitItem() shouldBe listOf(favoriteUrl)

                urlRepository.addURL(added)

                awaitItem() shouldBe listOf(added, favoriteUrl)
                cancelAndIgnoreRemainingEvents()
            }
        }

        should("re-subscribes to the filterFavorite branch when searchQuery switches from a query back to null") {
            val searchQuery = MutableStateFlow<String?>("favorite")
            val filterFavorite = MutableStateFlow(false)

            observeURLs(searchQuery = searchQuery, filterFavorite = filterFavorite).test {
                awaitItem() shouldBe listOf(favoriteUrl)

                searchQuery.value = null

                awaitItem() shouldBe allUrls

                filterFavorite.value = true

                awaitItem() shouldBe listOf(favoriteUrl)

                cancelAndIgnoreRemainingEvents()
            }
        }
    },
)
