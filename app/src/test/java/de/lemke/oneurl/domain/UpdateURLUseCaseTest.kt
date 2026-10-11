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
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateURLUseCaseTest : ShouldSpec(
    {
        val a = testUrl(shortURL = "https://short.url/a")
        val b = testUrl(shortURL = "https://short.url/b")

        should("invoke(url) updates the stored url once the injected dispatcher runs") {
            val repository = FakeURLRepository(listOf(a, b))
            val dispatcher = StandardTestDispatcher()
            val updateURL = UpdateURLUseCase(repository, dispatcher)
            val favorite = a.copy(favorite = true)

            coroutineScope {
                launch(Dispatchers.Unconfined) { updateURL(favorite) }
                repository.urls shouldBe listOf(a, b)

                dispatcher.scheduler.advanceUntilIdle()

                repository.urls shouldBe listOf(favorite, b)
            }
        }

        should("invoke(urls) updates every stored url once the injected dispatcher runs") {
            val repository = FakeURLRepository(listOf(a, b))
            val dispatcher = StandardTestDispatcher()
            val updateURL = UpdateURLUseCase(repository, dispatcher)
            val renamed = listOf(a.copy(title = "a"), b.copy(title = "b"))

            coroutineScope {
                launch(Dispatchers.Unconfined) { updateURL(renamed) }
                repository.urls shouldBe listOf(a, b)

                dispatcher.scheduler.advanceUntilIdle()

                repository.urls shouldBe renamed
            }
        }
    },
)
