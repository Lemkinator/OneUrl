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

import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.ui.widget.NoEntryView
import de.lemke.oneurl.HiltTestRule
import de.lemke.oneurl.R
import de.lemke.oneurl.data.FakeURLRepository
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.di.URLRepositoryModule
import de.lemke.oneurl.domain.model.URL
import io.kotest.matchers.shouldBe
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import de.lemke.commonutils.R as commonutilsR

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
@UninstallModules(URLRepositoryModule::class)
class MainActivityLoadingTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @BindValue
    @JvmField
    val urlRepository: URLRepository =
        object : URLRepository by FakeURLRepository() {
            override fun observeURLs(): Flow<List<URL>> = emptyFlow()
        }

    @Inject
    lateinit var settings: SettingsRepository

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
    }

    // common-utils' configureCommonUtilsSplashScreen keeps the real splash screen on-screen via a
    // pre-draw block for as long as !isUIReady; ActivityScenario.launch's visible() transition
    // idles the main looper until that resolves, which never happens with an ever-empty flow -
    // build the activity without a visible() window (no real traversal/splash loop) instead.
    @Test
    fun `collectState returns early while the view model is not ui-ready`() {
        val controller =
            Robolectric
                .buildActivity(MainActivity::class.java)
                .create()
                .start()
                .resume()
        try {
            awaitMainIdle()
            val noEntryView = controller.get().findViewById<NoEntryView>(R.id.noEntryView)
            noEntryView.text shouldBe controller.get().getString(commonutilsR.string.commonutils_no_results_found)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
