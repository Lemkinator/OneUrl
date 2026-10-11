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

package de.lemke.oneurl.domain.model

import com.android.volley.Request
import de.lemke.oneurl.data.VolleyQueue

internal class MinimalProvider : ShortURLProvider {
    override val name = "Minimal"
    override val baseURL = "https://minimal.example"

    override fun getCreateRequest(
        queue: VolleyQueue,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ): Request<*> = throw UnsupportedOperationException()
}

internal class ProviderWithLinks : ShortURLProvider {
    override val name = "WithLinks"
    override val baseURL = "https://withlinks.example"
    override val privacyURL = "https://withlinks.example/privacy"
    override val termsURL = "https://withlinks.example/terms"

    override fun getCreateRequest(
        queue: VolleyQueue,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ): Request<*> = throw UnsupportedOperationException()
}
