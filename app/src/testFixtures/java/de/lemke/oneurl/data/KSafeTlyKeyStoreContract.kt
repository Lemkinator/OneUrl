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

import android.content.Context
import de.lemke.oneurl.domain.model.TlyApiKey
import eu.anifantakis.lib.ksafe.KSafeProtection
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

abstract class KSafeTlyKeyStoreContract : TlyKeyStoreContract() {
    protected abstract val context: Context
    private val ksafes = KSafeFixture { context }

    override fun createStore(): TlyKeyStore = DefaultTlyKeyStore(KSafeSecretStore(ksafes.openEmpty()))

    @After
    fun closeStores() = runTest { ksafes.close() }

    @Test
    fun storedKeyIsEncryptedAndReadableByANewInstance() =
        runTest {
            val writer = ksafes.openEmpty()
            DefaultTlyKeyStore(KSafeSecretStore(writer)).set(TlyApiKey.ofOrNull("abc123")) shouldBe TlyKeyWrite.Saved
            writer.close()

            val reader = ksafes.open()

            DefaultTlyKeyStore(KSafeSecretStore(reader)).get() shouldBe keyOf("abc123")
            reader.getKeyInfo("tlyApiKey")?.protection shouldBe KSafeProtection.DEFAULT
        }
}
