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

import android.util.Log
import de.lemke.oneurl.domain.model.TlyApiKey
import eu.anifantakis.lib.ksafe.KSafe
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

/** Keeps the T.LY API key the user entered, if any. */
interface TlyKeyStore {
    suspend fun get(): TlyApiKey?

    /** Stores [apiKey], or removes the stored key when it is null. */
    suspend fun set(apiKey: TlyApiKey?)
}

/** Encrypted string storage behind [DefaultTlyKeyStore]. */
interface SecretStore {
    suspend fun get(key: String): String?

    suspend fun put(
        key: String,
        value: String,
    )

    suspend fun delete(key: String)
}

class KSafeSecretStore @Inject constructor(
    private val ksafe: KSafe,
) : SecretStore {
    override suspend fun get(key: String): String? = ksafe.get(key, null, String.serializer().nullable)

    override suspend fun put(
        key: String,
        value: String,
    ) = ksafe.put(key, value, String.serializer())

    override suspend fun delete(key: String) = ksafe.delete(key)
}

/** A failing [SecretStore], such as a broken Android Keystore, reads as no key and drops writes. */
class DefaultTlyKeyStore @Inject constructor(
    private val secrets: SecretStore,
) : TlyKeyStore {
    override suspend fun get(): TlyApiKey? = attempt(null) { secrets.get(KEY)?.let(TlyApiKey::ofOrNull) }

    override suspend fun set(apiKey: TlyApiKey?) =
        attempt(Unit) {
            if (apiKey == null) secrets.delete(KEY) else secrets.put(KEY, apiKey.token)
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> attempt(
        onFailure: T,
        block: suspend () -> T,
    ): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "The T.LY key store failed", e)
            onFailure
        }

    private companion object {
        const val KEY = "tlyApiKey"
        const val TAG = "DefaultTlyKeyStore"
    }
}
