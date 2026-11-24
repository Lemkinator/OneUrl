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
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.idleMainLooper
import de.lemke.oneurl.R
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.ui.ShortURLQRCodeViewModel.Companion.KEY_SHORT_URL
import dev.oneuiproject.oneui.layout.NavDrawerLayout
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import java.time.Duration
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
    val hiltRule = HiltTestRule(this)

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
            idleMainLooper()
            scenario.onActivity { activity -> activity.firstItemView().performClick() }
            idleMainLooper()
            scenario.onActivity { activity ->
                val started = shadowOf(activity).nextStartedActivity
                started.component?.className shouldBe URLActivity::class.java.name
                started.getStringExtra(KEY_SHORT_URL) shouldBe url.shortURL
            }
        }
    }

    @Test
    fun `double tap on a row opens one URLActivity`() {
        urlRepository.seedUrl("https://da.gd/opentwice1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.firstItemView().performClick()
                activity.firstItemView().performClick()
            }
            idleMainLooper()
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
            idleMainLooper()
            scenario.onActivity { activity -> activity.longClickFirstItem() }
            idleMainLooper()
            scenario.onActivity { activity -> activity.findViewById<View>(R.id.addFab).isVisible.shouldBeFalse() }
            scenario.onActivity { activity -> activity.longClickFirstItem() }
            idleMainLooper()
            scenario.onActivity { activity -> activity.firstItemView().performClick() }
            idleMainLooper()
            scenario.onActivity { activity -> activity.firstItemSelected().shouldBeTrue() }
            scenario.onActivity { activity -> activity.firstItemView().performClick() }
            idleMainLooper()
            scenario.onActivity { activity -> activity.firstItemSelected().shouldBeFalse() }
        }
    }

    @Test
    fun `onClickItemFavorite toggles the favorite flag via the view model`() {
        val url = urlRepository.seedUrl("https://da.gd/fav1", favorite = false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity -> activity.firstItemView().findViewById<View>(R.id.listItemFav).performClick() }
            idleMainLooper()
            runBlocking { urlRepository.getURL(url.shortURL)?.favorite } shouldBe true
        }
    }

    @Test
    fun `configureItemSwipeAnimator swipe start adds the item to favorites`() {
        val url = urlRepository.seedUrl("https://da.gd/swipestart1", favorite = false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            onView(withText("https://da.gd/swipestart1")).perform(swipeLeft())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            runBlocking { urlRepository.getURL(url.shortURL)?.favorite } shouldBe true
        }
    }

    @Test
    fun `configureItemSwipeAnimator swipe end removes the item from favorites`() {
        val url = urlRepository.seedUrl("https://da.gd/swipeend1", favorite = true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            onView(withText("https://da.gd/swipeend1")).perform(swipeRight())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            runBlocking { urlRepository.getURL(url.shortURL)?.favorite } shouldBe false
        }
    }

    @Test
    fun `launchActionMode onSelectAll selects and unselects every item`() {
        urlRepository.seedUrl("https://da.gd/selectall1")
        urlRepository.seedUrl("https://da.gd/selectall2")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity -> activity.longClickFirstItem() }
            idleMainLooper()
            scenario.onActivity { activity -> activity.selectAllView().performClick() }
            idleMainLooper()
            scenario.onActivity { activity -> activity.itemsSelected() shouldBe listOf(true, true) }
            scenario.onActivity { activity -> activity.selectAllView().performClick() }
            idleMainLooper()
            scenario.onActivity { activity -> activity.itemsSelected() shouldBe listOf(false, false) }
        }
    }

    @Test
    fun `launchActionMode onEnd while not in search mode leaves the fab shown`() {
        urlRepository.seedUrl("https://da.gd/endaction1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity -> activity.longClickFirstItem() }
            idleMainLooper()
            scenario.onActivity { activity -> activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).endActionMode() }
            idleMainLooper()
            scenario.onActivity { activity -> activity.findViewById<View>(R.id.addFab).isVisible.shouldBeTrue() }
        }
    }

    @Test
    fun `ending action mode while search mode is active keeps the fab hidden`() {
        urlRepository.seedUrl("https://da.gd/actionduringsearch1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity -> activity.onOptionsItemSelected(menuItem(R.id.menu_item_search)) }
            idleMainLooper()
            scenario.onActivity { activity -> activity.searchView().setQuery("da.gd", false) }
            idleMainLooper()
            scenario.onActivity { activity -> activity.longClickFirstItem() }
            idleMainLooper()
            scenario.onActivity { activity ->
                val drawerLayout = activity.findViewById<NavDrawerLayout>(R.id.drawerLayout)
                "search=${drawerLayout.isSearchMode} action=${drawerLayout.isActionMode}" shouldBe "search=true action=true"
                drawerLayout.endActionMode()
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                val drawerLayout = activity.findViewById<NavDrawerLayout>(R.id.drawerLayout)
                "search=${drawerLayout.isSearchMode} action=${drawerLayout.isActionMode}" shouldBe "search=true action=false"
                activity.findViewById<View>(R.id.addFab).isVisible.shouldBeFalse()
            }
        }
    }

    @Test
    fun `ending search mode while action mode is active keeps the fab hidden`() {
        urlRepository.seedUrl("https://da.gd/searchduringaction1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity -> activity.onOptionsItemSelected(menuItem(R.id.menu_item_search)) }
            idleMainLooper()
            scenario.onActivity { activity -> activity.searchView().setQuery("da.gd", false) }
            idleMainLooper()
            scenario.onActivity { activity -> activity.longClickFirstItem() }
            idleMainLooper()
            scenario.onActivity { activity ->
                val drawerLayout = activity.findViewById<NavDrawerLayout>(R.id.drawerLayout)
                drawerLayout.endSearchMode()
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                val drawerLayout = activity.findViewById<NavDrawerLayout>(R.id.drawerLayout)
                "search=${drawerLayout.isSearchMode} action=${drawerLayout.isActionMode}" shouldBe "search=false action=true"
                activity.findViewById<View>(R.id.addFab).isVisible.shouldBeFalse()
            }
        }
    }

    @Test
    fun `launchActionMode onSelectMenuItem delete removes the selected urls`() {
        val url = urlRepository.seedUrl("https://da.gd/delete1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity -> activity.selectFirstItemInActionMode() }
            idleMainLooper()
            scenario.onActivity { activity -> activity.clickActionModeMenuItem(R.id.menu_item_delete) }
            idleMainLooper()
            runBlocking { urlRepository.getURL(url.shortURL) } shouldBe null
        }
    }

    @Test
    fun `launchActionMode onSelectMenuItem add-to-favorites favorites the selected urls`() {
        val url = urlRepository.seedUrl("https://da.gd/bulkfav1", favorite = false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity -> activity.selectFirstItemInActionMode() }
            idleMainLooper()
            scenario.onActivity { activity -> activity.clickActionModeMenuItem(R.id.menu_item_add_to_favorites) }
            idleMainLooper()
            runBlocking { urlRepository.getURL(url.shortURL)?.favorite } shouldBe true
        }
    }

    @Test
    fun `launchActionMode onSelectMenuItem remove-from-favorites unfavorites the selected urls`() {
        val url = urlRepository.seedUrl("https://da.gd/bulkunfav1", favorite = true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity -> activity.selectFirstItemInActionMode() }
            idleMainLooper()
            scenario.onActivity { activity -> activity.clickActionModeMenuItem(R.id.menu_item_remove_from_favorites) }
            idleMainLooper()
            runBlocking { urlRepository.getURL(url.shortURL)?.favorite } shouldBe false
        }
    }

    @Test
    fun `launchActionMode onSelectMenuItem unmapped item is not handled and action mode stays open`() {
        urlRepository.seedUrl("https://da.gd/unmappedaction1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity -> activity.selectFirstItemInActionMode() }
            idleMainLooper()
            scenario.onActivity { activity ->
                val menu = activity.bottomActionModeBar().menu
                menu.add(Menu.NONE, UNMAPPED_ACTION_ITEM_ID, Menu.NONE, "unmapped")
                menu.performIdentifierAction(UNMAPPED_ACTION_ITEM_ID, 0)
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).isActionMode.shouldBeTrue()
            }
        }
    }

    @Test
    fun `block multi-selection outside action mode starts it via onBlockActionMode`() {
        urlRepository.seedUrl("https://da.gd/blockselect1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                val recycler = activity.findViewById<RecyclerView>(R.id.urlList)
                val listener = checkNotNull(recycler.seslGetOnMultiSelectedListener())
                listener.onMultiSelectStart(1, 1)
                listener.onMultiSelectStop(1, 1)
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).isActionMode.shouldBeTrue()
            }
        }
    }

    private fun MainActivity.bottomActionModeBar(): BottomNavigationView =
        findViewById<ViewGroup>(designR.id.tbl_footer_parent).descendants.filterIsInstance<BottomNavigationView>().single()

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
        idleMainLooper()
        firstItemView().performClick()
        idleMainLooper()
    }

    private fun MainActivity.clickActionModeMenuItem(
        @IdRes itemId: Int,
    ) {
        val bottomBarItem =
            (window.decorView as ViewGroup).descendants.firstOrNull { it.id == itemId }
                ?: error("Action mode menu item $itemId not found - ensure at least one item is selected first")
        bottomBarItem.performClick()
    }

    private companion object {
        const val UNMAPPED_ACTION_ITEM_ID = 987654322
    }
}
