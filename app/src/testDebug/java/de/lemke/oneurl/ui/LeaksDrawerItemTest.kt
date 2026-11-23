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

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import de.lemke.commonutils.HiltTestRule
import de.lemke.commonutils.bypassOobe
import de.lemke.commonutils.data.SettingsRepository
import de.lemke.commonutils.idleMainLooper
import de.lemke.oneurl.R
import dev.oneuiproject.oneui.navigation.widget.DrawerNavigationView
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import javax.inject.Inject
import leakcanary.AppWatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [36])
class LeaksDrawerItemTest {
    @get:Rule(order = 0)
    val hiltRule = HiltTestRule(this)

    @Inject
    lateinit var settings: SettingsRepository

    @Before
    fun setup() {
        hiltRule.inject()
        settings.bypassOobe()
        if (!AppWatcher.isInstalled) {
            AppWatcher.manualInstall(ApplicationProvider.getApplicationContext<HiltTestApplication>())
        }
    }

    @Test
    fun leaksMenuItemIsVisibleInTheDebugBuild() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            idleMainLooper()
            scenario.onActivity { activity ->
                activity
                    .findViewById<DrawerNavigationView>(R.id.navigationView)
                    .findMenuItem(R.id.leaks_dest)
                    .shouldNotBeNull()
                    .isVisible shouldBe true
            }
        }
    }
}
