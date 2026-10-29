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

import de.lemke.oneurl.domain.model.TlyApiKey
import kotlinx.coroutines.CompletableDeferred

/** Holds the key in memory; [get] waits for [gate] while one is set, and both calls fail while [failing] is set. */
class FakeTlyKeyStore(
    var apiKey: TlyApiKey? = null,
) : TlyKeyStore {
    var gate: CompletableDeferred<Unit>? = null
    var failing = false

    override suspend fun get(): StoredTlyKey {
        gate?.await()
        if (failing) return StoredTlyKey.Unavailable
        return apiKey?.let(StoredTlyKey::Key) ?: StoredTlyKey.None
    }

    override suspend fun set(apiKey: TlyApiKey?): TlyKeyWrite {
        if (failing) return TlyKeyWrite.Failed
        this.apiKey = apiKey
        return TlyKeyWrite.Saved
    }
}
