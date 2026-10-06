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
import android.app.Activity.RESULT_FIRST_USER
import android.app.Activity.RESULT_OK
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.ShadowFileProvider
import de.lemke.commonutils.ui.utils.urlEncode
import de.lemke.oneurl.R
import de.lemke.oneurl.TestDatabaseRule
import de.lemke.oneurl.data.QRCodeCache
import de.lemke.oneurl.data.QRCodeExporter
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.di.QRCodeExporterModule
import de.lemke.oneurl.domain.model.Dagd
import de.lemke.oneurl.domain.model.Gg
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_SHORTURL
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.File
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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
@Config(application = HiltTestApplication::class, sdk = [36], shadows = [ShadowFileProvider::class])
@UninstallModules(QRCodeExporterModule::class)
class URLActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val testDatabase = TestDatabaseRule()

    private val ioDispatcher = PausableDispatcher(Dispatchers.Main)

    @BindValue
    @JvmField
    val qrCodeExporter: QRCodeExporter = qrCodeExporterOn(ioDispatcher)

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
        withUrlScenario { scenario ->
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
                scenario.onActivity { activity ->
                    activity.onOptionsItemSelected(menuItem(itemId)).shouldBeTrue()
                    val startedIntent = shadowOf(activity).nextStartedActivity
                    startedIntent.action shouldBe Intent.ACTION_VIEW
                    startedIntent.data shouldBe expectedURL.toUri()
                }
                scenario.returnFromLaunchedScreen()
            }
        }
    }

    @Test
    fun `double tap on a scan menu item opens the scan URL once`() {
        withUrlActivity { activity ->
            activity.onOptionsItemSelected(menuItem(R.id.url_toolbar_urlhaus)).shouldBeTrue()
            activity.onOptionsItemSelected(menuItem(R.id.url_toolbar_urlhaus)).shouldBeTrue()

            val shadowActivity = shadowOf(activity)
            shadowActivity.nextStartedActivity.data shouldBe
                "https://urlhaus.abuse.ch/browse.php?search=${seededUrl.longURL.urlEncode()}".toUri()
            shadowActivity.nextStartedActivity shouldBe null
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
    fun `double tap on the qr imageview shows one qr bottom sheet`() {
        withUrlActivity { activity ->
            val qrImageView = activity.findViewById<android.view.View>(R.id.url_qr_imageview)

            qrImageView.performClick()
            qrImageView.performClick()
            activity.supportFragmentManager.executePendingTransactions()

            activity.supportFragmentManager.fragments
                .count { it is QRBottomSheet } shouldBe 1
        }
    }

    @Test
    fun `long-clicking the qr imageview copies it to the clipboard`() {
        withUrlActivity { activity ->
            activity.findViewById<android.view.View>(R.id.url_qr_imageview).performLongClick().shouldBeTrue()
            awaitMainIdle()

            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_copied_to_clipboard)
            val clip = activity.getSystemService(ClipboardManager::class.java).primaryClip!!
            val uri = clip.getItemAt(0).uri
            uri.toString() shouldMatch activity.qrCodeContentUriPattern("clipboard", "QRCode.png")
            clip.description.getMimeType(0) shouldBe "image/png"
            activity.contentResolver.getType(uri) shouldBe "image/png"
        }
    }

    @Test
    fun `double long-click on the qr imageview copies one clip with one toast`() {
        withUrlActivity { activity ->
            var clipChanges = 0
            activity.getSystemService(ClipboardManager::class.java).addPrimaryClipChangedListener { clipChanges++ }
            val qrImageView = activity.findViewById<android.view.View>(R.id.url_qr_imageview)

            qrImageView.performLongClick().shouldBeTrue()
            qrImageView.performLongClick().shouldBeTrue()
            awaitMainIdle()

            clipChanges shouldBe 1
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Copied to clipboard"
        }
    }

    @Test
    fun `qr imageview click and long-click do nothing when no url is loaded`() {
        withUrlActivity(shortURL = "https://da.gd/missing") { activity ->
            val qrImageView = activity.findViewById<android.view.View>(R.id.url_qr_imageview)

            qrImageView.performClick().shouldBeTrue()
            qrImageView.hasOnLongClickListeners().shouldBeTrue()
            qrImageView.performLongClick().shouldBeFalse()
            activity.supportFragmentManager.executePendingTransactions()
            awaitMainIdle()

            activity.supportFragmentManager.fragments
                .none { it is QRBottomSheet }
                .shouldBeTrue()
            activity.getSystemService(ClipboardManager::class.java).primaryClip shouldBe null
            activity.exportState() shouldBe QRCodeExport.Idle
        }
    }

    @Test
    fun `qr share that no app can receive shows the share error toast and stays unhandled`() {
        withUrlActivity { activity ->
            shadowOf(activity.application).checkActivities(true)

            activity.findViewById<android.view.View>(R.id.url_qr_share_button).performClick()
            awaitMainIdle()

            shadowOf(activity).nextStartedActivity shouldBe null
            ShadowToast.getTextOfLatestToast() shouldBe
                activity.getString(commonutilsR.string.commonutils_error_share_content_not_supported_on_device)
            (activity.exportState() is QRCodeExport.Share).shouldBeTrue()
        }
    }

    @Test
    fun `double tap on the qr share button opens one chooser for one written file`() {
        withUrlActivity { activity ->
            val shareButton = activity.findViewById<android.view.View>(R.id.url_qr_share_button)

            shareButton.performClick()
            shareButton.performClick()
            awaitMainIdle()

            val shadowActivity = shadowOf(activity)
            val chooser = shadowActivity.nextStartedActivity
            chooser.action shouldBe Intent.ACTION_CHOOSER
            val shareIntent = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
            shareIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java).toString() shouldMatch
                activity.qrCodeContentUriPattern("share", "QRCode.png")
            shadowActivity.nextStartedActivity shouldBe null
            File(activity.cacheDir, "share").walk().count { it.isFile } shouldBe 1
        }
    }

    @Test
    fun `double tap on the qr save button launches one document picker`() {
        withUrlActivity { activity ->
            val saveButton = activity.findViewById<android.view.View>(R.id.url_qr_save_button)

            saveButton.performClick()
            saveButton.performClick()
            awaitMainIdle()

            val shadowActivity = shadowOf(activity)
            shadowActivity.nextStartedActivityForResult.intent.action shouldBe Intent.ACTION_CREATE_DOCUMENT
            shadowActivity.nextStartedActivityForResult shouldBe null
        }
    }

    @Test
    fun `qr export controls are disabled while a share writes and the favorite toggle stays usable`() {
        withUrlScenario { scenario ->
            ioDispatcher.pause()
            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.url_qr_share_button).performClick()
            }
            awaitMainIdle()
            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.url_qr_save_button).isEnabled.shouldBeFalse()
                activity.findViewById<android.view.View>(R.id.url_qr_share_button).isEnabled.shouldBeFalse()
                activity.findViewById<android.view.View>(R.id.url_qr_imageview).isLongClickable.shouldBeFalse()

                activity.findViewById<BottomNavigationView>(R.id.url_bnv).selectedItemId = R.id.url_bnv_add_to_fav
            }
            awaitMainIdle()
            runBlocking { urlRepository.getURL(seededUrl.shortURL)?.favorite } shouldBe true

            ioDispatcher.resume()
            awaitMainIdle()

            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.url_qr_save_button).isEnabled.shouldBeTrue()
                activity.findViewById<android.view.View>(R.id.url_qr_share_button).isEnabled.shouldBeTrue()
                activity.findViewById<android.view.View>(R.id.url_qr_imageview).isLongClickable.shouldBeTrue()
                shadowOf(activity).nextStartedActivity.action shouldBe Intent.ACTION_CHOOSER
            }
        }
    }

    @Test
    fun `clicking the qr save button launches the export picker`() {
        withUrlActivity { activity ->
            activity.findViewById<android.view.View>(R.id.url_qr_save_button).performClick()
            awaitMainIdle()

            val startedForResult = shadowOf(activity).peekNextStartedActivityForResult()
            startedForResult shouldNotBe null
            startedForResult.intent.action shouldBe Intent.ACTION_CREATE_DOCUMENT
        }
    }

    @Test
    fun `export result OK saves the last bound qr bitmap`() {
        withUrlActivity { activity ->
            activity.findViewById<android.view.View>(R.id.url_qr_save_button).performClick()
            awaitMainIdle()
            val shadowActivity = shadowOf(activity)
            val startedForResult = shadowActivity.peekNextStartedActivityForResult()!!

            val exportFile = File(activity.cacheDir, "export.png").also { it.createNewFile() }

            shadowActivity.receiveResult(
                startedForResult.intent,
                RESULT_OK,
                Intent().apply { data = Uri.fromFile(exportFile) },
            )
            awaitMainIdle()

            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_image_saved)
            exportFile.length() shouldBeGreaterThan 0L
        }
    }

    @Test
    fun `export result cancelled does not save and shows no toast`() {
        withUrlActivity { activity ->
            activity.findViewById<android.view.View>(R.id.url_qr_save_button).performClick()
            awaitMainIdle()
            val shadowActivity = shadowOf(activity)
            val startedForResult = shadowActivity.peekNextStartedActivityForResult()!!

            shadowActivity.receiveResult(startedForResult.intent, RESULT_CANCELED, null)
            awaitMainIdle()

            ShadowToast.getLatestToast() shouldBe null
        }
    }

    @Test
    fun `export result with an unknown code and a uri writes nothing and shows no toast`() {
        val document = createPickedDocument()
        withUrlActivity { activity ->
            activity.findViewById<android.view.View>(R.id.url_qr_save_button).performClick()
            awaitMainIdle()

            activity.receiveDocumentPickerResult(RESULT_FIRST_USER, Intent().setData(Uri.fromFile(document)))
            awaitMainIdle()

            document.length() shouldBe 0L
            ShadowToast.shownToastCount() shouldBe 0
        }
    }

    @Test
    fun `export write that the document provider refuses deletes the document and shows the creating-file error toast`() {
        val provider = readOnlyDocumentProvider()
        withUrlActivity { activity ->
            activity.findViewById<android.view.View>(R.id.url_qr_save_button).performClick()
            awaitMainIdle()

            activity.receiveDocumentPickerResult(RESULT_OK, Intent().setData(provider.uri))
            awaitMainIdle()

            provider.document.exists().shouldBeFalse()
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Error creating file"
        }
    }

    @Test
    fun `export write that runs during a recreation finishes and toasts once in the recreated activity`() {
        val document = createPickedDocument()
        withUrlScenario { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.url_qr_save_button).performClick()
            }
            awaitMainIdle()
            ioDispatcher.pause()
            scenario.onActivity { activity ->
                activity.receiveDocumentPickerResult(RESULT_OK, Intent().setData(Uri.fromFile(document)))
            }
            awaitMainIdle()
            document.length() shouldBe 0L

            scenario.recreate()
            ioDispatcher.resume()
            awaitMainIdle()

            document.readBytes().take(PNG_SIGNATURE.size) shouldBe PNG_SIGNATURE
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Image saved"
        }
    }

    @Test
    fun `export result OK without a destination uri shows the creating-file error toast`() {
        withUrlActivity { activity ->
            activity.findViewById<android.view.View>(R.id.url_qr_save_button).performClick()
            awaitMainIdle()
            val shadowActivity = shadowOf(activity)
            val startedForResult = shadowActivity.peekNextStartedActivityForResult()!!

            shadowActivity.receiveResult(startedForResult.intent, RESULT_OK, Intent())
            awaitMainIdle()

            ShadowToast.shownToastCount() shouldBe 1
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
    fun `double tap on the short url button opens the short url once`() {
        withUrlActivity { activity ->
            val shortButton = activity.findViewById<android.view.View>(R.id.url_short_button)

            shortButton.performClick()
            shortButton.performClick()

            val shadowActivity = shadowOf(activity)
            shadowActivity.nextStartedActivity.data shouldBe seededUrl.shortURL.toUri()
            shadowActivity.nextStartedActivity shouldBe null
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
    fun `bnv delete that completes while the activity is paused finishes it only once resumed`() {
        withUrlScenario { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<BottomNavigationView>(R.id.url_bnv).selectedItemId = R.id.url_bnv_delete
            }
            scenario.moveToState(Lifecycle.State.STARTED)
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            awaitMainIdle()
            runBlocking { urlRepository.getURL(seededUrl.shortURL) } shouldBe null
            scenario.onActivity { it.isFinishing.shouldBeFalse() }

            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitMainIdle()

            scenario.onActivity { it.isFinishing.shouldBeTrue() }
        }
    }

    // ActivityScenario.recreate() resumes the old activity before recreating it, so a pending exit reaches it.
    // ActivityController.recreate() keeps the old activity stopped and walks only the new one through RESUMED.
    @Test
    fun `a deleted exit set while stopped survives a recreation and finishes only the recreated activity`() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), URLActivity::class.java)
                .putExtra(KEY_SHORTURL, seededUrl.shortURL)
        val controller = Robolectric.buildActivity(URLActivity::class.java, intent).setup()
        try {
            awaitMainIdle()
            val stopped = controller.get()
            stopped.findViewById<BottomNavigationView>(R.id.url_bnv).selectedItemId = R.id.url_bnv_delete
            controller.pause().stop()
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            awaitMainIdle()
            runBlocking { urlRepository.getURL(seededUrl.shortURL) } shouldBe null
            stopped.isFinishing.shouldBeFalse()
            stopped.exitState() shouldBe UrlDetailExit.Deleted

            controller.recreate()
            awaitMainIdle()

            val recreated = controller.get()
            recreated shouldNotBe stopped
            stopped.isFinishing.shouldBeFalse()
            recreated.isFinishing.shouldBeTrue()
            recreated.exitState() shouldBe UrlDetailExit.None
            ShadowToast.shownToastCount() shouldBe 0
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `loading a missing url shows one not-found toast and finishes the activity`() {
        withUrlActivity(shortURL = "https://da.gd/missing") { activity ->
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(R.string.error_url_not_found)
            activity.isFinishing.shouldBeTrue()
        }
    }

    private fun withUrlScenario(
        shortURL: String = seededUrl.shortURL,
        block: (ActivityScenario<URLActivity>) -> Unit,
    ) {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), URLActivity::class.java)
                .putExtra(KEY_SHORTURL, shortURL)
        ActivityScenario.launch<URLActivity>(intent).use { scenario ->
            awaitMainIdle()
            block(scenario)
        }
    }

    private fun withUrlActivity(
        shortURL: String = seededUrl.shortURL,
        block: (URLActivity) -> Unit,
    ) {
        withUrlScenario(shortURL) { scenario -> scenario.onActivity(block) }
    }

    private fun awaitMainIdle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun URLActivity.exitState(): UrlDetailExit = ViewModelProvider(this)[URLViewModel::class.java].exit.value

    private fun URLActivity.exportState(): QRCodeExport = ViewModelProvider(this)[URLViewModel::class.java].export.value

    private fun menuItem(itemId: Int): MenuItem = mockk { every { getItemId() } returns itemId }
}
