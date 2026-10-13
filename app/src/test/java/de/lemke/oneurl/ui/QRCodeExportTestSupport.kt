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
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.ui.utils.BitmapExporter
import de.lemke.commonutils.ui.utils.DefaultBitmapExporter
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher

/** The app's QR code exporter with its IO work on [ioDispatcher]. */
internal fun bitmapExporterOn(ioDispatcher: CoroutineDispatcher): BitmapExporter =
    DefaultBitmapExporter(ApplicationProvider.getApplicationContext(), ioDispatcher, clipLabel = "QR Code", cacheFileName = "QRCode.png")

/** An empty file, as the document picker creates it; a file:// URI of it is writable under Robolectric. */
internal fun createPickedDocument(): File =
    File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "export.png").apply { createNewFile() }
