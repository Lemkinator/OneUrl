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

import android.content.SharedPreferences
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.data.delegates
import de.lemke.commonutils.data.mapped
import de.lemke.oneurl.domain.model.ShortURLProvider
import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** OneUrl-specific settings, layered on top of common-utils [SettingsRepository]. */
class UserSettings(
    preferences: SharedPreferences,
    scope: CoroutineScope,
) : SettingsRepository(preferences) {
    /** The URL-shortening service currently selected for new URLs. */
    var selectedShortURLProvider: ShortURLProvider by preferences.delegates
        .string(ShortURLProviderCompanion.default.name)
        .mapped(ShortURLProviderCompanion::fromStringOrDefault) { it.name }

    /** The most recently entered alias in the Add-URL screen. */
    var lastAlias: String by preferences.delegates.string("")

    /** The most recently entered long URL in the Add-URL screen. */
    var lastURL: String by preferences.delegates.string("")

    /** The most recently entered description in the Add-URL screen. */
    var lastDescription: String by preferences.delegates.string("")

    /** The most recently generated QR code URL. */
    var qrURL: String by preferences.delegates.string("")

    /** Recently used QR code background colors, most recent first. */
    var qrRecentBackgroundColors: List<Int> by preferences.delegates.intList(listOf(DEFAULT_QR_BACKGROUND_COLOR))

    /** Recently used QR code foreground colors, most recent first. */
    var qrRecentForegroundColors: List<Int> by preferences.delegates.intList(listOf(DEFAULT_QR_FOREGROUND_COLOR))

    /** The QR code export size in pixels. */
    var qrSize: Int by preferences.delegates.int(DEFAULT_QR_SIZE)

    /** Whether QR codes are exported with a rounded frame. */
    var qrFrame: Boolean by preferences.delegates.boolean(true)

    /** Whether QR codes are exported with the app icon overlay. */
    var qrIcon: Boolean by preferences.delegates.boolean(true)

    /** Whether the QR code's position-detection anchors are tinted with the foreground color. */
    var qrTintAnchor: Boolean by preferences.delegates.boolean(false)

    /** Whether the QR code's border is tinted with the foreground color. */
    var qrTintBorder: Boolean by preferences.delegates.boolean(false)

    /** Whether the shortened URL is copied to the clipboard automatically after creation. */
    var autoCopyOnCreate: Boolean by preferences.delegates.boolean(false)

    /**
     * A [StateFlow] of the current [UserSettingsSnapshot]. See [settingsFlow] for the backing
     * OnSharedPreferenceChangeListener contract.
     */
    val flow: StateFlow<UserSettingsSnapshot> = settingsFlow(scope, ::snapshot)

    private fun snapshot() = UserSettingsSnapshot(selectedShortURLProvider = selectedShortURLProvider)

    companion object {
        const val DEFAULT_QR_BACKGROUND_COLOR = -1
        const val DEFAULT_QR_FOREGROUND_COLOR = -16777216
        const val DEFAULT_QR_SIZE = 512
    }
}

/**
 * Reactive snapshot of the settings other screens need to observe live.
 *
 * Deliberately narrow: every other [UserSettings] field is only ever read synchronously (at
 * ViewModel init or on submit) by the single screen that owns it. [selectedShortURLProvider] is the
 * one exception — [de.lemke.oneurl.ui.AddURLViewModel] must react to it changing while the Add-URL
 * screen is alive, because the user can change it from [de.lemke.oneurl.ui.ProviderActivity] in the
 * background.
 */
data class UserSettingsSnapshot(
    val selectedShortURLProvider: ShortURLProvider = ShortURLProviderCompanion.default,
)

/** Emits only when [UserSettingsSnapshot.selectedShortURLProvider] changes. */
val StateFlow<UserSettingsSnapshot>.selectedShortURLProvider: Flow<ShortURLProvider>
    get() = map { it.selectedShortURLProvider }.distinctUntilChanged()
