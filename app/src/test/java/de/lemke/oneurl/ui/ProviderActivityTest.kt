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
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.awaitMainLooperUntil
import de.lemke.commonutils.idleMainLooper
import de.lemke.oneurl.R
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.domain.model.Dagd
import de.lemke.oneurl.domain.model.Spoome
import de.lemke.oneurl.domain.model.VgdIsgd
import de.lemke.oneurl.domain.model.Zwsim
import dev.oneuiproject.oneui.layout.ToolbarLayout
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class ProviderActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @Inject
    lateinit var userSettings: UserSettings

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun `clicking a provider item in select mode finishes the activity`() {
        launchProviderActivity(ProviderScreenMode.Select).use { scenario ->
            scenario.onActivity { activity ->
                idleMainLooper()
                activity
                    .findViewById<RecyclerView>(R.id.provider_list)
                    .findViewHolderForAdapterPosition(0)!!
                    .itemView
                    .performClick()
            }
            idleMainLooper()
            scenario.onActivity { activity -> activity.isFinishing.shouldBeTrue() }
        }
    }

    @Test
    fun `clicking a provider item in info mode shows the provider info bottom sheet`() {
        launchProviderActivity(ProviderScreenMode.Info).use { scenario ->
            scenario.onActivity { activity ->
                idleMainLooper()
                activity
                    .findViewById<RecyclerView>(R.id.provider_list)
                    .findViewHolderForAdapterPosition(0)!!
                    .itemView
                    .performClick()
                idleMainLooper()
                activity.supportFragmentManager.findFragmentByTag(ProviderInfoBottomSheet::class.java.simpleName) shouldNotBe null
            }
        }
    }

    @Test
    fun `double tap on a provider item in info mode shows one provider info bottom sheet`() {
        launchProviderActivity(ProviderScreenMode.Info).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                val itemView =
                    activity
                        .findViewById<RecyclerView>(R.id.provider_list)
                        .findViewHolderForAdapterPosition(0)!!
                        .itemView
                itemView.performClick()
                itemView.performClick()
            }
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.supportFragmentManager.fragments.count { it is ProviderInfoBottomSheet } shouldBe 1
            }
        }
    }

    @Test
    fun `a provider info request shows one bottom sheet that a recreation does not show again`() {
        launchProviderActivity(ProviderScreenMode.Info).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity
                    .findViewById<RecyclerView>(R.id.provider_list)
                    .findViewHolderForAdapterPosition(0)!!
                    .itemView
                    .performClick()
            }
            idleMainLooper()
            scenario.recreate()
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.supportFragmentManager.fragments.count { it is ProviderInfoBottomSheet } shouldBe 1
                ViewModelProvider(activity)[ProviderViewModel::class.java].navigation.value shouldBe ProviderNavigation.None
            }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h240dp")
    fun `the list in select mode scrolls to the selected provider and reports the scroll done`() {
        userSettings.selectedShortURLProvider = Spoome.Emoji
        launchProviderActivity(ProviderScreenMode.Select).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                val layoutManager = activity.findViewById<RecyclerView>(R.id.provider_list).layoutManager as LinearLayoutManager
                layoutManager.findFirstVisibleItemPosition() shouldBeGreaterThan 0
                layoutManager.findLastVisibleItemPosition() shouldBe 5
                ViewModelProvider(activity)[ProviderViewModel::class.java].state.value.scrollToPosition shouldBe null
            }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h240dp")
    fun `the list in info mode starts at the top`() {
        userSettings.selectedShortURLProvider = Spoome.Emoji
        launchProviderActivity(ProviderScreenMode.Info).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                val layoutManager = activity.findViewById<RecyclerView>(R.id.provider_list).layoutManager as LinearLayoutManager
                layoutManager.findFirstVisibleItemPosition() shouldBe 0
            }
        }
    }

    @Test
    fun `select mode titles the screen Select provider and marks only the current provider`() {
        userSettings.selectedShortURLProvider = Spoome.Emoji
        launchProviderActivity(ProviderScreenMode.Select).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.screenTitle() shouldBe "Select provider"
                activity.markedProviderTitles() shouldBe listOf(Spoome.Emoji.name)
            }
        }
    }

    @Test
    fun `info mode titles the screen Provider info and marks no provider`() {
        userSettings.selectedShortURLProvider = Spoome.Emoji
        launchProviderActivity(ProviderScreenMode.Info).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.screenTitle() shouldBe "Provider info"
                activity.markedProviderTitles() shouldBe emptyList()
            }
        }
    }

    @Test
    @Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "de")
    fun `the screen title is localized in German per mode`() {
        launchProviderActivity(ProviderScreenMode.Select).use { scenario ->
            scenario.onActivity { activity -> activity.screenTitle() shouldBe "Anbieter auswählen" }
        }
        launchProviderActivity(ProviderScreenMode.Info).use { scenario ->
            scenario.onActivity { activity -> activity.screenTitle() shouldBe "Anbieter-Info" }
        }
    }

    @Test
    fun `a recreation keeps the mode title and mark`() {
        userSettings.selectedShortURLProvider = Dagd
        launchProviderActivity(ProviderScreenMode.Select).use { scenario ->
            idleMainLooper()
            scenario.recreate()
            idleMainLooper()
            scenario.onActivity { activity ->
                activity.screenTitle() shouldBe "Select provider"
                activity.markedProviderTitles() shouldBe listOf("da.gd")
            }
        }
    }

    @Test
    fun `the selected provider mark describes itself for accessibility`() {
        userSettings.selectedShortURLProvider = Dagd
        launchProviderActivity(ProviderScreenMode.Select).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity
                    .findViewById<RecyclerView>(R.id.provider_list)
                    .findViewHolderForAdapterPosition(2)!!
                    .itemView
                    .findViewById<View>(R.id.providerSelectedMark)
                    .contentDescription shouldBe "Selected provider"
            }
        }
    }

    @Test
    fun `provider info button lists the shown feature titles`() {
        assertFirstRowInfoDescription("Provider info for da.gd: Hint before redirecting, Custom alias, Analytics")
    }

    @Test
    @Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "de")
    fun `provider info button description is localized in German`() {
        assertFirstRowInfoDescription("Anbieter-Info zu da.gd: Hinweis vor Weiterleitung, Benutzerdefiniertes Kürzel, Analytics")
    }

    @Test
    fun `submitting the list without is gd removes its row and changes no other row`() {
        launchProviderActivity(ProviderScreenMode.Info).use { scenario ->
            scenario.onActivity { activity ->
                idleMainLooper()
                val recycler = activity.findViewById<RecyclerView>(R.id.provider_list)
                val adapter = recycler.adapter as ProviderActivity.ProviderAdapter
                val events = mutableListOf<String>()
                adapter.registerAdapterDataObserver(
                    object : RecyclerView.AdapterDataObserver() {
                        override fun onItemRangeChanged(
                            positionStart: Int,
                            itemCount: Int,
                            payload: Any?,
                        ) {
                            events += "changed $positionStart $itemCount"
                        }

                        override fun onItemRangeRemoved(
                            positionStart: Int,
                            itemCount: Int,
                        ) {
                            events += "removed $positionStart $itemCount"
                        }
                    },
                )
                val updated = listOf(Dagd, VgdIsgd.Vgd, Zwsim, Spoome.Default, Spoome.Emoji).map { ProviderItem(it, marked = false) }

                adapter.submitList(updated)
                awaitCurrentList(adapter, updated)
                idleMainLooper()

                events shouldBe listOf("removed 1 1")
                recycler
                    .findViewHolderForAdapterPosition(1)!!
                    .itemView
                    .findViewById<TextView>(R.id.providerTitle)
                    .text
                    .toString() shouldBe
                    "v.gd"
            }
        }
    }

    private fun ProviderActivity.screenTitle(): String =
        (findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ToolbarLayout).expandedTitle.toString()

    private fun ProviderActivity.markedProviderTitles(): List<String> {
        val recycler = findViewById<RecyclerView>(R.id.provider_list)
        return (0 until recycler.childCount)
            .map { recycler.getChildAt(it) }
            .filter { it.findViewById<View>(R.id.providerSelectedMark).isVisible }
            .map { it.findViewById<TextView>(R.id.providerTitle).text.toString() }
    }

    private fun awaitCurrentList(
        adapter: ProviderActivity.ProviderAdapter,
        expected: List<ProviderItem>,
    ) {
        awaitMainLooperUntil { adapter.currentList == expected }
    }

    private fun assertFirstRowInfoDescription(expected: String) {
        launchProviderActivity(ProviderScreenMode.Info).use { scenario ->
            scenario.onActivity { activity ->
                idleMainLooper()
                val row =
                    activity
                        .findViewById<RecyclerView>(R.id.provider_list)
                        .findViewHolderForAdapterPosition(0)!!
                        .itemView
                row.findViewById<TextView>(R.id.providerTitle).text.toString() shouldBe "da.gd"
                row.findViewById<View>(R.id.providerIconLayout).contentDescription shouldBe expected
                row.findViewById<View>(R.id.providerIcon1).importantForAccessibility shouldBe View.IMPORTANT_FOR_ACCESSIBILITY_NO
                row.findViewById<View>(R.id.providerIcon2).importantForAccessibility shouldBe View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }
    }
}
