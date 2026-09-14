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
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SaveLocation
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.oneurl.R
import de.lemke.oneurl.ui.QRBottomSheet.Companion.createQRBottomSheet
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import javax.inject.Inject
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
// isSamsungQuickShareAvailable() checks for the "com.samsung.android.app.sharelive" package via
// PackageManager; Robolectric's default shadow PackageManager never has it installed, so
// quickShareButton stays at its XML default (gone) in every test here.
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
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
            // Bitmap round-trips through a PNG-encoded byte array in the fragment's arguments, so
            // the decoded instance is never reference-equal to the original - compare content.
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
    fun `share button click starts the share chooser`() {
        mockkStatic(FileProvider::class)
        every { FileProvider.getUriForFile(any(), any(), any()) } returns "content://de.lemke.test.fileprovider/QRCode.png".toUri()
        try {
            withQrBottomSheet { activity, sheet ->
                sheet.requireView().findViewById<View>(R.id.shareButton).performClick()

                val startedIntent = shadowOf(activity).nextStartedActivity
                startedIntent.action shouldBe Intent.ACTION_CHOOSER
            }
        } finally {
            unmockkAll()
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

            shadowActivity.receiveResult(
                startedForResult.intent,
                RESULT_OK,
                Intent().apply { data = "content://de.lemke.oneurl.debug.fileprovider/export.png".toUri() },
            )

            ShadowToast.getLatestToast() shouldNotBe null
        }
    }
}
