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
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_SHORTURL
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// URLActivity calls setWindowTransparent(true), so a plain CREATED-state check (no Espresso
// root-view assertion) is used to avoid RootViewPicker window-focus timeouts.
//
// DB seeding uses GlobalScope.launch + CountDownLatch instead of runBlocking: androidx.room
// 2.8.4 strictly pins kotlinx-coroutines-bom to 1.9.0, which (via Gradle's consistent
// resolution between the main and androidTest runtime classpaths) downgrades
// kotlinx-coroutines-core on-device to 1.9.0, while the androidTest *compile* classpath still
// resolves the project's declared 1.11.0 (for kotlinx-coroutines-test). The compiler binds
// runBlocking()/runTest() call sites to BuildersKt.runBlockingK$default, a symbol that only
// exists in 1.11.0+, causing a NoSuchMethodError at runtime. launch()/launch$default has an
// identical signature in both versions, so it isn't affected.
@HiltAndroidTest
@LargeTest
@RunWith(AndroidJUnit4::class)
class URLActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var urlRepository: URLRepository

    private val seededUrl =
        URL(
            shortURL = "https://da.gd/seeded1",
            longURL = "https://example.com/seeded-page",
            shortURLProvider = ShortURLProviderCompanion.default,
            qr = createBitmap(64, 64).apply { eraseColor(Color.WHITE) },
            favorite = false,
            title = "Seeded title",
            description = "Seeded description",
            added = ZonedDateTime.parse("2024-01-15T10:30:00Z"),
        )

    @OptIn(DelicateCoroutinesApi::class)
    @Before
    fun setUp() {
        hiltRule.inject()
        val latch = CountDownLatch(1)
        GlobalScope.launch {
            urlRepository.addURL(seededUrl)
            latch.countDown()
        }
        latch.await(10, TimeUnit.SECONDS) shouldBe true
    }

    @Test
    fun activityLaunchesWithoutCrash() {
        ActivityScenario
            .launch<URLActivity>(
                Intent(ApplicationProvider.getApplicationContext(), URLActivity::class.java)
                    .putExtra(KEY_SHORTURL, seededUrl.shortURL),
            ).use { scenario ->
                scenario.state.isAtLeast(Lifecycle.State.CREATED) shouldBe true
            }
    }
}
