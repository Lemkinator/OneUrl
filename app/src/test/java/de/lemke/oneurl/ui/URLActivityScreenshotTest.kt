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
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.idleMainLooper
import de.lemke.oneurl.R
import de.lemke.oneurl.data.FakeShortURLClient
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.model.Dagd
import de.lemke.oneurl.domain.model.Spoome
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_SHORTURL
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class URLActivityScreenshotTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @Inject
    lateinit var urlRepository: URLRepository

    @Inject
    lateinit var shortURLClient: FakeShortURLClient

    private lateinit var seededUrl: URL

    @Before
    fun setup() {
        hiltRule.inject()
        seededUrl =
            URL(
                shortURL = "https://da.gd/seeded1",
                longURL = "https://example.com/seeded-page",
                shortURLProvider = Dagd,
                favorite = false,
                title = "Seeded title",
                description = "Seeded description",
                added = ZonedDateTime.parse("2024-01-15T10:30:00Z"),
            )
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

    @Test
    fun url_visits_default() {
        val url = seedVisitsUrl()
        launchAndCapture("src/test/screenshots/url_visits_default.png", url) { activity ->
            val label = activity.findViewById<TextView>(R.id.url_visits_label)
            val count = activity.findViewById<TextView>(R.id.url_visits_textview)
            assertVisitsRow(activity, "1,234", "Refresh")
            label.lineCount shouldBe 1
            val gap = count.left - label.right
            gap shouldBeGreaterThan -1
            gap shouldBeLessThan
                activity.resources.displayMetrics.density
                    .times(VISITS_GAP_TOLERANCE_DP)
                    .toInt()
        }
    }

    @Test
    @Config(qualifiers = "de-w320dp-h1400dp")
    fun url_visits_large_font_de() {
        RuntimeEnvironment.setFontScale(2f)
        val url = seedVisitsUrl()
        launchAndCapture("src/test/screenshots/url_visits_large_font_de.png", url) { activity ->
            assertVisitsRow(activity, "1.234", "Aktualisieren")
            activity.findViewById<TextView>(R.id.url_visits_label).lineCount shouldBeGreaterThan 1
        }
    }

    private fun seedVisitsUrl(): URL {
        val url = seededUrl.copy(shortURL = "https://spoo.me/seeded2", shortURLProvider = Spoome.Default)
        runBlocking { urlRepository.addURL(url) }
        shortURLClient.visitCounts[url.shortURL] = 1234
        return url
    }

    private fun assertVisitsRow(
        activity: URLActivity,
        expectedCount: String,
        expectedRefreshDescription: String,
    ) {
        val count = activity.findViewById<TextView>(R.id.url_visits_textview)
        val refresh = activity.findViewById<View>(R.id.url_visits_refresh_button)
        count.text.toString() shouldBe expectedCount
        count.width shouldBeGreaterThan count.paint.measureText(expectedCount).toInt() - 1
        count.right shouldBeLessThan refresh.left + 1
        refresh.width shouldBeGreaterThan 0
        refresh.contentDescription.toString() shouldBe expectedRefreshDescription
        val label = activity.findViewById<TextView>(R.id.url_visits_label)
        val baselineDiff = (count.top + count.baseline) - (label.top + label.baseline)
        baselineDiff shouldBe 0
    }

    private fun launchAndCapture(
        fileName: String,
        url: URL = seededUrl,
        check: (URLActivity) -> Unit = {},
    ) {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), URLActivity::class.java)
                .putExtra(KEY_SHORTURL, url.shortURL)
        ActivityScenario.launch<URLActivity>(intent).use { scenario ->
            idleMainLooper()
            assertQrLoaded(scenario)
            scenario.onActivity(check)
            onView(isRoot()).captureRoboImage(fileName)
        }
    }

    private fun assertQrLoaded(scenario: ActivityScenario<URLActivity>) {
        var loaded = false
        scenario.onActivity { activity ->
            loaded =
                (activity.findViewById<ImageView>(R.id.url_qr_imageview).drawable as? BitmapDrawable)
                    ?.bitmap != null
        }
        check(loaded) { "QR drawable did not load" }
    }

    private companion object {
        const val VISITS_GAP_TOLERANCE_DP = 8f
    }
}
