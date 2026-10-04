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
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.confirmVerified
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import de.lemke.commonutils.R as commonutilsR

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class LaunchQRCodeExportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val picker = mockk<ActivityResultLauncher<Intent>>()
    private val shareErrorText = context.getString(commonutilsR.string.commonutils_error_share_content_not_supported_on_device)

    @Test
    fun `CopyFailed shows the share error toast and is handled`() {
        context.launchQRCodeExport(QRCodeExport.CopyFailed, picker).shouldBeTrue()

        ShadowToast.getTextOfLatestToast() shouldBe shareErrorText
        confirmVerified(picker)
    }

    @Test
    fun `ShareFailed shows the share error toast and is handled`() {
        context.launchQRCodeExport(QRCodeExport.ShareFailed, picker).shouldBeTrue()

        ShadowToast.getTextOfLatestToast() shouldBe shareErrorText
        confirmVerified(picker)
    }
}
