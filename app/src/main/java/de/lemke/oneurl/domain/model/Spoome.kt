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
import com.android.volley.Request
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import de.lemke.commonutils.ui.utils.urlEncode
import de.lemke.commonutils.ui.utils.withHttps
import de.lemke.oneurl.R
import de.lemke.oneurl.data.VolleyQueue
import org.json.JSONException
import org.json.JSONObject
import de.lemke.commonutils.R as commonutilsR

/*
https://spoo.me/docs/api-reference/url-shortening/create-shortened-url.md
example:
POST https://spoo.me/api/v1/shorten {"long_url": "https://example.com", "alias": "abc"}
No API key. Anonymous limit: 20 requests per minute and 200 per day.

response (201):
{
    "alias": "abc",
    "short_url": "https://spoo.me/abc",
    "long_url": "https://example.com"
}

Errors ({"error", "code", "field"}):
400/422 field "long_url"  {"error":"URL is not allowed or invalid","code":"validation_error","field":"long_url"}
400/422 field "alias"     {"error":"alias: Value error, alphanumeric alias must be 3-16 characters",
                          "code":"validation_error","field":"alias"}
409                       {"error":"Alias is already in use","code":"conflict"}
429                       rate limit exceeded

Emoji links are created with an emoji-only alias. Only the legacy POST /emoji generates random emoji,
so the emoji provider uses it when no alias is given. It answers with an http:// short link and
{"UrlError": "..."} for an invalid URL. Legacy limit: 5 per minute and 50 per day.

Click count: GET https://spoo.me/api/v1/public/stats/{alias}
{"stats": {"summary": {"total_clicks": 0}}}
Reads links created by either API generation, unlike the legacy POST /stats/{alias}.
 */
sealed class Spoome : ShortURLProvider {
    final override val group = "spoo.me, spoo.me (emoji)"
    final override val baseURL = "https://spoo.me"
    final override val apiURL = "$baseURL/api/v1/shorten"

    override fun getAnalyticsURL(alias: String) = "$baseURL/stats/$alias"

    override fun sanitizeLongURL(url: String) = url.withHttps().trim()

    override fun getURLClickCount(
        queue: VolleyQueue,
        url: URL,
        callback: (clicks: Int?) -> Unit,
    ) {
        val tag = "GetURLVisitCount_$name"
        val requestURL = "$baseURL/api/v1/public/stats/${url.alias.urlEncode()}"
        Log.d(tag, "start request: $url")
        queue.add(
            JsonObjectRequest(
                Request.Method.GET,
                requestURL,
                null,
                { response ->
                    try {
                        Log.d(tag, "response: $response")
                        val clicks = response.getJSONObject("stats").getJSONObject("summary").getInt("total_clicks")
                        Log.d(tag, "clicks: $clicks")
                        callback(clicks)
                    } catch (e: JSONException) {
                        Log.e(tag, "error parsing click count response", e)
                        callback(null)
                    }
                },
                { error ->
                    Log.e(tag, "error: $error")
                    callback(null)
                },
            ),
        )
    }

    override fun getCreateRequest(
        queue: VolleyQueue,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ): JsonObjectRequest {
        val body = JSONObject().put("long_url", longURL)
        if (alias.isNotBlank()) body.put("alias", alias)
        return createRequest(apiURL, body, successCallback, errorCallback)
    }

    protected fun createRequest(
        requestURL: String,
        body: JSONObject?,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ): JsonObjectRequest {
        val tag = "SpoomeCreateRequest_$name"
        Log.d(tag, "start request: $requestURL")
        return object : JsonObjectRequest(
            Method.POST,
            requestURL,
            body,
            { response -> handleResponse(tag, response, successCallback, errorCallback) },
            { error -> handleError(tag, error, errorCallback) },
        ) {
            override fun getHeaders() = mapOf("Accept" to "application/json")
        }
    }

    private fun handleResponse(
        tag: String,
        response: JSONObject,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ) {
        Log.d(tag, "response: $response")
        val shortURL = response.optStringOrNull("short_url")?.trim()
        if (shortURL != null) {
            Log.d(tag, "shortURL: $shortURL")
            successCallback("https://" + shortURL.removePrefix("http://").removePrefix("https://"))
        } else {
            Log.e(tag, "error: response does not contain short_url")
            errorCallback(GenerateURLError.Unknown(HttpStatusCode.OK))
        }
    }

