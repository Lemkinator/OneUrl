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

import android.app.SearchManager
import android.content.Intent
import android.content.Intent.ACTION_PROCESS_TEXT
import android.content.Intent.ACTION_SEND
import android.content.Intent.EXTRA_PROCESS_TEXT
import android.content.Intent.EXTRA_TEXT
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.descendants
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutMeActivity
import de.lemke.commonutils.ui.activity.CommonUtilsSettingsActivity
import de.lemke.commonutils.ui.utils.COMMONUTILS_KEY_IS_SEARCH_MODE
import de.lemke.commonutils.ui.widget.NoEntryView
import de.lemke.oneurl.BuildConfig
import de.lemke.oneurl.R
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.ObserveURLsUseCase
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_SHORTURL
import dev.oneuiproject.oneui.layout.NavDrawerLayout
import dev.oneuiproject.oneui.navigation.widget.DrawerNavigationView
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import leakcanary.AppWatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import androidx.appcompat.R as appcompatR
import de.lemke.commonutils.R as commonutilsR
import dev.oneuiproject.oneui.design.R as designR

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
@Suppress("LargeClass")
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class MainActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // Default answer delegates to a real ObserveURLsUseCase backed by the injected repository, so
    // every test other than the isUIReady ones below gets full real search/filterFavorite
    // reactivity. Only overridden where the emission itself (not its content) is what's under test.
    @BindValue
    @JvmField
    val observeURLsStub: ObserveURLsUseCase = mockk()

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var urlRepository: URLRepository

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        every { observeURLsStub(any(), any()) } answers {
            ObserveURLsUseCase(urlRepository, Dispatchers.Unconfined).invoke(firstArg(), secondArg())
        }
        if (!AppWatcher.isInstalled) {
            AppWatcher.manualInstall(ApplicationProvider.getApplicationContext<HiltTestApplication>())
        }
    }

    // onCreate / onboarding

    @Test
    fun `onCreate onboarding required finishes the activity`() {
        settings.lastVersionCode = -1
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.state shouldBe Lifecycle.State.DESTROYED
        }
    }

    // onSaveInstanceState

    @Test
    fun `onSaveInstanceState without initialized binding returns early`() {
        settings.lastVersionCode = -1
        val controller =
            Robolectric
                .buildActivity(MainActivity::class.java)
                .create()
                .start()
                .resume()
        try {
            val outState = Bundle()
            controller.pause().saveInstanceState(outState)
            outState.containsKey(COMMONUTILS_KEY_IS_SEARCH_MODE).shouldBeFalse()
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `onSaveInstanceState with initialized binding restores search mode`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.onOptionsItemSelected(menuItem(R.id.menu_item_search)) }
            awaitMainIdle()
            scenario.recreate()
            scenario.onActivity { activity ->
                activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).isSearchMode.shouldBeTrue()
            }
        }
    }

    // onNewIntent

    @Test
    fun `onNewIntent action search sets the query on the active search view`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.onOptionsItemSelected(menuItem(R.id.menu_item_search))
            awaitMainIdle()
            controller.newIntent(Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "sometext"))
            awaitMainIdle()
            activity.findViewById<android.widget.TextView>(appcompatR.id.search_src_text).text.toString() shouldBe "sometext"
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `onNewIntent non search action does not start search mode`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            controller.newIntent(Intent("some.other.action"))
            awaitMainIdle()
            activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).isSearchMode.shouldBeFalse()
        } finally {
            controller.destroy()
        }
    }

    // onCreateOptionsMenu / onPrepareOptionsMenu / onOptionsItemSelected

    @Test
    fun `onPrepareOptionsMenu shows only-show-favorites item while filter is off`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity ->
                val menu = activity.mainToolbarMenu()
                menu.findItem(R.id.menu_item_show_all).isVisible.shouldBeFalse()
                menu.findItem(R.id.menu_item_only_show_favorites).isVisible.shouldBeTrue()
            }
        }
    }

    @Test
    fun `onPrepareOptionsMenu with a null menu returns early without crashing`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.onPrepareOptionsMenu(null).shouldBeTrue() }
        }
    }

    @Test
    fun `onPrepareOptionsMenu with a menu missing the filter items does not crash`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity ->
                val menu = mockk<Menu> { every { findItem(any()) } returns null }
                activity.onPrepareOptionsMenu(menu).shouldBeTrue()
            }
        }
    }

    @Test
    fun `onOptionsItemSelected toggles filterFavorite and flips the menu visibility both ways`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.onOptionsItemSelected(menuItem(R.id.menu_item_only_show_favorites)).shouldBeTrue() }
            advanceClockPastDebounce()
            scenario.onActivity { activity ->
                val menu = activity.mainToolbarMenu()
                menu.findItem(R.id.menu_item_show_all).isVisible.shouldBeTrue()
                menu.findItem(R.id.menu_item_only_show_favorites).isVisible.shouldBeFalse()
            }
            scenario.onActivity { activity -> activity.onOptionsItemSelected(menuItem(R.id.menu_item_show_all)).shouldBeTrue() }
            advanceClockPastDebounce()
            scenario.onActivity { activity ->
                val menu = activity.mainToolbarMenu()
                menu.findItem(R.id.menu_item_show_all).isVisible.shouldBeFalse()
                menu.findItem(R.id.menu_item_only_show_favorites).isVisible.shouldBeTrue()
            }
        }
    }

    @Test
    fun `onOptionsItemSelected search item starts search mode`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(menuItem(R.id.menu_item_search)).shouldBeTrue()
                activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).isSearchMode.shouldBeTrue()
            }
        }
    }

    @Test
    fun `onOptionsItemSelected unknown item delegates to super and returns false`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(menuItem(Menu.NONE)).shouldBeFalse()
            }
        }
    }

    // checkIntent

    @Test
    fun `checkIntent action send with text opens AddURLActivity with the shared text`() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                .setAction(ACTION_SEND)
                .setType("text/plain")
                .putExtra(EXTRA_TEXT, "https://example.com/shared")
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity ->
                val started = shadowOf(activity).nextStartedActivity
                started.component?.className shouldBe AddURLActivity::class.java.name
                started.getStringExtra("url") shouldBe "https://example.com/shared"
            }
        }
    }

    @Test
    fun `checkIntent action send with blank text does nothing`() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                .setAction(ACTION_SEND)
                .setType("text/plain")
                .putExtra(EXTRA_TEXT, "   ")
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> shadowOf(activity).nextStartedActivity shouldBe null }
        }
    }

    @Test
    fun `checkIntent action process text with text opens AddURLActivity with the selected text`() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                .setAction(ACTION_PROCESS_TEXT)
                .putExtra(EXTRA_PROCESS_TEXT, "https://example.com/selected")
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity ->
                val started = shadowOf(activity).nextStartedActivity
                started.component?.className shouldBe AddURLActivity::class.java.name
                started.getStringExtra("url") shouldBe "https://example.com/selected"
            }
        }
    }

    @Test
    fun `checkIntent action process text with blank text does nothing`() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                .setAction(ACTION_PROCESS_TEXT)
                .putExtra(EXTRA_PROCESS_TEXT, "   ")
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> shadowOf(activity).nextStartedActivity shouldBe null }
        }
    }

    @Test
    fun `checkIntent action send with a non text-plain type does nothing`() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                .setAction(ACTION_SEND)
                .setType("image/png")
                .putExtra(EXTRA_TEXT, "https://example.com/wrong-type")
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> shadowOf(activity).nextStartedActivity shouldBe null }
        }
    }

    @Test
    fun `checkIntent action send without extra text does nothing`() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                .setAction(ACTION_SEND)
                .setType("text/plain")
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> shadowOf(activity).nextStartedActivity shouldBe null }
        }
    }

    @Test
    fun `checkIntent action process text without extra does nothing`() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                .setAction(ACTION_PROCESS_TEXT)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> shadowOf(activity).nextStartedActivity shouldBe null }
        }
    }

    // startSearch

    @Test
    fun `startSearch onStart hides the fab and pre-fills the query from settings`() {
        settings.search = "prefill"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(menuItem(R.id.menu_item_search))
            }
            awaitMainIdle()
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.addFab).isVisible.shouldBeFalse()
                activity.searchView().query.toString() shouldBe "prefill"
            }
        }
    }

    @Test
    fun `startSearch onQuery non-submit updates settings and view model search`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.onOptionsItemSelected(menuItem(R.id.menu_item_search)) }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.searchView().setQuery("abc", false) }
            awaitMainIdle()
            settings.search shouldBe "abc"
        }
    }

    @Test
    fun `startSearch onQuery submit updates settings and view model search`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.onOptionsItemSelected(menuItem(R.id.menu_item_search)) }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.searchView().setQuery("xyz", true) }
            awaitMainIdle()
            settings.search shouldBe "xyz"
        }
    }

    @Test
    fun `startSearch onEnd without action mode shows the fab again`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.onOptionsItemSelected(menuItem(R.id.menu_item_search)) }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.findViewById<NavDrawerLayout>(R.id.drawerLayout).endSearchMode() }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.findViewById<View>(R.id.addFab).isVisible.shouldBeTrue() }
        }
    }

    // ToolbarLayout.startSearchMode ends any active action mode first (searchOnActionMode here is
    // NoDismiss, not Concurrent) - starting search from action mode surfaces the fab immediately,
    // via action mode's own onEnd, rather than deferring to when search itself ends.
    @Test
    fun `startSearch from action mode ends it and shows the fab immediately`() {
        seedUrl("https://da.gd/searchend1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.longClickFirstItem() }
            advanceClockPastDebounce()
            scenario.onActivity { activity ->
                val drawerLayout = activity.findViewById<NavDrawerLayout>(R.id.drawerLayout)
                drawerLayout.isActionMode.shouldBeTrue()
                activity.findViewById<View>(R.id.addFab).isVisible.shouldBeFalse()

                activity.onOptionsItemSelected(menuItem(R.id.menu_item_search))
            }
            advanceClockPastDebounce()
            scenario.onActivity { activity ->
                val drawerLayout = activity.findViewById<NavDrawerLayout>(R.id.drawerLayout)
                drawerLayout.isActionMode.shouldBeFalse()
                drawerLayout.isSearchMode.shouldBeTrue()

                drawerLayout.endSearchMode()
            }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.findViewById<View>(R.id.addFab).isVisible.shouldBeTrue() }
        }
    }

    // initDrawer

    @Test
    fun `initDrawer leaks menu item visibility matches debug build config`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity
                    .findViewById<DrawerNavigationView>(R.id.navigationView)
                    .findMenuItem(R.id.leaks_dest)
                    ?.isVisible shouldBe BuildConfig.DEBUG
            }
        }
    }

    @Test
    fun `initDrawer skips setting leaks item visibility when the item is not present in the navigation menu`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity ->
                val navigationView = activity.findViewById<DrawerNavigationView>(R.id.navigationView)
                navigationView.drawerMenu().removeItem(R.id.leaks_dest)
                activity.callInitDrawer()
                navigationView.findMenuItem(R.id.leaks_dest).shouldBeNull()
            }
            advanceClockPastDebounce()
            scenario.onActivity { activity ->
                activity.findViewById<DrawerNavigationView>(R.id.navigationView).drawerMenu().performIdentifierAction(R.id.qr_code_dest, 0)
            }
            awaitMainIdle()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity?.component?.className shouldBe GenerateQRCodeActivity::class.java.name
            }
        }
    }

    @Test
    fun `navigation item qr_code_dest opens GenerateQRCodeActivity`() =
        assertNavItemStarts(R.id.qr_code_dest, GenerateQRCodeActivity::class.java.name)

    @Test
    fun `navigation item provider_dest opens ProviderActivity`() =
        assertNavItemStarts(R.id.provider_dest, ProviderActivity::class.java.name)

    @Test
    fun `navigation item help_dest opens HelpActivity`() = assertNavItemStarts(R.id.help_dest, HelpActivity::class.java.name)

    @Test
    fun `navigation item about_app_dest opens CommonUtilsAboutActivity`() =
        assertNavItemStarts(R.id.about_app_dest, CommonUtilsAboutActivity::class.java.name)

    @Test
    fun `navigation item about_me_dest opens CommonUtilsAboutMeActivity`() =
        assertNavItemStarts(R.id.about_me_dest, CommonUtilsAboutMeActivity::class.java.name)

    @Test
    fun `navigation item settings_dest opens CommonUtilsSettingsActivity`() =
        assertNavItemStarts(R.id.settings_dest, CommonUtilsSettingsActivity::class.java.name)

    @Test
    fun `navigation item leaks_dest opens leak canary`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            advanceClockPastDebounce()
            scenario.onActivity { activity ->
                activity.findViewById<DrawerNavigationView>(R.id.navigationView).drawerMenu().performIdentifierAction(R.id.leaks_dest, 0)
            }
            awaitMainIdle()
            scenario.onActivity { activity ->
                shadowOf(activity)
                    .nextStartedActivity
                    ?.component
                    ?.className
                    ?.contains("leakcanary", ignoreCase = true)
                    .shouldBeTrue()
            }
        }
    }

    @Test
    fun `navigation item unmapped id is not handled and starts nothing`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            advanceClockPastDebounce()
            scenario.onActivity { activity ->
                val menu = activity.findViewById<DrawerNavigationView>(R.id.navigationView).drawerMenu()
                menu.add(Menu.NONE, UNMAPPED_NAV_ITEM_ID, Menu.NONE, "unmapped")
                menu.performIdentifierAction(UNMAPPED_NAV_ITEM_ID, 0)
            }
            awaitMainIdle()
            scenario.onActivity { activity -> shadowOf(activity).nextStartedActivity shouldBe null }
        }
    }

    // initRecycler

    @Test
    @Config(sdk = [29])
    fun `initRecycler below API R skips imm bottom padding`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.state shouldBe Lifecycle.State.RESUMED
            scenario.onActivity { activity ->
                activity
                    .findViewById<RecyclerView>(R.id.urlList)
                    .getTag(designR.id.tag_rv_imm_bottom_padding_listener) shouldBe null
            }
        }
    }

    // updateRecyclerView

    @Test
    fun `updateRecyclerView shows no-urls message when the list is empty`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity ->
                activity.noEntryView().text shouldBe activity.getString(R.string.no_urls)
            }
        }
    }

    @Test
    fun `updateRecyclerView shows no-results message when searching with no matches`() {
        seedUrl("https://da.gd/searchnomatch1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.onOptionsItemSelected(menuItem(R.id.menu_item_search)) }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.searchView().setQuery("no-such-query-matches-anything", false) }
            awaitMainIdle()
            scenario.onActivity { activity ->
                activity.noEntryView().text shouldBe activity.getString(commonutilsR.string.commonutils_no_results_found)
            }
        }
    }

    @Test
    fun `updateRecyclerView shows no-favorites message when filtering with no favorites`() {
        seedUrl("https://da.gd/nofav1", favorite = false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.onOptionsItemSelected(menuItem(R.id.menu_item_only_show_favorites)) }
            awaitMainIdle()
            scenario.onActivity { activity ->
                activity.noEntryView().text shouldBe activity.getString(R.string.no_favorite_urls)
            }
        }
    }

    @Test
    fun `updateRecyclerView clears the message and shows the list when non-empty`() {
        seedUrl("https://da.gd/listed1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity ->
                activity.noEntryView().text shouldBe ""
                activity.findViewById<RecyclerView>(R.id.urlList).adapter?.itemCount shouldBe 1
            }
        }
    }

    // collectEvents

    @Test
    fun `collectEvents NewItemAdded scrolls the list back to the top`() {
        repeat(PRESCROLL_ITEM_COUNT) { seedUrl("https://da.gd/bulk$it") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity ->
                val recycler = activity.findViewById<RecyclerView>(R.id.urlList)
                recycler.scrollToPosition(PRESCROLL_ITEM_COUNT - 1)
            }
            awaitMainIdle()
            scenario.onActivity { activity ->
                val layoutManager = activity.findViewById<RecyclerView>(R.id.urlList).layoutManager as LinearLayoutManager
                layoutManager.findFirstVisibleItemPosition() shouldNotBe 0
            }
            runBlocking { urlRepository.addURL(testUrl("https://da.gd/newest")) }
            awaitMainIdle()
            scenario.onActivity { activity ->
                val recycler = activity.findViewById<RecyclerView>(R.id.urlList)
                recycler.adapter?.itemCount shouldBe PRESCROLL_ITEM_COUNT + 1
                val layoutManager = recycler.layoutManager as LinearLayoutManager
                layoutManager.findFirstVisibleItemPosition() shouldBe 0
            }
        }
    }

    // collectState (isUIReady early return)

    // common-utils' configureCommonUtilsSplashScreen keeps the real splash screen on-screen via a
    // pre-draw block for as long as !isUIReady; ActivityScenario.launch's visible() transition
    // idles the main looper until that resolves, which never happens with an ever-empty flow -
    // build the activity without a visible() window (no real traversal/splash loop) instead.
    @Test
    fun `collectState returns early while the view model is not ui-ready`() {
        every { observeURLsStub(any(), any()) } returns emptyFlow()
        val controller =
            Robolectric
                .buildActivity(MainActivity::class.java)
                .create()
                .start()
                .resume()
        try {
            awaitMainIdle()
            // updateRecyclerView (which would overwrite this) never runs while isUIReady is false,
            // so noEntryView keeps widget_no_entry_view.xml's own design-time placeholder text.
            controller.get().noEntryView().text shouldBe controller.get().getString(commonutilsR.string.commonutils_no_results_found)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    // setupOnClickListeners

    @Test
    fun `onClickItem without action mode opens URLActivity for that url`() {
        val url = seedUrl("https://da.gd/openme1")
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
    fun `onClickItem and onLongClickItem toggle selection while in action mode`() {
        seedUrl("https://da.gd/select1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.longClickFirstItem() }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.findViewById<View>(R.id.addFab).isVisible.shouldBeFalse() }
            // Long-clicking again while already in action mode must not crash or relaunch it.
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
        val url = seedUrl("https://da.gd/fav1", favorite = false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.firstItemView().findViewById<View>(R.id.listItemFav).performClick() }
            awaitMainIdle()
            runBlocking { urlRepository.getURL(url.shortURL)?.favorite } shouldBe true
        }
    }

    // configureItemSwipeAnimator

    @Test
    fun `configureItemSwipeAnimator swipe start adds the item to favorites`() {
        val url = seedUrl("https://da.gd/swipestart1", favorite = false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.swipeFirstItem(ItemTouchHelper.START) }
            awaitMainIdle()
            runBlocking { urlRepository.getURL(url.shortURL)?.favorite } shouldBe true
        }
    }

    @Test
    fun `configureItemSwipeAnimator swipe end removes the item from favorites`() {
        val url = seedUrl("https://da.gd/swipeend1", favorite = true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.swipeFirstItem(ItemTouchHelper.END) }
            awaitMainIdle()
            runBlocking { urlRepository.getURL(url.shortURL)?.favorite } shouldBe false
        }
    }

    // launchActionMode

    @Test
    fun `launchActionMode onSelectAll selects and unselects every item`() {
        seedUrl("https://da.gd/selectall1")
        seedUrl("https://da.gd/selectall2")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.longClickFirstItem() }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.selectAllView().performClick() }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.firstItemSelected().shouldBeTrue() }
            scenario.onActivity { activity -> activity.selectAllView().performClick() }
            awaitMainIdle()
            scenario.onActivity { activity -> activity.firstItemSelected().shouldBeFalse() }
        }
    }

    @Test
    fun `launchActionMode onEnd while not in search mode leaves the fab shown`() {
        seedUrl("https://da.gd/endaction1")
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
        val url = seedUrl("https://da.gd/delete1")
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
        val url = seedUrl("https://da.gd/bulkfav1", favorite = false)
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
        val url = seedUrl("https://da.gd/bulkunfav1", favorite = true)
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
        seedUrl("https://da.gd/unmappedaction1")
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

    // urlAdapter (block multi-selection via S-Pen/mouse drag, bypassing onLongClickItem entirely)

    @Test
    fun `block multi-selection outside action mode starts it via onBlockActionMode`() {
        seedUrl("https://da.gd/blockselect1")
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

    // Helpers

    private fun assertNavItemStarts(
        @IdRes navItemId: Int,
        expectedClassName: String,
    ) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            advanceClockPastDebounce()
            scenario.onActivity { activity ->
                activity.findViewById<DrawerNavigationView>(R.id.navigationView).drawerMenu().performIdentifierAction(navItemId, 0)
            }
            awaitMainIdle()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity?.component?.className shouldBe expectedClassName
            }
        }
    }

    private fun MainActivity.searchView(): SearchView = (window.decorView as ViewGroup).descendants.filterIsInstance<SearchView>().first()

    private fun MainActivity.callInitDrawer() {
        MainActivity::class.java
            .getDeclaredMethod("initDrawer")
            .apply { isAccessible = true }
            .invoke(this)
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

    // NavDrawerLayout wires setSupportActionBar to its own internal Toolbar rather than the
    // window's native action bar, so shadowOf(activity).optionsMenu (which shadows the framework
    // panel-menu path) never populates - read the real Toolbar's own Menu instead.
    private fun MainActivity.mainToolbarMenu(): Menu = findViewById<Toolbar>(designR.id.toolbarlayout_main_toolbar).menu

    private fun MainActivity.noEntryView(): NoEntryView = findViewById(R.id.noEntryView)

    private fun MainActivity.firstItemView(): View = findViewById<RecyclerView>(R.id.urlList).findViewHolderForAdapterPosition(0)!!.itemView

    // SelectableLinearLayout overrides View.setSelected to drive its own checkbox/highlight
    // instead of the base isSelected flag (setSelectedAnimate never calls super.setSelected), so
    // the outer layout's own isSelected getter never reflects selection - listview_item.xml uses
    // checkMode="overlayCircle" with targetImage=listItemImg, so that ImageView's own (real,
    // non-overridden) isSelected is what setSelectedAnimate actually flips.
    private fun MainActivity.firstItemSelected(): Boolean = firstItemView().findViewById<View>(R.id.listItemImg).isSelected

    private fun MainActivity.selectAllView(): View = findViewById(designR.id.toolbarlayout_selectall)

    // seslStartLongPressMultiSelection (invoked from onLongClickItem) needs
    // RecyclerView.mPenDragSelectedItemArray, which SESL only lazily initializes from a real
    // dispatchTouchEvent(ACTION_DOWN) - a bare performLongClick() skips that and NPEs.
    private fun MainActivity.longClickFirstItem() {
        val recycler = findViewById<RecyclerView>(R.id.urlList)
        val downTime = SystemClock.uptimeMillis()
        recycler.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 1f, 1f, 0))
        recycler.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_UP, 1f, 1f, 0))
        firstItemView().performLongClick()
    }

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

    // DrawerNavigationView only exposes lookups (findMenuItem) and setNavigationItemSelectedListener,
    // not the backing Menu itself - reflection is the only seam to drive item selection by id.
    private fun DrawerNavigationView.drawerMenu(): MenuBuilder =
        DrawerNavigationView::class.java
            .getDeclaredField("navDrawerMenu")
            .apply { isAccessible = true }
            .get(this) as MenuBuilder

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

    private fun advanceClockPastDebounce() {
        // onNavigationSingleClick debounces clicks within 600ms of each other; move the clock
        // past that window so the very first click in a test isn't silently swallowed.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
    }

    private fun awaitMainIdle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun menuItem(itemId: Int): MenuItem = mockk { every { getItemId() } returns itemId }

    private fun seedUrl(
        shortURL: String,
        favorite: Boolean = false,
    ): URL = testUrl(shortURL, favorite = favorite).also { runBlocking { urlRepository.addURL(it) } }

    private fun testUrl(
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

    private companion object {
        const val PRESCROLL_ITEM_COUNT = 30
        const val UNMAPPED_NAV_ITEM_ID = 987654321
        const val UNMAPPED_ACTION_ITEM_ID = 987654322
    }
}
