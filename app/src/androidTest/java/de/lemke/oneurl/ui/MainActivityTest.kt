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

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.oneurl.R
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.model.Dagd
import de.lemke.oneurl.domain.model.URL
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@LargeTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var urlRepository: URLRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        settings.bypassOobe()
    }

    @Test
    fun activityLaunchesWithoutCrash() {
        ActivityScenario
            .launch<MainActivity>(
                Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java),
            ).use { scenario ->
                scenario.state shouldBe Lifecycle.State.RESUMED
            }
    }

    @Test
    fun urlAddedWhileStoppedAtTheTopOfALongListShowsInTheFirstRow() {
        runBlocking { repeat(LONG_LIST_SIZE) { urlRepository.addURL(testUrl("https://da.gd/bulk$it")) } }
        ActivityScenario
            .launch<MainActivity>(
                Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java),
            ).use { scenario ->
                scenario.waitUntil { urlList().adapter?.itemCount == LONG_LIST_SIZE }
                scenario.moveToState(Lifecycle.State.CREATED)
                runBlocking { urlRepository.addURL(testUrl("https://da.gd/newest")) }
                scenario.waitUntil { viewModelUrlCount() == LONG_LIST_SIZE + 1 }
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.waitUntil { urlList().adapter?.itemCount == LONG_LIST_SIZE + 1 }
                Thread.sleep(SETTLE_MS)
                scenario.waitUntil { urlList().scrollState == RecyclerView.SCROLL_STATE_IDLE }
                scenario.onActivity { activity ->
                    (activity.urlList().layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() shouldBe 0
                    (activity.urlList().findViewHolderForAdapterPosition(0) as URLAdapter.ViewHolder?)
                        ?.listItemTitle
                        ?.text
                        ?.toString() shouldBe "https://da.gd/newest"
                }
            }
    }

    private fun MainActivity.urlList(): RecyclerView = findViewById(R.id.urlList)

    private fun MainActivity.viewModelUrlCount(): Int =
        ViewModelProvider(this)[MainViewModel::class.java]
            .state.value.urls.size

    private fun ActivityScenario<MainActivity>.waitUntil(condition: MainActivity.() -> Boolean) {
        repeat(WAIT_ATTEMPTS) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            var met = false
            onActivity { met = it.condition() }
            if (met) return
            Thread.sleep(WAIT_STEP_MS)
        }
        error("condition not met within ${WAIT_ATTEMPTS * WAIT_STEP_MS} ms")
    }

    private fun testUrl(shortURL: String) =
        URL(
            shortURL = shortURL,
            longURL = "https://example.com/${shortURL.substringAfterLast('/')}",
            shortURLProvider = Dagd,
            favorite = false,
            title = "title",
            description = "description",
            added = ZonedDateTime.parse("2024-01-15T10:30:00Z"),
        )

    private companion object {
        const val LONG_LIST_SIZE = 30
        const val WAIT_ATTEMPTS = 500
        const val WAIT_STEP_MS = 10L
        const val SETTLE_MS = 500L
    }
}
