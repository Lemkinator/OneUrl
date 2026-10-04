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

import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.Dagd
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.Spoome
import de.lemke.oneurl.domain.model.VgdIsgd
import de.lemke.oneurl.domain.model.Zwsim
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.concurrent.TimeUnit
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
class ProviderActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun `clicking a provider item in select mode finishes the activity`() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), ProviderActivity::class.java)
                .putExtra(ProviderActivity.KEY_SELECT_PROVIDER, true)
        ActivityScenario.launch<ProviderActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                shadowOf(Looper.getMainLooper()).idle()
                activity
                    .findViewById<RecyclerView>(R.id.provider_list)
                    .findViewHolderForAdapterPosition(0)!!
                    .itemView
                    .performClick()
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity -> activity.isFinishing.shouldBeTrue() }
        }
    }

    @Test
    fun `clicking a provider item outside select mode shows the provider info bottom sheet`() {
        ActivityScenario.launch(ProviderActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                shadowOf(Looper.getMainLooper()).idle()
                activity
                    .findViewById<RecyclerView>(R.id.provider_list)
                    .findViewHolderForAdapterPosition(0)!!
                    .itemView
                    .performClick()
                shadowOf(Looper.getMainLooper()).idle()
                activity.supportFragmentManager.findFragmentByTag(ProviderInfoBottomSheet::class.java.simpleName) shouldNotBe null
            }
        }
    }

    @Test
    fun `double tap on a provider item outside select mode shows one provider info bottom sheet`() {
        ActivityScenario.launch(ProviderActivity::class.java).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                val itemView =
                    activity
                        .findViewById<RecyclerView>(R.id.provider_list)
                        .findViewHolderForAdapterPosition(0)!!
                        .itemView
                itemView.performClick()
                itemView.performClick()
            }
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity ->
                activity.supportFragmentManager.fragments.count { it is ProviderInfoBottomSheet } shouldBe 1
            }
        }
    }

    @Test
    fun `provider info button lists the shown feature titles`() {
        assertFirstRowInfoDescription("Provider info for da.gd: Custom alias, Analytics")
    }

    @Test
    @Config(application = HiltTestApplication::class, sdk = [36], qualifiers = "de")
    fun `provider info button description is localized in German`() {
        assertFirstRowInfoDescription("Anbieter-Info zu da.gd: Benutzerdefiniertes Kürzel, Analytics")
    }

    @Test
    fun `submitting the list without is gd removes its row and changes no other row`() {
        ActivityScenario.launch(ProviderActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                shadowOf(Looper.getMainLooper()).idle()
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
                val updated = listOf(Dagd, VgdIsgd.Vgd, Zwsim, Spoome.Default, Spoome.Emoji)

                adapter.submitList(updated)
                awaitCurrentList(adapter, updated)
                shadowOf(Looper.getMainLooper()).idle()

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

    private fun awaitCurrentList(
        adapter: ProviderActivity.ProviderAdapter,
        expected: List<ShortURLProvider>,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (adapter.currentList != expected && System.nanoTime() < deadline) {
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun assertFirstRowInfoDescription(expected: String) {
        ActivityScenario.launch(ProviderActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                shadowOf(Looper.getMainLooper()).idle()
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
