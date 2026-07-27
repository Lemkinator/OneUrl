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
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import org.json.JSONObject

/*
https://l4f.com
https://l4f.com/developers //requires api key
but example:
https://l4f.com/shorten?url=example.com&custom=asdf
response:
{
  "error": false,
  "message": "Link has been shortened",
  "data": {
    "id": 7498,
    "shorturl": "https://l4f.com/asdfasdfasdf"
  }
}
error (still return 200):
{
  "error": true,
  "message": "That alias is taken. Please choose another one."
}
{
  "error": true,
  "message": "Inappropriate aliases are not allowed."
}
{
  "error": true,
  "message": "Please enter a valid URL."
}
{
  "error": 429,
  "message": "Too Many Requests. Please retry later.",
  "Retry-After": 41
}
 */
object L4f : ShortURLProvider {
    override val enabled = false // redirects to apioption.com ??
    override val name = "l4f.com"
    override val baseURL = "https://l4f.com"
    override val apiURL = "$baseURL/shorten"
    override val aliasConfig =
        object : AliasConfig {
            override val minAliasLength = 3
            override val maxAliasLength = 100
            override val allowedAliasCharacters = "a-z, A-Z, 0-9"

            override fun isAliasValid(alias: String) = alias.matches(Regex("[a-zA-Z0-9]+"))
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
        )

    override fun sanitizeLongURL(url: String) = url.urlEncodeAmpersand().trim()

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ): JsonObjectRequest {
        val tag = "CreateRequest_check_$name"
        val url = "$apiURL?url=$longURL&custom=$alias"
        Log.d(tag, "start request: $url")
        return JsonObjectRequest(
            Request.Method.POST,
            url,
            null,
            { response -> handleResponse(tag, alias, response, successCallback, errorCallback) },
            { error -> handleError(tag, error, errorCallback) },
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun handleResponse(
        tag: String,
        alias: String,
        response: JSONObject,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ) {
        // Broad catch is intentional: this runs in a Volley callback on the main thread; an
        // escaping exception here would crash the whole app.
        try {
            Log.d(tag, "response: $response")
            val error = response.optBoolean("error")
            val message = response.optString("message")
            val shortURL = response.optJSONObject("data")?.optString("shorturl")
            Log.d(tag, "error: $error message: $message shortURL: $shortURL")
            when {
                !error && shortURL != null && (alias.isBlank() || shortURL == "$baseURL/$alias") -> {
                    successCallback(shortURL)
                }

                !error && shortURL != null -> {
                    errorCallback(GenerateURLError.URLExistsWithDifferentAlias)
                }

                message.contains("alias is taken", true) -> {
                    errorCallback(GenerateURLError.AliasAlreadyExists)
                }

                message.contains("Inappropriate alias", true) -> {
                    errorCallback(GenerateURLError.InvalidAlias)
                }

                message.contains("Please enter a valid URL", true) -> {
                    errorCallback(GenerateURLError.InvalidURL)
                }

                message.contains("Too Many Requests", true) -> {
                    errorCallback(GenerateURLError.RateLimitExceeded)
                }

                else -> {
                    errorCallback(GenerateURLError.Custom(200, message))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "error parsing response", e)
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
                else -> errorCallback(GenerateURLError.Unknown(statusCode))
            }
        } catch (e: Exception) {
            Log.e(tag, "error parsing error response", e)
            errorCallback(GenerateURLError.Unknown())
        }
    }
}
