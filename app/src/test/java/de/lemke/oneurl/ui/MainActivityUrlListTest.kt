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
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.ui.widget.NoEntryView
import de.lemke.oneurl.HiltTestRule
import de.lemke.oneurl.R
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.ObserveURLsUseCase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
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
    @get:Rule
    val hiltRule = HiltTestRule(this)

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
    }

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
        urlRepository.seedUrl("https://da.gd/searchnomatch1")
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
        urlRepository.seedUrl("https://da.gd/nofav1", favorite = false)
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
        urlRepository.seedUrl("https://da.gd/listed1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
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
            runBlocking { urlRepository.addURL(urlFixture("https://da.gd/newest")) }
            awaitUntil { scenario.read { it.urlList().adapter?.itemCount } == PRESCROLL_ITEM_COUNT + 1 }
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
            awaitMainIdle()
            scenario.read { it.urlList().firstVisiblePosition() } shouldBe 0
            scenario.moveToState(Lifecycle.State.CREATED)
            runBlocking { urlRepository.addURL(urlFixture("https://da.gd/newest")) }
            awaitUntil { scenario.read { it.viewModelUrlCount() } == PRESCROLL_ITEM_COUNT + 1 }
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitUntil { scenario.read { it.urlList().adapter?.itemCount } == PRESCROLL_ITEM_COUNT + 1 }
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
            awaitMainIdle()
            controller.get().urlList().scrollToPosition(PRESCROLL_ITEM_COUNT - 1)
            awaitMainIdle()
            controller.get().urlList().firstVisiblePosition() shouldNotBe 0
            controller.pause().stop()
            runBlocking { urlRepository.addURL(urlFixture("https://da.gd/newest")) }
            awaitUntil { controller.get().viewModelUrlCount() == PRESCROLL_ITEM_COUNT + 1 }
            controller.recreate()
            awaitMainIdle()
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
            awaitMainIdle()
            scenario.moveToState(Lifecycle.State.CREATED)
            runBlocking { urlRepository.addURL(urlFixture("https://da.gd/older")) }
            awaitUntil { scenario.read { it.viewModelUrlCount() } == PRESCROLL_ITEM_COUNT + 1 }
            runBlocking { urlRepository.addURL(urlFixture("https://da.gd/newer")) }
            awaitUntil { scenario.read { it.viewModelUrlCount() } == PRESCROLL_ITEM_COUNT + 2 }
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitUntil { scenario.read { it.urlList().adapter?.itemCount } == PRESCROLL_ITEM_COUNT + 2 }
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
            awaitMainIdle()
            scenario.onActivity { it.urlList().scrollToPosition(PRESCROLL_ITEM_COUNT - 1) }
            awaitMainIdle()
            val scrolledTo = scenario.read { it.urlList().firstVisiblePosition() }
            scrolledTo shouldNotBe 0
            scenario.moveToState(Lifecycle.State.CREATED)
            val deleted = urlFixture("https://da.gd/deleted")
            runBlocking { urlRepository.addURL(deleted) }
            awaitUntil { scenario.read { it.viewModelUrlCount() } == PRESCROLL_ITEM_COUNT + 1 }
            runBlocking { urlRepository.deleteURL(deleted) }
            awaitUntil { scenario.read { it.viewModelUrlCount() } == PRESCROLL_ITEM_COUNT }
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitUntil { scenario.read { it.adapterHoldsViewModelUrlInstances() } }
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
            awaitMainIdle()
            scenario.moveToState(Lifecycle.State.CREATED)
            runBlocking { urlRepository.addURL(urlFixture("https://da.gd/hidden")) }
            awaitUntil { scenario.read { it.viewModelUrlCount() } == PRESCROLL_ITEM_COUNT + 1 }
            scenario.onActivity { it.onOptionsItemSelected(menuItem(R.id.menu_item_only_show_favorites)) }
            awaitUntil { scenario.read { it.viewModelUrlCount() } == FAVORITE_ITEM_COUNT }
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitUntil { scenario.read { it.urlList().adapter?.itemCount } == FAVORITE_ITEM_COUNT }
            scenario.onActivity { it.onOptionsItemSelected(menuItem(R.id.menu_item_show_all)) }
            awaitUntil { scenario.read { it.urlList().adapter?.itemCount } == PRESCROLL_ITEM_COUNT + 1 }
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
            awaitMainIdle()
            runBlocking { urlRepository.addURL(urlFixture("https://da.gd/newest")) }
            awaitUntil { scenario.read { it.urlList().adapter?.itemCount } == PRESCROLL_ITEM_COUNT + 1 }
            scenario.read { it.viewModelReveal() } shouldBe null
            scenario.onActivity { it.urlList().scrollToPosition(PRESCROLL_ITEM_COUNT) }
            awaitMainIdle()
            val scrolledTo = scenario.read { it.urlList().firstVisiblePosition() }
            scrolledTo shouldNotBe 0

            scenario.recreate()
            awaitMainIdle()

            scenario.read { it.urlList().firstVisiblePosition() } shouldBe scrolledTo
        }
    }

    @Test
    fun `recreate without a new url keeps the scroll position`() {
        repeat(PRESCROLL_ITEM_COUNT) { urlRepository.seedUrl("https://da.gd/bulk$it") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitMainIdle()
            scenario.onActivity { it.urlList().scrollToPosition(PRESCROLL_ITEM_COUNT - 1) }
            awaitMainIdle()
            val scrolledTo = scenario.read { it.urlList().firstVisiblePosition() }
            scrolledTo shouldNotBe 0
            scenario.recreate()
            awaitMainIdle()
            scenario.read { it.urlList().firstVisiblePosition() } shouldBe scrolledTo
        }
    }

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
            controller.get().noEntryView().text shouldBe controller.get().getString(commonutilsR.string.commonutils_no_results_found)
        } finally {
            controller.pause().stop().destroy()
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

    private fun <T> ActivityScenario<MainActivity>.read(block: (MainActivity) -> T): T {
        val result = mutableListOf<T>()
        onActivity { result += block(it) }
        return result.single()
    }

    // AsyncListDiffer diffs on a background executor that idle() does not wait for.
    private fun awaitUntil(condition: () -> Boolean) {
        repeat(AWAIT_ATTEMPTS) {
            awaitMainIdle()
            if (condition()) {
                awaitMainIdle()
                return
            }
            Thread.sleep(AWAIT_STEP_MS)
        }
        error("condition not met within ${AWAIT_ATTEMPTS * AWAIT_STEP_MS} ms")
    }

    private companion object {
        const val PRESCROLL_ITEM_COUNT = 30
        const val FAVORITE_ITEM_COUNT = 3
        const val AWAIT_ATTEMPTS = 200
        const val AWAIT_STEP_MS = 10L
    }
}
