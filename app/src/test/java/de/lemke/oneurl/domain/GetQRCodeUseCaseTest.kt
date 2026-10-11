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
import de.lemke.oneurl.data.QRCodeCache
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun bitmap(): Bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class GetQRCodeUseCaseTest {
    private val url = "https://short.url/x"
    private val cache = QRCodeCache()
    private val generated = bitmap()
    private val renderer = FakeQRCodeRenderer(generated, placeholder = bitmap())

    @Test
    fun `a cache hit returns the cached bitmap and never renders`() =
        runTest {
            val cached = bitmap()
            cache[url] = cached
            val getQRCode = GetQRCodeUseCase(cache, GenerateQRCodeUseCase(renderer), StandardTestDispatcher(testScheduler))

            getQRCode(url) shouldBeSameInstanceAs cached

            renderer.renders shouldBe emptyList()
        }

    @Test
    fun `a cache miss renders the bitmap once, returns it and caches it`() =
        runTest {
            val getQRCode = GetQRCodeUseCase(cache, GenerateQRCodeUseCase(renderer), StandardTestDispatcher(testScheduler))

            getQRCode(url) shouldBeSameInstanceAs generated

            cache[url] shouldBeSameInstanceAs generated
            renderer.renders shouldBe listOf(Render(url, null))
        }

    @Test
    fun `a second call for the same url hits the cache`() =
        runTest {
            val getQRCode = GetQRCodeUseCase(cache, GenerateQRCodeUseCase(renderer), StandardTestDispatcher(testScheduler))

            getQRCode(url)
            getQRCode(url) shouldBeSameInstanceAs generated

            renderer.renders shouldBe listOf(Render(url, null))
        }
}
