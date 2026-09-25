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
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import androidx.appcompat.widget.SeslSeekBar
import androidx.picker3.app.SeslColorPickerDialog
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.ShadowFileProvider
import de.lemke.oneurl.R
import de.lemke.oneurl.data.UserSettings
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import java.io.File
import javax.inject.Inject
import org.junit.After
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
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36], shadows = [ShadowFileProvider::class])
class GenerateQRCodeActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var userSettings: UserSettings

    @Before
    fun setup() {
        resetFileProviderCache()
        hiltRule.inject()
        userSettings.qrURL = "https://example.com"
    }

    @After
    fun tearDown() = resetFileProviderCache()

    @Test
    fun `onOptionsItemSelected handles save-as-image and returns true`() {
        withActivity { activity ->
            activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_save_as_image)).shouldBeTrue()
        }
    }

    @Test
    fun `onOptionsItemSelected handles share and starts the chooser with the qr code uri`() {
        withActivity { activity ->
            activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_share)).shouldBeTrue()

            val startedIntent = shadowOf(activity).nextStartedActivity
            startedIntent.action shouldBe Intent.ACTION_CHOOSER
            val shareIntent = startedIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
            shareIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) shouldBe activity.qrCodeContentUri("QRCode.png")
            (shareIntent.flags and FLAG_GRANT_READ_URI_PERMISSION) shouldBe FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    @Test
    fun `onOptionsItemSelected delegates unmapped items to super and returns false`() {
        withActivity { activity ->
            activity.onOptionsItemSelected(menuItem(Menu.NONE)).shouldBeFalse()
        }
    }

    @Test
    fun `export result OK with a qr code saves the bitmap`() {
        withActivity { activity ->
            activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_save_as_image))
            val shadowActivity = shadowOf(activity)
            val startedForResult = shadowActivity.peekNextStartedActivityForResult()!!
            // A fake content:// uri has no registered provider under Robolectric, so openOutputStream
            // throws and the write never happens; a real file:// uri is actually writable.
            val exportFile = File(activity.cacheDir, "export.png").also { it.createNewFile() }

            shadowActivity.receiveResult(
                startedForResult.intent,
                RESULT_OK,
                Intent().apply { data = Uri.fromFile(exportFile) },
            )

            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_image_saved)
            BitmapFactory.decodeFile(exportFile.path) shouldNotBe null
        }
    }

    @Test
    fun `export result OK without a destination uri shows the creating-file error toast`() {
        withActivity { activity ->
            activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_save_as_image))
            val shadowActivity = shadowOf(activity)
            val startedForResult = shadowActivity.peekNextStartedActivityForResult()!!

            shadowActivity.receiveResult(startedForResult.intent, RESULT_OK, Intent())

            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_error_creating_file)
        }
    }

    @Test
    fun `export result other than OK does not save`() {
        withActivity { activity ->
            activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_save_as_image))
            val shadowActivity = shadowOf(activity)
            val startedForResult = shadowActivity.peekNextStartedActivityForResult()!!

            shadowActivity.receiveResult(startedForResult.intent, RESULT_CANCELED, null)

            ShadowToast.getLatestToast() shouldBe null
        }
    }

    @Test
    fun `clicking the qr code image copies it to the clipboard`() {
        withActivity { activity ->
            activity.registerPngTypeProvider()

            activity.findViewById<View>(R.id.qr_code).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_copied_to_clipboard)
            val clip = activity.getSystemService(ClipboardManager::class.java).primaryClip
            clip?.getItemAt(0)?.uri shouldBe activity.qrCodeContentUri("QRCode.png")
        }
    }

    @Test
    fun `moving the size seekbar updates the size edittext`() {
        withActivity { activity ->
            val seekbar = activity.findViewById<SeslSeekBar>(R.id.size_seekbar)
            val sizeEditText = activity.findViewById<EditText>(R.id.size_edittext)

            seekbar.progress = 800

            sizeEditText.text.toString() shouldBe "800"
        }
    }

    @Test
    fun `confirming a size above the maximum clamps the seekbar to the maximum`() {
        withActivity { activity ->
            val sizeEditText = activity.findViewById<EditText>(R.id.size_edittext)
            val seekbar = activity.findViewById<SeslSeekBar>(R.id.size_seekbar)

            sizeEditText.setText("9999")
            sizeEditText.onEditorAction(EditorInfo.IME_ACTION_DONE)

            seekbar.progress shouldBe 1024
        }
    }

    @Test
    fun `confirming a size below the minimum clamps the seekbar to the minimum`() {
        withActivity { activity ->
            val sizeEditText = activity.findViewById<EditText>(R.id.size_edittext)
            val seekbar = activity.findViewById<SeslSeekBar>(R.id.size_seekbar)

            sizeEditText.setText("10")
            sizeEditText.onEditorAction(EditorInfo.IME_ACTION_DONE)

            seekbar.progress shouldBe 512
        }
    }

    @Test
    fun `confirming non-numeric size text leaves the seekbar unchanged`() {
        withActivity { activity ->
            val sizeEditText = activity.findViewById<EditText>(R.id.size_edittext)
            val seekbar = activity.findViewById<SeslSeekBar>(R.id.size_seekbar)
            val before = seekbar.progress

            sizeEditText.setText("abcd")
            sizeEditText.onEditorAction(EditorInfo.IME_ACTION_DONE)

            seekbar.progress shouldBe before
        }
    }

    @Test
    fun `a later state emission does not re-run initControls`() {
        withActivity { activity ->
            val urlField = activity.findViewById<EditText>(R.id.editTextURL)
            urlField.setSelection(3)

            activity.findViewById<CompoundButton>(R.id.icon_checkbox).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            urlField.selectionStart shouldBe 3
            urlField.selectionEnd shouldBe 3
        }
    }

    @Test
    fun `toggling the frame checkbox persists roundedFrame`() {
        withActivity { activity ->
            val checkbox = activity.findViewById<CompoundButton>(R.id.frame_checkbox)
            val expected = !checkbox.isChecked

            checkbox.performClick()

            userSettings.qrFrame shouldBe expected
        }
    }

    @Test
    fun `toggling the icon checkbox persists icon`() {
        withActivity { activity ->
            val checkbox = activity.findViewById<CompoundButton>(R.id.icon_checkbox)
            val expected = !checkbox.isChecked

            checkbox.performClick()

            userSettings.qrIcon shouldBe expected
        }
    }

    @Test
    fun `toggling the tint border checkbox persists tintBorder`() {
        withActivity { activity ->
            val checkbox = activity.findViewById<CompoundButton>(R.id.tint_border_checkbox)
            val expected = !checkbox.isChecked

            checkbox.performClick()

            userSettings.qrTintBorder shouldBe expected
        }
    }

    @Test
    fun `toggling the tint anchor checkbox persists tintAnchor`() {
        withActivity { activity ->
            val checkbox = activity.findViewById<CompoundButton>(R.id.tint_anchor_checkbox)
            val expected = !checkbox.isChecked

            checkbox.performClick()

            userSettings.qrTintAnchor shouldBe expected
        }
    }

    @Test
    fun `confirming the background color picker persists the picked color`() {
        withActivity { activity ->
            activity.findViewById<View>(R.id.color_button_background).performClick()
            val dialog = ShadowDialog.getLatestDialog() as SeslColorPickerDialog
            dialog.isShowing shouldBe true

            dialog.setNewColor(0x336699)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            // AlertController dispatches the button's DialogInterface.OnClickListener through a
            // Handler message rather than calling it inline from performClick().
            shadowOf(Looper.getMainLooper()).idle()

            userSettings.qrRecentBackgroundColors.first() shouldBe 0x336699
        }
    }

    @Test
    fun `confirming the foreground color picker persists the picked color`() {
        withActivity { activity ->
            activity.findViewById<View>(R.id.color_button_foreground).performClick()
            val dialog = ShadowDialog.getLatestDialog() as SeslColorPickerDialog
            dialog.isShowing shouldBe true

            dialog.setNewColor(0x998877)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            userSettings.qrRecentForegroundColors.first() shouldBe 0x998877
        }
    }

    @Test
    fun `color buttons show the default white background and black foreground swatches`() {
        withActivity { activity ->
            val backgroundButton = activity.findViewById<Button>(R.id.color_button_background)
            val foregroundButton = activity.findViewById<Button>(R.id.color_button_foreground)

            backgroundButton.isEnabled.shouldBeTrue()
            backgroundButton.backgroundTintList?.defaultColor shouldBe Color.WHITE
            backgroundButton.currentTextColor shouldBe Color.BLACK
            foregroundButton.isEnabled.shouldBeTrue()
            foregroundButton.backgroundTintList?.defaultColor shouldBe Color.BLACK
            foregroundButton.currentTextColor shouldBe Color.WHITE
        }
    }

    @Test
    fun `color buttons show the most recent stored colors as swatches`() {
        userSettings.qrRecentBackgroundColors = listOf(Color.BLACK, Color.RED)
        userSettings.qrRecentForegroundColors = listOf(Color.WHITE, Color.BLUE)

        withActivity { activity ->
            val backgroundButton = activity.findViewById<Button>(R.id.color_button_background)
            val foregroundButton = activity.findViewById<Button>(R.id.color_button_foreground)

            backgroundButton.backgroundTintList?.defaultColor shouldBe Color.BLACK
            backgroundButton.currentTextColor shouldBe Color.WHITE
            foregroundButton.backgroundTintList?.defaultColor shouldBe Color.WHITE
            foregroundButton.currentTextColor shouldBe Color.BLACK
        }
    }

    @Test
    fun `confirming the background color picker rebinds the background swatch`() {
        withActivity { activity ->
            val backgroundButton = activity.findViewById<Button>(R.id.color_button_background)
            backgroundButton.performClick()
            val dialog = ShadowDialog.getLatestDialog() as SeslColorPickerDialog

            dialog.setNewColor(0xFF336699.toInt())
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            backgroundButton.backgroundTintList?.defaultColor shouldBe 0xFF336699.toInt()
            backgroundButton.currentTextColor shouldBe Color.WHITE
        }
    }

    @Test
    fun `confirming the foreground color picker rebinds the foreground swatch`() {
        withActivity { activity ->
            val foregroundButton = activity.findViewById<Button>(R.id.color_button_foreground)
            foregroundButton.performClick()
            val dialog = ShadowDialog.getLatestDialog() as SeslColorPickerDialog

            dialog.setNewColor(0xFFEEEEEE.toInt())
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            foregroundButton.backgroundTintList?.defaultColor shouldBe 0xFFEEEEEE.toInt()
            foregroundButton.currentTextColor shouldBe Color.BLACK
        }
    }

    @Test
    fun `a translucent black swatch over the light window background gets black text`() {
        userSettings.qrRecentBackgroundColors = listOf(0x80000000.toInt())

        withActivity { activity ->
            val backgroundButton = activity.findViewById<Button>(R.id.color_button_background)

            backgroundButton.backgroundTintList?.defaultColor shouldBe 0x80000000.toInt()
            backgroundButton.currentTextColor shouldBe Color.BLACK
        }
    }

    private fun withActivity(block: (GenerateQRCodeActivity) -> Unit) {
        ActivityScenario.launch(GenerateQRCodeActivity::class.java).use { scenario -> scenario.onActivity(block) }
    }

    private fun menuItem(itemId: Int): MenuItem = mockk { every { getItemId() } returns itemId }
}
