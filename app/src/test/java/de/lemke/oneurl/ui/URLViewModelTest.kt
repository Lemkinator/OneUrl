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

import android.content.ClipData
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.ui.utils.BitmapSaveResult
import de.lemke.commonutils.ui.utils.BitmapShareFile
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.domain.DeleteURLUseCase
import de.lemke.oneurl.domain.GetQRCodeUseCase
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.GetVisitCountUseCase
import de.lemke.oneurl.domain.UpdateURLUseCase
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.ui.FakeQRCodeExporter.Call
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher

private fun testUrl(
    shortURL: String = "https://short.url/x",
    longURL: String = "https://example.com",
    provider: ShortURLProvider = ShortURLProviderCompanion.default,
    favorite: Boolean = false,
) = URL(
    shortURL = shortURL,
    longURL = longURL,
    shortURLProvider = provider,
    favorite = favorite,
    title = "title",
    description = "description",
    added = ZonedDateTime.now(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class URLViewModelTest : ShouldSpec(
    {
        val getURL = mockk<GetURLUseCase>()
        val updateURL = mockk<UpdateURLUseCase>()
        val deleteURL = mockk<DeleteURLUseCase>()
        val getVisitCount = mockk<GetVisitCountUseCase>()
        val getQRCode = mockk<GetQRCodeUseCase>()
        val qrCode = mockk<Bitmap>()
        lateinit var userSettings: UserSettings
        lateinit var exporter: FakeQRCodeExporter

        fun newViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
            URLViewModel(savedStateHandle, getURL, updateURL, deleteURL, getVisitCount, getQRCode, userSettings, exporter)

        beforeEach {
            clearMocks(getURL, updateURL, deleteURL, getVisitCount, getQRCode)
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            exporter = FakeQRCodeExporter()
            coEvery { getURL(any<String>()) } returns null
            coEvery { getVisitCount(any()) } returns 42
            coEvery { updateURL(any<URL>()) } returns Unit
            coEvery { deleteURL(any<URL>()) } returns Unit
            coEvery { getQRCode(any()) } returns qrCode
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

        should("init holds the NotFound exit and leaves url null when the URL does not exist") {
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to "https://short.url/missing")))

            viewModel.exit.value shouldBe UrlDetailExit.NotFound
            viewModel.state.value.url shouldBe null
        }

        should("init holds no exit when the URL exists") {
            val url = testUrl()
            coEvery { getURL(url.shortURL) } returns url
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))

            viewModel.exit.value shouldBe UrlDetailExit.None
        }

        should("onExitHandled returns the exit to None") {
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to "https://short.url/missing")))

            viewModel.onExitHandled(UrlDetailExit.NotFound)

            viewModel.exit.value shouldBe UrlDetailExit.None
        }

        should("onExitHandled keeps a different exit") {
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to "https://short.url/missing")))

            viewModel.onExitHandled(UrlDetailExit.Deleted)

            viewModel.exit.value shouldBe UrlDetailExit.NotFound
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

        should("refreshVisitCount is a no-op when no url is loaded") {
            val viewModel = newViewModel()

            viewModel.refreshVisitCount()

            coVerify(exactly = 0) { getVisitCount(any()) }
        }

        should("refreshVisitCount called again after init re-triggers getVisitCount") {
            val url = testUrl()
            coEvery { getURL(url.shortURL) } returns url
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))

            viewModel.refreshVisitCount()

            coVerify(atLeast = 2) { getVisitCount(url) }
        }

        should("refreshVisitCount catches a plain exception without crashing or exiting") {
            // The catch branch logs via android.util.Log, which throws "not mocked" on the plain JVM
            // unless stubbed.
            mockkStatic(Log::class)
            every { Log.e(any(), any(), any()) } returns 0
            try {
                val url = testUrl()
                coEvery { getURL(url.shortURL) } returns url
                coEvery { getVisitCount(url) } throws RuntimeException("boom")
                val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))

                viewModel.exit.value shouldBe UrlDetailExit.None
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

        should("delete removes the loaded url and holds the Deleted exit") {
            val url = testUrl()
            coEvery { getURL(url.shortURL) } returns url
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))

            viewModel.delete()

            viewModel.exit.value shouldBe UrlDetailExit.Deleted
            coVerify(exactly = 1) { deleteURL(url) }
        }

        should("delete is a no-op when no url is loaded") {
            val viewModel = newViewModel()

            viewModel.delete()

            coVerify(exactly = 0) { deleteURL(any<URL>()) }
            viewModel.exit.value shouldBe UrlDetailExit.NotFound
        }

        should("init loads the QR code for the short URL once the URL is loaded") {
            val url = testUrl()
            coEvery { getURL(url.shortURL) } returns url
            val gate = CompletableDeferred<Unit>()
            coEvery { getQRCode(url.shortURL) } coAnswers {
                gate.await()
                qrCode
            }
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))

            viewModel.state.value.url shouldBe url
            viewModel.state.value.qrCode shouldBe null
            gate.complete(Unit)

            viewModel.state.value.qrCode shouldBe qrCode
            coVerifyOrder {
                getURL(url.shortURL)
                getQRCode(url.shortURL)
            }
        }

        should("init leaves the QR code null and never loads it when the URL does not exist") {
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to "https://short.url/missing")))

            viewModel.state.value.qrCode shouldBe null
            coVerify(exactly = 0) { getQRCode(any()) }
        }

        should("onSaveQRCode, onCopyQRCode and onShareQRCode export nothing before the QR code exists") {
            val url = testUrl()
            coEvery { getURL(url.shortURL) } returns url
            coEvery { getQRCode(url.shortURL) } coAnswers { awaitCancellation() }
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))

            viewModel.onSaveQRCode()
            viewModel.onCopyQRCode()
            viewModel.onShareQRCode()

            exporter.calls shouldBe emptyList()
            viewModel.export.value shouldBe QRCodeExport.Idle
        }

        should("onSaveQRCode writes the QR code under the short URL to the image save location") {
            val url = testUrl()
            coEvery { getURL(url.shortURL) } returns url
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))

            viewModel.onSaveQRCode()

            exporter.calls shouldBe listOf(Call.SaveToDirectory(SaveLocation.CUSTOM, qrCode, "https://short.url/x"))
            viewModel.export.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.DOWNLOADS))
        }

        should("onCopyQRCode holds the clip of the QR code") {
            val url = testUrl()
            coEvery { getURL(url.shortURL) } returns url
            val clip = mockk<ClipData>()
            exporter.clip = clip
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))

            viewModel.onCopyQRCode()

            exporter.calls shouldBe listOf(Call.CreateClip(qrCode))
            viewModel.export.value shouldBe QRCodeExport.Copy(clip)
        }

        should("onShareQRCode holds the share file of the QR code for the share sheet") {
            val url = testUrl()
            coEvery { getURL(url.shortURL) } returns url
            val file = BitmapShareFile.Written(mockk<Uri>())
            exporter.shareFile = file
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))

            viewModel.onShareQRCode()

            exporter.calls shouldBe listOf(Call.CreateShareFile(qrCode))
            viewModel.export.value shouldBe QRCodeExport.Share(file, ShareTarget.SHARE_SHEET)
        }

        should("onDocumentPicked writes the QR code into the created document") {
            val url = testUrl()
            coEvery { getURL(url.shortURL) } returns url
            val uri = mockk<Uri>()
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))

            viewModel.onDocumentPicked(DocumentPick.Created(uri))

            exporter.calls shouldBe listOf(Call.SaveToCreatedDocument(uri, qrCode))
            viewModel.export.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.CUSTOM))
        }

        should("onDocumentPicked during the QR code load deletes the created document and holds WriteFailed") {
            val url = testUrl()
            coEvery { getURL(url.shortURL) } returns url
            val gate = CompletableDeferred<Unit>()
            coEvery { getQRCode(url.shortURL) } coAnswers {
                gate.await()
                qrCode
            }
            val uri = mockk<Uri>()
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))

            viewModel.onDocumentPicked(DocumentPick.Created(uri))
            gate.complete(Unit)

            exporter.calls shouldBe listOf(Call.SaveToCreatedDocument(uri, null))
            exporter.deletedDocuments shouldBe listOf(uri)
            viewModel.export.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.WriteFailed)
        }

        should("onExportHandled returns the export to Idle") {
            val url = testUrl()
            coEvery { getURL(url.shortURL) } returns url
            exporter.shareFile = BitmapShareFile.Failed
            val viewModel = newViewModel(SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to url.shortURL)))
            viewModel.onShareQRCode()

            viewModel.onExportHandled(QRCodeExport.ShareFailed)

            viewModel.export.value shouldBe QRCodeExport.Idle
        }
    },
)
