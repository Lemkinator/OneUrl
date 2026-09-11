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

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.ui.utils.urlEncode
import de.lemke.oneurl.R
import de.lemke.oneurl.data.QRCodeCache
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.model.Dagd
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_SHORTURL
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
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
import org.robolectric.shadows.ShadowToast

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
//
// Dagd is used as in URLActivityScreenshotTest: its default getURLClickCount() resolves
// synchronously with no Volley/network I/O, so refreshVisitCount() (fired from URLViewModel.init)
// never touches the network Robolectric has no access to.
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class URLActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var urlRepository: URLRepository

    @Inject
    lateinit var qrCodeCache: QRCodeCache

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
    fun `onOptionsItemSelected opens the mapped scan URL and returns true`() {
        withUrlActivity { activity ->
            val handled = activity.onOptionsItemSelected(menuItem(R.id.url_toolbar_urlhaus))
            handled.shouldBeTrue()
            val startedIntent = shadowOf(activity).nextStartedActivity
            startedIntent.action shouldBe Intent.ACTION_VIEW
            startedIntent.data shouldBe "https://urlhaus.abuse.ch/browse.php?search=${seededUrl.longURL.urlEncode()}".toUri()
        }
    }

    @Test
    fun `onOptionsItemSelected delegates unmapped items to super and returns false`() {
        withUrlActivity { activity ->
            val handled = activity.onOptionsItemSelected(menuItem(Menu.NONE))
            handled.shouldBeFalse()
        }
    }

    @Test
    fun `onOptionsItemSelected returns false when no url is loaded`() {
        withUrlActivity(shortURL = "https://da.gd/missing") { activity ->
            val handled = activity.onOptionsItemSelected(menuItem(R.id.url_toolbar_urlhaus))
            handled.shouldBeFalse()
        }
    }

    @Test
    fun `bindURL uses a cached qr bitmap without generating a new one`() {
        val cached = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        qrCodeCache[seededUrl.shortURL] = cached

        withUrlActivity { activity ->
            val shown = (activity.findViewById<android.widget.ImageView>(R.id.url_qr_imageview).drawable as BitmapDrawable).bitmap
            shown shouldBe cached
        }
    }

    @Test
    fun `clicking the qr imageview shows the qr bottom sheet`() {
        withUrlActivity { activity ->
            activity.findViewById<android.view.View>(R.id.url_qr_imageview).performClick()
            activity.supportFragmentManager.executePendingTransactions()

            activity.supportFragmentManager.fragments
                .any { it is QRBottomSheet }
                .shouldBeTrue()
        }
    }

    @Test
    fun `long-clicking the qr imageview copies it to the clipboard`() {
        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(any(), any(), any()) } returns "content://de.lemke.test.fileprovider/QRCode.png".toUri()
        try {
            withUrlActivity { activity ->
                val handled = activity.findViewById<android.view.View>(R.id.url_qr_imageview).performLongClick()
                handled.shouldBeTrue()
            }
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `clicking the qr save button launches the export picker`() {
        withUrlActivity { activity ->
            activity.findViewById<android.view.View>(R.id.url_qr_save_button).performClick()

            val startedForResult = shadowOf(activity).peekNextStartedActivityForResult()
            startedForResult shouldNotBe null
        }
    }

    @Test
    fun `export result OK saves the last bound qr bitmap`() {
        withUrlActivity { activity ->
            activity.findViewById<android.view.View>(R.id.url_qr_save_button).performClick()
            val shadowActivity = shadowOf(activity)
            val startedForResult = shadowActivity.peekNextStartedActivityForResult()!!

            shadowActivity.receiveResult(
                startedForResult.intent,
                RESULT_OK,
                Intent().apply { data = "content://de.lemke.oneurl.debug.fileprovider/export.png".toUri() },
            )

            ShadowToast.getLatestToast() shouldNotBe null
        }
    }

    private fun withUrlActivity(
        shortURL: String = seededUrl.shortURL,
        block: (URLActivity) -> Unit,
    ) {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), URLActivity::class.java)
                .putExtra(KEY_SHORTURL, shortURL)
        ActivityScenario.launch<URLActivity>(intent).use { scenario ->
            // URLViewModel.init loads the URL via a real IO-dispatched Room query, then resumes on
            // Main — a single idle() right after launch can race ahead of that background read
            // finishing, especially under full-suite load. Poll instead of a single idle() call.
            awaitMainIdle()
            scenario.onActivity(block)
        }
    }

    private fun awaitMainIdle(iterations: Int = 40) {
        repeat(iterations) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }

    private fun menuItem(itemId: Int): MenuItem = mockk { every { getItemId() } returns itemId }
}
