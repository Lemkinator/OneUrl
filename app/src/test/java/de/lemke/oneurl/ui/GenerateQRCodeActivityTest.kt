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
import android.widget.PopupMenu
import androidx.appcompat.widget.SeslSeekBar
import androidx.lifecycle.ViewModelProvider
import androidx.picker3.app.SeslColorPickerDialog
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import de.lemke.commonutils.ShadowFileProvider
import de.lemke.oneurl.R
import de.lemke.oneurl.TestDatabaseRule
import de.lemke.oneurl.data.QRCodeExporter
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.di.QRCodeExporterModule
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.mockk.every
import io.mockk.mockk
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
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import de.lemke.commonutils.R as commonutilsR

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36], shadows = [ShadowFileProvider::class])
@UninstallModules(QRCodeExporterModule::class)
class GenerateQRCodeActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val testDatabase = TestDatabaseRule()

    private val ioDispatcher = PausableDispatcher(Dispatchers.Main)

    @BindValue
    @JvmField
    val qrCodeExporter: QRCodeExporter = qrCodeExporterOn(ioDispatcher)

    @Inject
    lateinit var userSettings: UserSettings

    @Before
    fun setup() {
        hiltRule.inject()
        userSettings.qrURL = "https://example.com"
    }

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
    fun `share that no app can receive shows the share error toast and stays unhandled`() {
        withActivity { activity ->
            shadowOf(activity.application).checkActivities(true)

            activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_share)).shouldBeTrue()
            shadowOf(Looper.getMainLooper()).idle()

            shadowOf(activity).nextStartedActivity shouldBe null
            ShadowToast.getTextOfLatestToast() shouldBe
                activity.getString(commonutilsR.string.commonutils_error_share_content_not_supported_on_device)
            val export = ViewModelProvider(activity)[GenerateQRCodeViewModel::class.java].export.value
            (export is QRCodeExport.Share).shouldBeTrue()
        }
    }

    @Test
    fun `double tap on share opens one chooser for one written file`() {
        withActivity { activity ->
            activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_share)).shouldBeTrue()
            activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_share)).shouldBeTrue()
            shadowOf(Looper.getMainLooper()).idle()

            val shadowActivity = shadowOf(activity)
            shadowActivity.nextStartedActivity.action shouldBe Intent.ACTION_CHOOSER
            shadowActivity.nextStartedActivity shouldBe null
            File(activity.cacheDir, "share").walk().count { it.isFile } shouldBe 1
        }
    }

    @Test
    fun `double tap on save-as-image launches one document picker`() {
        withActivity { activity ->
            activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_save_as_image)).shouldBeTrue()
            activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_save_as_image)).shouldBeTrue()
            shadowOf(Looper.getMainLooper()).idle()

            val shadowActivity = shadowOf(activity)
            shadowActivity.nextStartedActivityForResult.intent.action shouldBe Intent.ACTION_CREATE_DOCUMENT
            shadowActivity.nextStartedActivityForResult shouldBe null
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
            shadowOf(Looper.getMainLooper()).idle()
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
            shadowOf(Looper.getMainLooper()).idle()

            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_image_saved)
            BitmapFactory.decodeFile(exportFile.path) shouldNotBe null
        }
    }

    @Test
    fun `export result OK without a destination uri shows the creating-file error toast`() {
        withActivity { activity ->
            activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_save_as_image))
            shadowOf(Looper.getMainLooper()).idle()
            val shadowActivity = shadowOf(activity)
            val startedForResult = shadowActivity.peekNextStartedActivityForResult()!!

            shadowActivity.receiveResult(startedForResult.intent, RESULT_OK, Intent())
            shadowOf(Looper.getMainLooper()).idle()

            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_error_creating_file)
        }
    }

    @Test
    fun `export result other than OK does not save`() {
        withActivity { activity ->
            activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_save_as_image))
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
        withActivity { activity ->
            activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_save_as_image))
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
        withActivity { activity ->
            activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_save_as_image))
            shadowOf(Looper.getMainLooper()).idle()

            activity.receiveDocumentPickerResult(RESULT_OK, Intent().setData(provider.uri))
            shadowOf(Looper.getMainLooper()).idle()

            provider.document.exists().shouldBeFalse()
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe "Error creating file"
        }
    }

    @Test
    fun `export write that runs during a recreation finishes and toasts once in the recreated activity`() {
        val document = createPickedDocument()
        ActivityScenario.launch(GenerateQRCodeActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_save_as_image)).shouldBeTrue()
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
        }
    }

    @Test
    fun `export controls are disabled while a share writes and the size seekbar stays usable`() {
        ActivityScenario.launch(GenerateQRCodeActivity::class.java).use { scenario ->
            ioDispatcher.pause()
            scenario.onActivity { activity ->
                activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_share)).shouldBeTrue()
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.qr_code).isClickable.shouldBeFalse()
                activity.preparedExportMenuItemsEnabled() shouldBe listOf(false, false)

                activity.findViewById<SeslSeekBar>(R.id.size_seekbar).progress = 800

                ViewModelProvider(activity)[GenerateQRCodeViewModel::class.java].state.value.size shouldBe 800
            }

            ioDispatcher.resume()
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.qr_code).isClickable.shouldBeTrue()
                activity.preparedExportMenuItemsEnabled() shouldBe listOf(true, true)
                shadowOf(activity).nextStartedActivity.action shouldBe Intent.ACTION_CHOOSER
            }
        }
    }

    @Test
    fun `clicking the qr code image copies it to the clipboard`() {
        withActivity { activity ->
            activity.findViewById<View>(R.id.qr_code).performClick()
            shadowOf(Looper.getMainLooper()).idle()

            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_copied_to_clipboard)
            val clip = activity.getSystemService(ClipboardManager::class.java).primaryClip!!
            val uri = clip.getItemAt(0).uri
            uri.toString() shouldMatch activity.qrCodeContentUriPattern("clipboard", "QRCode.png")
            clip.description.label shouldBe "QR Code"
            clip.description.getMimeType(0) shouldBe "image/png"
            activity.contentResolver.getType(uri) shouldBe "image/png"
        }
    }

    @Test
    fun `double tap on the qr code image copies one clip with one toast`() {
        withActivity { activity ->
            var clipChanges = 0
            activity.getSystemService(ClipboardManager::class.java).addPrimaryClipChangedListener { clipChanges++ }
            val qrCode = activity.findViewById<View>(R.id.qr_code)

            qrCode.performClick()
            qrCode.performClick()
            shadowOf(Looper.getMainLooper()).idle()

            clipChanges shouldBe 1
            ShadowToast.shownToastCount() shouldBe 1
            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(commonutilsR.string.commonutils_copied_to_clipboard)
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
    fun `double tap on a color button shows one color picker`() {
        withActivity { activity ->
            val backgroundButton = activity.findViewById<View>(R.id.color_button_background)

            backgroundButton.performClick()
            backgroundButton.performClick()

            ShadowDialog.getShownDialogs().size shouldBe 1
            ShadowDialog.getLatestDialog().isShowing.shouldBeTrue()
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

    private fun GenerateQRCodeActivity.preparedExportMenuItemsEnabled(): List<Boolean> {
        val menu = PopupMenu(this, findViewById(R.id.qr_code)).menu
        menuInflater.inflate(R.menu.menu_qr, menu)
        onPrepareOptionsMenu(menu)
        return listOf(R.id.menu_item_qr_save_as_image, R.id.menu_item_qr_share).map { menu.findItem(it).isEnabled }
    }
}
