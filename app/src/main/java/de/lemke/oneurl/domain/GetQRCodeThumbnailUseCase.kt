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
import androidx.core.graphics.scale
import de.lemke.commonutils.di.DefaultDispatcher
import de.lemke.oneurl.data.QRCodeCache
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Square thumbnail QR codes of a URL. [cached] reads the thumbnail of a size from the cache without suspending.
 * [invoke] always generates a new one, scales it to the size and stores it in the cache.
 * Generation runs to completion even if the caller is cancelled, so the cache still receives the result.
 */
class GetQRCodeThumbnailUseCase @Inject constructor(
    private val qrCodeCache: QRCodeCache,
    private val generateQRCode: GenerateQRCodeUseCase,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    fun cached(
        url: String,
        sizePx: Int,
    ): Bitmap? = qrCodeCache[url, sizePx]

    // QrEncoder's icon overlay is a fixed dp size regardless of requested QR size; it would swallow
    // a 55dp code, so generate at QrEncoder's default size and downscale instead.
    suspend operator fun invoke(
        url: String,
        sizePx: Int,
    ): Bitmap =
        withContext(NonCancellable + defaultDispatcher) {
            generateQRCode(url).scale(sizePx, sizePx).also { qrCodeCache[url, sizePx] = it }
        }
}
