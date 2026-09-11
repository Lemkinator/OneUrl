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

import android.app.Application
import android.graphics.Bitmap
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun bitmap(byteCount: Int = 1024): Bitmap = mockk { every { this@mockk.byteCount } returns byteCount }

// android.util.LruCache needs a real Android runtime (not the default unit-test "not mocked"
// stub jar), hence Robolectric here.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class QRCodeCacheTest {
    @Test
    fun `get returns null for a url that was never cached`() {
        val cache = QRCodeCache()

        cache["https://short.url/x"] shouldBe null
    }

    @Test
    fun `set then get returns the same full-size bitmap for the same url`() {
        val cache = QRCodeCache()
        val bitmap = bitmap()

        cache["https://short.url/x"] = bitmap

        cache["https://short.url/x"] shouldBe bitmap
    }

    @Test
    fun `full-size and sized entries for the same url are independent`() {
        val cache = QRCodeCache()
        val fullSize = bitmap()
        val thumbnail = bitmap()

        cache["https://short.url/x"] = fullSize
        cache["https://short.url/x", 165] = thumbnail

        cache["https://short.url/x"] shouldBe fullSize
        cache["https://short.url/x", 165] shouldBe thumbnail
    }

    @Test
    fun `sized get returns null for a different size of a cached url`() {
        val cache = QRCodeCache()
        cache["https://short.url/x", 165] = bitmap()

        cache["https://short.url/x", 512] shouldBe null
    }

    @Test
    fun `sized get returns null for a different url at the same size`() {
        val cache = QRCodeCache()
        cache["https://short.url/x", 165] = bitmap()

        cache["https://short.url/y", 165] shouldBe null
    }

    @Test
    fun `full-size key does not collide with a sized entry whose composite key matches it`() {
        val cache = QRCodeCache()
        val collidingFullSize = bitmap()
        val sized = bitmap()

        // The sized cache's composite key is "$url@$sizePx" - a full-size url that happens to
        // equal that exact string must not read or overwrite the unrelated sized entry.
        cache["https://short.url/x@165"] = collidingFullSize
        cache["https://short.url/x", 165] = sized

        cache["https://short.url/x@165"] shouldBe collidingFullSize
        cache["https://short.url/x", 165] shouldBe sized
    }
}
