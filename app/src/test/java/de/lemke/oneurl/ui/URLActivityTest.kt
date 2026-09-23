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

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.ui.utils.urlEncode
import de.lemke.oneurl.R
import de.lemke.oneurl.data.QRCodeCache
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.domain.model.Dagd
import de.lemke.oneurl.domain.model.Gg
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_SHORTURL
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.verify
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
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import de.lemke.commonutils.R as commonutilsR

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
    fun `onOptionsItemSelected opens the mapped scan URL for every remaining toolbar item`() {
        withUrlActivity { activity ->
            val encoded = seededUrl.longURL.urlEncode()
            val expectedByItemId =
                mapOf(
                    R.id.url_toolbar_norton_safe_web to "https://safeweb.norton.com/report/show?url=$encoded",
                    R.id.url_toolbar_google_safe_browsing to
                        "https://transparencyreport.google.com/safe-browsing/search?url=$encoded",
                    R.id.url_toolbar_link_shield to "https://linkshieldapi.com/?url=$encoded",
                    R.id.url_toolbar_malshare to "https://malshare.com/search.php?query=$encoded",
                    R.id.url_toolbar_kaspersky to "https://opentip.kaspersky.com/$encoded/?tab=lookup",
                )
            expectedByItemId.forEach { (itemId, expectedURL) ->
                val handled = activity.onOptionsItemSelected(menuItem(itemId))
                handled.shouldBeTrue()
                val startedIntent = shadowOf(activity).nextStartedActivity
                startedIntent.action shouldBe Intent.ACTION_VIEW
                startedIntent.data shouldBe expectedURL.toUri()
            }
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
                verify { FileProvider.getUriForFile(any(), any(), any()) }
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

    @Test
    fun `export result cancelled does not save and shows no toast`() {
        withUrlActivity { activity ->
            activity.findViewById<android.view.View>(R.id.url_qr_save_button).performClick()
            val shadowActivity = shadowOf(activity)
            val startedForResult = shadowActivity.peekNextStartedActivityForResult()!!

            shadowActivity.receiveResult(startedForResult.intent, RESULT_CANCELED, null)

            ShadowToast.getLatestToast() shouldBe null
        }
    }

    @Test
    fun `export result OK without a destination uri shows the creating-file error toast`() {
        withUrlActivity { activity ->
            activity.findViewById<android.view.View>(R.id.url_qr_save_button).performClick()
            val shadowActivity = shadowOf(activity)
            val startedForResult = shadowActivity.peekNextStartedActivityForResult()!!

            shadowActivity.receiveResult(startedForResult.intent, RESULT_OK, Intent())

            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_error_creating_file)
        }
    }

    @Test
    fun `clicking the short url button opens the short url`() {
        withUrlActivity { activity ->
            activity.findViewById<android.view.View>(R.id.url_short_button).performClick()
            val startedIntent = shadowOf(activity).nextStartedActivity
            startedIntent.action shouldBe Intent.ACTION_VIEW
            startedIntent.data shouldBe seededUrl.shortURL.toUri()
        }
    }

    @Test
    fun `long-clicking the short url button copies it to the clipboard`() {
        withUrlActivity { activity ->
            val handled = activity.findViewById<android.view.View>(R.id.url_short_button).performLongClick()
            handled.shouldBeTrue()
            val clipboard = activity.getSystemService(ClipboardManager::class.java)
            clipboard.primaryClip
                ?.getItemAt(0)
                ?.text
                .toString() shouldBe seededUrl.shortURL
        }
    }

    @Test
    fun `clicking the short url share button shares the short url text`() {
        withUrlActivity { activity ->
            activity.findViewById<android.view.View>(R.id.url_short_share_button).performClick()
            val startedIntent = shadowOf(activity).nextStartedActivity
            startedIntent.action shouldBe Intent.ACTION_CHOOSER
            val inner = startedIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            inner?.getStringExtra(Intent.EXTRA_TEXT) shouldBe seededUrl.shortURL
        }
    }

    @Test
    fun `clicking the long url button opens the long url`() {
        withUrlActivity { activity ->
            activity.findViewById<android.view.View>(R.id.url_long_button).performClick()
            val startedIntent = shadowOf(activity).nextStartedActivity
            startedIntent.action shouldBe Intent.ACTION_VIEW
            startedIntent.data shouldBe seededUrl.longURL.toUri()
        }
    }

    @Test
    fun `long-clicking the long url button copies it to the clipboard`() {
        withUrlActivity { activity ->
            val handled = activity.findViewById<android.view.View>(R.id.url_long_button).performLongClick()
            handled.shouldBeTrue()
            val clipboard = activity.getSystemService(ClipboardManager::class.java)
            clipboard.primaryClip
                ?.getItemAt(0)
                ?.text
                .toString() shouldBe seededUrl.longURL
        }
    }

    @Test
    fun `clicking the long url share button shares the long url text`() {
        withUrlActivity { activity ->
            activity.findViewById<android.view.View>(R.id.url_long_share_button).performClick()
            val startedIntent = shadowOf(activity).nextStartedActivity
            startedIntent.action shouldBe Intent.ACTION_CHOOSER
            val inner = startedIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            inner?.getStringExtra(Intent.EXTRA_TEXT) shouldBe seededUrl.longURL
        }
    }

    @Test
    fun `bindURL shows the title and description when non-blank`() {
        withUrlActivity { activity ->
            activity.findViewById<android.view.View>(R.id.url_title_layout).isVisible.shouldBeTrue()
            activity.findViewById<android.view.View>(R.id.url_description_layout).isVisible.shouldBeTrue()
        }
    }

    @Test
    fun `bindURL hides the title and description when blank`() {
        val blankUrl = seededUrl.copy(shortURL = "https://da.gd/blankfields", title = "", description = "")
        runBlocking { urlRepository.addURL(blankUrl) }

        withUrlActivity(shortURL = blankUrl.shortURL) { activity ->
            activity.findViewById<android.view.View>(R.id.url_title_layout).isVisible.shouldBeFalse()
            activity.findViewById<android.view.View>(R.id.url_description_layout).isVisible.shouldBeFalse()
        }
    }

    @Test
    fun `urlVisitsRefreshButton is enabled and fully opaque once the initial refresh completes`() {
        withUrlActivity { activity ->
            val button = activity.findViewById<android.view.View>(R.id.url_visits_refresh_button)
            button.isEnabled.shouldBeTrue()
            button.alpha shouldBe 1f
        }
    }

    @Test
    fun `urlVisitsRefreshButton click re-invokes refreshVisitCount`() {
        mockkObject(Dagd)
        every { Dagd.getURLClickCount(any(), any(), any()) } answers { thirdArg<(Int?) -> Unit>().invoke(1) }
        try {
            withUrlActivity { activity ->
                awaitMainIdle()
                activity.findViewById<android.view.View>(R.id.url_visits_refresh_button).performClick()
                awaitMainIdle()
                verify(exactly = 2) { Dagd.getURLClickCount(any(), any(), any()) }
                val button = activity.findViewById<android.view.View>(R.id.url_visits_refresh_button)
                button.isEnabled.shouldBeTrue()
                button.alpha shouldBe 1f
            }
        } finally {
            unmockkObject(Dagd)
        }
    }

    @Test
    fun `visit views stay hidden when the provider never returns a visit count`() {
        val url = seededUrl.copy(shortURL = "https://gg.gg/no-visit-count", shortURLProvider = Gg)
        runBlocking { urlRepository.addURL(url) }

        withUrlActivity(shortURL = url.shortURL) { activity ->
            awaitMainIdle()
            activity.findViewById<android.view.View>(R.id.url_visits_divider).isVisible.shouldBeFalse()
            activity.findViewById<android.view.View>(R.id.url_visits_layout).isVisible.shouldBeFalse()
        }
    }

    @Test
    fun `visit count textview shows the formatted click count when the provider returns one`() {
        val url = seededUrl.copy(shortURL = "https://da.gd/visit-count-shown")
        runBlocking { urlRepository.addURL(url) }
        mockkObject(Dagd)
        every { Dagd.getURLClickCount(any(), any(), any()) } answers { thirdArg<(Int?) -> Unit>().invoke(42) }

        try {
            withUrlActivity(shortURL = url.shortURL) { activity ->
                awaitMainIdle()
                activity.findViewById<android.view.View>(R.id.url_visits_divider).isVisible.shouldBeTrue()
                activity.findViewById<android.view.View>(R.id.url_visits_layout).isVisible.shouldBeTrue()
                activity.findViewById<android.widget.TextView>(R.id.url_visits_textview).text.toString() shouldBe "42"
            }
        } finally {
            unmockkObject(Dagd)
        }
    }

    @Test
    fun `bnv analytics item is visible for a provider with analytics and opens the analytics URL`() {
        withUrlActivity { activity ->
            val bnv = activity.findViewById<BottomNavigationView>(R.id.url_bnv)
            bnv.menu
                .findItem(R.id.url_bnv_analytics)
                .isVisible
                .shouldBeTrue()

            bnv.selectedItemId = R.id.url_bnv_analytics

            val startedIntent = shadowOf(activity).nextStartedActivity
            startedIntent.action shouldBe Intent.ACTION_VIEW
            startedIntent.data shouldBe seededUrl.shortURLProvider.getAnalyticsURL(seededUrl.alias)!!.toUri()
        }
    }

    @Test
    fun `bnv analytics item is hidden and a no-op for a provider without analytics`() {
        val url = seededUrl.copy(shortURL = "https://gg.gg/no-analytics", shortURLProvider = Gg)
        runBlocking { urlRepository.addURL(url) }

        withUrlActivity(shortURL = url.shortURL) { activity ->
            val bnv = activity.findViewById<BottomNavigationView>(R.id.url_bnv)
            bnv.menu
                .findItem(R.id.url_bnv_analytics)
                .isVisible
                .shouldBeFalse()

            bnv.selectedItemId = R.id.url_bnv_analytics

            shadowOf(activity).nextStartedActivity shouldBe null
        }
    }

    @Test
    fun `bnv provider info item shows the provider info bottom sheet`() {
        withUrlActivity { activity ->
            activity.findViewById<BottomNavigationView>(R.id.url_bnv).selectedItemId = R.id.url_bnv_provider_info
            activity.supportFragmentManager.executePendingTransactions()

            activity.supportFragmentManager.fragments
                .any { it is ProviderInfoBottomSheet }
                .shouldBeTrue()
        }
    }

    // NavigationBarView wires its MenuBuilder's callback to the registered OnItemSelectedListener
    // unconditionally in its constructor, so performIdentifierAction reaches handleBnvItemSelected
    // for any item id in the menu - including one added at runtime that has no mapped case.
    @Test
    fun `bnv item selection with an unmapped id is a no-op`() {
        withUrlActivity { activity ->
            val bnv = activity.findViewById<BottomNavigationView>(R.id.url_bnv)
            val unmappedId = android.view.View.generateViewId()
            bnv.menu.add(Menu.NONE, unmappedId, Menu.NONE, "unmapped")

            bnv.menu.performIdentifierAction(unmappedId, 0)

            shadowOf(activity).nextStartedActivity shouldBe null
            activity.supportFragmentManager.fragments
                .any { it is QRBottomSheet || it is ProviderInfoBottomSheet }
                .shouldBeFalse()
        }
    }

    @Test
    fun `urlBnv shows add-to-fav and hides remove-from-fav for a non-favorite url`() {
        withUrlActivity { activity ->
            val menu = activity.findViewById<BottomNavigationView>(R.id.url_bnv).menu
            menu.findItem(R.id.url_bnv_add_to_fav).isVisible.shouldBeTrue()
            menu.findItem(R.id.url_bnv_remove_from_fav).isVisible.shouldBeFalse()
        }
    }

    @Test
    fun `urlBnv hides add-to-fav and shows remove-from-fav for a favorite url`() {
        val favUrl = seededUrl.copy(shortURL = "https://da.gd/fav-visible", favorite = true)
        runBlocking { urlRepository.addURL(favUrl) }

        withUrlActivity(shortURL = favUrl.shortURL) { activity ->
            val menu = activity.findViewById<BottomNavigationView>(R.id.url_bnv).menu
            menu.findItem(R.id.url_bnv_add_to_fav).isVisible.shouldBeFalse()
            menu.findItem(R.id.url_bnv_remove_from_fav).isVisible.shouldBeTrue()
        }
    }

    @Test
    fun `bnv add-to-fav item toggles favorite from false to true`() {
        withUrlActivity { activity ->
            activity.findViewById<BottomNavigationView>(R.id.url_bnv).selectedItemId = R.id.url_bnv_add_to_fav
            awaitMainIdle()

            runBlocking { urlRepository.getURL(seededUrl.shortURL)?.favorite } shouldBe true
        }
    }

    @Test
    fun `bnv remove-from-fav item toggles favorite from true to false`() {
        val favUrl = seededUrl.copy(shortURL = "https://da.gd/fav-toggle", favorite = true)
        runBlocking { urlRepository.addURL(favUrl) }

        withUrlActivity(shortURL = favUrl.shortURL) { activity ->
            activity.findViewById<BottomNavigationView>(R.id.url_bnv).selectedItemId = R.id.url_bnv_remove_from_fav
            awaitMainIdle()

            runBlocking { urlRepository.getURL(favUrl.shortURL)?.favorite } shouldBe false
        }
    }

    @Test
    fun `bnv delete item positive button deletes the url and finishes the activity`() {
        withUrlActivity { activity ->
            activity.findViewById<BottomNavigationView>(R.id.url_bnv).selectedItemId = R.id.url_bnv_delete

            val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
            dialog shouldNotBe null
            dialog!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            awaitMainIdle()

            runBlocking { urlRepository.getURL(seededUrl.shortURL) } shouldBe null
            activity.isFinishing.shouldBeTrue()
        }
    }

    @Test
    fun `bnv delete item negative button leaves the url intact`() {
        withUrlActivity { activity ->
            activity.findViewById<BottomNavigationView>(R.id.url_bnv).selectedItemId = R.id.url_bnv_delete

            val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
            dialog shouldNotBe null
            dialog!!.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
            awaitMainIdle()

            runBlocking { urlRepository.getURL(seededUrl.shortURL) } shouldNotBe null
            activity.isFinishing.shouldBeFalse()
        }
    }

    @Test
    fun `loading a missing url toasts not-found and finishes the activity`() {
        withUrlActivity(shortURL = "https://da.gd/missing") { activity ->
            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(R.string.error_url_not_found)
            activity.isFinishing.shouldBeTrue()
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
            awaitMainIdle()
            scenario.onActivity(block)
        }
    }

    private fun awaitMainIdle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun menuItem(itemId: Int): MenuItem = mockk { every { getItemId() } returns itemId }
}
