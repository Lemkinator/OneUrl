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

import android.view.Menu
import android.view.MenuItem
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.oneurl.R
import de.lemke.oneurl.data.UserSettings
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.every
import io.mockk.mockk
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// sdk = [36]: Robolectric 4.16.1 max supported SDK; bump when 4.17+ adds SDK 37.
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class GenerateQRCodeActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var userSettings: UserSettings

    @Before
    fun setup() {
        hiltRule.inject()
        userSettings.qrURL = "https://example.com"
    }

    @Test
    fun `onOptionsItemSelected handles save-as-image and returns true`() {
        withActivity { activity ->
            activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_save_as_image)).shouldBeTrue()
        }
    }

    @Test
    fun `onOptionsItemSelected handles share and returns true`() {
        withActivity { activity ->
            activity.onOptionsItemSelected(menuItem(R.id.menu_item_qr_share)).shouldBeTrue()
        }
    }

    @Test
    fun `onOptionsItemSelected delegates unmapped items to super and returns false`() {
        withActivity { activity ->
            activity.onOptionsItemSelected(menuItem(Menu.NONE)).shouldBeFalse()
        }
    }

    private fun withActivity(block: (GenerateQRCodeActivity) -> Unit) {
        ActivityScenario.launch(GenerateQRCodeActivity::class.java).use { scenario -> scenario.onActivity(block) }
    }

    private fun menuItem(itemId: Int): MenuItem = mockk { every { getItemId() } returns itemId }
}
