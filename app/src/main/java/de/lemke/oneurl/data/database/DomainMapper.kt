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

package de.lemke.oneurl.data.database

import de.lemke.oneurl.domain.model.ShortURLProviderCompanion
import de.lemke.oneurl.domain.model.URL

fun urlFromDb(urlDb: URLDb): URL =
    URL(
        shortURL = urlDb.shortURL,
        longURL = urlDb.longURL,
        shortURLProvider = ShortURLProviderCompanion.fromString(urlDb.shortURLProvider),
        qr = urlDb.qr,
        favorite = urlDb.favorite,
        title = urlDb.title,
        description = urlDb.description,
        added = urlDb.added,
    )

fun urlToDb(url: URL): URLDb =
    URLDb(
        shortURL = url.shortURL,
        longURL = url.longURL,
        shortURLProvider = url.shortURLProvider.name,
        qr = url.qr,
        favorite = url.favorite,
        title = url.title,
        description = url.description,
        added = url.added,
    )
