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
class AddURLUseCaseTest : ShouldSpec(
    {
        should("invoke stores the url once the injected dispatcher runs") {
            val repository = FakeURLRepository()
            val dispatcher = StandardTestDispatcher()
            val addURL = AddURLUseCase(repository, dispatcher)
            val url = testUrl(shortURL = "https://short.url/abc")

            coroutineScope {
                launch(Dispatchers.Unconfined) { addURL(url) }
                repository.urls shouldBe emptyList()

                dispatcher.scheduler.advanceUntilIdle()

                repository.urls shouldBe listOf(url)
            }
        }
    },
)
