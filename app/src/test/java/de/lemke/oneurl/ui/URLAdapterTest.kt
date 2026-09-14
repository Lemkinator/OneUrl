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
import android.graphics.drawable.BitmapDrawable
import android.text.Spanned
import android.text.style.TextAppearanceSpan
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.oneurl.R
import de.lemke.oneurl.data.QRCodeCache
import de.lemke.oneurl.domain.GenerateQRCodeUseCase
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.domain.testUrl
import io.kotest.matchers.booleans.shouldBeTrue
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
// Constructed directly (not via Hilt) so generateQRCode can be mocked; MainActivity only supplies
// a themed Context/LayoutInflater to inflate listview_item.xml and stays on an empty list of its
// own.
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
    private val generateQRCode = mockk<GenerateQRCodeUseCase>()

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
    }

    private fun freshBitmap() = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

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
                        generateQRCode = generateQRCode,
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
    fun `onViewRecycled cancels a pending qr load's UI update but keeps the cache write`() {
        val pendingDispatcher = StandardTestDispatcher()
        every { generateQRCode(any()) } returns freshBitmap()
        withAdapter(defaultDispatcher = pendingDispatcher) { adapter, holder ->
            val url = testUrl("https://short.url/recycle")
            adapter.submitList(listOf(url))

            adapter.onBindViewHolder(holder, 0)
            // setImageBitmap(null) wraps a BitmapDrawable(resources, null), not a null drawable.
            val placeholderDrawable = holder.listItemImg.drawable
            adapter.onViewRecycled(holder)
            pendingDispatcher.scheduler.advanceUntilIdle()

            val qrSizePx =
                holder.itemView.context.resources
                    .getDimensionPixelSize(R.dimen.list_item_qr_size)
            verify(exactly = 1) { generateQRCode(any()) }
            qrCodeCache[url.shortURL, qrSizePx] shouldNotBe null
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
            verify(exactly = 0) { generateQRCode(any()) }
        }
    }

    @Test
    fun `bind on cache miss generates and caches a thumbnail for a still-bound holder`() {
        every { generateQRCode(any()) } returns freshBitmap()
        withAdapter { adapter, holder ->
            val url = testUrl("https://short.url/miss")
            adapter.submitList(listOf(url))

            adapter.onBindViewHolder(holder, 0)

            val qrSizePx =
                holder.itemView.context.resources
                    .getDimensionPixelSize(R.dimen.list_item_qr_size)
            qrCodeCache[url.shortURL, qrSizePx] shouldNotBe null
            (holder.listItemImg.drawable as BitmapDrawable).bitmap shouldNotBe null
        }
    }

    // AppCompatResources deliberately returns a fresh, non-cached instance for vector drawables
    // (to keep per-instance tinting safe), so comparing against an independently-loaded reference
    // drawable's constantState never matches - instead confirm the icon actually changes when the
    // same holder is rebound with the opposite favorite state.
    @Test
    fun `bind swaps the favorite icon between favorite and non-favorite states`() {
        withAdapter { adapter, holder ->
            adapter.submitList(listOf(testUrl("https://short.url/fav-on", favorite = true)))
            adapter.onBindViewHolder(holder, 0)
            val favoriteIcon = holder.listItemFav.compoundDrawablesRelative[2]?.constantState

            adapter.submitList(listOf(testUrl("https://short.url/fav-on", favorite = false)))
            adapter.onBindViewHolder(holder, 0)
            val nonFavoriteIcon = holder.listItemFav.compoundDrawablesRelative[2]?.constantState

            favoriteIcon shouldNotBe null
            nonFavoriteIcon shouldNotBe null
            favoriteIcon shouldNotBe nonFavoriteIcon
        }
    }

    @Test
    fun `getItemViewType always returns 0`() {
        withAdapter { adapter, _ ->
            adapter.getItemViewType(0) shouldBe 0
        }
    }

    @Test
    fun `itemView click invokes onClickItem with the position, url, and holder`() {
        val url = testUrl("https://short.url/click")
        withAttachedAdapter(listOf(url)) { adapter, holder ->
            var capturedPosition: Int? = null
            var capturedUrl: URL? = null
            var capturedHolder: URLAdapter.ViewHolder? = null
            adapter.onClickItem = { position, u, h ->
                capturedPosition = position
                capturedUrl = u
                capturedHolder = h
            }

            holder.itemView.performClick()

            capturedPosition shouldBe 0
            capturedUrl shouldBe url
            capturedHolder shouldBe holder
        }
    }

    @Test
    fun `itemView long-click invokes onLongClickItem and returns true`() {
        val url = testUrl("https://short.url/longclick")
        withAttachedAdapter(listOf(url)) { adapter, holder ->
            var invoked = false
            adapter.onLongClickItem = { invoked = true }

            val handled = holder.itemView.performLongClick()

            handled.shouldBeTrue()
            invoked.shouldBeTrue()
        }
    }

    @Test
    fun `listItemFav click invokes onClickItemFavorite with the position and url`() {
        val url = testUrl("https://short.url/fav-click")
        withAttachedAdapter(listOf(url)) { adapter, holder ->
            var capturedPosition: Int? = null
            var capturedUrl: URL? = null
            adapter.onClickItemFavorite = { position, u ->
                capturedPosition = position
                capturedUrl = u
            }

            holder.listItemFav.performClick()

            capturedPosition shouldBe 0
            capturedUrl shouldBe url
        }
    }

    @Test
    fun `highlightWord setter notifies once per distinct value and skips a no-op re-set`() {
        withAdapter { adapter, _ ->
            adapter.submitList(listOf(testUrl("https://short.url/highlight-notify")))
            val observer = CountingAdapterObserver()
            adapter.registerAdapterDataObserver(observer)

            adapter.highlightWord = "term"
            observer.changeCount shouldBe 1

            adapter.highlightWord = "term"
            observer.changeCount shouldBe 1

            adapter.highlightWord = "other"
            observer.changeCount shouldBe 2
        }
    }

    @Test
    fun `onBindViewHolder with SELECTION_MODE payload animates the selection state`() {
        val url = testUrl("https://short.url/selection-mode")
        withAttachedAdapter(listOf(url)) { adapter, holder ->
            // toggleActionMode reads MultiSelectorDelegate's own adapter reference, which is only
            // set once this adapter is attached to a real RecyclerView (see withAttachedAdapter).
            adapter.toggleActionMode(true)

            adapter.onBindViewHolder(holder, 0, mutableListOf(URLAdapter.Payload.SELECTION_MODE))

            holder.selectableLayout.isSelectionMode.shouldBeTrue()
        }
    }

    @Test
    fun `onBindViewHolder with HIGHLIGHT payload re-highlights the bound text`() {
        withAdapter { adapter, holder ->
            val url = testUrl("https://short.url/highlight-term")
            adapter.submitList(listOf(url))
            adapter.highlightWord = "short"

            adapter.onBindViewHolder(holder, 0, mutableListOf(URLAdapter.Payload.HIGHLIGHT))

            val text = holder.listItemTitle.text
            val spans = (text as Spanned).getSpans(0, text.length, TextAppearanceSpan::class.java)
            spans.isNotEmpty().shouldBeTrue()
        }
    }

    private fun withAttachedAdapter(
        urls: List<URL>,
        block: (URLAdapter, URLAdapter.ViewHolder) -> Unit,
    ) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val adapter =
                    URLAdapter(
                        context = activity,
                        qrCodeCache = qrCodeCache,
                        generateQRCode = generateQRCode,
                        scope = CoroutineScope(dispatcher),
                        defaultDispatcher = dispatcher,
                        onAllSelectorStateChanged = {},
                        onBlockActionMode = {},
                    )
                adapter.submitList(urls)
                val recyclerView =
                    RecyclerView(activity).apply {
                        layoutManager = LinearLayoutManager(activity)
                        this.adapter = adapter
                    }
                // MultiSelectorDelegate's own adapter reference (needed by toggleActionMode) is
                // only set via this explicit call, not by assigning RecyclerView.adapter above.
                adapter.configureWith(recyclerView)
                val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
                recyclerView.measure(widthSpec, heightSpec)
                recyclerView.layout(0, 0, 1080, 1920)
                val holder = recyclerView.findViewHolderForAdapterPosition(0) as URLAdapter.ViewHolder
                block(adapter, holder)
            }
        }
    }

    private class CountingAdapterObserver : RecyclerView.AdapterDataObserver() {
        var changeCount = 0

        override fun onItemRangeChanged(
            positionStart: Int,
            itemCount: Int,
            payload: Any?,
        ) {
            changeCount++
        }
    }
}
