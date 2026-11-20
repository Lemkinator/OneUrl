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
import androidx.test.core.app.ApplicationProvider
import de.lemke.oneurl.data.FakeAndroidKeyStoreRule
import de.lemke.oneurl.data.KSafeSecretStore
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SecretsModuleTest {
    @get:Rule
    val androidKeyStore = FakeAndroidKeyStoreRule()

    @Test
    fun provideKSafe_storesASecretThatTheStoreReadsBack() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val store = KSafeSecretStore(SecretsModule.provideKSafe(context))

            store.put("tly", "secret")

            store.get("tly") shouldBe "secret"
            val storeDir = File(context.noBackupFilesDir, "ksafe")
            storeDir.isDirectory shouldBe true
            storeDir.walkTopDown().count { it.isFile } shouldBeGreaterThan 0
        }
    }
}
