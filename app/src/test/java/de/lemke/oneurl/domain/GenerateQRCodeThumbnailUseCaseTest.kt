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
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
//
// GraphicsMode.NATIVE is required (not the default "not mocked" stub) since these tests read back
// actual pixel content to confirm the thumbnail is really drawn, not just correctly sized.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GenerateQRCodeThumbnailUseCaseTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val generateQRCode = GenerateQRCodeUseCase(context)
    private val generateThumbnail = GenerateQRCodeThumbnailUseCase(context, generateQRCode)

    @Test
    fun `output bitmap is exactly sizePx by sizePx`() {
        val result = generateThumbnail("https://short.url/abc", 165)

        result.width shouldBe 165
        result.height shouldBe 165
    }

    @Test
    fun `output bitmap is not blank`() {
        val result = generateThumbnail("https://short.url/abc", 165)

        val backgroundColor = result.getPixel(0, 0)
        val hasNonBackgroundPixel =
            (0 until result.width).any { x ->
                (0 until result.height).any { y -> result.getPixel(x, y) != backgroundColor }
            }

        hasNonBackgroundPixel shouldBe true
    }

    @Test
    fun `two calls with the same url and size produce pixel-identical bitmaps`() {
        val first = generateThumbnail("https://short.url/abc", 165)
        val second = generateThumbnail("https://short.url/abc", 165)

        first.sameAs(second) shouldBe true
    }

    @Test
    fun `content too large for a QR code falls back to the slow path`() {
        val url = "https://short.url/" + "a".repeat(5000)
        val fallback = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val mockGenerateQRCode = mockk<GenerateQRCodeUseCase>()
        every { mockGenerateQRCode(url) } returns fallback
        val thumbnailWithMockedFallback = GenerateQRCodeThumbnailUseCase(context, mockGenerateQRCode)

        val result = thumbnailWithMockedFallback(url, 165)

        result.width shouldBe 165
        result.height shouldBe 165
        verify(exactly = 1) { mockGenerateQRCode(url) }
    }
}