    private fun handleError(
        tag: String,
        error: VolleyError,
        errorCallback: (error: GenerateURLError) -> Unit,
    ) {
        Log.e(tag, "error: $error")
        val networkResponse = error.networkResponse
        val statusCode = networkResponse?.statusCode
        val data = networkResponse?.data?.toString(Charsets.UTF_8)
        val response = data?.let { runCatching { JSONObject(it) }.getOrNull() }
        Log.e(tag, "$statusCode: message: ${error.message} data: $data")
        when {
            error is NoConnectionError -> {
                errorCallback(GenerateURLError.ServiceOffline)
            }

            error is ParseError -> {
                errorCallback(GenerateURLError.ServiceTemporarilyUnavailable(baseURL))
            }

            statusCode == null -> {
                errorCallback(GenerateURLError.Unknown())
            }

            statusCode == HttpStatusCode.TOO_MANY_REQUESTS -> {
                errorCallback(GenerateURLError.RateLimitExceeded)
            }

            statusCode == HttpStatusCode.CONFLICT -> {
                errorCallback(GenerateURLError.AliasAlreadyExists)
            }

            response?.has("UrlError") == true || response?.optString("field") == "long_url" -> {
                errorCallback(GenerateURLError.InvalidURL)
            }

            response?.optString("field") == "alias" -> {
                errorCallback(GenerateURLError.InvalidAlias)
            }

            data.isNullOrBlank() || response == null -> {
                errorCallback(GenerateURLError.Unknown(statusCode))
            }

            else -> {
                errorCallback(GenerateURLError.Custom(statusCode, data))
            }
        }
    }

    private companion object {
        const val MAX_ALIAS_LENGTH_DEFAULT = 15
        const val MAX_ALIAS_LENGTH_EMOJI = 30 // returns invalid alias if more than 30
    }

    object Default : Spoome() {
        override val name = "spoo.me"
        override val aliasConfig =
            object : AliasConfig {
                override val minAliasLength = 0
                override val maxAliasLength = MAX_ALIAS_LENGTH_DEFAULT
                override val allowedAliasCharacters = "a-z, A-Z, 0-9, _"

                override fun isAliasValid(alias: String) = alias.matches(Regex("[a-zA-Z0-9_]+"))
            }

        override fun getInfoContents(context: Context): List<ProviderInfo> =
            listOf(
                ProviderInfo(
                    dev.oneuiproject.oneui.R.drawable.ic_oui_tool_outline,
                    context.getString(R.string.alias),
                    context.getString(
                        R.string.alias_text,
                        aliasConfig.minAliasLength,
                        aliasConfig.maxAliasLength,
                        aliasConfig.allowedAliasCharacters,
                    ),
                ),
                ProviderInfo(
                    dev.oneuiproject.oneui.R.drawable.ic_oui_report,
                    context.getString(R.string.analytics),
                    context.getString(R.string.analytics_text),
                ),
            )
    }

    object Emoji : Spoome() {
        override val name = "spoo.me (emoji)"
        override val aliasConfig =
            object : AliasConfig {
                override val minAliasLength = 0
                override val maxAliasLength = MAX_ALIAS_LENGTH_EMOJI
                override val allowedAliasCharacters = "Emojis"

                // one or more characters that belong to the "Symbol, Other" Unicode category, which includes emoji characters
                override fun isAliasValid(alias: String) = alias.matches(Regex("\\p{So}+"))
            }

        override fun getCreateRequest(
            queue: VolleyQueue,
            longURL: String,
            alias: String,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit,
        ): JsonObjectRequest =
            if (alias.isBlank()) {
                createRequest("$baseURL/emoji?url=${longURL.urlEncode()}", null, successCallback, errorCallback)
            } else {
                super.getCreateRequest(queue, longURL, alias, successCallback, errorCallback)
            }

        override fun getTipsCardTitleAndInfo(context: Context) =
            Pair(
                context.getString(commonutilsR.string.commonutils_info),
                context.getString(R.string.emoji_text),
            )

        override fun getInfoContents(context: Context): List<ProviderInfo> =
            listOf(
                ProviderInfo(
                    dev.oneuiproject.oneui.R.drawable.ic_oui_emoji,
                    context.getString(R.string.emoji),
                    context.getString(R.string.emoji_text),
                ),
                ProviderInfo(
                    dev.oneuiproject.oneui.R.drawable.ic_oui_tool_outline,
                    context.getString(R.string.alias),
                    context.getString(
                        R.string.alias_text,
                        aliasConfig.minAliasLength,
                        aliasConfig.maxAliasLength,
                        aliasConfig.allowedAliasCharacters,
                    ),
                ),
                ProviderInfo(
                    dev.oneuiproject.oneui.R.drawable.ic_oui_report,
                    context.getString(R.string.analytics),
                    context.getString(R.string.analytics_text),
                ),
            )
    }
}
