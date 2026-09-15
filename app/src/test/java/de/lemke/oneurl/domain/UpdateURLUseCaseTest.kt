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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateURLUseCaseTest : ShouldSpec(
    {
        val urlRepository = mockk<URLRepository>()
        val dispatcher = StandardTestDispatcher()
        val updateURL = UpdateURLUseCase(urlRepository, dispatcher)

        should("invoke(url) suspends on the injected dispatcher before delegating to urlRepository.updateURL") {
            val url = testUrl(shortURL = "https://short.url/abc")
            coEvery { urlRepository.updateURL(url) } returns Unit

            coroutineScope {
                launch(Dispatchers.Unconfined) { updateURL(url) }
                coVerify(exactly = 0) { urlRepository.updateURL(url) }

                dispatcher.scheduler.advanceUntilIdle()

                coVerify(exactly = 1) { urlRepository.updateURL(url) }
            }
        }

        should("invoke(urls) suspends on the injected dispatcher before delegating to urlRepository.updateURLs") {
            val urls = listOf(testUrl(shortURL = "https://short.url/a"), testUrl(shortURL = "https://short.url/b"))
            coEvery { urlRepository.updateURLs(urls) } returns Unit

            coroutineScope {
                launch(Dispatchers.Unconfined) { updateURL(urls) }
                coVerify(exactly = 0) { urlRepository.updateURLs(urls) }

                dispatcher.scheduler.advanceUntilIdle()

                coVerify(exactly = 1) { urlRepository.updateURLs(urls) }
            }
        }
    },
)
