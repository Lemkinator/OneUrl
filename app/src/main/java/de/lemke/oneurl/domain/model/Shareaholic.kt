package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.NoConnectionError
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import de.lemke.commonutils.urlEncodeAmpersand
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import org.json.JSONObject

/*
example:
https://www.shareaholic.com/v2/share/shorten_link?url=example.com
https://www.shareaholic.com/v2/share/shorten_link?apikey=8943b7fd64cd8b1770ff5affa9a9437b&url=example.com/&service[name]=bitly //requires apikey, but can use key from docs???

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
            { response ->
                Log.d(tag, "response: $response")
                if (response.has("data")) {
                    val shortURL = response.getString("data").trim()
                    Log.d(tag, "shortURL: $shortURL")
                    successCallback(shortURL)
                    return@JsonObjectRequest
                }
                errorCallback(GenerateURLError.Unknown(context))
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val message = error.message
                    val networkResponse = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    val data = networkResponse?.data?.toString(Charsets.UTF_8)
                    Log.e(tag, "$statusCode: message: $message data: $data")
                    val response = data?.let { JSONObject(it) }
                    when {
                        error is NoConnectionError -> errorCallback(GenerateURLError.ServiceOffline(context))
                        statusCode == null -> errorCallback(GenerateURLError.Unknown(context))
                        data.isNullOrBlank() -> errorCallback(GenerateURLError.Unknown(context, statusCode))
                        response?.has("errors") == true -> {
                            val firstError = response.optJSONArray("errors")?.optJSONObject(0)
                            Log.e(tag, "first error: $firstError")
                            when (firstError?.optString("code")) {
                                "100" -> errorCallback(GenerateURLError.Unknown(context, 1100)) //100	apikey not provided
                                "101" -> errorCallback(GenerateURLError.Unknown(context, 1101)) //101	apikey provided is invalid
                                "140" -> errorCallback(GenerateURLError.Unknown(context, 1140)) //140	Missing URL
                                "141" -> errorCallback(GenerateURLError.InvalidURL(context)) //141	Invalid URL
                                "145" -> errorCallback(GenerateURLError.InvalidURL(context)) //145	URL shortening problem or unsafe URL
                                "429" -> errorCallback(GenerateURLError.RateLimitExceeded(context)) //429	rate_limit_exceeded
                                else -> if (firstError?.has("detail") == true)
                                    errorCallback(GenerateURLError.Custom(context, statusCode, firstError.getString("detail")))
                                else
                                    errorCallback(GenerateURLError.Unknown(context, statusCode))
                            }
                        }

                        else -> errorCallback(GenerateURLError.Unknown(context, statusCode))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        )
    }
}