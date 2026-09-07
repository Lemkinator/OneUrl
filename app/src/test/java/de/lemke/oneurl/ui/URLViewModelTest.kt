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

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import de.lemke.oneurl.domain.DeleteURLUseCase
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.GetVisitCountUseCase
import de.lemke.oneurl.domain.UpdateURLUseCase
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.model.URL
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.time.ZonedDateTime
import kotlinx.coroutines.awaitCancellation

private fun testUrl(
    shortURL: String = "https://short.url/x",
    longURL: String = "https://example.com",
    provider: ShortURLProvider = ShortURLProviderCompanion.default,
    favorite: Boolean = false,
) = URL(
    shortURL = shortURL,
    longURL = longURL,
    shortURLProvider = provider,
    qr = mockk<Bitmap>(),
    favorite = favorite,
    title = "title",
    description = "description",
    added = ZonedDateTime.now(),
)

class URLViewModelTest : ShouldSpec(
    {
        val getURL = mockk<GetURLUseCase>()
        val updateURL = mockk<UpdateURLUseCase>()
        val deleteURL = mockk<DeleteURLUseCase>()
        val getVisitCount = mockk<GetVisitCountUseCase>()

        fun newViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
            URLViewModel(savedStateHandle, getURL, updateURL, deleteURL, getVisitCount)

        beforeEach {
            clearMocks(getURL, updateURL, deleteURL, getVisitCount)
            coEvery { getURL(any<String>()) } returns null
            coEvery { getVisitCount(any()) } returns 42
            coEvery { updateURL(any<URL>()) } returns Unit
            coEvery { deleteURL(any<URL>()) } returns Unit
        }

        should("init loads the URL, stops loading, and refreshes the visit count") {
            val url = testUrl()
            coEvery { getURL(url.shortURL) } returns url
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))

            viewModel.state.value.url shouldBe url
            viewModel.state.value.isLoading shouldBe false
            viewModel.state.value.visitCount shouldBe 42
            viewModel.state.value.isRefreshingVisits
                .shouldBeFalse()
            coVerify(exactly = 1) { getVisitCount(url) }
        }

        should("init emits NotFound and leaves url null when the URL does not exist") {
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to "https://short.url/missing")))

            viewModel.events.test {
                awaitItem() shouldBe UrlDetailEvent.NotFound
            }
            viewModel.state.value.url shouldBe null
        }

        should("init falls back to an empty shortURL when no saved-state key is present") {
            newViewModel()

            coVerify(exactly = 1) { getURL("") }
        }

        should("toggleFavorite flips the loaded url's favorite flag and persists it") {
            val url = testUrl(favorite = false)
            coEvery { getURL(url.shortURL) } returns url
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))

            viewModel.toggleFavorite()

            viewModel.state.value.url
                ?.favorite shouldBe true
            coVerify(exactly = 1) { updateURL(url.copy(favorite = true)) }
        }

        should("toggleFavorite is a no-op when no url is loaded") {
            val viewModel = newViewModel()

            viewModel.toggleFavorite()

            coVerify(exactly = 0) { updateURL(any<URL>()) }
        }

        should("refreshVisitCount called again after init re-triggers getVisitCount") {
            val url = testUrl()
            coEvery { getURL(url.shortURL) } returns url
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))

            viewModel.refreshVisitCount()

            coVerify(atLeast = 2) { getVisitCount(url) }
        }

        should("refreshVisitCount catches a plain exception without crashing or emitting an event") {
            // The catch branch logs via android.util.Log, which throws "not mocked" on the plain JVM
            // unless stubbed.
            mockkStatic(Log::class)
            every { Log.e(any(), any(), any()) } returns 0
            try {
                val url = testUrl()
                coEvery { getURL(url.shortURL) } returns url
                coEvery { getVisitCount(url) } throws RuntimeException("boom")
                val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))

                viewModel.events.test {
                    expectNoEvents()
                }
                viewModel.state.value.isRefreshingVisits
                    .shouldBeFalse()
            } finally {
                unmockkStatic(Log::class)
            }
        }

        should("refreshVisitCount is a no-op reentrancy guard while already refreshing") {
            val url = testUrl()
            coEvery { getURL(url.shortURL) } returns url
            // init's own refreshVisitCount() call completes immediately via the default 42-returning
            // stub, so isRefreshingVisits is false again by construction time.
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))
            clearMocks(getVisitCount, answers = false, recordedCalls = true, childMocks = false, verificationMarks = true)
            coEvery { getVisitCount(url) } coAnswers { awaitCancellation() }

            viewModel.refreshVisitCount()
            viewModel.refreshVisitCount()

            coVerify(exactly = 1) { getVisitCount(url) }
        }

        should("delete removes the loaded url and emits Deleted") {
            val url = testUrl()
            coEvery { getURL(url.shortURL) } returns url
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))

            viewModel.events.test {
                viewModel.delete()
                awaitItem() shouldBe UrlDetailEvent.Deleted
            }
            coVerify(exactly = 1) { deleteURL(url) }
        }

        should("delete is a no-op when no url is loaded") {
            // No events.test wrapper here: init already sent a buffered NotFound event (this is the
            // not-found case per the brief), which a fresh collector would immediately receive and
            // make expectNoEvents() fail. Absence of a further event is implied by no deleteURL call.
            val viewModel = newViewModel()

            viewModel.delete()

            coVerify(exactly = 0) { deleteURL(any<URL>()) }
        }
    },
)
