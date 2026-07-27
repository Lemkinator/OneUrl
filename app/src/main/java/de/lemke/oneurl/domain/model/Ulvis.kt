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
import com.android.volley.Request
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import de.lemke.commonutils.ui.utils.urlEncodeAmpersand
import de.lemke.commonutils.ui.utils.withHttps
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import org.json.JSONException
import org.json.JSONObject

/*
https://ulvis.net/developer.html
example:
https://ulvis.net/api.php?url=https://example.com&custom=alias&private=1
https://ulvis.net/API/write/get?custom=alias&url=https://example.com

response:
{
  "success": true,
  "data": {
    "id": "example12",
    "url": "https://ulvis.net/example12",
    "full": "https://example.com"
  }
}


fail:
200 {
  "success": false,
  "error": {
    "code": 1,
    "msg": "invalid url"
  }
}
200 {
  "success": true,
  "data": {
    "status": "custom-taken"
  }
}

code 0:
{"success":false,"error":{"code":0,"msg":"domain not allowed"}}
code 1:
{"success":false,"error":{"code":1,"msg":"invalid url"}}
code 2:
{"success":false,"error":{"code":2,"msg":"custom name must be less than 60 chars"}}

stats: https://ulvis.net/API/read/get?id=example1
{
  "success": true,
  "data": {
    "id": "example1",
    "uses": "",
    "hits": "3",
    "ads": "1",
    "url": "https://ulvis.net/example1",
    "full": "https://example.com",
    "created": 1728048992,
    "expire": "",
    "last": 1728050640
  }
}
 */
object Ulvis : ShortURLProvider {
    override val enabled = false // deletes short URLs???? https://ulvis.net/bHPs
    override val name = "ulvis.net"
    override val baseURL = "https://ulvis.net"
    override val apiURL = "$baseURL/API/write/get"

    override val privacyURL = "$baseURL/privacy.html"
    override val termsURL = "$baseURL/disclaimer.html"
    override val aliasConfig =
        object : AliasConfig {
            override val minAliasLength = 0
            override val maxAliasLength = 60
            override val allowedAliasCharacters = "a-z, A-Z, 0-9"

            override fun isAliasValid(alias: String) = alias.matches(Regex("[a-zA-Z0-9]+"))
        }

    override fun sanitizeLongURL(url: String) = url.withHttps().urlEncodeAmpersand().trim()

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

    override fun getURLClickCount(
        context: Context,
        url: URL,
        callback: (clicks: Int?) -> Unit,
    ) {
        val tag = "GetURLVisitCount_$name"
        val requestURL = "$baseURL/API/read/get?id=${url.alias}"
        Log.d(tag, "start request: $requestURL")
        RequestQueueSingleton.getInstance(context).addToRequestQueue(
            JsonObjectRequest(
                Request.Method.POST,
                requestURL,
                null,
                { response ->
                    try {
                        Log.d(tag, "response: $response")
                        val clicks = response.getJSONObject("data").getInt("hits")
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
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ): JsonObjectRequest {
        val tag = "CreateRequest_$name"
        val url = "$apiURL?custom=$alias&url=$longURL"
        Log.d(tag, "start request: $url")
        return JsonObjectRequest(
            Request.Method.POST,
            url,
            null,
            { response -> handleResponse(tag, response, successCallback, errorCallback) },
            { error -> handleError(tag, error, errorCallback) },
        )
    }

    private fun handleResponse(
        tag: String,
        response: JSONObject,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ) {
        try {
            Log.d(tag, "response: $response")
            val error = response.optJSONObject("error")
            val data = response.optJSONObject("data")
            val shortURL = data?.optString("url")?.trim()
            Log.d(tag, "shortURL: $shortURL")
            val status = data?.optString("status")
            when {
                status == "custom-taken" -> {
                    errorCallback(GenerateURLError.AliasAlreadyExists)
                }

                !shortURL.isNullOrBlank() -> {
                    successCallback(shortURL)
                }

                error != null && error.has("code") -> {
                    val code = error.getInt("code")
                    when {
                        code == 0 -> errorCallback(GenerateURLError.DomainNotAllowed)
                        code == 1 -> errorCallback(GenerateURLError.InvalidURL)
                        code == 2 -> errorCallback(GenerateURLError.InvalidAlias)
                        error.has("msg") -> errorCallback(GenerateURLError.Custom(code, error.optString("msg")))
                        else -> errorCallback(GenerateURLError.Unknown(200))
                    }
                }

                else -> {
                    errorCallback(GenerateURLError.Unknown(200))
                }
            }
        } catch (e: JSONException) {
            Log.e(tag, "error parsing create response", e)
            errorCallback(GenerateURLError.Unknown(200))
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun handleError(
        tag: String,
        error: VolleyError,
        errorCallback: (error: GenerateURLError) -> Unit,
    ) {
        // Broad catch is intentional: this runs in a Volley callback on the main thread; an
        // escaping exception here would crash the whole app.
        try {
            Log.e(tag, "error: $error")
            val networkResponse = error.networkResponse
            val statusCode = networkResponse?.statusCode
            val data = networkResponse?.data?.toString(Charsets.UTF_8)
            Log.e(tag, "$statusCode: message: ${error.message} data: $data")
            when {
                error is NoConnectionError -> errorCallback(GenerateURLError.ServiceOffline)
                statusCode == null -> errorCallback(GenerateURLError.Unknown())
                statusCode == 403 -> errorCallback(GenerateURLError.ServiceTemporarilyUnavailable(baseURL))
                data.isNullOrBlank() -> errorCallback(GenerateURLError.Unknown(statusCode))
                else -> errorCallback(GenerateURLError.Custom(statusCode, data))
            }
        } catch (e: Exception) {
            Log.e(tag, "error parsing error response", e)
            errorCallback(GenerateURLError.Unknown())
        }
    }
}
