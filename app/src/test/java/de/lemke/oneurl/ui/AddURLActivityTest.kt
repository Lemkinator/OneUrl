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
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.oneurl.R
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.domain.AddURLUseCase
import de.lemke.oneurl.domain.GetURLTitleUseCase
import de.lemke.oneurl.domain.GetURLUseCase
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.GenerateURLResult
import de.lemke.oneurl.domain.generateURL.GenerateURLUseCase
import de.lemke.oneurl.domain.model.Murl
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.URL
import de.lemke.oneurl.domain.model.VgdIsgd
import de.lemke.oneurl.domain.testUrl
import de.lemke.oneurl.ui.ProviderActivity.Companion.KEY_SELECT_PROVIDER
import de.lemke.oneurl.ui.URLActivity.Companion.KEY_SHORTURL
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.mockk
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
//
// VgdIsgd.Vgd (minAliasLength=5, maxAliasLength=30) is used rather than ShortURLProviderCompanion.default
// so both the "too short" and "too long" alias branches are reachable — several providers (including
// Dagd) have minAliasLength=0, making "too short" dead code for them. Tinyurl would also fit the length
// bounds but has enabled=false (API retired), so getIfEnabledOrDefault() silently falls back to the
// default provider and the assignment in setup() would have no effect.
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class AddURLActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var userSettings: UserSettings

    @BindValue
    @JvmField
    val getURL: GetURLUseCase = mockk()

    @BindValue
    @JvmField
    val getURLTitle: GetURLTitleUseCase = mockk()

    @BindValue
    @JvmField
    val generateURL: GenerateURLUseCase = mockk()

    @BindValue
    @JvmField
    val addURL: AddURLUseCase = mockk()

    private val aliasConfig = checkNotNull(VgdIsgd.Vgd.aliasConfig)

    @Before
    fun setup() {
        hiltRule.inject()
        userSettings.selectedShortURLProvider = VgdIsgd.Vgd
        coEvery { getURL(any<ShortURLProvider>(), any()) } returns emptyList()
        coEvery { getURLTitle(any()) } returns "title"
        coEvery { generateURL(any(), any(), any(), any()) } returns GenerateURLResult.Success("https://short.url/abc")
        coEvery { addURL(any()) } returns Unit
    }

    @Test
    fun `submit sets too-short error`() {
        withAddURLActivity { activity, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("ab")
            submit()
            aliasField.error shouldBe
                activity.resources.getQuantityString(
                    R.plurals.error_alias_too_short,
                    aliasConfig.minAliasLength,
                    aliasConfig.minAliasLength,
                )
        }
    }

    @Test
    fun `submit sets too-long error`() {
        withAddURLActivity { activity, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("a".repeat(31))
            submit()
            aliasField.error shouldBe activity.getString(R.string.error_alias_too_long, aliasConfig.maxAliasLength)
        }
    }

    @Test
    fun `submit sets invalid-characters error`() {
        withAddURLActivity { activity, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("not valid!")
            submit()
            aliasField.error shouldBe
                activity.getString(R.string.error_invalid_alias_allowed_characters, aliasConfig.allowedAliasCharacters)
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
            shadowOf(Looper.getMainLooper()).idle()

            aliasField.text.toString() shouldBe "user-typed-alias"
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
        withAddURLActivity { activity, urlField, _, submit ->
            urlField.setText("")
            submit()
            urlField.error shouldBe activity.getString(R.string.error_empty_url)
        }
    }

    @Test
    fun `renderLoadingState shows progress and disables inputs while a lookup is pending`() {
        // getURL is resolved with a blank-alias match (not emptyList()) so the coroutine takes the
        // AlreadyShortened early-return - the only path that reaches isLoading=false without a real
        // Dispatchers.Default hop through the (unmocked) Room-backed AddURLUseCase.
        val pending = CompletableDeferred<List<URL>>()
        coEvery { getURL(any<ShortURLProvider>(), any()) } coAnswers { pending.await() }

        withAddURLActivity { activity, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("")
            submit()
            shadowOf(Looper.getMainLooper()).idle()

            activity.findViewById<View>(R.id.addUrlFooterProgress).isVisible.shouldBeTrue()
            activity.findViewById<View>(R.id.addUrlFooterButton).isVisible.shouldBeFalse()
            activity.findViewById<TextView>(R.id.addUrlFooterProgressText).text.toString() shouldBe
                activity.getString(R.string.checking_duplicates)
            urlField.isEnabled.shouldBeFalse()
            aliasField.isEnabled.shouldBeFalse()
            activity.findViewById<View>(R.id.providerSelection).isEnabled.shouldBeFalse()

            pending.complete(listOf(testUrl(shortURL = "https://vgd.io/existing", provider = VgdIsgd.Vgd)))
            shadowOf(Looper.getMainLooper()).idle()

            activity.findViewById<View>(R.id.addUrlFooterButton).isVisible.shouldBeTrue()
            activity.findViewById<View>(R.id.addUrlFooterProgress).isVisible.shouldBeFalse()
            urlField.isEnabled.shouldBeTrue()
        }
    }

    @Test
    fun `submit with a blank alias and an existing match shows AlreadyShortened dialog that navigates to URLActivity`() {
        val existing = testUrl(shortURL = "https://vgd.io/existing", provider = VgdIsgd.Vgd)
        coEvery { getURL(VgdIsgd.Vgd, "https://example.com") } returns listOf(existing)

        withAddURLActivity { activity, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("")
            submit()
            shadowOf(Looper.getMainLooper()).idle()

            val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
            dialog shouldNotBe null
            dialog!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            // AlertController dispatches the button's DialogInterface.OnClickListener through a
            // Handler message rather than calling it inline from performClick().
            shadowOf(Looper.getMainLooper()).idle()

            val startedIntent = shadowOf(activity).nextStartedActivity
            startedIntent.getStringExtra(KEY_SHORTURL) shouldBe existing.shortURL
        }
    }

    @Test
    fun `submit shows an error dialog wired through configureFor on generateURL failure`() {
        coEvery { generateURL(any(), any(), any(), any()) } returns GenerateURLResult.Failure(GenerateURLError.NoInternet)

        withAddURLActivity { activity, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("")
            submit()
            shadowOf(Looper.getMainLooper()).idle()

            val dialog = ShadowDialog.getLatestDialog() as? AlertDialog
            dialog shouldNotBe null
            dialog!!
                .findViewById<TextView>(androidx.appcompat.R.id.alertTitle)
                ?.text
                .toString() shouldBe activity.getString(R.string.no_internet)
        }
    }

    @Test
    fun `submit with autoCopyOnCreate copies the short url and finishes`() {
        userSettings.autoCopyOnCreate = true

        withAddURLActivity { activity, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("")
            submit()
            shadowOf(Looper.getMainLooper()).idle()

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
            shadowOf(Looper.getMainLooper()).idle()

            ShadowToast.getTextOfLatestToast() shouldBe activity.getString(R.string.url_added)
            activity.isFinishing.shouldBeTrue()
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
