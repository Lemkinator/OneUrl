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

/** Answers the title set in [titles] for a URL, or null; records each requested URL. */
class FakePageTitleFetcher : PageTitleFetcher {
    val titles = mutableMapOf<String, String>()
    val requested = mutableListOf<String>()

    override suspend fun title(url: String): String? {
        requested += url
        return titles[url]
    }
}
