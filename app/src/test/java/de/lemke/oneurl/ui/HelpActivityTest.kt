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
import android.view.View
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.oneurl.HiltTestRule
import de.lemke.oneurl.R
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
class HelpActivityTest {
    @get:Rule
    val hiltRule = HiltTestRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun `double tap on the provider info button opens one ProviderActivity`() {
        withHelpActivity { activity ->
            val providerInfoButton = activity.findViewById<View>(R.id.providerInfoButton)

            providerInfoButton.performClick()
            providerInfoButton.performClick()

            val shadowActivity = shadowOf(activity)
            shadowActivity.nextStartedActivity.component?.className shouldBe ProviderActivity::class.java.name
            shadowActivity.nextStartedActivity shouldBe null
        }
    }

    @Test
    fun `double tap on the contact button opens one email draft`() {
        withHelpActivity { activity ->
            val contactMeButton = activity.findViewById<View>(R.id.contactMeButton)

            contactMeButton.performClick()
            contactMeButton.performClick()

            val shadowActivity = shadowOf(activity)
            val emailIntent = shadowActivity.nextStartedActivity
            emailIntent.action shouldBe Intent.ACTION_SENDTO
            emailIntent.getStringArrayExtra(Intent.EXTRA_EMAIL)?.toList() shouldBe listOf("oneurl@leonard-lemke.com")
            shadowActivity.nextStartedActivity shouldBe null
        }
    }

    private fun withHelpActivity(block: (HelpActivity) -> Unit) {
        ActivityScenario.launch(HelpActivity::class.java).use { scenario -> scenario.onActivity(block) }
    }
}
