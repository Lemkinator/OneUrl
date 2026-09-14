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

import android.graphics.Color
import androidx.appcompat.content.res.AppCompatResources
import androidx.test.core.app.ApplicationProvider
import de.lemke.oneurl.App
import dev.oneuiproject.oneui.qr.utils.QrEncoder
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import de.lemke.commonutils.R as commonutilsR

// QrEncoder draws through android.graphics; the default Robolectric legacy graphics stub cannot
// produce a real bitmap, hence the native graphics mode.
@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GenerateQRCodeUseCaseTest {
    private val context = ApplicationProvider.getApplicationContext<App>()
    private val generateQRCode = GenerateQRCodeUseCase(context)

    @Test
    fun `default overload returns a bitmap`() =
        runTest {
            generateQRCode("https://example.com") shouldNotBe null
        }

    @Test
    fun `customized overload with icon returns a bitmap of the requested size`() =
        runTest {
            val result =
                generateQRCode(
                    "https://example.com",
                    size = 300,
                    foregroundColor = Color.BLACK,
                    backgroundColor = Color.WHITE,
                    tintAnchor = true,
                    tintBorder = true,
                    icon = true,
                    roundedFrame = true,
                )

            result.shouldBeInstanceOf<android.graphics.Bitmap>()
        }

    @Test
    fun `customized overload without icon still returns a bitmap`() =
        runTest {
            val result =
                generateQRCode(
                    "https://example.com",
                    size = 200,
                    foregroundColor = Color.BLACK,
                    backgroundColor = Color.WHITE,
                    tintAnchor = false,
                    tintBorder = false,
                    icon = false,
                    roundedFrame = false,
                )

            result shouldNotBe null
        }

    @Test
    fun `invalid size falls back to the no-support placeholder bitmap`() =
        runTest {
            val result =
                generateQRCode(
                    "https://example.com",
                    size = -1,
                    foregroundColor = Color.BLACK,
                    backgroundColor = Color.WHITE,
                    tintAnchor = false,
                    tintBorder = false,
                    icon = false,
                    roundedFrame = false,
                )

            result shouldNotBe null
        }

    @Test
    fun `default overload falls back to the no-support placeholder when QrEncoder throws`() =
        runTest {
            mockkConstructor(QrEncoder::class)
            every { anyConstructed<QrEncoder>().setIcon(any<Int>()) } throws RuntimeException("boom")

            try {
                generateQRCode("https://example.com") shouldNotBe null
            } finally {
                unmockkConstructor(QrEncoder::class)
            }
        }

    @Test
    fun `no-support placeholder still renders when the launcher icon drawable is unavailable`() =
        runTest {
            mockkStatic(AppCompatResources::class)
            every { AppCompatResources.getDrawable(context, commonutilsR.drawable.ic_launcher_themed) } returns null

            try {
                val result =
                    generateQRCode(
                        "https://example.com",
                        size = -1,
                        foregroundColor = Color.BLACK,
                        backgroundColor = Color.WHITE,
                        tintAnchor = false,
                        tintBorder = false,
                        icon = false,
                        roundedFrame = false,
                    )

                result shouldNotBe null
            } finally {
                unmockkStatic(AppCompatResources::class)
            }
        }
}
