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

import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.idleMainLooper
import de.lemke.commonutils.returnFromLaunchedScreen
import de.lemke.oneurl.R
import de.lemke.oneurl.data.FakeTlyKeyStore
import de.lemke.oneurl.domain.model.Dagd
import de.lemke.oneurl.domain.model.Shareaholic
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.Tinyurl
import de.lemke.oneurl.domain.model.Tly
import de.lemke.oneurl.domain.model.TlyApiKey
import de.lemke.oneurl.domain.model.VgdIsgd
import de.lemke.oneurl.ui.ProviderInfoBottomSheet.Companion.showProviderInfoBottomSheet
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class ProviderInfoBottomSheetTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @Inject
    lateinit var tlyKeys: FakeTlyKeyStore

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun `title shows the provider name`() {
        withBottomSheet(Dagd) { fragment ->
            titleOf(fragment).text.toString() shouldBe Dagd.name
        }
    }

    @Test
    fun `group row stays hidden when the provider name equals its group`() {
        withBottomSheet(Dagd) { fragment ->
            groupButtonOf(fragment).isVisible.shouldBeFalse()
            groupTextOf(fragment).isVisible.shouldBeFalse()
        }
    }

    @Test
    fun `group row is shown with the group name when it differs from the provider name`() {
        withBottomSheet(VgdIsgd.Vgd) { fragment ->
            groupButtonOf(fragment).isVisible.shouldBeTrue()
            groupTextOf(fragment).isVisible.shouldBeTrue()
            groupTextOf(fragment).text.toString() shouldBe VgdIsgd.Vgd.group
        }
    }

    @Test
    fun `bindInfoContents leaves every row hidden for a provider with no info contents`() {
        withBottomSheet(Shareaholic) { fragment -> assertInfoContentsBound(fragment, Shareaholic) }
    }

    @Test
    fun `bindInfoContents fills exactly one row for a provider with a single info content`() {
        withBottomSheet(Tinyurl) { fragment -> assertInfoContentsBound(fragment, Tinyurl) }
    }

    @Test
    fun `bindInfoContents fills two rows for a provider with two info contents`() {
        withBottomSheet(Dagd) { fragment -> assertInfoContentsBound(fragment, Dagd) }
    }

    @Test
    fun `bindInfoContents fills three rows for a provider with three info contents`() {
        withBottomSheet(VgdIsgd.Vgd) { fragment -> assertInfoContentsBound(fragment, VgdIsgd.Vgd) }
    }

    @Test
    fun `bindInfoButtons binds title and icon for every info button`() {
        withBottomSheet(VgdIsgd.Vgd) { fragment ->
            val expected = VgdIsgd.Vgd.getInfoButtons(fragment.requireContext())
            expected.forEachIndexed { index, info ->
                infoButtonButtonOf(fragment, index).apply {
                    text.toString() shouldBe info.title
                    isVisible.shouldBeTrue()
                }
            }
        }
    }

    @Test
    fun `clicking an info button opens its link`() {
        withScenarioAndBottomSheet(VgdIsgd.Vgd) { scenario ->
            val expected = VgdIsgd.Vgd.getInfoButtons(ApplicationProvider.getApplicationContext())
            expected.forEachIndexed { index, info ->
                scenario.onActivity { activity ->
                    infoButtonButtonOf(activity.providerInfoBottomSheet(), index).performClick()
                    shadowOf(activity).nextStartedActivity.data.toString() shouldBe info.linkOrDescription
                }
                scenario.returnFromLaunchedScreen()
            }
        }
    }

    @Test
    fun `double tap on an info button opens its link once`() {
        withActivityAndBottomSheet(VgdIsgd.Vgd) { activity, fragment ->
            val button = infoButtonButtonOf(fragment, 0)

            button.performClick()
            button.performClick()

            val shadowActivity = shadowOf(activity)
            shadowActivity.nextStartedActivity.data.toString() shouldBe VgdIsgd.Vgd.getInfoButtons(activity)[0].linkOrDescription
            shadowActivity.nextStartedActivity shouldBe null
        }
    }

    @Test
    fun `showing the bottom sheet twice before it is added adds one sheet`() {
        ActivityScenario.launch(ProviderActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.showProviderInfoBottomSheet(Dagd).shouldBeTrue()
                activity.showProviderInfoBottomSheet(Tinyurl).shouldBeFalse()
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.providerInfoBottomSheet().requireArguments().getString(ProviderInfoBottomSheet.KEY_PROVIDER) shouldBe Dagd.name
            }
        }
    }

    @Test
    fun `showProviderInfoBottomSheet extension adds the fragment under its simple name tag`() {
        ActivityScenario.launch(ProviderActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.showProviderInfoBottomSheet(Dagd)
                activity.supportFragmentManager.executePendingTransactions()
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag(ProviderInfoBottomSheet::class.java.simpleName)
                fragment.shouldNotBeNull()
                (fragment as ProviderInfoBottomSheet).requireArguments().getString(ProviderInfoBottomSheet.KEY_PROVIDER) shouldBe Dagd.name
            }
        }
    }

    @Test
    fun `showProviderInfoBottomSheet with an explicit FragmentManager bundles the provider name`() {
        ActivityScenario.launch(ProviderActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                showProviderInfoBottomSheet(activity.supportFragmentManager, VgdIsgd.Vgd)
                activity.supportFragmentManager.executePendingTransactions()
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<ProviderInfoBottomSheet>()
                        .single()
                fragment.requireArguments().getString(ProviderInfoBottomSheet.KEY_PROVIDER) shouldBe VgdIsgd.Vgd.name
            }
        }
    }

    @Test
    fun `a T_LY provider shows the key entry below the API key info row`() {
        withBottomSheet(Tly.Default) { fragment ->
            viewOf<View>(fragment, R.id.providerBottomSheetTlyKey).isVisible.shouldBeTrue()
            infoButtonOf(fragment, 0).text.toString() shouldBe "API key"
            infoTextOf(fragment, 0).text.toString() shouldBe "You can add your own API key. It needs a T.LY plan with API access."
            infoButtonOf(fragment, 1).isVisible.shouldBeFalse()
        }
    }

    @Test
    fun `a provider other than T_LY hides the key entry`() {
        withBottomSheet(Dagd) { fragment ->
            viewOf<View>(fragment, R.id.providerBottomSheetTlyKey).isVisible.shouldBeFalse()
        }
    }

    @Test
    fun `the key entry shows the stored key and takes input`() {
        tlyKeys.apiKey = TlyApiKey.ofOrNull("abc123")

        withBottomSheet(Tly.Default) { fragment ->
            keyInputOf(fragment).text.toString() shouldBe "abc123"
            keyInputOf(fragment).isEnabled.shouldBeTrue()
        }
    }

    @Test
    fun `the key entry stays disabled and empty until the stored key is read`() {
        val read = CompletableDeferred<Unit>()
        tlyKeys.gate = read
        tlyKeys.apiKey = TlyApiKey.ofOrNull("abc123")

        withBottomSheet(Tly.Default) { fragment ->
            keyInputOf(fragment).isEnabled.shouldBeFalse()
            keyInputOf(fragment).text.toString() shouldBe ""

            read.complete(Unit)
            idleMainLooper()

            keyInputOf(fragment).isEnabled.shouldBeTrue()
            keyInputOf(fragment).text.toString() shouldBe "abc123"
        }
    }

    @Test
    fun `typing a key stores it and clearing the field removes it`() {
        withBottomSheet(Tly.Default) { fragment ->
            keyInputOf(fragment).setText("abc123")
            idleMainLooper()
            tlyKeys.apiKey shouldBe TlyApiKey.ofOrNull("abc123")

            keyInputOf(fragment).setText("")
            idleMainLooper()
            tlyKeys.apiKey shouldBe null
        }
    }

    @Test
    fun `a pasted key with line breaks and tabs keeps only the key in the field and the store`() {
        withBottomSheet(Tly.Default) { fragment ->
            keyInputOf(fragment).setText("abc\n12\t3\r\n")
            idleMainLooper()

            keyInputOf(fragment).text.toString() shouldBe "abc123"
            tlyKeys.apiKey shouldBe TlyApiKey.ofOrNull("abc123")
        }
    }

    @Test
    fun `a key the store cannot save shows the error toast and keeps the typed text`() {
        withBottomSheet(Tly.Default) { fragment ->
            tlyKeys.failing = true

            keyInputOf(fragment).setText("abc123")
            idleMainLooper()

            ShadowToast.getTextOfLatestToast() shouldBe "Error"
            keyInputOf(fragment).text.toString() shouldBe "abc123"
        }
    }

    @Test
    fun `the key link opens the T_LY key settings`() {
        withScenarioAndBottomSheet(Tly.Default) { scenario ->
            scenario.onActivity { activity ->
                viewOf<View>(activity.providerInfoBottomSheet(), R.id.providerBottomSheetTlyKeyLink).performClick()
                shadowOf(activity).nextStartedActivity.data.toString() shouldBe "https://t.ly/settings#/api"
            }
        }
    }

    private fun keyInputOf(fragment: ProviderInfoBottomSheet): TextInputEditText = viewOf(fragment, R.id.providerBottomSheetTlyKeyInput)

    private fun assertInfoContentsBound(
        fragment: ProviderInfoBottomSheet,
        provider: ShortURLProvider,
    ) {
        val expected = provider.getInfoContents(fragment.requireContext())
        expected.forEachIndexed { index, info ->
            infoButtonOf(fragment, index).apply {
                text.toString() shouldBe info.title
                isVisible.shouldBeTrue()
            }
            infoTextOf(fragment, index).apply {
                text.toString() shouldBe info.linkOrDescription
                isVisible.shouldBeTrue()
            }
        }
        (expected.size..3).forEach { index ->
            infoButtonOf(fragment, index).isVisible.shouldBeFalse()
            infoTextOf(fragment, index).isVisible.shouldBeFalse()
        }
    }

    private fun withBottomSheet(
        provider: ShortURLProvider,
        block: (ProviderInfoBottomSheet) -> Unit,
    ) {
        withActivityAndBottomSheet(provider) { _, fragment -> block(fragment) }
    }

    private fun withScenarioAndBottomSheet(
        provider: ShortURLProvider,
        block: (ActivityScenario<ProviderActivity>) -> Unit,
    ) {
        ActivityScenario.launch(ProviderActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.showProviderInfoBottomSheet(provider) }
            idleMainLooper()
            block(scenario)
        }
    }

    private fun withActivityAndBottomSheet(
        provider: ShortURLProvider,
        block: (ProviderActivity, ProviderInfoBottomSheet) -> Unit,
    ) {
        withScenarioAndBottomSheet(provider) { scenario ->
            scenario.onActivity { activity -> block(activity, activity.providerInfoBottomSheet()) }
        }
    }

    private fun ProviderActivity.providerInfoBottomSheet(): ProviderInfoBottomSheet =
        supportFragmentManager.fragments
            .filterIsInstance<ProviderInfoBottomSheet>()
            .single()

    private fun titleOf(fragment: ProviderInfoBottomSheet): TextView = viewOf(fragment, R.id.providerBottomSheetTitle)

    private fun groupButtonOf(fragment: ProviderInfoBottomSheet): AppCompatButton = viewOf(fragment, R.id.providerBottomSheetInfoGroup)

    private fun groupTextOf(fragment: ProviderInfoBottomSheet): TextView = viewOf(fragment, R.id.providerBottomSheetInfoGroupText)

    private fun infoButtonOf(
        fragment: ProviderInfoBottomSheet,
        index: Int,
    ): AppCompatButton =
        viewOf(
            fragment,
            when (index) {
                0 -> R.id.providerBottomSheetInfo1
                1 -> R.id.providerBottomSheetInfo2
                2 -> R.id.providerBottomSheetInfo3
                3 -> R.id.providerBottomSheetInfo4
                else -> error("no info content view at index $index")
            },
        )

    private fun infoTextOf(
        fragment: ProviderInfoBottomSheet,
        index: Int,
    ): TextView =
        viewOf(
            fragment,
            when (index) {
                0 -> R.id.providerBottomSheetInfoText1
                1 -> R.id.providerBottomSheetInfoText2
                2 -> R.id.providerBottomSheetInfoText3
                3 -> R.id.providerBottomSheetInfoText4
                else -> error("no info content view at index $index")
            },
        )

    private fun infoButtonButtonOf(
        fragment: ProviderInfoBottomSheet,
        index: Int,
    ): AppCompatButton =
        viewOf(
            fragment,
            when (index) {
                0 -> R.id.providerBottomSheetInfoButton1
                1 -> R.id.providerBottomSheetInfoButton2
                2 -> R.id.providerBottomSheetInfoButton3
                else -> error("no info button view at index $index")
            },
        )

    private fun <T : View> viewOf(
        fragment: ProviderInfoBottomSheet,
        id: Int,
    ): T = fragment.dialog!!.findViewById(id)
}
