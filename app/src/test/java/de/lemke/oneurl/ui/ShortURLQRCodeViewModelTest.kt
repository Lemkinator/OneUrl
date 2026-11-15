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
import androidx.lifecycle.SavedStateHandle
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.ui.utils.BitmapExport
import de.lemke.commonutils.ui.utils.BitmapSaveResult
import de.lemke.commonutils.ui.utils.BitmapShareFile
import de.lemke.commonutils.ui.utils.DocumentPick
import de.lemke.commonutils.ui.utils.FakeBitmapExporter
import de.lemke.commonutils.ui.utils.FakeBitmapExporter.Call
import de.lemke.commonutils.ui.utils.ShareTarget
import de.lemke.oneurl.data.FakeQRCodeRenderer
import de.lemke.oneurl.data.FakeQRCodeRenderer.Render
import de.lemke.oneurl.data.QRCodeCache
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.domain.GenerateQRCodeUseCase
import de.lemke.oneurl.domain.GetQRCodeUseCase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class ShortURLQRCodeViewModelTest : ShouldSpec(
    {
        val qrCode = mockk<Bitmap> { every { byteCount } returns 1024 }
        val shortURL = "https://short.url/x"
        lateinit var settingsScope: CoroutineScope
        lateinit var userSettings: UserSettings
        lateinit var renderer: FakeQRCodeRenderer
        lateinit var exporter: FakeBitmapExporter

        fun newViewModel(
            savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf(ShortURLQRCodeViewModel.KEY_SHORT_URL to shortURL)),
            defaultDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
        ) = ShortURLQRCodeViewModel(
            savedStateHandle,
            GetQRCodeUseCase(QRCodeCache(), GenerateQRCodeUseCase(renderer), defaultDispatcher),
            userSettings,
            exporter,
        )

        fun loadedViewModel(
            savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf(ShortURLQRCodeViewModel.KEY_SHORT_URL to shortURL)),
            defaultDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
        ) = newViewModel(savedStateHandle, defaultDispatcher).also { it.load() }

        beforeEach {
            settingsScope = CoroutineScope(Job() + UnconfinedTestDispatcher())
            userSettings = UserSettings(FakeSharedPreferences(), settingsScope)
            renderer = FakeQRCodeRenderer(qrCode, placeholder = mockk())
            exporter = FakeBitmapExporter()
        }

        afterEach {
            settingsScope.cancel()
        }

        should("init takes the short URL from the saved state and renders no QR code before load") {
            val viewModel = newViewModel()

            viewModel.state.value shouldBe ShortURLQRCodeUiState(shortURL = shortURL, qrCode = null)
            renderer.renders shouldBe emptyList()
        }

        should("load renders the QR code of the short URL from the saved state") {
            val viewModel = newViewModel()

            viewModel.load()

            viewModel.state.value shouldBe ShortURLQRCodeUiState(shortURL = shortURL, qrCode = qrCode)
            renderer.renders shouldBe listOf(Render(shortURL, null))
        }

        should("load called again renders the QR code once") {
            val viewModel = newViewModel()

            viewModel.load()
            viewModel.load()

            renderer.renders shouldBe listOf(Render(shortURL, null))
        }

        should("load called while the first load runs renders the QR code once") {
            val qrCodeDispatcher = StandardTestDispatcher()
            val viewModel = newViewModel(defaultDispatcher = qrCodeDispatcher)

            viewModel.load()
            viewModel.load()
            qrCodeDispatcher.scheduler.advanceUntilIdle()

            viewModel.state.value.qrCode shouldBe qrCode
            renderer.renders shouldBe listOf(Render(shortURL, null))
        }

        should("load renders no QR code when the saved-state key is missing") {
            val viewModel = loadedViewModel(SavedStateHandle())

            viewModel.state.value shouldBe ShortURLQRCodeUiState(shortURL = "", qrCode = null)
            renderer.renders shouldBe emptyList()
        }

        should("load renders no QR code for an empty short URL") {
            val viewModel = loadedViewModel(SavedStateHandle(mapOf(ShortURLQRCodeViewModel.KEY_SHORT_URL to "")))

            viewModel.state.value shouldBe ShortURLQRCodeUiState(shortURL = "", qrCode = null)
            renderer.renders shouldBe emptyList()
        }

        should("onSave, onCopy, onShare and onQuickShare export nothing before load") {
            val viewModel = newViewModel()

            viewModel.onSave()
            viewModel.onCopy()
            viewModel.onShare()
            viewModel.onQuickShare()

            exporter.calls shouldBe emptyList()
            viewModel.export.value shouldBe BitmapExport.Idle
        }

        should("onSave, onCopy, onShare and onQuickShare export nothing without a QR code") {
            val viewModel = loadedViewModel(SavedStateHandle())

            viewModel.onSave()
            viewModel.onCopy()
            viewModel.onShare()
            viewModel.onQuickShare()

            exporter.calls shouldBe emptyList()
            viewModel.export.value shouldBe BitmapExport.Idle
        }

        should("onSave writes the QR code under the short URL to the image save location and holds OpenPicker") {
            val viewModel = loadedViewModel()

            viewModel.onSave()

            exporter.calls shouldBe listOf(Call.SaveToDirectory(SaveLocation.CUSTOM, qrCode, shortURL))
            viewModel.export.value shouldBe BitmapExport.OpenPicker(shortURL)
        }

        should("onCopy holds the clip of the QR code") {
            val clip = mockk<ClipData>()
            exporter.clip = clip
            val viewModel = loadedViewModel()

            viewModel.onCopy()

            exporter.calls shouldBe listOf(Call.CreateClip(qrCode))
            viewModel.export.value shouldBe BitmapExport.Copy(clip)
        }

        should("onShare holds the share file of the QR code for the share sheet") {
            val file = BitmapShareFile.Written(mockk<Uri>())
            exporter.shareFile = file
            val viewModel = loadedViewModel()

            viewModel.onShare()

            exporter.calls shouldBe listOf(Call.CreateShareFile(qrCode))
            viewModel.export.value shouldBe BitmapExport.Share(file, ShareTarget.SHARE_SHEET)
        }

        should("onQuickShare holds the share file of the QR code for Quick Share") {
            val file = BitmapShareFile.Written(mockk<Uri>())
            exporter.shareFile = file
            val viewModel = loadedViewModel()

            viewModel.onQuickShare()

            exporter.calls shouldBe listOf(Call.CreateShareFile(qrCode))
            viewModel.export.value shouldBe BitmapExport.Share(file, ShareTarget.QUICK_SHARE)
        }

        should("onDocumentPicked writes the QR code into the created document") {
            val uri = mockk<Uri>()
            val viewModel = loadedViewModel()

            viewModel.onDocumentPicked(DocumentPick.Created(uri))

            exporter.calls shouldBe listOf(Call.SaveToCreatedDocument(uri, qrCode))
            viewModel.export.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.CUSTOM))
        }

        should("onDocumentPicked during the QR code load deletes the created document and holds WriteFailed") {
            val qrCodeDispatcher = StandardTestDispatcher()
            val uri = mockk<Uri>()
            val viewModel = loadedViewModel(defaultDispatcher = qrCodeDispatcher)

            viewModel.onDocumentPicked(DocumentPick.Created(uri))
            qrCodeDispatcher.scheduler.advanceUntilIdle()

            exporter.calls shouldBe listOf(Call.SaveToCreatedDocument(uri, null))
            exporter.deletedDocuments shouldBe listOf(uri)
            viewModel.export.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed)
        }

        should("onDocumentPicked without a short URL deletes the created document and holds WriteFailed") {
            val uri = mockk<Uri>()
            val viewModel = loadedViewModel(SavedStateHandle())

            viewModel.onDocumentPicked(DocumentPick.Created(uri))

            exporter.calls shouldBe listOf(Call.SaveToCreatedDocument(uri, null))
            exporter.deletedDocuments shouldBe listOf(uri)
            viewModel.export.value shouldBe BitmapExport.SaveFinished(BitmapSaveResult.WriteFailed)
        }

        should("onExportHandled returns the export to Idle") {
            val viewModel = loadedViewModel()
            viewModel.onShare()

            viewModel.onExportHandled(BitmapExport.ShareFailed)

            viewModel.export.value shouldBe BitmapExport.Idle
        }
    },
)
