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
import de.lemke.oneurl.data.QRCodeCache
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun bitmap(): Bitmap = mockk { every { byteCount } returns 1024 }

// QRCodeCache wraps android.util.LruCache, which needs a real Android runtime, hence Robolectric here.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class GetQRCodeUseCaseTest {
    private val url = "https://short.url/x"
    private val cache = QRCodeCache()
    private val generateQRCode = mockk<GenerateQRCodeUseCase>()

    @Test
    fun `a cache hit returns the cached bitmap and never generates`() =
        runTest {
            val cached = bitmap()
            cache[url] = cached
            val getQRCode = GetQRCodeUseCase(cache, generateQRCode, StandardTestDispatcher(testScheduler))

            getQRCode(url) shouldBe cached

            verify(exactly = 0) { generateQRCode(any<String>()) }
        }

    @Test
    fun `a cache miss generates the bitmap once, returns it and caches it`() =
        runTest {
            val generated = bitmap()
            every { generateQRCode(url) } returns generated
            val getQRCode = GetQRCodeUseCase(cache, generateQRCode, StandardTestDispatcher(testScheduler))

            getQRCode(url) shouldBe generated

            cache[url] shouldBe generated
            verify(exactly = 1) { generateQRCode(url) }
        }

    @Test
    fun `a second call for the same url hits the cache`() =
        runTest {
            val generated = bitmap()
            every { generateQRCode(url) } returns generated
            val getQRCode = GetQRCodeUseCase(cache, generateQRCode, StandardTestDispatcher(testScheduler))

            getQRCode(url)
            getQRCode(url) shouldBe generated

            verify(exactly = 1) { generateQRCode(url) }
        }
}
