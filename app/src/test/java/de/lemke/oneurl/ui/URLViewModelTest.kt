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

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import de.lemke.oneurl.data.FakeShortURLClient
import de.lemke.oneurl.data.FakeURLRepository
import de.lemke.oneurl.domain.DeleteURLUseCase
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.GetVisitCountUseCase
import de.lemke.oneurl.domain.UpdateURLUseCase
import de.lemke.oneurl.domain.testUrl
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class URLViewModelTest : ShouldSpec(
    {
        val url = testUrl(shortURL = "https://short.url/x")
        lateinit var repository: FakeURLRepository
        lateinit var shortURLClient: FakeShortURLClient

        fun newViewModel(shortURL: String? = url.shortURL) =
            URLViewModel(
                if (shortURL == null) SavedStateHandle() else SavedStateHandle(mapOf(ShortURLQRCodeViewModel.KEY_SHORT_URL to shortURL)),
                GetURLUseCase(repository, UnconfinedTestDispatcher()),
                UpdateURLUseCase(repository, UnconfinedTestDispatcher()),
                DeleteURLUseCase(repository, UnconfinedTestDispatcher()),
                GetVisitCountUseCase(shortURLClient),
            )

        beforeEach {
            repository = FakeURLRepository(listOf(url))
            shortURLClient = FakeShortURLClient().apply { visitCounts[url.shortURL] = 42 }
        }

        should("init loads the URL and refreshes the visit count") {
            val viewModel = newViewModel()

            viewModel.state.value.url shouldBe url
            viewModel.state.value.visitCount shouldBe 42
            viewModel.state.value.isRefreshingVisits
                .shouldBeFalse()
        }

        should("init holds the NotFound exit and leaves url null when the URL does not exist") {
            val viewModel = newViewModel(shortURL = "https://short.url/missing")

            viewModel.exit.value shouldBe UrlDetailExit.NotFound
            viewModel.state.value.url shouldBe null
        }

        should("init holds no exit when the URL exists") {
            newViewModel().exit.value shouldBe UrlDetailExit.None
        }

        should("onExitHandled returns the exit to None") {
            val viewModel = newViewModel(shortURL = "https://short.url/missing")

            viewModel.onExitHandled(UrlDetailExit.NotFound)

            viewModel.exit.value shouldBe UrlDetailExit.None
        }

        should("onExitHandled keeps a different exit") {
            val viewModel = newViewModel(shortURL = "https://short.url/missing")

            viewModel.onExitHandled(UrlDetailExit.Deleted)

            viewModel.exit.value shouldBe UrlDetailExit.NotFound
        }

        should("init falls back to an empty shortURL when no saved-state key is present") {
            val blank = testUrl(shortURL = "")
            repository = FakeURLRepository(listOf(blank))

            newViewModel(shortURL = null).state.value.url shouldBe blank
        }

        should("toggleFavorite flips the loaded url's favorite flag and persists it") {
            val viewModel = newViewModel()

            viewModel.toggleFavorite()

            viewModel.state.value.url
                ?.favorite shouldBe true
            repository.urls shouldBe listOf(url.copy(favorite = true))
        }

        should("toggleFavorite is a no-op when no url is loaded") {
            val viewModel = newViewModel(shortURL = "https://short.url/missing")

            viewModel.toggleFavorite()

            repository.urls shouldBe listOf(url)
        }

        should("refreshVisitCount is a no-op when no url is loaded") {
            val viewModel = newViewModel(shortURL = "https://short.url/missing")

            viewModel.refreshVisitCount()

            shortURLClient.visitCountRequests shouldBe emptyList()
        }

        should("refreshVisitCount called again after init shows the new visit count") {
            val viewModel = newViewModel()
            shortURLClient.visitCounts[url.shortURL] = 43

            viewModel.refreshVisitCount()

            viewModel.state.value.visitCount shouldBe 43
        }

        should("refreshVisitCount catches a plain exception without crashing or exiting") {
            // The catch branch logs via android.util.Log, which throws "not mocked" on the plain JVM
            // unless stubbed.
            mockkStatic(Log::class)
            every { Log.e(any(), any(), any()) } returns 0
            try {
                shortURLClient.visitCountFailure = IllegalStateException("boom")
                val viewModel = newViewModel()

                viewModel.exit.value shouldBe UrlDetailExit.None
                viewModel.state.value.isRefreshingVisits
                    .shouldBeFalse()
                viewModel.state.value.visitCount shouldBe null
            } finally {
                unmockkStatic(Log::class)
            }
        }

        should("refreshVisitCount is a no-op reentrancy guard while already refreshing") {
            val viewModel = newViewModel()
            shortURLClient.gate = CompletableDeferred()

            viewModel.refreshVisitCount()
            viewModel.refreshVisitCount()

            viewModel.state.value.isRefreshingVisits
                .shouldBeTrue()
            shortURLClient.visitCountRequests shouldBe listOf(url, url)
        }

        should("delete removes the loaded url and holds the Deleted exit") {
            val viewModel = newViewModel()

            viewModel.delete()

            viewModel.exit.value shouldBe UrlDetailExit.Deleted
            repository.urls shouldBe emptyList()
        }

        should("delete is a no-op when no url is loaded") {
            val viewModel = newViewModel(shortURL = "https://short.url/missing")

            viewModel.delete()

            repository.urls shouldBe listOf(url)
            viewModel.exit.value shouldBe UrlDetailExit.NotFound
        }
    },
)
