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

import de.lemke.oneurl.data.FakeURLRepository
import de.lemke.oneurl.domain.model.Dagd
import de.lemke.oneurl.domain.model.Tinyurl
import de.lemke.oneurl.domain.model.URL
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class GetURLUseCaseTest : ShouldSpec(
    {
        val newer = testUrl(shortURL = "https://da.gd/newer", provider = Dagd)
        val older = testUrl(shortURL = "https://da.gd/older", provider = Dagd)
        val otherProvider = testUrl(shortURL = "https://tinyurl.com/x", provider = Tinyurl)
        val repository = FakeURLRepository(listOf(newer, otherProvider, older))

        should("invoke(shortURL) returns the stored url once the injected dispatcher runs") {
            val dispatcher = StandardTestDispatcher()
            val getURL = GetURLUseCase(repository, dispatcher)
            var result: URL? = null

            coroutineScope {
                launch(Dispatchers.Unconfined) { result = getURL("https://da.gd/older") }
                result shouldBe null

                dispatcher.scheduler.advanceUntilIdle()

                result shouldBe older
            }
        }

        should("invoke(shortURL) returns null for an unknown short url") {
            val getURL = GetURLUseCase(repository, Dispatchers.Unconfined)

            getURL("https://da.gd/missing") shouldBe null
        }

        should("invoke(shortURLProvider, longURL) lists the provider's urls newest first once the injected dispatcher runs") {
            val dispatcher = StandardTestDispatcher()
            val getURL = GetURLUseCase(repository, dispatcher)
            var result: List<URL>? = null

            coroutineScope {
                launch(Dispatchers.Unconfined) { result = getURL(Dagd, "https://example.com") }
                result shouldBe null

                dispatcher.scheduler.advanceUntilIdle()

                result shouldBe listOf(newer, older)
            }
        }
    },
)
