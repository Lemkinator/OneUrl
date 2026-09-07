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
import io.kotest.core.spec.style.ShouldSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class UpdateURLUseCaseTest : ShouldSpec(
    {
        val urlRepository = mockk<URLRepository>()
        val updateURL = UpdateURLUseCase(urlRepository)

        should("invoke(url) delegates to urlRepository.updateURL with the given url") {
            val url = testUrl(shortURL = "https://short.url/abc")
            coEvery { urlRepository.updateURL(url) } returns Unit

            updateURL(url)

            coVerify(exactly = 1) { urlRepository.updateURL(url) }
        }

        should("invoke(urls) delegates to urlRepository.updateURLs with the given urls") {
            val urls = listOf(testUrl(shortURL = "https://short.url/a"), testUrl(shortURL = "https://short.url/b"))
            coEvery { urlRepository.updateURLs(urls) } returns Unit

            updateURL(urls)

            coVerify(exactly = 1) { urlRepository.updateURLs(urls) }
        }
    },
)
