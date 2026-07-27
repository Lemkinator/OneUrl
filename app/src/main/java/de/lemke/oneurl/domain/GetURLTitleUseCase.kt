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

package de.lemke.oneurl.domain

import android.content.Context
import android.util.Log
import com.android.volley.toolbox.StringRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.commonutils.withHttps
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class GetURLTitleUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(url: String): String? =
        suspendCancellableCoroutine { cont ->
            val req =
                StringRequest(
                    url.withHttps(),
                    { response ->
                        if (!cont.isActive) return@StringRequest
                        // Broad catch is intentional: Volley delivers this callback on the main thread, so an
                        // unexpected exception here must not escape and crash the whole app.
                        try {
                            val title =
                                Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                                    .find(response)
                                    ?.groupValues
                                    ?.get(1)
                                    ?.trim()
                            Log.d("GetURLTitleUseCase", "title: $title")
                            cont.resume(title)
                        } catch (e: Exception) {
                            Log.e("GetURLTitleUseCase", "error parsing title from response", e)
                            cont.resume(null)
                        }
                    },
                    { error ->
                        if (!cont.isActive) return@StringRequest
                        error.printStackTrace()
                        cont.resume(null)
                    },
                )
            RequestQueueSingleton.getInstance(context).addToRequestQueue(req)
            cont.invokeOnCancellation { req.cancel() }
        }
}
