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

import android.content.Context
import android.util.Log
import com.android.volley.NoConnectionError
import com.android.volley.ParseError
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.data.VolleyQueue
import org.json.JSONObject
import de.lemke.commonutils.R as commonutilsR

/*
login required (also for alias), but:
https://t.ly/api/v1/link/shorten
https://t.ly/api/v1/link/providers
t.ly, ibit.ly, twtr.to, jpeg.ly, is.gd, rebrand.ly, tinyurl, bit.ly
t.ly: shows t.ly for valid links (example.com) and tinyurl.com else (example), works also when bit.ly asks for t.ly api key?
ibit.ly: responds ibit.ly for valid links (example.com) and tinyurl.com else (example)
twtr.to: responds t.ly for valid links (example.com) and tinyurl.com else (example)
jpeg.ly: responds jpeg.ly for valid links (example.com) and tinyurl.com else (example)
is.gd: responds is.gd for valid links (example.com) and tinyurl.com else (example)
rebrand.ly: responds t.ly for valid links (example.com) and tinyurl.com else (example)
tinyurl: tinyurl.com
bit.ly: responds t.ly for valid links (example.com) and tinyurl.com else (example), some requests:
T.LY account and API key required to create additional short links.
wtf? :D anyway...

json:
{
  "long_url":"https://example.com",
  "provider":"ibit.ly"
}
header:
accept:application/json
content-type:application/json;charset=UTF-8
origin:chrome-extension://oodfdmglhbbkkcngodjjagblikmoegpa

responds:
{
    "short_url": "https:\/\/t.ly\/xqZ7s",
    "provider_success": true,
    "info": {
        "description": null,
        "redirect_url": "https:\/\/example.com",
        "title": "https:\/\/example.com",
        "url": "https:\/\/example.com",
        "twitter_url": "https:\/\/twitter.com\/intent\/tweet?text=-&url=https:\/\/t.ly\/xqZ7s&related=Weatherext,tim_leland",
        "facebook_url": "https:\/\/www.facebook.com\/sharer\/sharer.php?u=https:\/\/t.ly\/xqZ7s",
        "can_update": true
    },
    "domain": "t.ly",
    "short_id": "xqZ7s"
}

errors:
422 {
  "message": "The long url field is required.",
  "errors": {
    "long_url": [
      "The long url field is required."
    ]
  }
}
422 {
  "message": "T.LY account and API key required to create additional short links."
}
 */
private const val KEYED_API_URL = "https://api.t.ly/api/v1/link/shorten"
private val KEY_REJECTED_STATUS_CODES = setOf(HttpStatusCode.UNAUTHORIZED, HttpStatusCode.PAYMENT_REQUIRED, HttpStatusCode.FORBIDDEN)

sealed class Tly : ShortURLProvider {
    abstract val domain: String
    final override val group = "t.ly [experimental]"
    final override val baseURL = "https://t.ly"
    final override val apiURL = "$baseURL/api/v1/link/shorten"
    final override val privacyURL = "$baseURL/privacy"
    final override val termsURL = "$baseURL/terms"
    val keySettingsURL = "$baseURL/settings#/api"

