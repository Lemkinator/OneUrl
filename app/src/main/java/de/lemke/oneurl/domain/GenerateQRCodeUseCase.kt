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

package de.lemke.oneurl.domain

import android.graphics.Bitmap
import android.util.Log
import de.lemke.oneurl.data.QRCodeRenderer
import de.lemke.oneurl.domain.model.QRCodeStyle
import javax.inject.Inject

class GenerateQRCodeUseCase @Inject constructor(
    private val renderer: QRCodeRenderer,
) {
    operator fun invoke(url: String): Bitmap = renderOrPlaceholder(url, style = null)

    operator fun invoke(
        url: String,
        style: QRCodeStyle,
    ): Bitmap = renderOrPlaceholder(url, style)

    @Suppress("TooGenericExceptionCaught")
    private fun renderOrPlaceholder(
        url: String,
        style: QRCodeStyle?,
    ): Bitmap =
        try {
            renderer.render(url, style) ?: renderer.placeholder()
        } catch (e: Exception) {
            Log.e("GenerateQRCodeUseCase", "error: ${e.message}", e)
            renderer.placeholder()
        }
}
