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
import de.lemke.oneurl.BuildConfig
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject

class CheckURLSafetyUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    sealed class UrlhausResult {
        data object Ok : UrlhausResult()

        data class Blacklisted(val message: String, val urlhausLink: String?, val virustotalLink: String?) : UrlhausResult()
    }

    suspend operator fun invoke(url: String): UrlhausResult =
        suspendCancellableCoroutine { cont ->
            val tag = "UrlhausCheckRequest"
            val checkUrlApi = "https://urlhaus-api.abuse.ch/v1/url"
            Log.d(tag, "start request: $checkUrlApi")
            val req =
                object : StringRequest(
                    Method.POST,
                    checkUrlApi,
                    { response ->
                        if (cont.isActive) {
                            cont.resume(parseUrlhausResponse(tag, url, response))
                        }
                    },
                    { error ->
                        if (cont.isActive) {
                            Log.w(tag, "Skipped Urlhaus Check because of error: $error")
                            cont.resume(UrlhausResult.Ok)
                        }
                    },
                ) {
                    override fun getParams() = mapOf("url" to url)

                    override fun getHeaders() = mapOf("Auth-Key" to BuildConfig.URL_HAUS_AUTH_KEY)
                }
            RequestQueueSingleton.getInstance(context).addToRequestQueue(req)
            cont.invokeOnCancellation { req.cancel() }
        }

    @Suppress("TooGenericExceptionCaught")
    private fun parseUrlhausResponse(
        tag: String,
        url: String,
        response: String,
    ): UrlhausResult =
        try {
            Log.d(tag, "response: $response")
            val responseJson = JSONObject(response)
            val blacklists = responseJson.optJSONObject("blacklists")
            when {
                responseJson.optString("query_status") != "ok" -> {
                    Log.d(tag, "Urlhaus Check returned no results for $url")
                    UrlhausResult.Ok
                }

                blacklists == null -> {
                    Log.d(tag, "Urlhaus Check: no blacklist data for $url")
                    UrlhausResult.Ok
                }

                else -> {
                    buildBlacklistedResult(responseJson, blacklists)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Skipped Urlhaus Check for $url because of Exception: $e")
            UrlhausResult.Ok
        }

    private fun buildBlacklistedResult(
        responseJson: JSONObject,
        blacklists: JSONObject,
    ): UrlhausResult.Blacklisted {
        val surblListed = blacklists.optString("surbl", "not listed") != "not listed"
        val spamhausStatus = blacklists.optString("spamhaus_dbl", "not listed")
        val spamhausListed = spamhausStatus != "not listed"
        val listedOn =
            buildString {
                append("URLhaus")
                if (surblListed) append(", SURBL")
                if (spamhausListed) append(", Spamhaus")
            }
        val urlhausLink = responseJson.optString("urlhaus_reference").ifBlank { null }
        val virustotalLink =
            responseJson
                .optJSONArray("payloads")
                ?.optJSONObject(0)
                ?.optJSONObject("virustotal")
                ?.optString("link")
                ?.ifBlank { null }
        return UrlhausResult.Blacklisted(
            context.getString(R.string.error_urlhaus_blacklisted, listedOn, reasonFor(spamhausStatus)),
            urlhausLink,
            virustotalLink,
        )
    }

    private fun reasonFor(spamhausStatus: String): String =
        when (spamhausStatus) {
            "spammer_domain" -> context.getString(R.string.error_urlhaus_spammer_domain)
            "phishing_domain" -> context.getString(R.string.error_urlhaus_phishing_domain)
            "botnet_cc_domain" -> context.getString(R.string.error_urlhaus_botnet_cc_domain)
            "abused_legit_spam" -> context.getString(R.string.error_urlhaus_abused_legit_spam)
            "abused_legit_malware" -> context.getString(R.string.error_urlhaus_abused_legit_malware)
            "abused_legit_phishing" -> context.getString(R.string.error_urlhaus_abused_legit_phishing)
            "abused_legit_botnetcc" -> context.getString(R.string.error_urlhaus_abused_legit_botnetcc)
            "abused_redirector" -> context.getString(R.string.error_urlhaus_abused_redirector)
            else -> context.getString(R.string.error_urlhaus_default)
        }
}
