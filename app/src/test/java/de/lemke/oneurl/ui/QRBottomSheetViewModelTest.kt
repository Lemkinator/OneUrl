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
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.ui.utils.BitmapSaveResult
import de.lemke.commonutils.ui.utils.BitmapShareFile
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.domain.GetQRCodeUseCase
import de.lemke.oneurl.ui.FakeQRCodeExporter.Call
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class QRBottomSheetViewModelTest : ShouldSpec(
    {
        val getQRCode = mockk<GetQRCodeUseCase>()
        val qrCode = mockk<Bitmap>()
        val shortURL = "https://short.url/x"
        lateinit var userSettings: UserSettings
        lateinit var exporter: FakeQRCodeExporter

        fun newViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf(QRBottomSheet.KEY_SHORT_URL to shortURL))) =
            QRBottomSheetViewModel(savedStateHandle, getQRCode, userSettings, exporter)

        beforeEach {
            clearMocks(getQRCode)
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            exporter = FakeQRCodeExporter()
            coEvery { getQRCode(any()) } returns qrCode
        }

        should("init takes the short URL from the saved state and loads its QR code") {
            val viewModel = newViewModel()

            viewModel.state.value shouldBe QRBottomSheetUiState(shortURL = shortURL, qrCode = qrCode)
            coVerify(exactly = 1) { getQRCode(shortURL) }
        }

        should("init loads no QR code when the saved-state key is missing") {
            val viewModel = newViewModel(SavedStateHandle())

            viewModel.state.value shouldBe QRBottomSheetUiState(shortURL = "", qrCode = null)
            coVerify(exactly = 0) { getQRCode(any()) }
        }

        should("init loads no QR code for an empty short URL") {
            val viewModel = newViewModel(SavedStateHandle(mapOf(QRBottomSheet.KEY_SHORT_URL to "")))

            viewModel.state.value shouldBe QRBottomSheetUiState(shortURL = "", qrCode = null)
            coVerify(exactly = 0) { getQRCode(any()) }
        }

        should("onSave, onShare and onQuickShare export nothing without a QR code") {
            val viewModel = newViewModel(SavedStateHandle())

            viewModel.onSave()
            viewModel.onShare()
            viewModel.onQuickShare()

            exporter.calls shouldBe emptyList()
            viewModel.export.value shouldBe QRCodeExport.Idle
        }

        should("onSave writes the QR code under the short URL to the image save location") {
            val viewModel = newViewModel()

            viewModel.onSave()

            exporter.calls shouldBe listOf(Call.SaveToDirectory(SaveLocation.CUSTOM, qrCode, shortURL))
            viewModel.export.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.DOWNLOADS))
        }

        should("onSave holds OpenPicker with the short URL as file name when the location needs the picker") {
            exporter.directoryResult = BitmapSaveResult.NeedsPicker
            val viewModel = newViewModel()

            viewModel.onSave()

            viewModel.export.value shouldBe QRCodeExport.OpenPicker(shortURL)
        }

        should("onShare holds the share file of the QR code for the share sheet") {
            val file = BitmapShareFile.Written(mockk<Uri>())
            exporter.shareFile = file
            val viewModel = newViewModel()

            viewModel.onShare()

            exporter.calls shouldBe listOf(Call.CreateShareFile(qrCode))
            viewModel.export.value shouldBe QRCodeExport.Share(file, ShareTarget.SHARE_SHEET)
        }

        should("onQuickShare holds the share file of the QR code for Quick Share") {
            val file = BitmapShareFile.Written(mockk<Uri>())
            exporter.shareFile = file
            val viewModel = newViewModel()

            viewModel.onQuickShare()

            exporter.calls shouldBe listOf(Call.CreateShareFile(qrCode))
            viewModel.export.value shouldBe QRCodeExport.Share(file, ShareTarget.QUICK_SHARE)
        }

        should("onDocumentPicked writes the QR code into the created document") {
            val uri = mockk<Uri>()
            val viewModel = newViewModel()

            viewModel.onDocumentPicked(DocumentPick.Created(uri))

            exporter.calls shouldBe listOf(Call.SaveToCreatedDocument(uri, qrCode))
            viewModel.export.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.CUSTOM))
        }

        should("onDocumentPicked during the QR code load waits for the QR code and then writes it") {
            val gate = CompletableDeferred<Unit>()
            coEvery { getQRCode(shortURL) } coAnswers {
                gate.await()
                qrCode
            }
            val uri = mockk<Uri>()
            val viewModel = newViewModel()

            viewModel.onDocumentPicked(DocumentPick.Created(uri))

            exporter.calls shouldBe emptyList()
            gate.complete(Unit)
            exporter.calls shouldBe listOf(Call.SaveToCreatedDocument(uri, qrCode))
            viewModel.export.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.CUSTOM))
        }

        should("onDocumentPicked without a short URL hands a missing QR code to the write") {
            exporter.documentResult = BitmapSaveResult.WriteFailed
            val uri = mockk<Uri>()
            val viewModel = newViewModel(SavedStateHandle())

            viewModel.onDocumentPicked(DocumentPick.Created(uri))

            exporter.calls shouldBe listOf(Call.SaveToCreatedDocument(uri, null))
            viewModel.export.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.WriteFailed)
        }

        should("onExportHandled returns the export to Idle") {
            val viewModel = newViewModel()
            viewModel.onShare()

            viewModel.onExportHandled(QRCodeExport.ShareFailed)

            viewModel.export.value shouldBe QRCodeExport.Idle
        }
    },
)
