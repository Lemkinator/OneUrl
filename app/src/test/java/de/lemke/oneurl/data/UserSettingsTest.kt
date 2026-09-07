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

package de.lemke.oneurl.data

import android.app.Application
import android.content.SharedPreferences
import de.lemke.commonutils.data.assertDelegatedKeys
import de.lemke.commonutils.freshTestPreferences
import de.lemke.oneurl.data.UserSettings.Companion.DEFAULT_QR_BACKGROUND_COLOR
import de.lemke.oneurl.data.UserSettings.Companion.DEFAULT_QR_FOREGROUND_COLOR
import de.lemke.oneurl.data.UserSettings.Companion.DEFAULT_QR_SIZE
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pins OneUrl's own settings invariants (defaults, round-trip, golden key set) against the real [UserSettings]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class UserSettingsTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var settings: UserSettings
    private val testScope = CoroutineScope(UnconfinedTestDispatcher())

    private fun reload() = UserSettings(prefs, testScope)

    @Before
    fun setUp() {
        prefs = freshTestPreferences()
        settings = UserSettings(prefs, testScope)
    }

    @Test
    fun `defaults on fresh store`() {
        settings.selectedShortURLProvider shouldBe ShortURLProviderCompanion.default
        settings.lastAlias shouldBe ""
        settings.lastURL shouldBe ""
        settings.lastDescription shouldBe ""
        settings.qrURL shouldBe ""
        settings.qrRecentBackgroundColors shouldBe listOf(DEFAULT_QR_BACKGROUND_COLOR)
        settings.qrRecentForegroundColors shouldBe listOf(DEFAULT_QR_FOREGROUND_COLOR)
        settings.qrSize shouldBe DEFAULT_QR_SIZE
        settings.qrFrame.shouldBeTrue()
        settings.qrIcon.shouldBeTrue()
        settings.qrTintAnchor.shouldBeFalse()
        settings.qrTintBorder.shouldBeFalse()
        settings.autoCopyOnCreate.shouldBeFalse()
    }

    @Test
    fun `lastAlias round-trips`() {
        settings.lastAlias = "my-alias"
        reload().lastAlias shouldBe "my-alias"
    }

    @Test
    fun `lastURL round-trips`() {
        settings.lastURL = "https://example.com"
        reload().lastURL shouldBe "https://example.com"
    }

    @Test
    fun `lastDescription round-trips`() {
        settings.lastDescription = "a description"
        reload().lastDescription shouldBe "a description"
    }

    @Test
    fun `qrURL round-trips`() {
        settings.qrURL = "https://example.com/qr"
        reload().qrURL shouldBe "https://example.com/qr"
    }

    @Test
    fun `qrRecentBackgroundColors round-trips`() {
        val colors = listOf(0xFF0000FF.toInt(), 0xFF00FF00.toInt())
        settings.qrRecentBackgroundColors = colors
        reload().qrRecentBackgroundColors shouldBe colors
    }

    @Test
    fun `qrRecentForegroundColors round-trips`() {
        val colors = listOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt())
        settings.qrRecentForegroundColors = colors
        reload().qrRecentForegroundColors shouldBe colors
    }

    @Test
    fun `qrSize round-trips`() {
        settings.qrSize = 256
        reload().qrSize shouldBe 256
    }

    @Test
    fun `qrFrame round-trips false`() {
        settings.qrFrame = false
        reload().qrFrame.shouldBeFalse()
    }

    @Test
    fun `qrIcon round-trips false`() {
        settings.qrIcon = false
        reload().qrIcon.shouldBeFalse()
    }

    @Test
    fun `qrTintAnchor round-trips true`() {
        settings.qrTintAnchor = true
        reload().qrTintAnchor.shouldBeTrue()
    }

    @Test
    fun `qrTintBorder round-trips true`() {
        settings.qrTintBorder = true
        reload().qrTintBorder.shouldBeTrue()
    }

    @Test
    fun `autoCopyOnCreate round-trips true`() {
        settings.autoCopyOnCreate = true
        reload().autoCopyOnCreate.shouldBeTrue()
    }

    @Test
    fun `selectedShortURLProvider round-trips a non-default provider`() {
        val nonDefault = ShortURLProviderCompanion.enabled.first { it != ShortURLProviderCompanion.default }
        settings.selectedShortURLProvider = nonDefault
        reload().selectedShortURLProvider shouldBe nonDefault
    }

    @Test
    fun `selectedShortURLProvider falls back to default on garbage raw value`() {
        prefs.edit().putString("selectedShortURLProvider", "not-a-real-provider").apply()
        reload().selectedShortURLProvider shouldBe ShortURLProviderCompanion.default
    }

    @Test
    fun `selectedShortURLProvider stores the provider name as the raw string key`() {
        val nonDefault = ShortURLProviderCompanion.enabled.first { it != ShortURLProviderCompanion.default }
        settings.selectedShortURLProvider = nonDefault
        prefs.getString("selectedShortURLProvider", null) shouldBe nonDefault.name
    }

    @Test
    fun `delegated keys are pinned`() {
        assertDelegatedKeys(
            UserSettings::class.java,
            setOf(
                "selectedShortURLProvider",
                "lastAlias",
                "lastURL",
                "lastDescription",
                "qrURL",
                "qrRecentBackgroundColors",
                "qrRecentForegroundColors",
                "qrSize",
                "qrFrame",
                "qrIcon",
                "qrTintAnchor",
                "qrTintBorder",
                "autoCopyOnCreate",
            ),
        )
    }
}
