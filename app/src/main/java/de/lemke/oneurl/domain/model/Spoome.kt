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
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import de.lemke.commonutils.R as commonutilsR

/*
https://spoo.me/docs/api-reference/url-shortening/create-shortened-url.md
example:
POST https://spoo.me/api/v1/shorten {"long_url": "https://example.com", "alias": "abc"}
No API key. Anonymous create limit: 20 requests per minute.

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
422 (validation)          {"detail":[{"loc":["body","alias"],"msg":"...","type":"..."}]}

Emoji links use an emoji-only alias, or "alias_type": "emoji" without alias for a generated one.

Click count: GET https://spoo.me/api/v1/public/stats/{alias}?start_date={ISO 8601}&end_date={ISO 8601}
{"stats": {"summary": {"total_clicks": 0}}}
Without start_date only the last 7 days count, and a range above 90 days answers 400, so the count covers
the last 90 days at most, or the time since the creation of a younger link.
 */
sealed class Spoome : ShortURLProvider {
    final override val group = "spoo.me, spoo.me (emoji)"
    final override val baseURL = "https://spoo.me"
    final override val apiURL = "$baseURL/api/v1/shorten"

    final override val clickCountPeriod = ClickCountPeriod.Last90Days
    protected open val generatedAliasType: String? = null

    override fun getAnalyticsURL(alias: String) = "$baseURL/stats/$alias"

    override fun sanitizeLongURL(url: String) = url.withHttps().trim()

    override fun getURLClickCount(
        queue: VolleyQueue,
        url: URL,
        callback: (clicks: Int?) -> Unit,
    ) {
        val end = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val created = url.added.toInstant().truncatedTo(ChronoUnit.MILLIS)
        if (created.isBefore(end)) {
            requestClicks(queue, url, maxOf(created, end.minus(MAX_STATS_RANGE)), end, callback)
        } else {
            callback(0)
        }
    }

    override fun getCreateRequest(
        queue: VolleyQueue,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ): JsonObjectRequest {
        val body = JSONObject().put("long_url", longURL)
        if (alias.isNotBlank()) {
            body.put("alias", alias)
        } else {
            generatedAliasType?.let { body.put("alias_type", it) }
        }
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

    private fun requestClicks(
        queue: VolleyQueue,
        url: URL,
        start: Instant,
        end: Instant,
        callback: (clicks: Int?) -> Unit,
    ) {
        val tag = "GetURLVisitCount_$name"
        val requestURL =
            "$baseURL/api/v1/public/stats/${url.alias.urlEncode()}" +
                "?start_date=${start.toString().urlEncode()}&end_date=${end.toString().urlEncode()}"
        Log.d(tag, "start request: $requestURL")
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

            response?.concerns("long_url") == true -> {
                errorCallback(GenerateURLError.InvalidURL)
            }

            response?.concerns("alias") == true -> {
                errorCallback(GenerateURLError.InvalidAlias)
            }

            data.isNullOrBlank() || response == null -> {
                errorCallback(GenerateURLError.Unknown(statusCode))
            }

            else -> {
                errorCallback(GenerateURLError.Custom(statusCode, response.optStringOrNull("error") ?: data))
            }
        }
    }

    private fun JSONObject.concerns(field: String): Boolean {
        val detail = optJSONArray("detail") ?: JSONArray()
        return optString("field") == field ||
            (0 until detail.length()).any { i ->
                val loc = detail.optJSONObject(i)?.optJSONArray("loc") ?: JSONArray()
                (0 until loc.length()).any { loc.optString(it) == field }
            }
    }

    private companion object {
        val MAX_STATS_RANGE: Duration = Duration.ofDays(90)
        const val MIN_ALIAS_LENGTH_DEFAULT = 3
        const val MAX_ALIAS_LENGTH_DEFAULT = 16
        const val MAX_ALIAS_LENGTH_EMOJI = 15
    }

    object Default : Spoome() {
        override val name = "spoo.me"
        override val aliasConfig =
            object : AliasConfig {
                override val minAliasLength = MIN_ALIAS_LENGTH_DEFAULT
                override val maxAliasLength = MAX_ALIAS_LENGTH_DEFAULT
                override val allowedAliasCharacters = "a-z, A-Z, 0-9, _, -"

                override fun isAliasValid(alias: String) = alias.matches(Regex("[a-zA-Z0-9_-]+"))
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
        override val generatedAliasType = "emoji"
        override val aliasConfig =
            object : AliasConfig {
                override val minAliasLength = 1
                override val maxAliasLength = MAX_ALIAS_LENGTH_EMOJI
                override val allowedAliasCharacters = "Emojis"

                override fun aliasLength(alias: String) = alias.codePointCount(0, alias.length)

                override fun isAliasValid(alias: String) = alias.isNotEmpty() && AcceptedEmoji.containsAll(alias)
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
