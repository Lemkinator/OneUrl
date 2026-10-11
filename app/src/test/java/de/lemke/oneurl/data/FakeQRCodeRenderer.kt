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

package de.lemke.oneurl.data

import android.graphics.Bitmap
import de.lemke.oneurl.domain.model.QRCodeStyle

/** Renders every QR code as [qrCode], or throws [failure] once set, and draws [placeholder]; records each call. */
internal class FakeQRCodeRenderer(
    var qrCode: Bitmap?,
    private val placeholder: Bitmap,
) : QRCodeRenderer {
    var failure: Exception? = null
    val renders = mutableListOf<Render>()
    var placeholdersDrawn = 0
        private set

    override fun render(
        url: String,
        style: QRCodeStyle?,
    ): Bitmap? {
        renders += Render(url, style)
        failure?.let { throw it }
        return qrCode
    }

    override fun placeholder(): Bitmap {
        placeholdersDrawn++
        return placeholder
    }

    data class Render(
        val url: String,
        val style: QRCodeStyle?,
    )
}
