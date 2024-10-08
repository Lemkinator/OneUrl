package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.urlEncodeAmpersand
import org.json.JSONObject

/*
example:
https://www.shareaholic.com/v2/share/shorten_link?url=example.com
https://www.shareaholic.com/v2/share/shorten_link?apikey=8943b7fd64cd8b1770ff5affa9a9437b&url=example.com/&service[name]=bitly //requires apikey, but can use key from docs???
 */

class Shareaholic : ShortURLProvider {
    override val enabled = true
    override val name = "go.shr.lc"
    override val group = name
    override val baseURL = "https://www.shareaholic.com/"
    override val apiURL = "${baseURL}v2/share/shorten_link"
    override val infoURL = baseURL
    override val privacyURL = "${baseURL}privacy/"
    override val termsURL = "${baseURL}terms/"
    override val aliasConfig = null

    override fun getAnalyticsURL(alias: String) = null

    override fun sanitizeLongURL(url: String) = url.urlEncodeAmpersand().trim()

    //Info
    override val infoIcons: List<Int> = listOf()

    override fun getInfoContents(context: Context): List<ProviderInfo> = listOf()

    override fun getInfoButtons(context: Context): List<ProviderInfo> = listOf(
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_privacy,
            context.getString(R.string.privacy_policy),
            privacyURL
        ),
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_memo_outline,
            context.getString(R.string.tos),
            termsURL
        ),
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_info_outline,
            context.getString(R.string.more_information),
            infoURL
        )
    )

    override fun getTipsCardTitleAndInfo(context: Context) = null

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): JsonObjectRequest {
        val tag = "ShareaholicCreateRequest"
        val url = "$apiURL?url=$longURL"
        Log.d(tag, "start request: $url")
        return JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                Log.d(tag, "response: $response")
                /*
                {
                    "status_code": "200",
                    "data": "https://go.shr.lc/2sZ8JZo"
                }

                {
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
                if (response.has("data")) {
                    val shortURL = response.getString("data").trim()
                    Log.d(tag, "shortURL: $shortURL")
                    successCallback(shortURL)
                    return@JsonObjectRequest
                }
                if (response.has("errors")) {
                    val firstError = response.optJSONArray("errors")?.optJSONObject(0)
                    Log.e(tag, "error: $firstError")
                    when (firstError?.optString("code")) {
                        "100" -> errorCallback(GenerateURLError.Unknown(context)) //100	apikey not provided
                        "101" -> errorCallback(GenerateURLError.Unknown(context)) //101	apikey provided is invalid
                        "140" -> errorCallback(GenerateURLError.Unknown(context)) //140	Missing URL
                        "141" -> errorCallback(GenerateURLError.InvalidURL(context)) //141	Invalid URL
                        "145" -> errorCallback(GenerateURLError.InvalidURL(context)) //145	URL shortening problem or unsafe URL
                        "429" -> errorCallback(GenerateURLError.RateLimitExceeded(context)) //429	rate_limit_exceeded
                        else -> errorCallback(GenerateURLError.Custom(context, firstError?.optString("detail")))
                    }
                    return@JsonObjectRequest
                }
                errorCallback(GenerateURLError.Unknown(context))
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val networkResponse: NetworkResponse? = error.networkResponse
                    Log.e(tag, "networkResponse: $networkResponse")
                    val statusCode = networkResponse?.statusCode
                    Log.e(tag, "statusCode: $statusCode")
                    if (networkResponse == null || statusCode == null) {
                        errorCallback(GenerateURLError.Unknown(context))
                        return@JsonObjectRequest
                    }
                    val response = JSONObject(String(networkResponse.data))
                    if (response.has("errors")) {
                        val firstError = response.optJSONArray("errors")?.optJSONObject(0)
                        Log.e(tag, "error: $firstError")
                        when (firstError?.optString("code")) {
                            "100" -> errorCallback(GenerateURLError.Unknown(context)) //100	apikey not provided
                            "101" -> errorCallback(GenerateURLError.Unknown(context)) //101	apikey provided is invalid
                            "140" -> errorCallback(GenerateURLError.Unknown(context)) //140	Missing URL
                            "141" -> errorCallback(GenerateURLError.InvalidURL(context)) //141	Invalid URL
                            "145" -> errorCallback(GenerateURLError.InvalidURL(context)) //145	URL shortening problem or unsafe URL
                            "429" -> errorCallback(GenerateURLError.RateLimitExceeded(context)) //429	rate_limit_exceeded
                            else -> errorCallback(GenerateURLError.Custom(context, firstError?.optString("detail")))
                        }
                        return@JsonObjectRequest
                    }
                    Log.e(tag, "data: ${String(networkResponse.data)}")
                    errorCallback(GenerateURLError.Custom(context, context.getString(R.string.error_unknown) + " ($statusCode)"))
                } catch (e: Exception) {
                    Log.e(tag, "error: $e")
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        )
    }
}