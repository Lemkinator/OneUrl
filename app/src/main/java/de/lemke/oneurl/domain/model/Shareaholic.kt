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
import de.lemke.commonutils.urlEncodeAmpersand
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import org.json.JSONObject

/*
example:
https://www.shareaholic.com/v2/share/shorten_link?url=example.com
https://www.shareaholic.com/v2/share/shorten_link?apikey=8943b7fd64cd8b1770ff5affa9a9437b&url=example.com/&service[name]=bitly
//requires apikey, but can use key from docs???

response:
{
    "status_code": "200",
    "data": "https://go.shr.lc/2sZ8JZo"
}
error:
400 {
    "errors":[
        {
            "code":"140",
            "source":{
                "pointer":"/data/attributes/url"
            },
            "detail":"Missing URL. See https://www.shareaholic.com/api/shortener/ for usage examples."
        }
    ]
}
 */
object Shareaholic : ShortURLProvider {
    override val name = "go.shr.lc"
    override val baseURL = "https://www.shareaholic.com"
    override val apiURL = "$baseURL/v2/share/shorten_link"
    override val privacyURL = "$baseURL/privacy"
    override val termsURL = "$baseURL/terms"

    override fun sanitizeLongURL(url: String) = url.urlEncodeAmpersand().trim()

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ): JsonObjectRequest {
        val tag = "CreateRequest_$name"
        val url = "$apiURL?url=$longURL"
        Log.d(tag, "start request: $url")
        return JsonObjectRequest(
            Request.Method.GET,
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
        Log.d(tag, "response: $response")
        if (response.has("data")) {
            val shortURL = response.getString("data").trim()
            Log.d(tag, "shortURL: $shortURL")
            successCallback(shortURL)
            return
        }
        errorCallback(GenerateURLError.Unknown())
    }

    @Suppress("TooGenericExceptionCaught")
    private fun handleError(
        tag: String,
        error: VolleyError,
        errorCallback: (error: GenerateURLError) -> Unit,
    ) {
        try {
            Log.e(tag, "error: $error")
            val message = error.message
            val networkResponse = error.networkResponse
            val statusCode = networkResponse?.statusCode
            val data = networkResponse?.data?.toString(Charsets.UTF_8)
            Log.e(tag, "$statusCode: message: $message data: $data")
            val response = data?.let { JSONObject(it) }
            when {
                error is NoConnectionError -> {
                    errorCallback(GenerateURLError.ServiceOffline)
                }

                statusCode == null -> {
                    errorCallback(GenerateURLError.Unknown())
                }

                data.isNullOrBlank() -> {
                    errorCallback(GenerateURLError.Unknown(statusCode))
                }

                response?.has("errors") == true -> {
                    handleApiErrors(tag, response, statusCode, errorCallback)
                }

                else -> {
                    errorCallback(GenerateURLError.Unknown(statusCode))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "error parsing error response", e)
            errorCallback(GenerateURLError.Unknown())
        }
    }

    private fun handleApiErrors(
        tag: String,
        response: JSONObject,
        statusCode: Int,
        errorCallback: (error: GenerateURLError) -> Unit,
    ) {
        val firstError = response.optJSONArray("errors")?.optJSONObject(0)
        Log.e(tag, "first error: $firstError")
        when (firstError?.optString("code")) {
            "100" -> {
                errorCallback(GenerateURLError.Unknown(1100))
            }

            // 100	apikey not provided
            "101" -> {
                errorCallback(GenerateURLError.Unknown(1101))
            }

            // 101	apikey provided is invalid
            "140" -> {
                errorCallback(GenerateURLError.Unknown(1140))
            }

            // 140	Missing URL
            "141" -> {
                errorCallback(GenerateURLError.InvalidURL)
            }

            // 141	Invalid URL
            "145" -> {
                errorCallback(GenerateURLError.InvalidURL)
            }

            // 145	URL shortening problem or unsafe URL
            "429" -> {
                errorCallback(GenerateURLError.RateLimitExceeded)
            }

            // 429	rate_limit_exceeded
            else -> {
                if (firstError?.has("detail") == true) {
                    errorCallback(GenerateURLError.Custom(statusCode, firstError.getString("detail")))
                } else {
                    errorCallback(GenerateURLError.Unknown(statusCode))
                }
            }
        }
    }
}
