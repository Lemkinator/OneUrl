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

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import androidx.appcompat.R as appcompatR
import de.lemke.commonutils.R as commonutilsR

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class AddURLErrorDialogsTest {
    private fun themedActivity(): Activity =
        Robolectric
            .buildActivity(Activity::class.java)
            .setup()
            .get()
            .apply { setTheme(commonutilsR.style.CommonUtils_AppTheme) }

    private fun dialogFor(
        context: Context,
        error: GenerateURLError,
    ): AlertDialog =
        AlertDialog.Builder(context).apply { configureFor(error) }.create().apply {
            show()
            shadowOf(Looper.getMainLooper()).idle()
        }

    private class ThrowingStartActivityContext(base: Context) : ContextWrapper(base) {
        override fun startActivity(intent: Intent): Unit = throw ActivityNotFoundException("no settings app")
    }

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    private fun AlertDialog.messageText(): String? = findViewById<TextView>(android.R.id.message)?.text?.toString()

    private fun AlertDialog.titleText(): String? = findViewById<TextView>(appcompatR.id.alertTitle)?.text?.toString()

    @Test
    fun `configureFor NoInternet sets title and message and opens wireless settings from the positive button`() {
        val activity = themedActivity()
        val dialog = dialogFor(activity, GenerateURLError.NoInternet)

        dialog.titleText() shouldBe activity.getString(R.string.no_internet)
        dialog.messageText() shouldBe activity.getString(R.string.no_internet_text)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMainLooper()

        shadowOf(activity).nextStartedActivity.action shouldBe Settings.ACTION_WIRELESS_SETTINGS
    }

    @Test
    fun `configureFor NoInternet toasts an error when wireless settings cannot be opened`() {
        val context = ThrowingStartActivityContext(themedActivity())
        val dialog = dialogFor(context, GenerateURLError.NoInternet)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMainLooper()

        ShadowToast.getTextOfLatestToast() shouldBe context.getString(commonutilsR.string.commonutils_error)
    }

    @Test
    fun `configureFor BlacklistedURL with a custom message shows only the urlhaus button`() {
        val activity = themedActivity()
        val error = GenerateURLError.BlacklistedURL(message = "custom blacklist message", urlhausLink = "https://urlhaus.example/1")
        val dialog = dialogFor(activity, error)

        dialog.titleText() shouldBe activity.getString(commonutilsR.string.commonutils_error)
        dialog.messageText() shouldBe error.message
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isVisible.shouldBeFalse()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString() shouldBe activity.getString(R.string.url_safety_urlhaus)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMainLooper()
        shadowOf(activity).nextStartedActivity.data.toString() shouldBe error.urlhausLink
    }

    @Test
    fun `configureFor BlacklistedURL without a message falls back to the default text and shows only the virustotal button`() {
        val activity = themedActivity()
        val error = GenerateURLError.BlacklistedURL(virustotalLink = "https://virustotal.example/1")
        val dialog = dialogFor(activity, error)

        dialog.messageText() shouldBe activity.getString(R.string.error_blacklisted_url)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isVisible.shouldBeFalse()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text.toString() shouldBe activity.getString(R.string.url_safety_virustotal)

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        idleMainLooper()
        shadowOf(activity).nextStartedActivity.data.toString() shouldBe error.virustotalLink
    }

    @Test
    fun `configureFor BlacklistedURL with neither link shows no buttons`() {
        val activity = themedActivity()
        val dialog = dialogFor(activity, GenerateURLError.BlacklistedURL())

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isVisible.shouldBeFalse()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isVisible.shouldBeFalse()
    }

    @Test
    fun `configureFor ServiceTemporarilyUnavailable opens the provider base url from the positive button`() {
        val activity = themedActivity()
        val error = GenerateURLError.ServiceTemporarilyUnavailable(providerBaseURL = "https://provider.example")
        val dialog = dialogFor(activity, error)

        dialog.titleText() shouldBe activity.getString(R.string.error_service_unavailable)
        dialog.messageText() shouldBe activity.getString(R.string.error_service_unavailable_text)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMainLooper()
        shadowOf(activity).nextStartedActivity.data.toString() shouldBe error.providerBaseURL
    }

    @Test
    fun `configureFor Custom with a custom title uses it verbatim`() {
        val activity = themedActivity()
        val error = GenerateURLError.Custom(statusCode = 418, customMessage = "teapot", customTitle = "I'm a teapot")
        val dialog = dialogFor(activity, error)

        dialog.titleText() shouldBe error.customTitle
        dialog.messageText() shouldBe error.customMessage
    }

    @Test
    fun `configureFor Custom without a custom title falls back to the generic error title with the status code`() {
        val activity = themedActivity()
        val error = GenerateURLError.Custom(statusCode = 503, customMessage = "unavailable")
        val dialog = dialogFor(activity, error)

        dialog.titleText() shouldBe activity.getString(R.string.error_custom_with_status_code, error.statusCode)
        dialog.messageText() shouldBe error.customMessage
    }

    @Test
    fun `configureFor Unknown with a status code shows it in the message`() {
        val activity = themedActivity()
        val dialog = dialogFor(activity, GenerateURLError.Unknown(statusCode = 500))

        dialog.titleText() shouldBe activity.getString(commonutilsR.string.commonutils_error)
        dialog.messageText() shouldBe activity.getString(R.string.error_unknown_with_status_code, 500)
    }

    @Test
    fun `configureFor Unknown without a status code shows the generic unknown error message`() {
        val activity = themedActivity()
        val dialog = dialogFor(activity, GenerateURLError.Unknown())

        dialog.messageText() shouldBe activity.getString(commonutilsR.string.commonutils_error_unknown)
    }

    @Test
    fun `configureFor falls back to the simple error message for every remaining named error`() {
        val activity = themedActivity()
        val cases =
            mapOf(
                GenerateURLError.AliasAlreadyExists to R.string.error_alias_already_exists,
                GenerateURLError.URLExistsWithDifferentAlias to R.string.error_url_already_exists_with_different_alias,
                GenerateURLError.InvalidURL to R.string.error_invalid_url,
                GenerateURLError.InvalidAlias to R.string.error_invalid_alias,
                GenerateURLError.InvalidURLOrAlias to R.string.error_invalid_url_or_alias,
                GenerateURLError.InternalServerError to R.string.error_internal_server_error,
                GenerateURLError.ServiceOffline to R.string.error_service_offline,
                GenerateURLError.RateLimitExceeded to R.string.error_rate_limit_exceeded,
                GenerateURLError.DomainNotAllowed to R.string.error_domain_not_allowed,
            )

        cases.forEach { (error, expectedMessageRes) ->
            val dialog = dialogFor(activity, error)
            dialog.titleText() shouldBe activity.getString(commonutilsR.string.commonutils_error)
            dialog.messageText() shouldBe activity.getString(expectedMessageRes)
        }
    }
}