    override fun getInfoContents(context: Context): List<ProviderInfo> =
        listOf(
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_labs,
                context.getString(commonutilsR.string.commonutils_experimental),
                context.getString(R.string.tly_info),
            ),
        )

    override fun getTipsCardTitleAndInfo(context: Context): Pair<String, String>? =
        Pair(
            context.getString(commonutilsR.string.commonutils_info),
            context.getString(R.string.tly_info),
        )

    final override fun getCreateRequest(
        queue: VolleyQueue,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ): JsonObjectRequest = getTlyCreateRequest(null, longURL, successCallback, errorCallback)

    /** Creates under the account of [apiKey] through the documented API, or without an account when it is null. */
    fun getTlyCreateRequest(
        apiKey: TlyApiKey?,
        longURL: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ): JsonObjectRequest {
        val tag = "CreateRequest_$name"
        val url = if (apiKey == null) apiURL else KEYED_API_URL
        val body =
            if (apiKey == null) {
                mapOf("long_url" to longURL, "provider" to domain)
            } else {
                mapOf("long_url" to longURL, "domain" to "https://$domain/")
            }
        Log.d(tag, "start request: $url $body")
        return object : JsonObjectRequest(
            Method.POST,
            url,
            JSONObject(body),
            { response ->
                Log.d(tag, "response: $response")
                val shortURL = response.optStringOrNull("short_url")?.trim()
                if (shortURL != null) {
                    Log.d(tag, "shortURL: $shortURL")
                    successCallback(shortURL)
                } else {
                    Log.e(tag, "error: no shortURL in response")
                    errorCallback(GenerateURLError.Unknown(HttpStatusCode.OK))
                }
            },
            { error -> handleTlyError(tag, error, apiKey != null, errorCallback) },
        ) {
            override fun getHeaders() =
                if (apiKey == null) {
                    mapOf(
                        "accept" to "application/json",
                        "content-type" to "application/json;charset=UTF-8",
                        "origin" to "chrome-extension://oodfdmglhbbkkcngodjjagblikmoegpa",
                    )
                } else {
                    mapOf(
                        "accept" to "application/json",
                        "content-type" to "application/json;charset=UTF-8",
                        "authorization" to "Bearer ${apiKey.token}",
                    )
                }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun handleTlyError(
        tag: String,
        error: VolleyError,
        keyed: Boolean,
        errorCallback: (error: GenerateURLError) -> Unit,
    ) {
        // An exception escaping a Volley callback on the main thread crashes the app.
        try {
            errorCallback(toGenerateURLError(tag, error, keyed))
        } catch (e: Exception) {
            Log.e(tag, "error parsing error response", e)
            errorCallback(GenerateURLError.Unknown())
        }
    }

    private fun toGenerateURLError(
        tag: String,
        error: VolleyError,
        keyed: Boolean,
    ): GenerateURLError {
        Log.e(tag, "error: $error")
        val networkResponse = error.networkResponse
        val statusCode = networkResponse?.statusCode
        val data = networkResponse?.data?.toString(Charsets.UTF_8)
        val message = data?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it).optStringOrNull("message") }.getOrNull() }
        Log.e(tag, "$statusCode: message: ${error.message} data: $data")
        Log.e(tag, "response message: $message")
        return when {
            error is NoConnectionError -> GenerateURLError.ServiceOffline
            error is ParseError -> GenerateURLError.ServiceTemporarilyUnavailable(baseURL)
            statusCode == null -> GenerateURLError.Unknown()
            keyed && statusCode in KEY_REJECTED_STATUS_CODES -> GenerateURLError.TlyKeyRejected
            statusCode == HttpStatusCode.SERVICE_UNAVAILABLE -> GenerateURLError.ServiceTemporarilyUnavailable(baseURL)
            data.isNullOrBlank() || message.isNullOrBlank() -> GenerateURLError.Unknown(statusCode)
            message.contains("long url field is required", true) -> GenerateURLError.InvalidURL
            !keyed && message.contains("T.LY account and API key", true) -> GenerateURLError.RateLimitExceeded
            else -> GenerateURLError.Custom(statusCode, message)
        }
    }

    object Default : Tly() {
        override val name = "t.ly"
        override val domain = "t.ly"
    }

    object Ibitly : Tly() {
        override val name = "t.ly (ibit.ly)"
        override val domain = "ibit.ly"
    }

    object Jpegly : Tly() {
        override val name = "t.ly (jpeg.ly)"
        override val domain = "jpeg.ly"
    }

    object Rebrandly : Tly() {
        override val enabled = false // useless, only responds with t.ly??
        override val name = "t.ly (rebrand.ly)"
        override val domain = "rebrand.ly"
    }

    object Bitly : Tly() {
        override val enabled = false // requires api key after some requests?
        override val name = "t.ly (bit.ly)"
        override val domain = "bit.ly"
    }
}
