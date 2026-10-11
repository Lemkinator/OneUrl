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
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import de.lemke.oneurl.App
import de.lemke.oneurl.domain.model.QRCodeStyle
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DefaultQRCodeRendererTest {
    private val context = ApplicationProvider.getApplicationContext<App>()
    private val renderer = DefaultQRCodeRenderer(context)

    private fun style(
        size: Int = 300,
        roundedFrame: Boolean = false,
    ) = QRCodeStyle(
        size = size,
        foregroundColor = Color.BLACK,
        backgroundColor = Color.WHITE,
        tintAnchor = true,
        tintBorder = true,
        icon = true,
        roundedFrame = roundedFrame,
    )

    private fun Bitmap.centerPixel(): Int = getPixel(width / 2, height / 2)

    @Test
    fun `render without a style draws the default framed QR code`() {
        val qrCode = renderer.render("https://example.com", null)!!

        qrCode.width shouldBe FRAMED_DEFAULT_SIZE_PX
        qrCode.height shouldBe FRAMED_DEFAULT_SIZE_PX
    }

    @Test
    fun `render with a style draws the QR code at the style size`() {
        val qrCode = renderer.render("https://example.com", style(size = 300))!!

        qrCode.width shouldBe 300
        qrCode.height shouldBe 300
    }

    @Test
    fun `render with a rounded frame adds the frame border around the style size`() {
        val qrCode = renderer.render("https://example.com", style(size = 300, roundedFrame = true))!!

        qrCode.width shouldBe 300 + 2 * FRAME_BORDER_PX
    }

    @Test
    fun `render without the icon leaves the center to the QR modules`() {
        val withIcon = renderer.render("https://example.com", style())!!
        val withoutIcon = renderer.render("https://example.com", style().copy(icon = false))!!

        withIcon.sameAs(withoutIcon) shouldBe false
    }

    @Test
    fun `render returns null for content too long to encode`() {
        renderer.render("https://example.com/" + "a".repeat(TOO_LONG_CONTENT_LENGTH), null) shouldBe null
    }

    @Test
    fun `render throws for a negative size`() {
        shouldThrow<IllegalArgumentException> { renderer.render("https://example.com", style(size = -1)) }
    }

    @Test
    fun `placeholder draws the framed card with the launcher icon in the center`() {
        val placeholder = renderer.placeholder()

        placeholder.width shouldBe FRAMED_DEFAULT_SIZE_PX
        placeholder.height shouldBe FRAMED_DEFAULT_SIZE_PX
        placeholder.centerPixel() shouldNotBe CARD_COLOR
    }

    @Test
    fun `placeholder without an icon leaves the card center empty`() {
        val placeholder = drawQRCodePlaceholder(context, icon = null)

        placeholder.width shouldBe FRAMED_DEFAULT_SIZE_PX
        placeholder.centerPixel() shouldBe CARD_COLOR
        placeholder.getPixel(0, 0) shouldBe Color.TRANSPARENT
    }

    private companion object {
        // 200 dp plus a 12 dp border on each side at the 420 dpi test density.
        const val FRAME_BORDER_PX = 31
        const val FRAMED_DEFAULT_SIZE_PX = 525 + 2 * FRAME_BORDER_PX
        const val CARD_COLOR = 0xFFFCFCFC.toInt()
        const val TOO_LONG_CONTENT_LENGTH = 3000
    }
}
