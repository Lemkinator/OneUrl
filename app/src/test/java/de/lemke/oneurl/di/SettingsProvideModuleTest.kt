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

package de.lemke.oneurl.di

import android.app.Application
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SettingsProvideModuleTest {
    private val scope = TestScope()

    @After
    fun cancelScope() {
        scope.cancel()
    }

    @Test
    fun provideUserSettings_writesToDefaultSharedPreferences() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        SettingsProvideModule.provideUserSettings(context, scope).lastURL = "https://example.com"

        PreferenceManager.getDefaultSharedPreferences(context).getString("lastURL", null) shouldBe "https://example.com"
    }
}
