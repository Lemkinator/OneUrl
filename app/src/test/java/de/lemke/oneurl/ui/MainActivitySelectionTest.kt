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
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.core.view.descendants
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.oneurl.R
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_SHORTURL
import dev.oneuiproject.oneui.layout.NavDrawerLayout
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import dev.oneuiproject.oneui.design.R as designR

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class MainActivitySelectionTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var urlRepository: URLRepository

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
    }

    @Test
    fun `onClickItem without action mode opens URLActivity for that url`() {
        val url = urlRepository.seedUrl("https://da.gd/openme1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.firstItemView().performClick() }
            awaitMainIdle()
            scenario.onActivity { activity ->
                val started = shadowOf(activity).nextStartedActivity
                started.component?.className shouldBe URLActivity::class.java.name
                started.getStringExtra(KEY_SHORTURL) shouldBe url.shortURL
            }
        }
    }

    @Test
    fun `double tap on a row opens one URLActivity`() {
        urlRepository.seedUrl("https://da.gd/opentwice1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity ->
                activity.firstItemView().performClick()
                activity.firstItemView().performClick()
            }
            awaitMainIdle()
            scenario.onActivity { activity ->
                val shadowActivity = shadowOf(activity)
                shadowActivity.nextStartedActivity.component?.className shouldBe URLActivity::class.java.name
                shadowActivity.nextStartedActivity shouldBe null
            }
        }
    }

    @Test
    fun `onClickItem and onLongClickItem toggle selection while in action mode`() {
        urlRepository.seedUrl("https://da.gd/select1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.longClickFirstItem() }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.findViewById<View>(R.id.addFab).isVisible.shouldBeFalse() }
            scenario.onActivity { activity -> activity.longClickFirstItem() }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.firstItemView().performClick() }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.firstItemSelected().shouldBeTrue() }
            scenario.onActivity { activity -> activity.firstItemView().performClick() }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.firstItemSelected().shouldBeFalse() }
        }
    }

    @Test
    fun `onClickItemFavorite toggles the favorite flag via the view model`() {
        val url = urlRepository.seedUrl("https://da.gd/fav1", favorite = false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.firstItemView().findViewById<View>(R.id.listItemFav).performClick() }
            awaitMainIdle()
            runBlocking { urlRepository.getURL(url.shortURL)?.favorite } shouldBe true
        }
    }

    @Test
    fun `configureItemSwipeAnimator swipe start adds the item to favorites`() {
        val url = urlRepository.seedUrl("https://da.gd/swipestart1", favorite = false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.swipeFirstItem(ItemTouchHelper.START) }
            awaitMainIdle()
            runBlocking { urlRepository.getURL(url.shortURL)?.favorite } shouldBe true
        }
    }

    @Test
    fun `configureItemSwipeAnimator swipe end removes the item from favorites`() {
        val url = urlRepository.seedUrl("https://da.gd/swipeend1", favorite = true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.swipeFirstItem(ItemTouchHelper.END) }
            awaitMainIdle()
            runBlocking { urlRepository.getURL(url.shortURL)?.favorite } shouldBe false
        }
    }

    @Test
    fun `launchActionMode onSelectAll selects and unselects every item`() {
        urlRepository.seedUrl("https://da.gd/selectall1")
        urlRepository.seedUrl("https://da.gd/selectall2")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.longClickFirstItem() }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.selectAllView().performClick() }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.itemsSelected() shouldBe listOf(true, true) }
            scenario.onActivity { activity -> activity.selectAllView().performClick() }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.itemsSelected() shouldBe listOf(false, false) }
        }
    }

    @Test
    fun `launchActionMode onEnd while not in search mode leaves the fab shown`() {
        urlRepository.seedUrl("https://da.gd/endaction1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.longClickFirstItem() }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).endActionMode() }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.findViewById<View>(R.id.addFab).isVisible.shouldBeTrue() }
        }
    }

    @Test
    fun `launchActionMode onSelectMenuItem delete removes the selected urls`() {
        val url = urlRepository.seedUrl("https://da.gd/delete1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.selectFirstItemInActionMode() }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.clickActionModeMenuItem(R.id.menu_item_delete) }
            awaitMainIdle()
            runBlocking { urlRepository.getURL(url.shortURL) } shouldBe null
        }
    }

    @Test
    fun `launchActionMode onSelectMenuItem add-to-favorites favorites the selected urls`() {
        val url = urlRepository.seedUrl("https://da.gd/bulkfav1", favorite = false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.selectFirstItemInActionMode() }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.clickActionModeMenuItem(R.id.menu_item_add_to_favorites) }
            awaitMainIdle()
            runBlocking { urlRepository.getURL(url.shortURL)?.favorite } shouldBe true
        }
    }

    @Test
    fun `launchActionMode onSelectMenuItem remove-from-favorites unfavorites the selected urls`() {
        val url = urlRepository.seedUrl("https://da.gd/bulkunfav1", favorite = true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.selectFirstItemInActionMode() }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.clickActionModeMenuItem(R.id.menu_item_remove_from_favorites) }
            awaitMainIdle()
            runBlocking { urlRepository.getURL(url.shortURL)?.favorite } shouldBe false
        }
    }

    @Test
    fun `launchActionMode onSelectMenuItem unmapped item is not handled and action mode stays open`() {
        urlRepository.seedUrl("https://da.gd/unmappedaction1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.selectFirstItemInActionMode() }
            awaitMainIdle()
            scenario.onActivity { activity ->
                val menu =
                    activity
                        .findViewById<NavDrawerLayout>(R.id.drawerLayout)
                        .bottomActionModeBar()
                        .menu
                menu.add(Menu.NONE, UNMAPPED_ACTION_ITEM_ID, Menu.NONE, "unmapped")
                menu.performIdentifierAction(UNMAPPED_ACTION_ITEM_ID, 0)
            }
            awaitMainIdle()
            scenario.onActivity { activity ->
                activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).isActionMode.shouldBeTrue()
            }
        }
    }

    @Test
    fun `block multi-selection outside action mode starts it via onBlockActionMode`() {
        urlRepository.seedUrl("https://da.gd/blockselect1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity ->
                val recycler = activity.findViewById<RecyclerView>(R.id.urlList)
                val listener = checkNotNull(recycler.seslGetOnMultiSelectedListener())
                listener.onMultiSelectStart(1, 1)
                listener.onMultiSelectStop(1, 1)
            }
            awaitMainIdle()
            scenario.onActivity { activity ->
                activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).isActionMode.shouldBeTrue()
            }
        }
    }

    // The bottom action-mode menu (a plain, unnamed BottomNavigationView added programmatically to
    // the footer) is a private field on ToolbarLayout, a superclass of NavDrawerLayout - walk up the
    // hierarchy since Java reflection's getDeclaredField only searches the exact class it's called on.
    private fun NavDrawerLayout.bottomActionModeBar(): BottomNavigationView {
        var cls: Class<*> = javaClass
        while (true) {
            try {
                return cls
                    .getDeclaredField("bottomActionModeBar")
                    .apply { isAccessible = true }
                    .get(this) as BottomNavigationView
            } catch (e: NoSuchFieldException) {
                cls = cls.superclass ?: throw e
            }
        }
    }

    // SelectableLinearLayout overrides View.setSelected to drive its own checkbox/highlight
    // instead of the base isSelected flag (setSelectedAnimate never calls super.setSelected), so
    // the outer layout's own isSelected getter never reflects selection - listview_item.xml uses
    // checkMode="overlayCircle" with targetImage=listItemImg, so that ImageView's own (real,
    // non-overridden) isSelected is what setSelectedAnimate actually flips.
    private fun MainActivity.firstItemSelected(): Boolean = firstItemView().findViewById<View>(R.id.listItemImg).isSelected

    private fun MainActivity.itemsSelected(): List<Boolean> {
        val recycler = findViewById<RecyclerView>(R.id.urlList)
        return List(recycler.adapter!!.itemCount) { position ->
            recycler
                .findViewHolderForAdapterPosition(position)!!
                .itemView
                .findViewById<View>(R.id.listItemImg)
                .isSelected
        }
    }

    private fun MainActivity.selectAllView(): View = findViewById(designR.id.toolbarlayout_selectall)

    private fun MainActivity.selectFirstItemInActionMode() {
        longClickFirstItem()
        shadowOf(Looper.getMainLooper()).idle()
        firstItemView().performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun MainActivity.clickActionModeMenuItem(
        @IdRes itemId: Int,
    ) {
        val bottomBarItem =
            (window.decorView as ViewGroup).descendants.firstOrNull { it.id == itemId }
                ?: error("Action mode menu item $itemId not found - ensure at least one item is selected first")
        bottomBarItem.performClick()
    }

    private fun MainActivity.swipeFirstItem(direction: Int) {
        val recycler = findViewById<RecyclerView>(R.id.urlList)
        val viewHolder = recycler.findViewHolderForAdapterPosition(0)!!
        val callback = recycler.itemTouchCallback()
        callback.getMovementFlags(recycler, viewHolder)
        callback.onSelectedChanged(viewHolder, ItemTouchHelper.ACTION_STATE_SWIPE)
        callback.onSwiped(viewHolder, direction)
    }

    // SESL's ItemTouchHelper.attachToRecyclerView registers a private anonymous
    // OnItemTouchListener field (not `this`), so RecyclerView.mOnItemTouchListeners never holds an
    // ItemTouchHelper instance directly - reach the owning helper through the listener's synthetic
    // outer-class reference instead.
    private fun RecyclerView.itemTouchCallback(): ItemTouchHelper.Callback {
        val listeners =
            RecyclerView::class.java
                .getDeclaredField("mOnItemTouchListeners")
                .apply { isAccessible = true }
                .get(this) as List<*>
        val listener = checkNotNull(listeners.firstOrNull())
        val helper =
            listener.javaClass
                .getDeclaredField("this\$0")
                .apply { isAccessible = true }
                .get(listener) as ItemTouchHelper
        return ItemTouchHelper::class.java
            .getDeclaredField("mCallback")
            .apply { isAccessible = true }
            .get(helper) as ItemTouchHelper.Callback
    }

    private companion object {
        const val UNMAPPED_ACTION_ITEM_ID = 987654322
    }
}
