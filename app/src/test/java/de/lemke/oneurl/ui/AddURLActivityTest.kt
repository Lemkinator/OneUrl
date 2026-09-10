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

import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.oneurl.R
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.domain.model.Murl
import de.lemke.oneurl.domain.model.VgdIsgd
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    private val aliasConfig = checkNotNull(VgdIsgd.Vgd.aliasConfig)

    @Before
    fun setup() {
        hiltRule.inject()
        userSettings.selectedShortURLProvider = VgdIsgd.Vgd
    }

    @Test
    fun `submit sets too-short error`() {
        withAddURLActivity { activity, urlField, aliasField, submit ->
            urlField.setText("https://example.com")
            aliasField.setText("ab")
            submit()
            aliasField.error shouldBe activity.getString(R.string.error_alias_too_short, aliasConfig.minAliasLength)
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
