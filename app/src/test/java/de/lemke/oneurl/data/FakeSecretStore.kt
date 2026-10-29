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

class FakeSecretStore(
    private val values: MutableMap<String, String> = mutableMapOf(),
) : SecretStore {
    var failure: Exception? = null

    fun stored(key: String): String? = values[key]

    override suspend fun get(key: String): String? {
        failure?.let { throw it }
        return values[key]
    }

    override suspend fun put(
        key: String,
        value: String,
    ) {
        failure?.let { throw it }
        values[key] = value
    }

    override suspend fun delete(key: String) {
        failure?.let { throw it }
        values.remove(key)
    }
}
