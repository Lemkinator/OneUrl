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
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Environment
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.ShadowFileProvider
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.oneurl.R
import de.lemke.oneurl.data.QRCodeCache
import de.lemke.oneurl.data.QRCodeExporter
import de.lemke.oneurl.di.QRCodeExporterModule
import de.lemke.oneurl.ui.QRBottomSheet.Companion.createQRBottomSheet
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import de.lemke.commonutils.R as commonutilsR

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
//
// isSamsungQuickShareAvailable() checks for the "com.samsung.android.app.sharelive" package via
// PackageManager; Robolectric's default shadow PackageManager never has it installed, so
// quickShareButton stays at its XML default (gone) in every test here.
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36], shadows = [ShadowFileProvider::class])
@UninstallModules(QRCodeExporterModule::class)
class QRBottomSheetTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    private val ioDispatcher = PausableDispatcher(Dispatchers.Main)

    @BindValue
    @JvmField
    val qrCodeExporter: QRCodeExporter = qrCodeExporterOn(ioDispatcher)

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var qrCodeCache: QRCodeCache

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
    }

    private fun freshQrBitmap(): Bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    private fun MainActivity.qrBottomSheet(): QRBottomSheet = supportFragmentManager.findFragmentByTag("qr") as QRBottomSheet

    private fun withQrBottomSheet(
        shortURL: String = "https://short.url/qr",
        qrCode: Bitmap = freshQrBitmap(),
        saveLocation: SaveLocation = SaveLocation.CUSTOM,
        block: (MainActivity, QRBottomSheet) -> Unit,
    ) {
        qrCodeCache[shortURL] = qrCode
        settings.imageSaveLocation = saveLocation
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val sheet = createQRBottomSheet(shortURL)
                sheet.show(activity.supportFragmentManager, "qr")
                activity.supportFragmentManager.executePendingTransactions()
                shadowOf(Looper.getMainLooper()).idle()
                block(activity, sheet)
            }
        }
    }

    @Test
    fun `onCreateDialog skips the collapsed state and expands the bottom sheet on show`() {
        withQrBottomSheet { _, sheet ->
            val dialog = sheet.dialog as BottomSheetDialog
            dialog.behavior.skipCollapsed.shouldBeTrue()
            dialog.behavior.state shouldBe BottomSheetBehavior.STATE_EXPANDED
        }
    }

    @Test
    fun `onViewCreated binds the title and the qr bitmap`() {
        val qr = freshQrBitmap()
        withQrBottomSheet(shortURL = "https://short.url/qr-title", qrCode = qr) { _, sheet ->
            val view = sheet.requireView()
            view.findViewById<TextView>(R.id.title).text.toString() shouldBe "https://short.url/qr-title"
            val shown = (view.findViewById<ImageView>(R.id.qrCode).drawable as BitmapDrawable).bitmap
            shown shouldBe qr
        }
    }

    @Test
    fun `onViewCreated leaves quick share hidden when Samsung Quick Share is unavailable`() {
        withQrBottomSheet { _, sheet ->
            sheet
                .requireView()
                .findViewById<View>(R.id.quickShareButton)
                .isVisible
                .shouldBeFalse()
        }
    }

    @Test
    fun `onViewCreated shows quick share when Samsung Quick Share is available`() {
        qrCodeCache["https://short.url/quick-share"] = freshQrBitmap()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                shadowOf(activity.packageManager).installPackage(
                    PackageInfo().also { it.packageName = "com.samsung.android.app.sharelive" },
                )
                val sheet = createQRBottomSheet("https://short.url/quick-share")
                sheet.show(activity.supportFragmentManager, "qr-quick-share")
                activity.supportFragmentManager.executePendingTransactions()
                shadowOf(Looper.getMainLooper()).idle()

                sheet
                    .requireView()
                    .findViewById<View>(R.id.quickShareButton)
                    .isVisible
                    .shouldBeTrue()
            }
        }
    }

    @Test
    fun `onViewCreated with no short url leaves the title empty and the qr buttons disabled`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val sheet = QRBottomSheet()
                sheet.show(activity.supportFragmentManager, "qr-empty")
                activity.supportFragmentManager.executePendingTransactions()
                shadowOf(Looper.getMainLooper()).idle()

                val view = sheet.requireView()
                view.findViewById<TextView>(R.id.title).text.toString() shouldBe ""
                val buttons = listOf(R.id.saveButton, R.id.shareButton, R.id.quickShareButton).map { view.findViewById<View>(it) }
                buttons.map { it.isEnabled } shouldBe listOf(false, false, false)
                view.findViewById<View>(R.id.saveButton).performClick()
                shadowOf(Looper.getMainLooper()).idle()
                shadowOf(activity).nextStartedActivityForResult shouldBe null
            }
        }
    }

    @Test
    fun `share button click starts the share chooser`() {
        withQrBottomSheet { activity, sheet ->
            sheet.requireView().findViewById<View>(R.id.shareButton).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            val startedIntent = shadowOf(activity).nextStartedActivity
            startedIntent.action shouldBe Intent.ACTION_CHOOSER
            val shareIntent = startedIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
            val stream = shareIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)!!
            stream.toString() shouldMatch activity.qrCodeContentUriPattern("share", "QRCode.png")
            activity.cacheFile(stream).length() shouldBeGreaterThan 0L
            (shareIntent.flags and FLAG_GRANT_READ_URI_PERMISSION) shouldBe FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    @Test
    fun `double tap on the share button opens one chooser`() {
        withQrBottomSheet { activity, sheet ->
            val shareButton = sheet.requireView().findViewById<View>(R.id.shareButton)

            shareButton.performClick()
            shareButton.performClick()
            shadowOf(Looper.getMainLooper()).idle()

            val shadowActivity = shadowOf(activity)
            shadowActivity.nextStartedActivity.action shouldBe Intent.ACTION_CHOOSER
            shadowActivity.nextStartedActivity shouldBe null
        }
    }

    @Test
    fun `double tap on the quick share button sends the qr code once without a chooser`() {
        withQrBottomSheet { activity, sheet ->
            val quickShareButton = sheet.requireView().findViewById<View>(R.id.quickShareButton)

            quickShareButton.performClick()
            quickShareButton.performClick()
            shadowOf(Looper.getMainLooper()).idle()

            val shadowActivity = shadowOf(activity)
            val sendIntent = shadowActivity.nextStartedActivity
            sendIntent.action shouldBe Intent.ACTION_SEND
            sendIntent.type shouldBe "image/png"
            sendIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java).toString() shouldMatch
                activity.qrCodeContentUriPattern("share", "QRCode.png")
            shadowActivity.nextStartedActivity shouldBe null
        }
    }

    @Test
    fun `save button click launches the export picker`() {
        withQrBottomSheet { activity, sheet ->
            sheet.requireView().findViewById<View>(R.id.saveButton).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            val startedForResult = shadowOf(activity).peekNextStartedActivityForResult()
            startedForResult shouldNotBe null
            startedForResult.intent.action shouldBe Intent.ACTION_CREATE_DOCUMENT
        }
    }

    @Test
    fun `double tap on save with a fixed location writes one file and shows one toast`() {
        withQrBottomSheet(saveLocation = SaveLocation.DOWNLOADS) { _, sheet ->
            val saveButton = sheet.requireView().findViewById<View>(R.id.saveButton)

            saveButton.performClick()
            saveButton.performClick()
            shadowOf(Looper.getMainLooper()).idle()

            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).listFiles()?.size shouldBe 1
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Image saved: Downloads"
        }
    }

    @Test
    fun `export result OK saves the bound qr bitmap`() {
        withQrBottomSheet { activity, sheet ->
            sheet.requireView().findViewById<View>(R.id.saveButton).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            val shadowActivity = shadowOf(activity)
            val startedForResult = shadowActivity.peekNextStartedActivityForResult()!!
            // A real, writable file:// uri - a fake content:// uri has no registered provider under
            // Robolectric, so openOutputStream throws and the write never actually happens.
            val exportFile = File(activity.cacheDir, "export.png").also { it.createNewFile() }

            shadowActivity.receiveResult(
                startedForResult.intent,
                RESULT_OK,
                Intent().apply { data = Uri.fromFile(exportFile) },
            )
            shadowOf(Looper.getMainLooper()).idle()

            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_image_saved)
            exportFile.length() shouldBeGreaterThan 0L
        }
    }

    @Test
    fun `export result cancelled does not save and shows no toast`() {
        withQrBottomSheet { activity, sheet ->
            sheet.requireView().findViewById<View>(R.id.saveButton).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            val shadowActivity = shadowOf(activity)
            val startedForResult = shadowActivity.peekNextStartedActivityForResult()!!

            shadowActivity.receiveResult(startedForResult.intent, RESULT_CANCELED, null)
            shadowOf(Looper.getMainLooper()).idle()

            ShadowToast.getLatestToast() shouldBe null
        }
    }

    @Test
    fun `export result with an unknown code and a uri writes nothing and shows no toast`() {
        val document = createPickedDocument()
        withQrBottomSheet { activity, sheet ->
            sheet.requireView().findViewById<View>(R.id.saveButton).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            activity.receiveDocumentPickerResult(RESULT_FIRST_USER, Intent().setData(Uri.fromFile(document)))
            shadowOf(Looper.getMainLooper()).idle()

            document.length() shouldBe 0L
            ShadowToast.shownToastCount() shouldBe 0
        }
    }

    @Test
    fun `export write that the document provider refuses deletes the document and shows the creating-file error toast`() {
        val provider = readOnlyDocumentProvider()
        withQrBottomSheet { activity, sheet ->
            sheet.requireView().findViewById<View>(R.id.saveButton).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            activity.receiveDocumentPickerResult(RESULT_OK, Intent().setData(provider.uri))
            shadowOf(Looper.getMainLooper()).idle()

            provider.document.exists().shouldBeFalse()
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Error creating file"
        }
    }

    @Test
    fun `export write that runs during a recreation finishes and toasts once in the recreated sheet`() {
        val document = createPickedDocument()
        qrCodeCache["https://short.url/qr"] = freshQrBitmap()
        settings.imageSaveLocation = SaveLocation.CUSTOM
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                createQRBottomSheet("https://short.url/qr").show(activity.supportFragmentManager, "qr")
                activity.supportFragmentManager.executePendingTransactions()
                shadowOf(Looper.getMainLooper()).idle()
                activity
                    .qrBottomSheet()
                    .requireView()
                    .findViewById<View>(R.id.saveButton)
                    .performClick()
            }
            shadowOf(Looper.getMainLooper()).idle()
            ioDispatcher.pause()
            scenario.onActivity { activity ->
                activity.receiveDocumentPickerResult(RESULT_OK, Intent().setData(Uri.fromFile(document)))
            }
            shadowOf(Looper.getMainLooper()).idle()
            document.length() shouldBe 0L

            scenario.recreate()
            ioDispatcher.resume()
            shadowOf(Looper.getMainLooper()).idle()

            document.readBytes().take(PNG_SIGNATURE.size) shouldBe PNG_SIGNATURE
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Image saved"
            scenario.onActivity { activity -> activity.qrBottomSheet().isAdded.shouldBeTrue() }
        }
    }

    @Test
    fun `qr buttons are disabled while a share writes`() {
        withQrBottomSheet { activity, sheet ->
            val view = sheet.requireView()
            val buttons = listOf(R.id.saveButton, R.id.shareButton, R.id.quickShareButton).map { view.findViewById<View>(it) }
            ioDispatcher.pause()

            view.findViewById<View>(R.id.shareButton).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            buttons.map { it.isEnabled } shouldBe listOf(false, false, false)
            ioDispatcher.resume()
            shadowOf(Looper.getMainLooper()).idle()
            buttons.map { it.isEnabled } shouldBe listOf(true, true, true)
            shadowOf(activity).nextStartedActivity.action shouldBe Intent.ACTION_CHOOSER
        }
    }

    @Test
    fun `export result OK without a destination uri shows the creating-file error toast`() {
        withQrBottomSheet { activity, sheet ->
            sheet.requireView().findViewById<View>(R.id.saveButton).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            val shadowActivity = shadowOf(activity)
            val startedForResult = shadowActivity.peekNextStartedActivityForResult()!!

            shadowActivity.receiveResult(startedForResult.intent, RESULT_OK, Intent())
            shadowOf(Looper.getMainLooper()).idle()

            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_error_creating_file)
        }
    }
}
