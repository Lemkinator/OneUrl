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
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.oneurl.R
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldNotBe
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
}
