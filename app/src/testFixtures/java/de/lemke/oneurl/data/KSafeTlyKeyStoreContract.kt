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
import eu.anifantakis.lib.ksafe.KSafe
import java.io.File
import org.junit.After

/** [TlyKeyStoreContract] over [KSafeSecretStore] on a [KSafe] file that each test starts empty. */
abstract class KSafeTlyKeyStoreContract : TlyKeyStoreContract() {
    protected abstract val context: Context
    private val directory by lazy { File(context.cacheDir, "tly-key-store-test") }
    private val openStores = mutableListOf<KSafe>()

    override fun createStore(): TlyKeyStore {
        directory.deleteRecursively()
        val ksafe = KSafe(context, fileName = "tlykeytest${System.nanoTime()}", baseDir = directory)
        openStores += ksafe
        return DefaultTlyKeyStore(KSafeSecretStore(ksafe))
    }

    @After
    fun deleteStore() {
        openStores.forEach(KSafe::close)
        directory.deleteRecursively()
    }
}
