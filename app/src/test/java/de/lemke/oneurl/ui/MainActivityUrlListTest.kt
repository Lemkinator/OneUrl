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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.awaitMainLooperUntil
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.idleMainLooper
import de.lemke.commonutils.read
import de.lemke.commonutils.ui.widget.NoEntryView
import de.lemke.oneurl.R
import de.lemke.oneurl.data.URLRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import de.lemke.commonutils.R as commonutilsR
import dev.oneuiproject.oneui.design.R as designR

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class MainActivityUrlListTest {
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
    @Config(sdk = [29])
    fun `initRecycler below API R skips imm bottom padding`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.state shouldBe Lifecycle.State.RESUMED
            scenario.onActivity { activity ->
                activity
                    .findViewById<RecyclerView>(R.id.urlList)
                    .getTag(designR.id.tag_rv_imm_bottom_padding_listener) shouldBe null
            }
        }
    }

    @Test
    fun `updateRecyclerView shows no-urls message when the list is empty`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.noEntryView().text shouldBe activity.getString(R.string.no_urls)
            }
        }
    }

    @Test
    fun `updateRecyclerView shows no-results message when searching with no matches`() {
        urlRepository.seedUrl("https://da.gd/searchnomatch1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity -> activity.onOptionsItemSelected(menuItem(R.id.menu_item_search)) }
            idleMainLooper()
            scenario.onActivity { activity -> activity.searchView().setQuery("no-such-query-matches-anything", false) }
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.noEntryView().text shouldBe activity.getString(commonutilsR.string.commonutils_no_results_found)
            }
        }
    }

    @Test
    fun `updateRecyclerView shows no-favorites message when filtering with no favorites`() {
        urlRepository.seedUrl("https://da.gd/nofav1", favorite = false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity -> activity.onOptionsItemSelected(menuItem(R.id.menu_item_only_show_favorites)) }
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.noEntryView().text shouldBe activity.getString(R.string.no_favorite_urls)
            }
        }
    }

    @Test
    fun `updateRecyclerView clears the message and shows the list when non-empty`() {
        urlRepository.seedUrl("https://da.gd/listed1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.noEntryView().text shouldBe ""
                activity.findViewById<RecyclerView>(R.id.urlList).adapter?.itemCount shouldBe 1
            }
        }
    }

    @Test
    fun `a new url scrolls the list back to the top`() {
        repeat(PRESCROLL_ITEM_COUNT) { urlRepository.seedUrl("https://da.gd/bulk$it") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                val recycler = activity.findViewById<RecyclerView>(R.id.urlList)
                recycler.scrollToPosition(PRESCROLL_ITEM_COUNT - 1)
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                val layoutManager = activity.findViewById<RecyclerView>(R.id.urlList).layoutManager as LinearLayoutManager
                layoutManager.findFirstVisibleItemPosition() shouldNotBe 0
            }
            runBlocking { urlRepository.addURL(urlFixture("https://da.gd/newest")) }
            awaitMainLooperUntil { scenario.read { it.urlList().adapter?.itemCount } == PRESCROLL_ITEM_COUNT + 1 }
            scenario.onActivity { activity ->
                val recycler = activity.findViewById<RecyclerView>(R.id.urlList)
                recycler.adapter?.itemCount shouldBe PRESCROLL_ITEM_COUNT + 1
                val layoutManager = recycler.layoutManager as LinearLayoutManager
                layoutManager.findFirstVisibleItemPosition() shouldBe 0
            }
        }
    }

    @Test
    fun `a reveal that arrives before the list commits the url reveals it`() {
        repeat(PRESCROLL_ITEM_COUNT) { urlRepository.seedUrl("https://da.gd/bulk$it") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.read { it.urlList().firstVisiblePosition() } shouldBe 0
            scenario.moveToState(Lifecycle.State.CREATED)
            runBlocking { urlRepository.addURL(urlFixture("https://da.gd/newest")) }
            awaitMainLooperUntil { scenario.read { it.viewModelUrlCount() } == PRESCROLL_ITEM_COUNT + 1 }
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitMainLooperUntil { scenario.read { it.urlList().adapter?.itemCount } == PRESCROLL_ITEM_COUNT + 1 }
            scenario.onActivity { activity ->
                activity.urlList().firstVisiblePosition() shouldBe 0
                activity.urlList().shortURLAt(0) shouldBe "https://da.gd/newest"
            }
        }
    }

    @Test
    fun `a reveal that arrives after the list committed the url reveals it`() {
        repeat(PRESCROLL_ITEM_COUNT) { urlRepository.seedUrl("https://da.gd/bulk$it") }
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            idleMainLooper()
            controller.get().urlList().scrollToPosition(PRESCROLL_ITEM_COUNT - 1)
            idleMainLooper()
            controller.get().urlList().firstVisiblePosition() shouldNotBe 0
            controller.pause().stop()
            runBlocking { urlRepository.addURL(urlFixture("https://da.gd/newest")) }
            awaitMainLooperUntil { controller.get().viewModelUrlCount() == PRESCROLL_ITEM_COUNT + 1 }
            controller.recreate()
            idleMainLooper()
            controller.get().urlList().firstVisiblePosition() shouldBe 0
            controller.get().urlList().shortURLAt(0) shouldBe "https://da.gd/newest"
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `two urls added in a row reveal the newer one`() {
        repeat(PRESCROLL_ITEM_COUNT) { urlRepository.seedUrl("https://da.gd/bulk$it") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.moveToState(Lifecycle.State.CREATED)
            runBlocking { urlRepository.addURL(urlFixture("https://da.gd/older")) }
            awaitMainLooperUntil { scenario.read { it.viewModelUrlCount() } == PRESCROLL_ITEM_COUNT + 1 }
            runBlocking { urlRepository.addURL(urlFixture("https://da.gd/newer")) }
            awaitMainLooperUntil { scenario.read { it.viewModelUrlCount() } == PRESCROLL_ITEM_COUNT + 2 }
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitMainLooperUntil { scenario.read { it.urlList().adapter?.itemCount } == PRESCROLL_ITEM_COUNT + 2 }
            scenario.onActivity { activity ->
                activity.urlList().firstVisiblePosition() shouldBe 0
                activity.urlList().shortURLAt(0) shouldBe "https://da.gd/newer"
                activity.urlList().shortURLAt(1) shouldBe "https://da.gd/older"
            }
        }
    }

    @Test
    fun `a new url deleted before the list shows it leaves the scroll position alone`() {
        repeat(PRESCROLL_ITEM_COUNT) { urlRepository.seedUrl("https://da.gd/bulk$it") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { it.urlList().scrollToPosition(PRESCROLL_ITEM_COUNT - 1) }
            idleMainLooper()
            val scrolledTo = scenario.read { it.urlList().firstVisiblePosition() }
            scrolledTo shouldNotBe 0
            scenario.moveToState(Lifecycle.State.CREATED)
            val deleted = urlFixture("https://da.gd/deleted")
            runBlocking { urlRepository.addURL(deleted) }
            awaitMainLooperUntil { scenario.read { it.viewModelUrlCount() } == PRESCROLL_ITEM_COUNT + 1 }
            runBlocking { urlRepository.deleteURL(deleted) }
            awaitMainLooperUntil { scenario.read { it.viewModelUrlCount() } == PRESCROLL_ITEM_COUNT }
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitMainLooperUntil { scenario.read { it.adapterHoldsViewModelUrlInstances() } }
            scenario.onActivity { activity ->
                activity.urlList().adapter?.itemCount shouldBe PRESCROLL_ITEM_COUNT
                activity.urlList().firstVisiblePosition() shouldBe scrolledTo
            }
        }
    }

    @Test
    fun `a favorites filter that hides the new url drops its reveal`() {
        repeat(PRESCROLL_ITEM_COUNT) { urlRepository.seedUrl("https://da.gd/bulk$it", favorite = it < FAVORITE_ITEM_COUNT) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.moveToState(Lifecycle.State.CREATED)
            runBlocking { urlRepository.addURL(urlFixture("https://da.gd/hidden")) }
            awaitMainLooperUntil { scenario.read { it.viewModelUrlCount() } == PRESCROLL_ITEM_COUNT + 1 }
            scenario.onActivity { it.onOptionsItemSelected(menuItem(R.id.menu_item_only_show_favorites)) }
            awaitMainLooperUntil { scenario.read { it.viewModelUrlCount() } == FAVORITE_ITEM_COUNT }
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitMainLooperUntil { scenario.read { it.urlList().adapter?.itemCount } == FAVORITE_ITEM_COUNT }
            scenario.onActivity { it.onOptionsItemSelected(menuItem(R.id.menu_item_show_all)) }
            awaitMainLooperUntil { scenario.read { it.urlList().adapter?.itemCount } == PRESCROLL_ITEM_COUNT + 1 }
            scenario.onActivity { activity ->
                activity.urlList().firstVisiblePosition() shouldNotBe 0
                activity.urlList().shortURLAt(0) shouldBe null
            }
        }
    }

    @Test
    fun `a handled reveal does not scroll again after a recreation`() {
        repeat(PRESCROLL_ITEM_COUNT) { urlRepository.seedUrl("https://da.gd/bulk$it") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            runBlocking { urlRepository.addURL(urlFixture("https://da.gd/newest")) }
            awaitMainLooperUntil { scenario.read { it.urlList().adapter?.itemCount } == PRESCROLL_ITEM_COUNT + 1 }
            scenario.read { it.viewModelReveal() } shouldBe null
            scenario.onActivity { it.urlList().scrollToPosition(PRESCROLL_ITEM_COUNT) }
            idleMainLooper()
            val scrolledTo = scenario.read { it.urlList().firstVisiblePosition() }
            scrolledTo shouldNotBe 0

            scenario.recreate()
            idleMainLooper()

            scenario.read { it.urlList().firstVisiblePosition() } shouldBe scrolledTo
        }
    }

    @Test
    fun `recreate without a new url keeps the scroll position`() {
        repeat(PRESCROLL_ITEM_COUNT) { urlRepository.seedUrl("https://da.gd/bulk$it") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { it.urlList().scrollToPosition(PRESCROLL_ITEM_COUNT - 1) }
            idleMainLooper()
            val scrolledTo = scenario.read { it.urlList().firstVisiblePosition() }
            scrolledTo shouldNotBe 0
            scenario.recreate()
            idleMainLooper()
            scenario.read { it.urlList().firstVisiblePosition() } shouldBe scrolledTo
        }
    }

    private fun MainActivity.noEntryView(): NoEntryView = findViewById(R.id.noEntryView)

    private fun MainActivity.urlList(): RecyclerView = findViewById(R.id.urlList)

    private fun RecyclerView.firstVisiblePosition(): Int = (layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()

    private fun RecyclerView.shortURLAt(position: Int): String? =
        (findViewHolderForAdapterPosition(position) as URLAdapter.ViewHolder?)?.listItemTitle?.text?.toString()

    private fun MainActivity.viewModelUrlCount(): Int =
        ViewModelProvider(this)[MainViewModel::class.java]
            .state.value.urls.size

    private fun MainActivity.viewModelReveal(): String? =
        ViewModelProvider(this)[MainViewModel::class.java]
            .state.value.reveal

    private fun MainActivity.adapterHoldsViewModelUrlInstances(): Boolean {
        val urls = ViewModelProvider(this)[MainViewModel::class.java].state.value.urls
        val adapter = urlList().adapter as URLAdapter
        return adapter.itemCount == urls.size && urls.indices.all { adapter.getItemByPosition(it) === urls[it] }
    }

    private companion object {
        const val PRESCROLL_ITEM_COUNT = 30
        const val FAVORITE_ITEM_COUNT = 3
    }
}
