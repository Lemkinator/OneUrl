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
import android.view.Menu
import android.view.View
import androidx.annotation.IdRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.ui.activity.CommonUtilsAboutActivity
import de.lemke.commonutils.ui.activity.CommonUtilsAboutMeActivity
import de.lemke.commonutils.ui.activity.CommonUtilsSettingsActivity
import de.lemke.commonutils.ui.utils.COMMONUTILS_KEY_IS_SEARCH_MODE
import de.lemke.oneurl.BuildConfig
import de.lemke.oneurl.HiltTestRule
import de.lemke.oneurl.R
import de.lemke.oneurl.data.URLRepository
import dev.oneuiproject.oneui.layout.NavDrawerLayout
import dev.oneuiproject.oneui.navigation.widget.DrawerNavigationView
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import javax.inject.Inject
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
import dev.oneuiproject.oneui.design.R as designR

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class MainActivityTest {
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
        if (!AppWatcher.isInstalled) {
            AppWatcher.manualInstall(ApplicationProvider.getApplicationContext<HiltTestApplication>())
        }
    }

    @Test
    fun `onCreate onboarding required finishes the activity`() {
        settings.lastVersionCode = -1
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.state shouldBe Lifecycle.State.DESTROYED
        }
    }

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
            awaitMainIdle()
            scenario.onActivity { activity ->
                val menu = activity.mainToolbarMenu()
                menu.findItem(R.id.menu_item_show_all).isVisible.shouldBeTrue()
                menu.findItem(R.id.menu_item_only_show_favorites).isVisible.shouldBeFalse()
            }
            scenario.onActivity { activity -> activity.onOptionsItemSelected(menuItem(R.id.menu_item_show_all)).shouldBeTrue() }
            awaitMainIdle()
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
        urlRepository.seedUrl("https://da.gd/searchend1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity -> activity.longClickFirstItem() }
            awaitMainIdle()
            scenario.onActivity { activity ->
                val drawerLayout = activity.findViewById<NavDrawerLayout>(R.id.drawerLayout)
                drawerLayout.isActionMode.shouldBeTrue()
                activity.findViewById<View>(R.id.addFab).isVisible.shouldBeFalse()

                activity.onOptionsItemSelected(menuItem(R.id.menu_item_search))
            }
            awaitMainIdle()
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
            awaitMainIdle()
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
            scenario.onActivity { activity ->
                val menu = activity.findViewById<DrawerNavigationView>(R.id.navigationView).drawerMenu()
                menu.add(Menu.NONE, UNMAPPED_NAV_ITEM_ID, Menu.NONE, "unmapped")
                menu.performIdentifierAction(UNMAPPED_NAV_ITEM_ID, 0)
            }
            awaitMainIdle()
            scenario.onActivity { activity -> shadowOf(activity).nextStartedActivity shouldBe null }
        }
    }

    @Test
    fun `double tap on a navigation item opens its screen once`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity ->
                val menu = activity.findViewById<DrawerNavigationView>(R.id.navigationView).drawerMenu()
                menu.performIdentifierAction(R.id.help_dest, 0)
                menu.performIdentifierAction(R.id.help_dest, 0)
            }
            awaitMainIdle()
            scenario.onActivity { activity ->
                val shadowActivity = shadowOf(activity)
                shadowActivity.nextStartedActivity.component?.className shouldBe HelpActivity::class.java.name
                shadowActivity.nextStartedActivity shouldBe null
            }
        }
    }

    @Test
    fun `a navigation item opens its screen again after the user returns`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity ->
                activity.findViewById<DrawerNavigationView>(R.id.navigationView).drawerMenu().performIdentifierAction(R.id.help_dest, 0)
            }
            scenario.returnFromLaunchedScreen()
            scenario.onActivity { activity ->
                activity.findViewById<DrawerNavigationView>(R.id.navigationView).drawerMenu().performIdentifierAction(R.id.help_dest, 0)
            }
            awaitMainIdle()
            scenario.onActivity { activity ->
                val shadowActivity = shadowOf(activity)
                shadowActivity.nextStartedActivity.component?.className shouldBe HelpActivity::class.java.name
                shadowActivity.nextStartedActivity.component?.className shouldBe HelpActivity::class.java.name
            }
        }
    }

    @Test
    fun `double tap on the add fab opens one AddURLActivity`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity ->
                val addFab = activity.findViewById<View>(R.id.addFab)
                addFab.performClick()
                addFab.performClick()
            }
            awaitMainIdle()
            scenario.onActivity { activity ->
                val shadowActivity = shadowOf(activity)
                shadowActivity.nextStartedActivity.component?.className shouldBe AddURLActivity::class.java.name
                shadowActivity.nextStartedActivity shouldBe null
            }
        }
    }

    private fun assertNavItemStarts(
        @IdRes navItemId: Int,
        expectedClassName: String,
    ) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { activity ->
                activity.findViewById<DrawerNavigationView>(R.id.navigationView).drawerMenu().performIdentifierAction(navItemId, 0)
            }
            awaitMainIdle()
            scenario.onActivity { activity ->
                shadowOf(activity).nextStartedActivity?.component?.className shouldBe expectedClassName
            }
        }
    }

    private fun MainActivity.callInitDrawer() {
        MainActivity::class.java
            .getDeclaredMethod("initDrawer")
            .apply { isAccessible = true }
            .invoke(this)
    }

    // NavDrawerLayout wires setSupportActionBar to its own internal Toolbar rather than the
    // window's native action bar, so shadowOf(activity).optionsMenu (which shadows the framework
    // panel-menu path) never populates - read the real Toolbar's own Menu instead.
    private fun MainActivity.mainToolbarMenu(): Menu = findViewById<Toolbar>(designR.id.toolbarlayout_main_toolbar).menu

    // DrawerNavigationView only exposes lookups (findMenuItem) and setNavigationItemSelectedListener,
    // not the backing Menu itself - reflection is the only seam to drive item selection by id.
    private fun DrawerNavigationView.drawerMenu(): MenuBuilder =
        DrawerNavigationView::class.java
            .getDeclaredField("navDrawerMenu")
            .apply { isAccessible = true }
            .get(this) as MenuBuilder

    private companion object {
        const val UNMAPPED_NAV_ITEM_ID = 987654321
    }
}
