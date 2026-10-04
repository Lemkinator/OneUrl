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
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import io.kotest.assertions.withClue
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
        override fun startActivity(
            intent: Intent,
            options: Bundle?,
        ): Unit = throw ActivityNotFoundException("no settings app")
    }

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    private fun AlertDialog.messageText(): String? = findViewById<TextView>(android.R.id.message)?.text?.toString()

    private fun AlertDialog.titleText(): String? = findViewById<TextView>(appcompatR.id.alertTitle)?.text?.toString()

    private fun withProviderHint(message: String): String = "$message\nIf this keeps happening, try another provider."

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
        dialog.messageText() shouldBe withProviderHint(UNAVAILABLE_MESSAGE)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMainLooper()
        shadowOf(activity).nextStartedActivity.data.toString() shouldBe error.providerBaseURL
    }

    @Test
    fun `configureFor Custom shows a short plain sentence as is under the HTTP status title`() {
        val dialog = dialogFor(themedActivity(), GenerateURLError.Custom(statusCode = 400, customMessage = "Alias contains spaces."))

        dialog.titleText() shouldBe "Error (HTTP 400)"
        dialog.messageText() shouldBe withProviderHint("Alias contains spaces.")
    }

    @Test
    fun `configureFor Custom drops the line break a plain-text body ends with`() {
        val dialog = dialogFor(themedActivity(), GenerateURLError.Custom(statusCode = 400, customMessage = "Invalid URL.\n"))

        dialog.messageText() shouldBe withProviderHint("Invalid URL.")
    }

    @Test
    fun `configureFor Custom shows the status message instead of an HTML body`() {
        val body = "<html><head><title>404 Not Found</title></head><body>Not Found</body></html>"
        val dialog = dialogFor(themedActivity(), GenerateURLError.Custom(statusCode = 404, customMessage = body))

        dialog.titleText() shouldBe "Error (HTTP 404)"
        dialog.messageText() shouldBe withProviderHint(NOT_HANDLED_MESSAGE)
    }

    @Test
    fun `configureFor Custom shows the status message instead of a JSON object body`() {
        val dialog = dialogFor(themedActivity(), GenerateURLError.Custom(statusCode = 400, customMessage = """{"error":"invalid_url"}"""))

        dialog.messageText() shouldBe withProviderHint(REJECTED_MESSAGE)
    }

    @Test
    fun `configureFor Custom shows the status message instead of a JSON array body`() {
        val dialog = dialogFor(themedActivity(), GenerateURLError.Custom(statusCode = 429, customMessage = """["slow down"]"""))

        dialog.messageText() shouldBe withProviderHint(RATE_LIMIT_MESSAGE)
    }

    @Test
    fun `configureFor Custom shows the status message instead of a multi-line text`() {
        val body = "Service Unavailable\nThe server is temporarily unable to service your request."
        val dialog = dialogFor(themedActivity(), GenerateURLError.Custom(statusCode = 503, customMessage = body))

        dialog.messageText() shouldBe withProviderHint(UNAVAILABLE_MESSAGE)
    }

    @Test
    fun `configureFor Custom shows the status message instead of a text with a carriage return`() {
        val dialog = dialogFor(themedActivity(), GenerateURLError.Custom(statusCode = 400, customMessage = "Line one\rLine two"))

        dialog.messageText() shouldBe withProviderHint(REJECTED_MESSAGE)
    }

    @Test
    fun `configureFor Custom shows the status message instead of a text with a Unicode line break`() {
        val activity = themedActivity()
        val lineBreaks = mapOf("U+2028" to "\u2028", "U+2029" to "\u2029", "U+0085" to "\u0085")

        lineBreaks.forEach { (name, lineBreak) ->
            withClue(name) {
                val dialog = dialogFor(activity, GenerateURLError.Custom(statusCode = 400, customMessage = "Line one${lineBreak}Line two"))
                dialog.messageText() shouldBe withProviderHint(REJECTED_MESSAGE)
            }
        }
    }

    @Test
    fun `configureFor Custom shows the status message instead of a 200-character text`() {
        val dialog = dialogFor(themedActivity(), GenerateURLError.Custom(statusCode = 403, customMessage = "a".repeat(200)))

        dialog.messageText() shouldBe withProviderHint(REFUSED_MESSAGE)
    }

    @Test
    fun `configureFor Custom shows a text of up to 150 characters as is`() {
        val activity = themedActivity()
        val longest = "a".repeat(150)
        val tooLong = "a".repeat(151)

        val longestDialog = dialogFor(activity, GenerateURLError.Custom(statusCode = 403, customMessage = longest))
        val tooLongDialog = dialogFor(activity, GenerateURLError.Custom(statusCode = 403, customMessage = tooLong))

        longestDialog.messageText() shouldBe withProviderHint(longest)
        tooLongDialog.messageText() shouldBe withProviderHint(REFUSED_MESSAGE)
    }

    @Test
    fun `configureFor Custom shows the status message instead of a blank text`() {
        val dialog = dialogFor(themedActivity(), GenerateURLError.Custom(statusCode = 500, customMessage = " "))

        dialog.messageText() shouldBe withProviderHint(UNAVAILABLE_MESSAGE)
    }

    @Test
    fun `configureFor Unknown shows the status message of its HTTP status under the HTTP status title`() {
        val activity = themedActivity()
        val cases =
            mapOf(
                100 to UNKNOWN_MESSAGE,
                200 to UNKNOWN_MESSAGE,
                400 to REJECTED_MESSAGE,
                401 to REFUSED_MESSAGE,
                403 to REFUSED_MESSAGE,
                404 to NOT_HANDLED_MESSAGE,
                418 to UNKNOWN_MESSAGE,
                429 to RATE_LIMIT_MESSAGE,
                500 to UNAVAILABLE_MESSAGE,
                503 to UNAVAILABLE_MESSAGE,
                599 to UNAVAILABLE_MESSAGE,
            )

        cases.forEach { (statusCode, expectedMessage) ->
            withClue("HTTP $statusCode") {
                val dialog = dialogFor(activity, GenerateURLError.Unknown(statusCode))
                dialog.titleText() shouldBe "Error (HTTP $statusCode)"
                dialog.messageText() shouldBe withProviderHint(expectedMessage)
            }
        }
    }

    @Test
    fun `configureFor labels a code outside 100 to 599 as a code instead of an HTTP status`() {
        val activity = themedActivity()
        val dialog = dialogFor(activity, GenerateURLError.Unknown(statusCode = 1100))
        val customDialog = dialogFor(activity, GenerateURLError.Custom(statusCode = 5, customMessage = "custom name too long"))

        dialog.titleText() shouldBe "Error (code 1100)"
        dialog.messageText() shouldBe withProviderHint(UNKNOWN_MESSAGE)
        customDialog.titleText() shouldBe "Error (code 5)"
        customDialog.messageText() shouldBe withProviderHint("custom name too long")
        dialogFor(activity, GenerateURLError.Unknown(statusCode = 99)).titleText() shouldBe "Error (code 99)"
        dialogFor(activity, GenerateURLError.Unknown(statusCode = 600)).titleText() shouldBe "Error (code 600)"
    }

    @Test
    fun `configureFor Unknown without a status code shows the generic unknown error`() {
        val dialog = dialogFor(themedActivity(), GenerateURLError.Unknown())

        dialog.titleText() shouldBe "Error"
        dialog.messageText() shouldBe withProviderHint(UNKNOWN_MESSAGE)
    }

    @Test
    fun `configureFor adds the provider hint to rate limit and internal server errors`() {
        val activity = themedActivity()

        dialogFor(activity, GenerateURLError.RateLimitExceeded).messageText() shouldBe withProviderHint(RATE_LIMIT_MESSAGE)
        dialogFor(activity, GenerateURLError.InternalServerError).messageText() shouldBe withProviderHint("Internal server error.")
    }

    @Test
    fun `configureFor shows the simple error message without the provider hint for every remaining named error`() {
        val activity = themedActivity()
        val cases =
            mapOf(
                GenerateURLError.AliasAlreadyExists to R.string.error_alias_already_exists,
                GenerateURLError.URLExistsWithDifferentAlias to R.string.error_url_already_exists_with_different_alias,
                GenerateURLError.InvalidURL to R.string.error_invalid_url,
                GenerateURLError.InvalidAlias to R.string.error_invalid_alias,
                GenerateURLError.InvalidURLOrAlias to R.string.error_invalid_url_or_alias,
                GenerateURLError.ServiceOffline to R.string.error_service_offline,
                GenerateURLError.DomainNotAllowed to R.string.error_domain_not_allowed,
            )

        cases.forEach { (error, expectedMessageRes) ->
            val dialog = dialogFor(activity, error)
            dialog.titleText() shouldBe activity.getString(commonutilsR.string.commonutils_error)
            dialog.messageText() shouldBe activity.getString(expectedMessageRes)
        }
    }

    private companion object {
        const val REJECTED_MESSAGE = "The provider rejected the request."
        const val REFUSED_MESSAGE = "The provider refused access."
        const val NOT_HANDLED_MESSAGE = "This provider can't handle requests right now. It may be down or discontinued."
        const val RATE_LIMIT_MESSAGE =
            "Rate limit exceeded. Please wait at least 5 minutes before making the next request. " +
                "Otherwise, you may be blocked from using this service."
        const val UNAVAILABLE_MESSAGE = "Service currently unavailable. Please try again later."
        const val UNKNOWN_MESSAGE = "Unknown error"
    }
}
