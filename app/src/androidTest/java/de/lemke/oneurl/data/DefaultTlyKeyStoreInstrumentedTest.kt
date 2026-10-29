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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import de.lemke.oneurl.domain.model.TlyApiKey
import eu.anifantakis.lib.ksafe.KSafe
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class DefaultTlyKeyStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.cacheDir, "tly-key-store-test")
    private lateinit var store: DefaultTlyKeyStore

    @Before
    fun createStore() {
        directory.deleteRecursively()
        store = DefaultTlyKeyStore(KSafe(context, fileName = "tlykeytest", baseDir = directory))
    }

    @After
    fun deleteStore() {
        directory.deleteRecursively()
    }

    @Test
    fun getReturnsNullBeforeAKeyWasStored() =
        runTest {
            store.get() shouldBe null
        }

    @Test
    fun getReturnsTheStoredKey() =
        runTest {
            store.set(TlyApiKey.ofOrNull("abc123"))

            store.get() shouldBe TlyApiKey.ofOrNull("abc123")
        }

    @Test
    fun setReplacesTheStoredKey() =
        runTest {
            store.set(TlyApiKey.ofOrNull("first"))

            store.set(TlyApiKey.ofOrNull("second"))

            store.get() shouldBe TlyApiKey.ofOrNull("second")
        }

    @Test
    fun setNullRemovesTheStoredKey() =
        runTest {
            store.set(TlyApiKey.ofOrNull("abc123"))

            store.set(null)

            store.get() shouldBe null
        }
}
