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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun bitmap(size: Int = 4): Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class GetQRCodeThumbnailUseCaseTest {
    private val url = "https://short.url/x"
    private val cache = QRCodeCache()
    private val renderer = FakeQRCodeRenderer(bitmap(size = 40), placeholder = bitmap())

    @Test
    fun `cached returns null for a url without a thumbnail`() =
        runTest {
            val getThumbnail = GetQRCodeThumbnailUseCase(cache, GenerateQRCodeUseCase(renderer), StandardTestDispatcher(testScheduler))

            getThumbnail.cached(url, 10) shouldBe null
        }

    @Test
    fun `cached returns the thumbnail stored for the same url and size only`() =
        runTest {
            val getThumbnail = GetQRCodeThumbnailUseCase(cache, GenerateQRCodeUseCase(renderer), StandardTestDispatcher(testScheduler))
            val stored = bitmap()
            cache[url, 10] = stored

            getThumbnail.cached(url, 10) shouldBeSameInstanceAs stored
            getThumbnail.cached(url, 20) shouldBe null
            getThumbnail.cached("https://short.url/y", 10) shouldBe null
            renderer.renders shouldBe emptyList()
        }

    @Test
    fun `invoke renders once, scales to a square of the requested size and caches under url and size`() =
        runTest {
            val getThumbnail = GetQRCodeThumbnailUseCase(cache, GenerateQRCodeUseCase(renderer), StandardTestDispatcher(testScheduler))

            val thumbnail = getThumbnail(url, 10)

            thumbnail.width shouldBe 10
            thumbnail.height shouldBe 10
            cache[url, 10] shouldBeSameInstanceAs thumbnail
            cache[url] shouldBe null
            renderer.renders shouldBe listOf(Render(url, null))
        }

    @Test
    fun `invoke finishes and caches the thumbnail although the caller is cancelled`() =
        runTest {
            val getThumbnail = GetQRCodeThumbnailUseCase(cache, GenerateQRCodeUseCase(renderer), StandardTestDispatcher(testScheduler))

            val caller = async(start = CoroutineStart.UNDISPATCHED) { getThumbnail(url, 10) }
            caller.cancel()

            renderer.renders shouldBe emptyList()
            cache[url, 10] shouldBe null

            testScheduler.advanceUntilIdle()

            cache[url, 10]?.width shouldBe 10
            renderer.renders shouldBe listOf(Render(url, null))
        }
}
