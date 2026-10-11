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
import de.lemke.commonutils.ui.utils.DocumentPick
import de.lemke.oneurl.data.FakeQRCodeRenderer
import de.lemke.oneurl.data.FakeQRCodeRenderer.Render
import de.lemke.oneurl.data.FakeShortURLClient
import de.lemke.oneurl.data.FakeURLRepository
import de.lemke.oneurl.data.QRCodeCache
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.domain.DeleteURLUseCase
import de.lemke.oneurl.domain.GenerateQRCodeUseCase
import de.lemke.oneurl.domain.GetQRCodeUseCase
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.GetVisitCountUseCase
import de.lemke.oneurl.domain.UpdateURLUseCase
import de.lemke.oneurl.domain.testUrl
import de.lemke.oneurl.ui.FakeQRCodeExporter.Call
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class URLViewModelTest : ShouldSpec(
    {
        val url = testUrl(shortURL = "https://short.url/x")
        val qrCode = mockk<Bitmap> { every { byteCount } returns 1024 }
        lateinit var repository: FakeURLRepository
        lateinit var shortURLClient: FakeShortURLClient
        lateinit var renderer: FakeQRCodeRenderer
        lateinit var userSettings: UserSettings
        lateinit var exporter: FakeQRCodeExporter

        fun newViewModel(
            shortURL: String? = url.shortURL,
            qrCodeDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        ) = URLViewModel(
            if (shortURL == null) SavedStateHandle() else SavedStateHandle(mapOf(URLActivity.KEY_SHORTURL to shortURL)),
            GetURLUseCase(repository, Dispatchers.Unconfined),
            UpdateURLUseCase(repository, Dispatchers.Unconfined),
            DeleteURLUseCase(repository, Dispatchers.Unconfined),
            GetVisitCountUseCase(shortURLClient),
            GetQRCodeUseCase(QRCodeCache(), GenerateQRCodeUseCase(renderer), qrCodeDispatcher),
            userSettings,
            exporter,
        )

        beforeEach {
            repository = FakeURLRepository(listOf(url))
            shortURLClient = FakeShortURLClient().apply { visitCounts[url.shortURL] = 42 }
            renderer = FakeQRCodeRenderer(qrCode, placeholder = mockk())
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            exporter = FakeQRCodeExporter()
        }

        should("init loads the URL, stops loading, and refreshes the visit count") {
            val viewModel = newViewModel()

            viewModel.state.value.url shouldBe url
            viewModel.state.value.isLoading shouldBe false
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

        should("init loads the QR code for the short URL once the URL is loaded") {
            val qrCodeDispatcher = StandardTestDispatcher()
            val viewModel = newViewModel(qrCodeDispatcher = qrCodeDispatcher)

            viewModel.state.value.url shouldBe url
            viewModel.state.value.qrCode shouldBe null
            qrCodeDispatcher.scheduler.advanceUntilIdle()

            viewModel.state.value.qrCode shouldBe qrCode
            renderer.renders shouldBe listOf(Render(url.shortURL, null))
        }

        should("init leaves the QR code null and never renders it when the URL does not exist") {
            val viewModel = newViewModel(shortURL = "https://short.url/missing")

            viewModel.state.value.qrCode shouldBe null
            renderer.renders shouldBe emptyList()
        }

        should("onSaveQRCode, onCopyQRCode and onShareQRCode export nothing before the QR code exists") {
            val viewModel = newViewModel(qrCodeDispatcher = StandardTestDispatcher())

            viewModel.onSaveQRCode()
            viewModel.onCopyQRCode()
            viewModel.onShareQRCode()

            exporter.calls shouldBe emptyList()
            viewModel.export.value shouldBe QRCodeExport.Idle
        }

        should("onSaveQRCode exports nothing when the URL does not exist") {
            val viewModel = newViewModel(shortURL = "https://short.url/missing")

            viewModel.onSaveQRCode()

            exporter.calls shouldBe emptyList()
            viewModel.export.value shouldBe QRCodeExport.Idle
        }

        should("onSaveQRCode writes the QR code under the short URL to the image save location") {
            val viewModel = newViewModel()

            viewModel.onSaveQRCode()

            exporter.calls shouldBe listOf(Call.SaveToDirectory(SaveLocation.CUSTOM, qrCode, "https://short.url/x"))
            viewModel.export.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.DOWNLOADS))
        }

        should("onCopyQRCode holds the clip of the QR code") {
            val clip = mockk<ClipData>()
            exporter.clip = clip
            val viewModel = newViewModel()

            viewModel.onCopyQRCode()

            exporter.calls shouldBe listOf(Call.CreateClip(qrCode))
            viewModel.export.value shouldBe QRCodeExport.Copy(clip)
        }

        should("onShareQRCode holds the share file of the QR code for the share sheet") {
            val file = BitmapShareFile.Written(mockk<Uri>())
            exporter.shareFile = file
            val viewModel = newViewModel()

            viewModel.onShareQRCode()

            exporter.calls shouldBe listOf(Call.CreateShareFile(qrCode))
            viewModel.export.value shouldBe QRCodeExport.Share(file, ShareTarget.SHARE_SHEET)
        }

        should("onDocumentPicked writes the QR code into the created document") {
            val uri = mockk<Uri>()
            val viewModel = newViewModel()

            viewModel.onDocumentPicked(DocumentPick.Created(uri))

            exporter.calls shouldBe listOf(Call.SaveToCreatedDocument(uri, qrCode))
            viewModel.export.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.CUSTOM))
        }

        should("onDocumentPicked during the QR code load deletes the created document and holds WriteFailed") {
            val qrCodeDispatcher = StandardTestDispatcher()
            val uri = mockk<Uri>()
            val viewModel = newViewModel(qrCodeDispatcher = qrCodeDispatcher)

            viewModel.onDocumentPicked(DocumentPick.Created(uri))
            qrCodeDispatcher.scheduler.advanceUntilIdle()

            exporter.calls shouldBe listOf(Call.SaveToCreatedDocument(uri, null))
            exporter.deletedDocuments shouldBe listOf(uri)
            viewModel.export.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.WriteFailed)
        }

        should("onExportHandled returns the export to Idle") {
            exporter.shareFile = BitmapShareFile.Failed
            val viewModel = newViewModel()
            viewModel.onShareQRCode()

            viewModel.onExportHandled(QRCodeExport.ShareFailed)

            viewModel.export.value shouldBe QRCodeExport.Idle
        }
    },
)
