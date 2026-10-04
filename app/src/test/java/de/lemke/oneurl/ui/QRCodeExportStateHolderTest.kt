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
import app.cash.turbine.test
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.ui.utils.BitmapSaveResult
import de.lemke.commonutils.ui.utils.BitmapShareFile
import de.lemke.oneurl.ui.FakeQRCodeExporter.Call
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class QRCodeExportStateHolderTest : ShouldSpec(
    {
        val qrCode = mockk<Bitmap>()
        val fileName = "https://short.url/x"
        lateinit var exporter: FakeQRCodeExporter

        fun newHolder() = QRCodeExportStateHolder(CoroutineScope(Dispatchers.Main), exporter)

        beforeEach {
            exporter = FakeQRCodeExporter()
        }

        should("start Idle") {
            newHolder().state.value shouldBe QRCodeExport.Idle
        }

        should("save writes the QR code to the location and holds the finished result") {
            val holder = newHolder()

            holder.save(qrCode, fileName, SaveLocation.PICTURES)

            exporter.calls shouldBe listOf(Call.SaveToDirectory(SaveLocation.PICTURES, qrCode, fileName))
            holder.state.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.DOWNLOADS))
        }

        should("save holds OpenPicker with the file name when the location needs the picker") {
            exporter.directoryResult = BitmapSaveResult.NeedsPicker
            val holder = newHolder()

            holder.save(qrCode, fileName, SaveLocation.CUSTOM)

            holder.state.value shouldBe QRCodeExport.OpenPicker(fileName)
        }

        should("an export is Running until its work returns, and a second save, copy or share meanwhile does nothing") {
            val gate = CompletableDeferred<Unit>()
            exporter.gate = gate
            val holder = newHolder()

            holder.state.test {
                awaitItem() shouldBe QRCodeExport.Idle
                holder.save(qrCode, fileName, SaveLocation.PICTURES)
                awaitItem() shouldBe QRCodeExport.Running
                holder.save(qrCode, "other", SaveLocation.DCIM)
                holder.copy(qrCode)
                holder.share(qrCode, ShareTarget.SHARE_SHEET)
                exporter.calls shouldBe listOf(Call.SaveToDirectory(SaveLocation.PICTURES, qrCode, fileName))
                gate.complete(Unit)
                awaitItem() shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.DOWNLOADS))
            }
        }

        should("onHandled keeps a running export") {
            exporter.gate = CompletableDeferred()
            val holder = newHolder()

            holder.copy(qrCode)
            holder.onHandled(QRCodeExport.CopyFailed)

            holder.state.value shouldBe QRCodeExport.Running
        }

        should("onHandled returns to Idle and admits the next export") {
            val holder = newHolder()

            holder.save(qrCode, fileName, SaveLocation.PICTURES)
            holder.onHandled(QRCodeExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.DOWNLOADS)))
            holder.state.value shouldBe QRCodeExport.Idle
            holder.share(qrCode, ShareTarget.SHARE_SHEET)

            exporter.calls shouldBe
                listOf(
                    Call.SaveToDirectory(SaveLocation.PICTURES, qrCode, fileName),
                    Call.CreateShareFile(qrCode),
                )
        }

        should("onHandled keeps a result other than the handled one") {
            exporter.directoryResult = BitmapSaveResult.NeedsPicker
            val holder = newHolder()

            holder.save(qrCode, fileName, SaveLocation.CUSTOM)
            holder.onHandled(QRCodeExport.CopyFailed)

            holder.state.value shouldBe QRCodeExport.OpenPicker(fileName)
        }

        should("a tap while a result waits replaces that result") {
            exporter.directoryResult = BitmapSaveResult.NeedsPicker
            val holder = newHolder()

            holder.save(qrCode, fileName, SaveLocation.CUSTOM)
            holder.copy(qrCode)

            holder.state.value shouldBe QRCodeExport.CopyFailed
        }

        should("copy holds the written clip") {
            val clip = mockk<ClipData>()
            exporter.clip = clip
            val holder = newHolder()

            holder.copy(qrCode)

            exporter.calls shouldBe listOf(Call.CreateClip(qrCode))
            holder.state.value shouldBe QRCodeExport.Copy(clip)
        }

        should("copy holds CopyFailed when no clip could be written") {
            val holder = newHolder()

            holder.copy(qrCode)

            holder.state.value shouldBe QRCodeExport.CopyFailed
        }

        should("share holds the written share file for the share sheet") {
            val file = BitmapShareFile.Written(mockk<Uri>())
            exporter.shareFile = file
            val holder = newHolder()

            holder.share(qrCode, ShareTarget.SHARE_SHEET)

            exporter.calls shouldBe listOf(Call.CreateShareFile(qrCode))
            holder.state.value shouldBe QRCodeExport.Share(file, ShareTarget.SHARE_SHEET)
        }

        should("share holds the written share file for Quick Share") {
            val file = BitmapShareFile.Written(mockk<Uri>())
            exporter.shareFile = file
            val holder = newHolder()

            holder.share(qrCode, ShareTarget.QUICK_SHARE)

            holder.state.value shouldBe QRCodeExport.Share(file, ShareTarget.QUICK_SHARE)
        }

        should("share holds ShareFailed when the share file could not be written") {
            exporter.shareFile = BitmapShareFile.Failed
            val holder = newHolder()

            holder.share(qrCode, ShareTarget.SHARE_SHEET)

            holder.state.value shouldBe QRCodeExport.ShareFailed
        }

        should("share returns to Idle when the share file write was dropped") {
            exporter.shareFile = BitmapShareFile.Dropped
            val holder = newHolder()

            holder.share(qrCode, ShareTarget.SHARE_SHEET)

            holder.state.value shouldBe QRCodeExport.Idle
        }

        should("onDocumentPicked writes the QR code into the created document") {
            val uri = mockk<Uri>()
            val holder = newHolder()

            holder.onDocumentPicked(DocumentPick.Created(uri)) { qrCode }

            exporter.calls shouldBe listOf(Call.SaveToCreatedDocument(uri, qrCode))
            holder.state.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.CUSTOM))
        }

        should("onDocumentPicked hands a missing QR code to the write, which reports WriteFailed") {
            exporter.documentResult = BitmapSaveResult.WriteFailed
            val uri = mockk<Uri>()
            val holder = newHolder()

            holder.onDocumentPicked(DocumentPick.Created(uri)) { null }

            exporter.calls shouldBe listOf(Call.SaveToCreatedDocument(uri, null))
            holder.state.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.WriteFailed)
        }

        should("onDocumentPicked returns to Idle when the write reports Canceled") {
            exporter.documentResult = BitmapSaveResult.Canceled
            val holder = newHolder()

            holder.onDocumentPicked(DocumentPick.Created(mockk<Uri>())) { qrCode }

            holder.state.value shouldBe QRCodeExport.Idle
        }

        should("onDocumentPicked holds WriteFailed for a result without a URI") {
            val holder = newHolder()

            holder.onDocumentPicked(DocumentPick.MissingUri) { qrCode }

            exporter.calls shouldBe emptyList()
            holder.state.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.WriteFailed)
        }

        should("onDocumentPicked stays silent for a canceled picker") {
            val holder = newHolder()

            holder.onDocumentPicked(DocumentPick.Canceled) { qrCode }

            exporter.calls shouldBe emptyList()
            holder.state.value shouldBe QRCodeExport.Idle
        }
    },
)
