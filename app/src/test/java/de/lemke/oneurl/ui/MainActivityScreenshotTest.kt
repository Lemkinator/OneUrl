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

import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.model.URL
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityScreenshotTest {
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
    fun main_default() {
        seedUrls()
        launchAndCapture("src/test/screenshots/main_default.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun main_default_dark() {
        seedUrls()
        launchAndCapture("src/test/screenshots/main_default_dark.png")
    }

    @Test
    fun main_empty() {
        launchAndCapture("src/test/screenshots/main_empty.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun main_empty_dark() {
        launchAndCapture("src/test/screenshots/main_empty_dark.png")
    }

    private fun launchAndCapture(fileName: String) {
        ActivityScenario.launch(MainActivity::class.java).use {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            onView(isRoot()).captureRoboImage(fileName)
        }
    }

    private fun seedUrls() =
        runBlocking {
            urlRepository.addURL(testUrl("https://da.gd/blog1", "My Blog", "Personal blog homepage"))
            urlRepository.addURL(testUrl("https://da.gd/docs42", "Project Docs", "Documentation site", favorite = true))
            urlRepository.addURL(testUrl("https://da.gd/repo9x", "Repository", "Source code repository"))
        }

    private fun testUrl(
        shortURL: String,
        title: String,
        description: String,
        favorite: Boolean = false,
    ) = URL(
        shortURL = shortURL,
        longURL = "https://example.com/${title.lowercase().replace(' ', '-')}",
        shortURLProvider = ShortURLProviderCompanion.default,
        qr = createBitmap(64, 64).apply { eraseColor(Color.WHITE) },
        favorite = favorite,
        title = title,
        description = description,
        added = ZonedDateTime.now(),
    )
}
