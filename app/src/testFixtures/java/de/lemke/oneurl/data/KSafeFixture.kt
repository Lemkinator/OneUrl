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
import java.security.KeyStore
import java.util.UUID

/** Opens [KSafe] instances over one file name and directory, so a later [open] reads what an earlier instance wrote. */
class KSafeFixture(
    private val context: () -> Context,
) {
    val fileName = "secretstoretest${UUID.randomUUID().toString().replace("-", "")}"
    private val directory by lazy { File(context().cacheDir, fileName) }
    private val opened = mutableListOf<KSafe>()

    fun open(): KSafe = KSafe(context(), fileName = fileName, baseDir = directory).also { opened += it }

    fun openEmpty(): KSafe {
        directory.deleteRecursively()
        return open()
    }

    /** The Keystore aliases that belong to this fixture's store. */
    fun aliases(): List<String> =
        KeyStore
            .getInstance("AndroidKeyStore")
            .apply { load(null) }
            .aliases()
            .toList()
            .filter { fileName in it }

    /**
     * Deletes the store's Keystore keys and files, then closes every instance this fixture opened.
     * The keys go through a fresh instance: `clearAll` on a closed [KSafe] never returns, and a test may have closed its own.
     */
    suspend fun close() {
        open().clearAll()
        opened.forEach(KSafe::close)
        directory.deleteRecursively()
    }
}
