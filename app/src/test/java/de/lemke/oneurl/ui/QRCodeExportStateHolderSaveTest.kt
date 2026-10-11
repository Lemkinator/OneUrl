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

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.ui.utils.BitmapSaveResult
import de.lemke.commonutils.ui.utils.FakeBitmapExporter
import de.lemke.commonutils.ui.utils.FakeBitmapExporter.Call
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class QRCodeExportStateHolderSaveTest {
    private val qrCode = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val exporter = FakeBitmapExporter()

    private fun saveToPictures(): QRCodeExportStateHolder {
        val holder = QRCodeExportStateHolder(CoroutineScope(Dispatchers.Main), exporter)
        holder.save(qrCode, "https://short.url/x", SaveLocation.PICTURES)
        shadowOf(Looper.getMainLooper()).idle()
        return holder
    }

    @Test
    fun `save to a fixed location records the call and holds the saved result`() {
        val holder = saveToPictures()

        exporter.calls shouldBe listOf(Call.SaveToDirectory(SaveLocation.PICTURES, qrCode, "https://short.url/x"))
        holder.state.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.Saved(SaveLocation.PICTURES))
    }

    @Test
    fun `save to a fixed location holds a failed write`() {
        exporter.directoryResult = BitmapSaveResult.WriteFailed

        saveToPictures().state.value shouldBe QRCodeExport.SaveFinished(BitmapSaveResult.WriteFailed)
    }
}
