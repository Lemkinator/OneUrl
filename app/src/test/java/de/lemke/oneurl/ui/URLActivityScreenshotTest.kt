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
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.model.Dagd
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_SHORTURL
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
//
// The seeded URL uses Dagd directly (rather than ShortURLProviderCompanion.default) since Dagd
// does not override ShortURLProvider.getURLClickCount() — the interface default synchronously
// calls callback(null) with no Volley/network I/O. refreshVisitCount() (fired from
// URLViewModel.init) therefore resolves immediately instead of hitting the network Robolectric
// has no access to.
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class URLActivityScreenshotTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var urlRepository: URLRepository

    private val seededUrl =
        URL(
            shortURL = "https://da.gd/seeded1",
            longURL = "https://example.com/seeded-page",
            shortURLProvider = Dagd,
            qr = createBitmap(64, 64).apply { eraseColor(Color.WHITE) },
            favorite = false,
            title = "Seeded title",
            description = "Seeded description",
            added = ZonedDateTime.parse("2024-01-15T10:30:00Z"),
        )

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking { urlRepository.addURL(seededUrl) }
    }

    @Test
    fun url_default() {
        launchAndCapture("src/test/screenshots/url_default.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun url_default_dark() {
        launchAndCapture("src/test/screenshots/url_default_dark.png")
    }

    private fun launchAndCapture(fileName: String) {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), URLActivity::class.java)
                .putExtra(KEY_SHORTURL, seededUrl.shortURL)
        ActivityScenario.launch<URLActivity>(intent).use {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            onView(isRoot()).captureRoboImage(fileName)
        }
    }
}
