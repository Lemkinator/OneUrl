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

import androidx.test.core.app.ApplicationProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.lemke.oneurl.data.database.AppDatabase
import org.junit.rules.ExternalResource

/**
 * Closes the test's in-memory [AppDatabase] from [TestPersistenceModule] once the test finishes.
 *
 * Must be ordered after `HiltAndroidRule`: that rule discards the test's component when it finishes.
 */
class TestDatabaseRule : ExternalResource() {
    override fun after() {
        EntryPointAccessors
            .fromApplication<TestDatabaseEntryPoint>(ApplicationProvider.getApplicationContext())
            .appDatabase()
            .close()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TestDatabaseEntryPoint {
        fun appDatabase(): AppDatabase
    }
}
