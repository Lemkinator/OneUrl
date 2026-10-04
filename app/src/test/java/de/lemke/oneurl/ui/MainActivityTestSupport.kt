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

import android.os.Looper
import android.os.SystemClock
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.descendants
import androidx.recyclerview.widget.RecyclerView
import de.lemke.oneurl.R
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.model.URL
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf

internal fun awaitMainIdle() {
    shadowOf(Looper.getMainLooper()).idle()
}

internal fun menuItem(itemId: Int): MenuItem = mockk { every { getItemId() } returns itemId }

internal fun MainActivity.searchView(): SearchView = (window.decorView as ViewGroup).descendants.filterIsInstance<SearchView>().first()

internal fun MainActivity.firstItemView(): View = findViewById<RecyclerView>(R.id.urlList).findViewHolderForAdapterPosition(0)!!.itemView

// seslStartLongPressMultiSelection (invoked from onLongClickItem) needs
// RecyclerView.mPenDragSelectedItemArray, which SESL only lazily initializes from a real
// dispatchTouchEvent(ACTION_DOWN) - a bare performLongClick() skips that and NPEs.
internal fun MainActivity.longClickFirstItem() {
    val recycler = findViewById<RecyclerView>(R.id.urlList)
    val downTime = SystemClock.uptimeMillis()
    recycler.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 1f, 1f, 0))
    recycler.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_UP, 1f, 1f, 0))
    firstItemView().performLongClick()
}

internal fun URLRepository.seedUrl(
    shortURL: String,
    favorite: Boolean = false,
): URL = urlFixture(shortURL, favorite = favorite).also { runBlocking { addURL(it) } }

internal fun urlFixture(
    shortURL: String,
    favorite: Boolean = false,
) = URL(
    shortURL = shortURL,
    longURL = "https://example.com/${shortURL.substringAfterLast('/')}",
    shortURLProvider = ShortURLProviderCompanion.default,
    favorite = favorite,
    title = "title",
    description = "description",
    added = ZonedDateTime.parse("2024-01-15T10:30:00Z"),
)
