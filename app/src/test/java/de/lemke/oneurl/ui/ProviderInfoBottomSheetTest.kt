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
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.Dagd
import de.lemke.oneurl.domain.model.Shareaholic
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.Tinyurl
import de.lemke.oneurl.domain.model.VgdIsgd
import de.lemke.oneurl.ui.ProviderInfoBottomSheet.Companion.showProviderInfoBottomSheet
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class ProviderInfoBottomSheetTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

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
        withActivityAndBottomSheet(VgdIsgd.Vgd) { activity, fragment ->
            val expected = VgdIsgd.Vgd.getInfoButtons(fragment.requireContext())
            expected.forEachIndexed { index, info ->
                infoButtonButtonOf(fragment, index).performClick()
                shadowOf(activity).nextStartedActivity.data.toString() shouldBe info.linkOrDescription
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
            shadowOf(Looper.getMainLooper()).idle()
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
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<ProviderInfoBottomSheet>()
                        .single()
                fragment.requireArguments().getString(ProviderInfoBottomSheet.KEY_PROVIDER) shouldBe VgdIsgd.Vgd.name
            }
        }
    }

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

    private fun withActivityAndBottomSheet(
        provider: ShortURLProvider,
        block: (ProviderActivity, ProviderInfoBottomSheet) -> Unit,
    ) {
        ActivityScenario.launch(ProviderActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.showProviderInfoBottomSheet(provider) }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.fragments
                        .filterIsInstance<ProviderInfoBottomSheet>()
                        .single()
                block(activity, fragment)
            }
        }
    }

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
