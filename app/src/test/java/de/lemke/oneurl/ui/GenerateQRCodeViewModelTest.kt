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
import android.graphics.Color
import android.net.Uri
import de.lemke.commonutils.data.FakeSharedPreferences
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.ui.utils.BitmapSaveResult
import de.lemke.commonutils.ui.utils.BitmapShareFile
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.domain.GenerateQRCodeUseCase
import de.lemke.oneurl.ui.FakeQRCodeExporter.Call
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class GenerateQRCodeViewModelTest : ShouldSpec(
    {
        val generateQRCode = mockk<GenerateQRCodeUseCase>()
        val qrCode = mockk<Bitmap>()
        lateinit var userSettings: UserSettings
        lateinit var mainScheduler: TestCoroutineScheduler
        lateinit var exporter: FakeQRCodeExporter

        fun newViewModel() = GenerateQRCodeViewModel(userSettings, generateQRCode, exporter)

        beforeEach {
            mainScheduler = UnconfinedTestDispatcher().scheduler
            clearMocks(generateQRCode)
            userSettings = UserSettings(FakeSharedPreferences(), CoroutineScope(UnconfinedTestDispatcher()))
            exporter = FakeQRCodeExporter()
            every {
                generateQRCode(any(), any(), any(), any(), any(), any(), any(), any())
            } returns qrCode
        }

        should("init state is seeded from userSettings and a QR code is generated") {
            userSettings.qrURL = "https://example.com"
            userSettings.qrSize = 256
            userSettings.qrRecentForegroundColors = listOf(0x111111, 0x222222)
            userSettings.qrRecentBackgroundColors = listOf(0x333333, 0x444444)
            userSettings.qrTintAnchor = true
            userSettings.qrTintBorder = true
            userSettings.qrIcon = false
            userSettings.qrFrame = false

            val viewModel = newViewModel()
            val state = viewModel.state.value

            state.url shouldBe "https://example.com"
            state.size shouldBe 256
            state.foregroundColor shouldBe 0x111111
            state.backgroundColor shouldBe 0x333333
            state.tintAnchor.shouldBeTrue()
            state.tintBorder.shouldBeTrue()
            state.icon.shouldBeFalse()
            state.roundedFrame.shouldBeFalse()
            state.recentForegroundColors shouldBe listOf(0x111111, 0x222222)
            state.recentBackgroundColors shouldBe listOf(0x333333, 0x444444)
            state.isLoading.shouldBeFalse()
            state.qrCode shouldBe qrCode
            verify(exactly = 1) {
                generateQRCode("https://example.com", 256, 0x111111, 0x333333, true, true, false, false)
            }
        }

        should("init falls back to default colors when no recent colors are persisted") {
            userSettings.qrRecentForegroundColors = emptyList()
            userSettings.qrRecentBackgroundColors = emptyList()

            val viewModel = newViewModel()
            val state = viewModel.state.value

            state.foregroundColor shouldBe Color.BLACK
            state.backgroundColor shouldBe Color.WHITE
        }

        should("setUrl updates state.url immediately and regenerates the QR code") {
            val viewModel = newViewModel()

            viewModel.setUrl("https://new.example.com")

            viewModel.state.value.url shouldBe "https://new.example.com"
            verify(exactly = 1) { generateQRCode("https://new.example.com", any(), any(), any(), any(), any(), any(), any()) }
        }

        should("setSize updates state.size immediately and regenerates the QR code") {
            val viewModel = newViewModel()

            viewModel.setSize(1024)

            viewModel.state.value.size shouldBe 1024
            verify(exactly = 1) { generateQRCode(any(), 1024, any(), any(), any(), any(), any(), any()) }
        }

        should("setRoundedFrame flips state, persists immediately, and regenerates") {
            val viewModel = newViewModel()

            viewModel.setRoundedFrame(false)

            viewModel.state.value.roundedFrame
                .shouldBeFalse()
            userSettings.qrFrame.shouldBeFalse()
            verify(exactly = 1) { generateQRCode(any(), any(), any(), any(), any(), any(), any(), false) }
        }

        should("setIcon flips state, persists immediately, and regenerates") {
            val viewModel = newViewModel()

            viewModel.setIcon(false)

            viewModel.state.value.icon
                .shouldBeFalse()
            userSettings.qrIcon.shouldBeFalse()
            verify(exactly = 1) { generateQRCode(any(), any(), any(), any(), any(), any(), false, any()) }
        }

        should("setTintBorder flips state, persists immediately, and regenerates") {
            val viewModel = newViewModel()

            viewModel.setTintBorder(true)

            viewModel.state.value.tintBorder
                .shouldBeTrue()
            userSettings.qrTintBorder.shouldBeTrue()
            verify(exactly = 1) { generateQRCode(any(), any(), any(), any(), any(), true, any(), any()) }
        }

        should("setTintAnchor flips state, persists immediately, and regenerates") {
            val viewModel = newViewModel()

            viewModel.setTintAnchor(true)

            viewModel.state.value.tintAnchor
                .shouldBeTrue()
            userSettings.qrTintAnchor.shouldBeTrue()
            verify(exactly = 1) { generateQRCode(any(), any(), any(), any(), true, any(), any(), any()) }
        }

        should("setForegroundColor prepends the color, dedupes, and persists the recent list") {
            userSettings.qrRecentForegroundColors = listOf(0x1, 0x2, 0x3)
            val viewModel = newViewModel()

            viewModel.setForegroundColor(0x2)

            viewModel.state.value.foregroundColor shouldBe 0x2
            viewModel.state.value.recentForegroundColors shouldBe listOf(0x2, 0x1, 0x3)
            userSettings.qrRecentForegroundColors shouldBe listOf(0x2, 0x1, 0x3)
        }

        should("setForegroundColor caps the recent list at 6 entries, dropping the oldest") {
            userSettings.qrRecentForegroundColors = listOf(0x1, 0x2, 0x3, 0x4, 0x5, 0x6)
            val viewModel = newViewModel()

            viewModel.setForegroundColor(0x7)

            viewModel.state.value.recentForegroundColors shouldBe listOf(0x7, 0x1, 0x2, 0x3, 0x4, 0x5)
            userSettings.qrRecentForegroundColors shouldBe listOf(0x7, 0x1, 0x2, 0x3, 0x4, 0x5)
        }

        should("setBackgroundColor prepends the color, dedupes, and persists the recent list") {
            userSettings.qrRecentBackgroundColors = listOf(0x1, 0x2, 0x3)
            val viewModel = newViewModel()

            viewModel.setBackgroundColor(0x2)

            viewModel.state.value.backgroundColor shouldBe 0x2
            viewModel.state.value.recentBackgroundColors shouldBe listOf(0x2, 0x1, 0x3)
            userSettings.qrRecentBackgroundColors shouldBe listOf(0x2, 0x1, 0x3)
        }

        should("setBackgroundColor caps the recent list at 6 entries, dropping the oldest") {
            userSettings.qrRecentBackgroundColors = listOf(0x1, 0x2, 0x3, 0x4, 0x5, 0x6)
            val viewModel = newViewModel()

            viewModel.setBackgroundColor(0x7)

            viewModel.state.value.recentBackgroundColors shouldBe listOf(0x7, 0x1, 0x2, 0x3, 0x4, 0x5)
            userSettings.qrRecentBackgroundColors shouldBe listOf(0x7, 0x1, 0x2, 0x3, 0x4, 0x5)
        }

        should("setUrl persists the debounced value to userSettings.qrURL once the delay elapses") {
            val viewModel = newViewModel()

            viewModel.setUrl("https://debounced.example.com")
            mainScheduler.advanceUntilIdle()

            userSettings.qrURL shouldBe "https://debounced.example.com"
        }

        should("setUrl cancels the previous debounce job so only the latest value is ever persisted") {
            userSettings.qrURL = "https://initial.example.com"
            val viewModel = newViewModel()

            viewModel.setUrl("https://first.example.com")
            mainScheduler.advanceTimeBy(100.milliseconds)
            viewModel.setUrl("https://second.example.com")
            mainScheduler.advanceTimeBy(250.milliseconds)
            userSettings.qrURL shouldBe "https://initial.example.com"

            mainScheduler.advanceUntilIdle()
            userSettings.qrURL shouldBe "https://second.example.com"
        }

        should("setSize persists the debounced value to userSettings.qrSize once the delay elapses") {
            val viewModel = newViewModel()

            viewModel.setSize(900)
            mainScheduler.advanceUntilIdle()

            userSettings.qrSize shouldBe 900
        }

        should("setSize cancels the previous debounce job so only the latest value is ever persisted") {
            userSettings.qrSize = 512
            val viewModel = newViewModel()

            viewModel.setSize(700)
            mainScheduler.advanceTimeBy(100.milliseconds)
            viewModel.setSize(900)
            mainScheduler.advanceTimeBy(250.milliseconds)
            userSettings.qrSize shouldBe 512

            mainScheduler.advanceUntilIdle()
            userSettings.qrSize shouldBe 900
        }

        should("onSave writes the current QR code under its URL to the image save location") {
            userSettings.qrURL = "https://example.com"
            val viewModel = newViewModel()

            viewModel.onSave()

            exporter.calls shouldBe listOf(Call.SaveToDirectory(SaveLocation.CUSTOM, qrCode, "https://example.com"))
            viewModel.export.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.DOWNLOADS))
        }

        should("onSave holds OpenPicker with the URL as file name when the location needs the picker") {
            userSettings.qrURL = "https://example.com"
            exporter.directoryResult = BitmapSaveResult.NeedsPicker
            val viewModel = newViewModel()

            viewModel.onSave()

            viewModel.export.value shouldBe QRCodeExport.OpenPicker("https://example.com")
        }

        should("onCopy holds the clip of the current QR code") {
            val clip = mockk<ClipData>()
            exporter.clip = clip
            val viewModel = newViewModel()

            viewModel.onCopy()

            exporter.calls shouldBe listOf(Call.CreateClip(qrCode))
            viewModel.export.value shouldBe QRCodeExport.Copy(clip)
        }

        should("onShare holds the share file of the current QR code for the share sheet") {
            val file = BitmapShareFile.Written(mockk<Uri>())
            exporter.shareFile = file
            val viewModel = newViewModel()

            viewModel.onShare()

            exporter.calls shouldBe listOf(Call.CreateShareFile(qrCode))
            viewModel.export.value shouldBe QRCodeExport.Share(file, ShareTarget.SHARE_SHEET)
        }

        should("onDocumentPicked writes the current QR code into the created document") {
            val uri = mockk<Uri>()
            val viewModel = newViewModel()

            viewModel.onDocumentPicked(DocumentPick.Created(uri))

            exporter.calls shouldBe listOf(Call.SaveToCreatedDocument(uri, qrCode))
            viewModel.export.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.CUSTOM))
        }

        should("onExportHandled returns the export to Idle") {
            val viewModel = newViewModel()
            viewModel.onSave()

            viewModel.onExportHandled(QRCodeExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.DOWNLOADS)))

            viewModel.export.value shouldBe QRCodeExport.Idle
        }
    },
)
