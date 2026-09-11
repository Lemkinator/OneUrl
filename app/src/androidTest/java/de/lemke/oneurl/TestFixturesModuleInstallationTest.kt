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

import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import de.lemke.oneurl.data.UserSettings
import de.lemke.oneurl.data.database.AppDatabase
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards against Hilt's KSP module aggregation silently dropping a @TestInstallIn module
 * declared in src/testFixtures for the instrumented (src/androidTest) side, leaving the
 * production SettingsProvideModule/PersistenceModule active undetected. src/androidTest resolves
 * testFixtures-hosted modules through a separate discovery path than the Robolectric (src/test)
 * side, so that side's TestFixturesModuleInstallationTest does not cover this one. Keep this test
 * even though it currently passes - a silent regression here produces no other failing test.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TestFixturesModuleInstallationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var settings: UserSettings

    @Inject
    lateinit var database: AppDatabase

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun injectedSettingsDoNotWriteThroughToProductionSharedPreferences() {
        val productionPrefs = PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
        val lastAliasBefore = productionPrefs.all["lastAlias"]
        settings.lastAlias = "leak-guard-probe"
        assertEquals(
            "lastAlias changed in production SharedPreferences - TestSettingsModule (src/testFixtures) was " +
                "NOT installed; production SettingsProvideModule won instead",
            lastAliasBefore,
            productionPrefs.all["lastAlias"],
        )
    }

    @Test
    fun injectedDatabaseIsInMemoryNotTheProductionFileBackedAppDatabase() {
        assertNull(
            "AppDatabase is file-backed ('app') - TestPersistenceModule (src/testFixtures) was NOT installed; " +
                "production PersistenceModule won instead",
            database.openHelper.databaseName,
        )
    }
}
