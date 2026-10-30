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

import de.lemke.commonutils.ui.activity.CommonUtilsSettingsActivity
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import de.lemke.commonutils.R as commonutilsR

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [36])
class AppTest {
    @After
    fun resetSettingsActivity() {
        CommonUtilsSettingsActivity.preferences = emptyList()
    }

    @Test
    fun `the application sets the settings preferences without MainActivity`() {
        CommonUtilsSettingsActivity.preferences shouldBe settingsPreferences
        settingsPreferences shouldBe
            listOf(
                commonutilsR.xml.preferences_design,
                R.xml.preferences,
                commonutilsR.xml.preferences_backup,
                commonutilsR.xml.preferences_dev_options_delete_app_data,
                commonutilsR.xml.preferences_more_info,
            )
    }
}
