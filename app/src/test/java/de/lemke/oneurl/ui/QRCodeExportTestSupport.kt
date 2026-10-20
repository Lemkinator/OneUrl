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

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.ui.utils.BitmapExporter
import de.lemke.commonutils.ui.utils.DefaultBitmapExporter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher

/** The app's QR code exporter with its IO work on [ioDispatcher]. */
internal fun bitmapExporterOn(ioDispatcher: CoroutineDispatcher): BitmapExporter =
    DefaultBitmapExporter(ApplicationProvider.getApplicationContext(), ioDispatcher, clipLabel = "QR Code", cacheFileName = "QRCode.png")

/** An empty file, as the document picker creates it; a file:// URI of it is writable under Robolectric. */
internal fun createPickedDocument(): File =
    File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "export.png").apply { createNewFile() }

/** Asserts this intent opens the create-document picker for a PNG named [fileNamePrefix] plus a timestamp. */
internal fun Intent.shouldBePngPicker(fileNamePrefix: String) {
    action shouldBe Intent.ACTION_CREATE_DOCUMENT
    type shouldBe "image/png"
    getStringExtra(Intent.EXTRA_TITLE)!! shouldMatch
        Regex("${Regex.escape(fileNamePrefix)}_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}\\.png")
}
