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
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.ShadowFileProvider
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.oneurl.R
import de.lemke.oneurl.ui.QRBottomSheet.Companion.createQRBottomSheet
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import javax.inject.Inject
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
class QRBottomSheetTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var settings: SettingsRepository

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
    }

    private fun freshQrBitmap(): Bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    private fun withQrBottomSheet(
        title: String = "https://short.url/qr",
        qrCode: Bitmap = freshQrBitmap(),
        saveLocation: SaveLocation = SaveLocation.CUSTOM,
        block: (MainActivity, QRBottomSheet) -> Unit,
    ) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val sheet = createQRBottomSheet(title, qrCode, saveLocation)
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
        withQrBottomSheet(title = "https://short.url/qr-title", qrCode = qr) { _, sheet ->
            val view = sheet.requireView()
            view.findViewById<TextView>(R.id.title).text.toString() shouldBe "https://short.url/qr-title"
            val shown = (view.findViewById<ImageView>(R.id.qrCode).drawable as BitmapDrawable).bitmap
            shown.sameAs(qr).shouldBeTrue()
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
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                shadowOf(activity.packageManager).installPackage(
                    PackageInfo().also { it.packageName = "com.samsung.android.app.sharelive" },
                )
                val sheet = createQRBottomSheet("https://short.url/quick-share", freshQrBitmap(), SaveLocation.CUSTOM)
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
    fun `onViewCreated with no bundled qr leaves the title bound and skips wiring the qr buttons`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val sheet = QRBottomSheet()
                sheet.show(activity.supportFragmentManager, "qr-empty")
                activity.supportFragmentManager.executePendingTransactions()
                shadowOf(Looper.getMainLooper()).idle()

                val view = sheet.requireView()
                view.findViewById<TextView>(R.id.title).text.toString() shouldBe ""
                view.findViewById<View>(R.id.saveButton).performClick().shouldBeFalse()
            }
        }
    }

    @Test
    fun `share button click starts the share chooser`() {
        withQrBottomSheet { activity, sheet ->
            sheet.requireView().findViewById<View>(R.id.shareButton).performClick()

            val startedIntent = shadowOf(activity).nextStartedActivity
            startedIntent.action shouldBe Intent.ACTION_CHOOSER
            val shareIntent = startedIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
            shareIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) shouldBe activity.qrCodeContentUri("QRCode.png")
            (shareIntent.flags and FLAG_GRANT_READ_URI_PERMISSION) shouldBe FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    @Test
    fun `save button click launches the export picker`() {
        withQrBottomSheet { activity, sheet ->
            sheet.requireView().findViewById<View>(R.id.saveButton).performClick()

            val startedForResult = shadowOf(activity).peekNextStartedActivityForResult()
            startedForResult shouldNotBe null
        }
    }

    @Test
    fun `export result OK saves the bound qr bitmap`() {
        withQrBottomSheet { activity, sheet ->
            sheet.requireView().findViewById<View>(R.id.saveButton).performClick()
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

            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_image_saved)
        }
    }

    @Test
    fun `export result cancelled does not save and shows no toast`() {
        withQrBottomSheet { activity, sheet ->
            sheet.requireView().findViewById<View>(R.id.saveButton).performClick()
            val shadowActivity = shadowOf(activity)
            val startedForResult = shadowActivity.peekNextStartedActivityForResult()!!

            shadowActivity.receiveResult(startedForResult.intent, RESULT_CANCELED, null)

            ShadowToast.getLatestToast() shouldBe null
        }
    }

    @Test
    fun `export result OK without a destination uri shows the creating-file error toast`() {
        withQrBottomSheet { activity, sheet ->
            sheet.requireView().findViewById<View>(R.id.saveButton).performClick()
            val shadowActivity = shadowOf(activity)
            val startedForResult = shadowActivity.peekNextStartedActivityForResult()!!

            shadowActivity.receiveResult(startedForResult.intent, RESULT_OK, Intent())

            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_error_creating_file)
        }
    }
}
