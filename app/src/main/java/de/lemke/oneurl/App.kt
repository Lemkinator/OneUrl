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

package de.lemke.oneurl

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.data.applyDarkMode
import de.lemke.commonutils.ui.utils.setupCommonUtilsAboutActivity
import de.lemke.commonutils.ui.utils.setupCommonUtilsSettingsActivity
import javax.inject.Inject
import de.lemke.commonutils.R as commonutilsR

internal val settingsPreferences =
    listOf(
        commonutilsR.xml.preferences_design,
        R.xml.preferences,
        commonutilsR.xml.preferences_backup,
        commonutilsR.xml.preferences_dev_options_delete_app_data,
        commonutilsR.xml.preferences_more_info,
    )

/**
 * Main entry point into the application process.
 * Registered in the AndroidManifest.xml file.
 */
@HiltAndroidApp
class App : Application() {
    @Inject
    lateinit var settings: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        settings.applyDarkMode()
        setupCommonUtilsAboutActivity(appVersion = BuildConfig.VERSION_NAME)
        setupCommonUtilsSettingsActivity(settingsPreferences)
    }
}
