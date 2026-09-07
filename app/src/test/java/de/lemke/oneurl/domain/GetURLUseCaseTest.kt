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

import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class GetURLUseCaseTest : ShouldSpec(
    {
        val urlRepository = mockk<URLRepository>()
        val getURL = GetURLUseCase(urlRepository)

        should("invoke(shortURL) delegates to urlRepository.getURL and returns its result") {
            val url = testUrl(shortURL = "https://short.url/abc")
            coEvery { urlRepository.getURL("https://short.url/abc") } returns url

            val result = getURL("https://short.url/abc")

            result shouldBe url
            coVerify(exactly = 1) { urlRepository.getURL("https://short.url/abc") }
        }

        should("invoke(shortURL) returns null when urlRepository has no match") {
            coEvery { urlRepository.getURL("https://short.url/missing") } returns null

            val result = getURL("https://short.url/missing")

            result shouldBe null
        }

        should("invoke(shortURLProvider, longURL) delegates to urlRepository.getURL and returns its result") {
            val provider = ShortURLProviderCompanion.default
            val urls = listOf(testUrl(shortURL = "https://short.url/abc", provider = provider))
            coEvery { urlRepository.getURL(provider, "https://example.com") } returns urls

            val result = getURL(provider, "https://example.com")

            result shouldBe urls
            coVerify(exactly = 1) { urlRepository.getURL(provider, "https://example.com") }
        }
    },
)
