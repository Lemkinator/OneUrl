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

import android.app.Application
import android.graphics.Bitmap
import de.lemke.oneurl.data.FakeQRCodeRenderer
import de.lemke.oneurl.data.FakeQRCodeRenderer.Render
import de.lemke.oneurl.domain.model.QRCodeStyle
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class GenerateQRCodeUseCaseTest {
    private val qrCode = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
    private val placeholder = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private val renderer = FakeQRCodeRenderer(qrCode, placeholder)
    private val generateQRCode = GenerateQRCodeUseCase(renderer)
    private val style =
        QRCodeStyle(
            size = 300,
            foregroundColor = 0xFF112233.toInt(),
            backgroundColor = 0xFFFFFFFF.toInt(),
            tintAnchor = true,
            tintBorder = false,
            icon = true,
            roundedFrame = false,
        )

    @Test
    fun `default overload renders the url without a style`() {
        generateQRCode("https://example.com") shouldBeSameInstanceAs qrCode

        renderer.renders shouldBe listOf(Render("https://example.com", null))
        renderer.placeholdersDrawn shouldBe 0
    }

    @Test
    fun `styled overload renders the url in the given style`() {
        generateQRCode("https://example.com", style) shouldBeSameInstanceAs qrCode

        renderer.renders shouldBe listOf(Render("https://example.com", style))
        renderer.placeholdersDrawn shouldBe 0
    }

    @Test
    fun `a render without a bitmap returns the placeholder`() {
        renderer.qrCode = null

        generateQRCode("https://example.com", style) shouldBeSameInstanceAs placeholder

        renderer.placeholdersDrawn shouldBe 1
    }

    @Test
    fun `a failed default render returns the placeholder`() {
        renderer.failure = IllegalArgumentException("boom")

        generateQRCode("https://example.com") shouldBeSameInstanceAs placeholder

        renderer.placeholdersDrawn shouldBe 1
    }

    @Test
    fun `a failed styled render returns the placeholder`() {
        renderer.failure = IllegalStateException("boom")

        generateQRCode("https://example.com", style) shouldBeSameInstanceAs placeholder

        renderer.placeholdersDrawn shouldBe 1
    }
}
