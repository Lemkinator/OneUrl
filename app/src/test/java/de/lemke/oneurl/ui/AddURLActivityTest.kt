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

import android.content.ClipboardManager
import android.content.DialogInterface
import android.os.Looper
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.idleMainLooper
import de.lemke.oneurl.R
import de.lemke.oneurl.data.FakeNetworkMonitor
import de.lemke.oneurl.data.FakePageTitleFetcher
import de.lemke.oneurl.data.FakeShortURLClient
import de.lemke.oneurl.data.URLRepository
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.domain.model.GenerateURLError
import de.lemke.oneurl.domain.model.GenerateURLResult
import de.lemke.oneurl.domain.model.Murl
import de.lemke.oneurl.domain.model.Shareaholic
import de.lemke.oneurl.domain.model.Spoome
import de.lemke.oneurl.domain.model.VgdIsgd
import de.lemke.oneurl.domain.testUrl
import de.lemke.oneurl.ui.ProviderActivity.Companion.KEY_SELECT_PROVIDER
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_SHORTURL
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class AddURLActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @Inject
    lateinit var userSettings: UserSettings

    @Inject
    lateinit var urlRepository: URLRepository

    @Inject
    lateinit var networkMonitor: FakeNetworkMonitor

    @Inject
    lateinit var pageTitleFetcher: FakePageTitleFetcher

    @Inject
    lateinit var shortURLClient: FakeShortURLClient

    @Before
    fun setup() {
        hiltRule.inject()
        userSettings.selectedShortURLProvider = VgdIsgd.Vgd
        pageTitleFetcher.titles["https://example.com"] = "title"
        shortURLClient.results[VgdIsgd.Vgd] = GenerateURLResult.Success("https://short.url/abc")
    }

    @Test
    fun `submit sets too-short error`() {
        withAddURLActivity { _, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("ab")
            submit()
            aliasField.error shouldBe "Alias too short (min. 5 characters)."
        }
    }

    @Test
    fun `submit sets too-long error`() {
        withAddURLActivity { _, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("a".repeat(31))
            submit()
            aliasField.error shouldBe "Alias exceeds the 30-character limit."
        }
    }

    @Test
    fun `submit accepts an emoji alias of 15 emoji`() {
        userSettings.selectedShortURLProvider = Spoome.Emoji
        shortURLClient.results[Spoome.Emoji] = GenerateURLResult.Success("https://spoo.me/short")
        withAddURLActivity { _, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("😀".repeat(15))
            submit()
            aliasField.error.shouldBeNull()
        }
    }

    @Test
    fun `submit sets too-long error for an emoji alias of 16 emoji`() {
        userSettings.selectedShortURLProvider = Spoome.Emoji
        withAddURLActivity { _, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("😀".repeat(16))
            submit()
            aliasField.error shouldBe "Alias exceeds the 15-character limit."
        }
    }

    @Test
    fun `submit sets invalid-characters error`() {
        withAddURLActivity { _, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("not valid!")
            submit()
            aliasField.error shouldBe "Invalid alias (allowed characters: a-z, A-Z, 0-9, _)."
        }
    }

    @Test
    fun `submit sets no alias error for a valid alias`() {
        withAddURLActivity { _, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("valid_alias")
            submit()
            aliasField.error.shouldBeNull()
        }
    }

    @Test
    fun `submit skips alias validation for a blank alias`() {
        withAddURLActivity { _, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("")
            submit()
            aliasField.error.shouldBeNull()
        }
    }

    @Test
    fun `submit skips alias validation for a provider without alias support`() {
        userSettings.selectedShortURLProvider = Murl
        withAddURLActivity { _, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("not valid!")
            submit()
            aliasField.error.shouldBeNull()
        }
    }

    @Test
    fun `initial state populates fields from user settings and focuses the url field`() {
        userSettings.lastURL = "https://saved.example.com"
        userSettings.lastAlias = "saved-alias"
        userSettings.lastDescription = "saved description"

        withAddURLActivity { activity, urlField, aliasField, _ ->
            urlField.text.toString() shouldBe "https://saved.example.com"
            aliasField.text.toString() shouldBe "saved-alias"
            activity.findViewById<EditText>(R.id.editTextDescription).text.toString() shouldBe "saved description"
            urlField.isFocused.shouldBeTrue()
        }
    }

    @Test
    fun `a later state emission does not re-run initViews`() {
        withAddURLActivity { _, _, aliasField, _ ->
            aliasField.setText("user-typed-alias")

            userSettings.selectedShortURLProvider = Murl
            idleMainLooper()

            aliasField.text.toString() shouldBe "user-typed-alias"
        }
    }

    @Test
    fun `provider info button lists the shown feature titles`() {
        withAddURLActivity { activity, _, _, _ ->
            activity.findViewById<View>(R.id.providerIconLayout).contentDescription shouldBe
                "Provider info for v.gd: Hint before redirecting, Custom alias, Analytics"
            activity.findViewById<View>(R.id.providerIcon1).importantForAccessibility shouldBe View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    @Test
    fun `provider info button names only the provider when it has no features`() {
        userSettings.selectedShortURLProvider = Shareaholic
        withAddURLActivity { activity, _, _, _ ->
            activity.findViewById<View>(R.id.providerIconLayout).contentDescription shouldBe "Provider info for go.shr.lc"
        }
    }

    @Test
    fun `clicking provider selection opens ProviderActivity for selection`() {
        withAddURLActivity { activity, _, _, _ ->
            activity.findViewById<View>(R.id.providerSelection).performClick()

            val startedIntent = shadowOf(activity).nextStartedActivity
            startedIntent.component?.className shouldBe ProviderActivity::class.java.name
            startedIntent.getBooleanExtra(KEY_SELECT_PROVIDER, false).shouldBeTrue()
        }
    }

    @Test
    fun `double tap on provider selection opens one ProviderActivity`() {
        withAddURLActivity { activity, _, _, _ ->
            val providerSelection = activity.findViewById<View>(R.id.providerSelection)

            providerSelection.performClick()
            providerSelection.performClick()

            val shadowActivity = shadowOf(activity)
            shadowActivity.nextStartedActivity.component?.className shouldBe ProviderActivity::class.java.name
            shadowActivity.nextStartedActivity shouldBe null
        }
    }

    @Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "w320dp")
    @Test
    fun `initFooterButton uses match_parent width on a compact screen`() {
        withAddURLActivity { activity, _, _, _ ->
            activity.findViewById<View>(R.id.addUrlFooterButton).layoutParams.width shouldBe MATCH_PARENT
        }
    }

    @Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "w480dp")
    @Test
    fun `initFooterButton keeps the default width on a regular screen`() {
        withAddURLActivity { activity, _, _, _ ->
            activity.findViewById<View>(R.id.addUrlFooterButton).layoutParams.width shouldNotBe MATCH_PARENT
        }
    }

    // The toolbar's navigation icon runs in showNavButtonAsBack mode (activity_add_url.xml sets
    // app:showNavButtonAsBack="true"), so ToolbarLayoutButtonsHandler.updateNavButton wires the
    // real navigation click straight to onBackPressedDispatcher.onBackPressed() and never calls this
    // listener - the only way to reach it is to invoke the compiled callback directly.
    @Test
    fun `onCreate navigation button listener hides the keyboard and finishes`() {
        withAddURLActivity { activity, _, _, _ ->
            val onNavigationClick =
                AddURLActivity::class.java.getDeclaredMethod("onCreate\$lambda\$0", AddURLActivity::class.java, View::class.java)
            onNavigationClick.isAccessible = true

            onNavigationClick.invoke(null, activity, activity.findViewById<View>(R.id.urlInputLayout))

            activity.isFinishing.shouldBeTrue()
        }
    }

    @Test
    fun `submit sets empty-url error for a blank url`() {
        withAddURLActivity { _, urlField, _, submit ->
            urlField.setText("")
            submit()
            urlField.error shouldBe "Empty URL."
        }
    }

    @Test
    fun `renderLoadingState shows progress and disables inputs while the short url is being generated`() {
        val pending = CompletableDeferred<Unit>()
        shortURLClient.gate = pending
        shortURLClient.results[VgdIsgd.Vgd] = GenerateURLResult.Failure(GenerateURLError.RateLimitExceeded)

        withAddURLActivity { activity, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("")
            submit()
            idleMainLooper()

            activity.findViewById<View>(R.id.addUrlFooterProgress).isVisible.shouldBeTrue()
            activity.findViewById<View>(R.id.addUrlFooterButton).isVisible.shouldBeFalse()
            activity.findViewById<TextView>(R.id.addUrlFooterProgressText).text.toString() shouldBe "Generating URL…"
            urlField.isEnabled.shouldBeFalse()
            aliasField.isEnabled.shouldBeFalse()
            activity.findViewById<View>(R.id.providerSelection).isEnabled.shouldBeFalse()

            pending.complete(Unit)
            idleMainLooper()

            activity.findViewById<View>(R.id.addUrlFooterButton).isVisible.shouldBeTrue()
            activity.findViewById<View>(R.id.addUrlFooterProgress).isVisible.shouldBeFalse()
            urlField.isEnabled.shouldBeTrue()
        }
    }

    @Test
    fun `submit with a blank alias and an existing match shows AlreadyShortened dialog that navigates to URLActivity`() {
        val existing = testUrl(shortURL = "https://vgd.io/existing", provider = VgdIsgd.Vgd)
        runBlocking { urlRepository.addURL(existing) }

        withAddURLActivity { activity, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("")
            submit()
            idleMainLooper()

            val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
            dialog shouldNotBe null
            dialog!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            // AlertController dispatches the button's DialogInterface.OnClickListener through a
            // Handler message rather than calling it inline from performClick().
            idleMainLooper()

            val startedIntent = shadowOf(activity).nextStartedActivity
            startedIntent.getStringExtra(KEY_SHORTURL) shouldBe existing.shortURL
        }
    }

    @Test
    fun `submit shows an error dialog wired through configureFor on generateURL failure`() {
        networkMonitor.online = false

        withAddURLActivity { _, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("")
            submit()
            idleMainLooper()

            val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
            dialog shouldNotBe null
            dialog!!
                .findViewById<TextView>(androidx.appcompat.R.id.alertTitle)
                ?.text
                .toString() shouldBe "No internet connection available…"
        }
    }

    @Test
    fun `a second error while the error dialog shows adds no second dialog`() {
        networkMonitor.online = false

        withAddURLActivity { _, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("")
            submit()
            idleMainLooper()
            submit()
            idleMainLooper()

            ShadowDialog.getShownDialogs().size shouldBe 1
            ShadowDialog.getLatestDialog().isShowing.shouldBeTrue()
        }
    }

    @Test
    fun `submit with autoCopyOnCreate copies the short url and finishes`() {
        userSettings.autoCopyOnCreate = true

        withAddURLActivity { activity, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("")
            submit()
            idleMainLooper()

            val clipboard = activity.getSystemService(ClipboardManager::class.java)
            clipboard.primaryClip
                ?.getItemAt(0)
                ?.text
                .toString() shouldBe "https://short.url/abc"
            activity.isFinishing.shouldBeTrue()
        }
    }

    @Test
    fun `submit without autoCopyOnCreate shows a saved toast and finishes`() {
        userSettings.autoCopyOnCreate = false

        withAddURLActivity { activity, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("")
            submit()
            idleMainLooper()

            ShadowToast.getTextOfLatestToast() shouldBe "URL added"
            activity.isFinishing.shouldBeTrue()
        }
    }

    @Test
    fun `an error that arrives while the activity is paused shows its dialog once after the resume and not again after recreation`() {
        val pending = CompletableDeferred<Unit>()
        shortURLClient.gate = pending
        shortURLClient.results[VgdIsgd.Vgd] = GenerateURLResult.Failure(GenerateURLError.RateLimitExceeded)

        ActivityScenario.launch(AddURLActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.editTextURL).setText("https://example.com")
                activity.findViewById<View>(R.id.addUrlFooterButton).performClick()
            }
            scenario.moveToState(Lifecycle.State.STARTED)
            pending.complete(Unit)
            idleMainLooper()
            ShadowDialog.getShownDialogs().size shouldBe 0

            scenario.moveToState(Lifecycle.State.RESUMED)
            idleMainLooper()
            ShadowDialog.getShownDialogs().size shouldBe 1

            scenario.recreate()
            idleMainLooper()
            ShadowDialog.getShownDialogs().size shouldBe 1
        }
    }

    private fun withAddURLActivity(
        block: (activity: AddURLActivity, urlField: EditText, aliasField: EditText, submit: () -> Unit) -> Unit,
    ) {
        ActivityScenario.launch(AddURLActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val urlField = activity.findViewById<EditText>(R.id.editTextURL)
                val aliasField = activity.findViewById<EditText>(R.id.editTextAlias)
                val footerButton = activity.findViewById<View>(R.id.addUrlFooterButton)
                block(activity, urlField, aliasField) { footerButton.performClick() }
            }
        }
    }
}
