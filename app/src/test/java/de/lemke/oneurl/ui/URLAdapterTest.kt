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

package de.lemke.oneurl.ui

import android.graphics.Bitmap
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.oneurl.R
import de.lemke.oneurl.data.QRCodeCache
import de.lemke.oneurl.domain.GenerateQRCodeThumbnailUseCase
import de.lemke.oneurl.domain.testUrl
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
//
// Constructed directly (not via Hilt) so generateQRCodeThumbnail can be mocked; MainActivity only
// supplies a themed Context/LayoutInflater to inflate listview_item.xml and stays on an empty list
// of its own. scope is always UnconfinedTestDispatcher; defaultDispatcher defaults to the same one
// so bindQrCode's coroutine runs to completion synchronously (no idling required), but a test can
// override it to hold the load pending instead - see `onViewRecycled cancels a pending qr load`.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class URLAdapterTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var qrCodeCache: QRCodeCache

    @Inject
    lateinit var settings: SettingsRepository

    private val dispatcher = UnconfinedTestDispatcher()
    private val generateQRCodeThumbnail = mockk<GenerateQRCodeThumbnailUseCase>()

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
    }

    private fun freshBitmap() = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    // scope stays Unconfined so submitList/onBindViewHolder run synchronously up to the first real
    // suspension point; defaultDispatcher is overridable so a test can hold a launched qr load
    // pending (rather than let it complete inline) by passing a dispatcher that won't run until
    // explicitly advanced.
    private fun withAdapter(
        defaultDispatcher: CoroutineDispatcher = dispatcher,
        block: (URLAdapter, URLAdapter.ViewHolder) -> Unit,
    ) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val adapter =
                    URLAdapter(
                        context = activity,
                        qrCodeCache = qrCodeCache,
                        generateQRCodeThumbnail = generateQRCodeThumbnail,
                        scope = CoroutineScope(dispatcher),
                        defaultDispatcher = defaultDispatcher,
                        onAllSelectorStateChanged = {},
                        onBlockActionMode = {},
                    )
                val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
                block(adapter, holder)
            }
        }
    }

    @Test
    fun `onViewRecycled with no pending qr load is a no-op`() {
        withAdapter { adapter, holder ->
            adapter.onViewRecycled(holder)
        }
    }

    @Test
    fun `onViewRecycled cancels a pending qr load`() {
        // StandardTestDispatcher queues rather than runs its work, so the withContext dispatch in
        // bindQrCode is still suspended (genuinely pending) when onViewRecycled cancels it below -
        // unlike UnconfinedTestDispatcher, which would already have completed the load during
        // onBindViewHolder, making the cancellation a no-op the test couldn't detect.
        val pendingDispatcher = StandardTestDispatcher()
        every { generateQRCodeThumbnail(any(), any()) } returns freshBitmap()
        withAdapter(defaultDispatcher = pendingDispatcher) { adapter, holder ->
            val url = testUrl("https://short.url/recycle")
            adapter.submitList(listOf(url))

            adapter.onBindViewHolder(holder, 0)
            // setImageBitmap(null) wraps a BitmapDrawable(resources, null) rather than clearing the
            // drawable to a null reference, so "still not loaded" is asserted as "unchanged from the
            // pre-load placeholder", not as a null drawable.
            val placeholderDrawable = holder.listItemImg.drawable
            adapter.onViewRecycled(holder)
            pendingDispatcher.scheduler.advanceUntilIdle()

            val qrSizePx =
                holder.itemView.context.resources
                    .getDimensionPixelSize(R.dimen.list_item_qr_size)
            verify(exactly = 0) { generateQRCodeThumbnail(any(), any()) }
            qrCodeCache[url.shortURL, qrSizePx] shouldBe null
            holder.listItemImg.drawable shouldBe placeholderDrawable
        }
    }

    @Test
    fun `bind uses a cached thumbnail without generating a new one`() {
        withAdapter { adapter, holder ->
            val url = testUrl("https://short.url/cached")
            val qrSizePx =
                holder.itemView.context.resources
                    .getDimensionPixelSize(R.dimen.list_item_qr_size)
            val cached = freshBitmap()
            qrCodeCache[url.shortURL, qrSizePx] = cached
            adapter.submitList(listOf(url))

            adapter.onBindViewHolder(holder, 0)

            holder.listItemImg.drawable shouldNotBe null
            verify(exactly = 0) { generateQRCodeThumbnail(any(), any()) }
        }
    }

    @Test
    fun `bind on cache miss generates and caches a thumbnail for a still-bound holder`() {
        every { generateQRCodeThumbnail(any(), any()) } returns freshBitmap()
        withAdapter { adapter, holder ->
            val url = testUrl("https://short.url/miss")
            adapter.submitList(listOf(url))

            adapter.onBindViewHolder(holder, 0)

            val qrSizePx =
                holder.itemView.context.resources
                    .getDimensionPixelSize(R.dimen.list_item_qr_size)
            qrCodeCache[url.shortURL, qrSizePx] shouldNotBe null
            holder.listItemImg.drawable shouldNotBe null
        }
    }
}
