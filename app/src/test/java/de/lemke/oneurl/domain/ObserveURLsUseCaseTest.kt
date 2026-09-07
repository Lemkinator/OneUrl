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
import de.lemke.oneurl.data.URLRepository
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class ObserveURLsUseCaseTest : ShouldSpec(
    {
        val urlRepository = mockk<URLRepository>()
        val observeURLs = ObserveURLsUseCase(urlRepository)

        val favoriteUrl = testUrl(shortURL = "https://short.url/fav", favorite = true, title = "favorite title")
        val plainUrl = testUrl(shortURL = "https://short.url/plain", favorite = false, title = "plain title")
        val allUrls = listOf(favoriteUrl, plainUrl)

        should("emits emptyList when searchQuery is non-null and blank, regardless of repository contents") {
            every { urlRepository.observeURLs() } returns flowOf(allUrls)

            observeURLs(searchQuery = flowOf("   "), filterFavorite = flowOf(false)).test {
                awaitItem() shouldBe emptyList()
                awaitComplete()
            }
        }

        should("emits repository urls filtered by url.contains(query) when searchQuery is non-null and non-blank") {
            every { urlRepository.observeURLs() } returns flowOf(allUrls)

            observeURLs(searchQuery = flowOf("favorite"), filterFavorite = flowOf(false)).test {
                awaitItem() shouldBe listOf(favoriteUrl)
                awaitComplete()
            }
        }

        should("emits repository urls filtered to favorite == true when searchQuery is null and filterFavorite is true") {
            every { urlRepository.observeURLs() } returns flowOf(allUrls)

            observeURLs(searchQuery = flowOf(null), filterFavorite = flowOf(true)).test {
                awaitItem() shouldBe listOf(favoriteUrl)
                awaitComplete()
            }
        }

        should("emits all repository urls unfiltered when searchQuery is null and filterFavorite is false") {
            every { urlRepository.observeURLs() } returns flowOf(allUrls)

            observeURLs(searchQuery = flowOf(null), filterFavorite = flowOf(false)).test {
                awaitItem() shouldBe allUrls
                awaitComplete()
            }
        }

        should("re-subscribes to the filterFavorite branch when searchQuery switches from a query back to null") {
            every { urlRepository.observeURLs() } returns flowOf(allUrls)
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
