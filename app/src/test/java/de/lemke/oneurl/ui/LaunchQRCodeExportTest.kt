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

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import de.lemke.commonutils.ui.utils.BitmapShareFile
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class LaunchQRCodeExportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val picker = RecordingActivityResultLauncher()
    private val shareFile = BitmapShareFile.Written(Uri.parse("content://de.lemke.oneurl.test/share/QRCode.png"))

    @Test
    fun `CopyFailed shows the share error toast and is handled`() {
        context.launchQRCodeExport(QRCodeExport.CopyFailed, picker).shouldBeTrue()

        ShadowToast.getTextOfLatestToast() shouldBe SHARE_ERROR
        picker.launched shouldBe emptyList()
    }

    @Test
    fun `ShareFailed shows the share error toast and is handled`() {
        context.launchQRCodeExport(QRCodeExport.ShareFailed, picker).shouldBeTrue()

        ShadowToast.getTextOfLatestToast() shouldBe SHARE_ERROR
        picker.launched shouldBe emptyList()
    }

    @Test
    fun `OpenPicker that the latch admits launches one document picker and is handled`() {
        context.launchQRCodeExport(QRCodeExport.OpenPicker("QRCode"), picker).shouldBeTrue()

        picker.launched.map { it.action } shouldBe listOf(Intent.ACTION_CREATE_DOCUMENT)
        ShadowToast.shownToastCount() shouldBe 0
    }

    @Test
    fun `OpenPicker that the latch drops stays unhandled`() {
        picker.admits = false

        context.launchQRCodeExport(QRCodeExport.OpenPicker("QRCode"), picker).shouldBeFalse()

        picker.launched shouldBe emptyList()
        ShadowToast.shownToastCount() shouldBe 0
    }

    @Test
    fun `OpenPicker without a document picker shows the saving error toast and is handled`() {
        picker.failure = ActivityNotFoundException()

        context.launchQRCodeExport(QRCodeExport.OpenPicker("QRCode"), picker).shouldBeTrue()

        ShadowToast.shownToastCount() shouldBe 1
        ShadowToast.getTextOfLatestToast() shouldBe "Error: Saving content is not supported on your device."
    }

    @Test
    fun `Share to the share sheet that no app can receive shows the share error toast and is handled`() {
        shadowOf(context as Application).checkActivities(true)

        context.launchQRCodeExport(QRCodeExport.Share(shareFile, ShareTarget.SHARE_SHEET), picker).shouldBeTrue()

        ShadowToast.shownToastCount() shouldBe 1
        ShadowToast.getTextOfLatestToast() shouldBe SHARE_ERROR
    }

    @Test
    fun `Share to Quick Share that no app can receive shows the share error toast and is handled`() {
        shadowOf(context as Application).checkActivities(true)

        context.launchQRCodeExport(QRCodeExport.Share(shareFile, ShareTarget.QUICK_SHARE), picker).shouldBeTrue()

        ShadowToast.shownToastCount() shouldBe 1
        ShadowToast.getTextOfLatestToast() shouldBe SHARE_ERROR
    }

    private companion object {
        const val SHARE_ERROR = "Error: Sharing content is not supported on your device."
    }
}
