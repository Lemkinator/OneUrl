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
import de.lemke.oneurl.ui.ShortURLQRCodeViewModel.Companion.KEY_SHORT_URL
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

    @Test
    fun url_visits_max_count() {
        val url = seedVisitsUrl(Int.MAX_VALUE)
        launchAndCapture("src/test/screenshots/url_visits_max_count.png", url) { activity ->
            assertVisitsRow(activity, "2,147,483,647", "Refresh")
            activity.findViewById<TextView>(R.id.url_visits_textview).lineCount shouldBe 1
            activity.findViewById<TextView>(R.id.url_visits_label).lineCount shouldBe 2
        }
    }

    @Test
    @Config(qualifiers = "de-w320dp-h1400dp")
    fun url_info_rows_large_font_de() {
        RuntimeEnvironment.setFontScale(2f)
        val url = seedLongTextUrl()
        launchAndCapture("src/test/screenshots/url_info_rows_large_font_de.png", url) { activity ->
            assertInfoRow(activity, R.id.url_title_label, R.id.url_title_textview, "Titel: ", LONG_TITLE)
            assertInfoRow(
                activity,
                R.id.url_description_label,
                R.id.url_description_textview,
                "Beschreibung: ",
                LONG_DESCRIPTION,
            )
            assertInfoRow(activity, R.id.url_added_label, R.id.url_added_textview, "Erstellt: ", null)
            activity.findViewById<TextView>(R.id.url_description_label).lineCount shouldBeGreaterThan 1
            activity.findViewById<TextView>(R.id.url_title_textview).lineCount shouldBeGreaterThan 1
            INFO_ROWS.forEach { (_, valueId) ->
                val value = activity.findViewById<TextView>(valueId)
                val minWidth = (value.parent as View).width / 2 - INFO_ROW_MARGIN_DP * activity.resources.displayMetrics.density
                value.width shouldBeGreaterThan minWidth.toInt() - 1
            }
        }
    }

    @Test
    fun url_info_rows_default() {
        val url = seedLongTextUrl()
        launchAndCapture("src/test/screenshots/url_info_rows_default.png", url) { activity ->
            assertInfoRow(activity, R.id.url_title_label, R.id.url_title_textview, "Title: ", LONG_TITLE)
            assertInfoRow(
                activity,
                R.id.url_description_label,
                R.id.url_description_textview,
                "Description: ",
                LONG_DESCRIPTION,
            )
            assertInfoRow(activity, R.id.url_added_label, R.id.url_added_textview, "Created: ", null)
            activity.findViewById<TextView>(R.id.url_title_textview).lineCount shouldBeGreaterThan 1
            INFO_ROWS.forEach { (labelId, valueId) ->
                val gap =
                    activity.findViewById<TextView>(valueId).left - activity.findViewById<TextView>(labelId).right
                gap shouldBeGreaterThan -1
                gap shouldBeLessThan
                    activity.resources.displayMetrics.density
                        .times(VISITS_GAP_TOLERANCE_DP)
                        .toInt()
            }
        }
    }

    private fun seedVisitsUrl(visits: Int = 1234): URL {
        val url = seededUrl.copy(shortURL = "https://spoo.me/seeded2", shortURLProvider = Spoome.Default)
        runBlocking { urlRepository.addURL(url) }
        shortURLClient.visitCounts[url.shortURL] = visits
        return url
    }

    private fun seedLongTextUrl(): URL {
        val url =
            seededUrl.copy(
                shortURL = "https://da.gd/seeded3",
                title = LONG_TITLE,
                description = LONG_DESCRIPTION,
            )
        runBlocking { urlRepository.addURL(url) }
        return url
    }

    private fun assertInfoRow(
        activity: URLActivity,
        labelId: Int,
        valueId: Int,
        expectedLabel: String,
        expectedValue: String?,
    ) {
        val label = activity.findViewById<TextView>(labelId)
        val value = activity.findViewById<TextView>(valueId)
        val row = value.parent as View
        label.text.toString() shouldBe expectedLabel
        if (expectedValue != null) value.text.toString() shouldBe expectedValue
        value.left shouldBeGreaterThan label.right - 1
        value.right shouldBeLessThan row.width + 1
        value.bottom shouldBeLessThan row.height + 1
        value.height shouldBeGreaterThan value.lineHeight * value.lineCount - 1
        val valueBaseline = value.top + value.baseline
        val labelBaseline = label.top + label.baseline
        valueBaseline shouldBe labelBaseline
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
        val countBaseline = count.top + count.baseline
        val labelBaseline = label.top + label.baseline
        countBaseline shouldBe labelBaseline
    }

    private fun launchAndCapture(
        fileName: String,
        url: URL = seededUrl,
        check: (URLActivity) -> Unit = {},
    ) {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), URLActivity::class.java)
                .putExtra(KEY_SHORT_URL, url.shortURL)
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
        const val INFO_ROW_MARGIN_DP = 18
        val INFO_ROWS =
            listOf(
                R.id.url_title_label to R.id.url_title_textview,
                R.id.url_description_label to R.id.url_description_textview,
                R.id.url_added_label to R.id.url_added_textview,
            )
        const val LONG_TITLE = "A very long page title that keeps going across several lines to prove the value wraps inside its row"
        const val LONG_DESCRIPTION = "A long description over several lines"
    }
}
