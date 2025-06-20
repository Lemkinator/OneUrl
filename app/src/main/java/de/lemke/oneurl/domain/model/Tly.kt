package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.NoConnectionError
import com.android.volley.toolbox.JsonObjectRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
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
bit.ly: responds t.ly for valid links (example.com) and tinyurl.com else (example), some requests: T.LY account and API key required to create additional short links.
wtf? :D anyways...

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
sealed class Tly : ShortURLProvider {
    final override val group = "t.ly [experimental]"
    final override val baseURL = "https://t.ly"
    final override val apiURL = "$baseURL/api/v1/link/shorten"
    final override val privacyURL = "$baseURL/privacy"
    final override val termsURL = "$baseURL/terms"

    override fun getInfoContents(context: Context): List<ProviderInfo> = listOf(
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_labs,
            context.getString(commonutilsR.string.commonutils_experimental),
            context.getString(R.string.tly_info)
        ),
    )

    override fun getTipsCardTitleAndInfo(context: Context): Pair<String, String>? = Pair(
        context.getString(commonutilsR.string.commonutils_info),
        context.getString(R.string.tly_info)
    )

    fun getTlyCreateRequest(
        context: Context,
        provider: String,
        longURL: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ): JsonObjectRequest {
        val tag = "CreateRequest_$name"
        Log.d(tag, "start request: $apiURL {long_url=$longURL provider=$provider}")
        return object : JsonObjectRequest(
            Method.POST,
            apiURL,
            JSONObject(
                mapOf(
                    "long_url" to longURL,
                    "provider" to provider,
                )
            ),
            { response ->
                Log.d(tag, "response: $response")
                if (response.has("short_url")) {
                    val shortURL = response.getString("short_url").trim()
                    Log.d(tag, "shortURL: $shortURL")
                    successCallback(shortURL)
                } else {
                    Log.e(tag, "error: no shortURL")
                    errorCallback(GenerateURLError.Unknown(context, 200))
                }
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val networkResponse = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    val data = networkResponse?.data?.toString(Charsets.UTF_8)
                    val message = data?.let { JSONObject(it) }?.optString("message")
                    Log.e(tag, "$statusCode: message: ${error.message} data: $data")
                    Log.e(tag, "response message: $message")
                    when {
                        error is NoConnectionError -> errorCallback(GenerateURLError.ServiceOffline(context))
                        statusCode == null -> errorCallback(GenerateURLError.Unknown(context))
                        data.isNullOrBlank() || message.isNullOrBlank() -> errorCallback(GenerateURLError.Unknown(context, statusCode))
                        message.contains("long url field is required", true) -> errorCallback(GenerateURLError.InvalidURL(context))
                        message.contains("T.LY account and API key", true) -> errorCallback(GenerateURLError.RateLimitExceeded(context))
                        statusCode == 503 -> errorCallback(GenerateURLError.ServiceTemporarilyUnavailable(context, this))
                        else -> errorCallback(GenerateURLError.Custom(context, statusCode, message))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        ) {
            override fun getHeaders() = mapOf(
                "accept" to "application/json",
                "content-type" to "application/json;charset=UTF-8",
                "origin" to "chrome-extension://oodfdmglhbbkkcngodjjagblikmoegpa"
            )
        }
    }

    object Default : Tly() {
        override val name = "t.ly"

        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit,
        ): JsonObjectRequest = getTlyCreateRequest(context, "t.ly", longURL, successCallback, errorCallback)
    }

    object Ibitly : Tly() {
        override val name = "t.ly (ibit.ly)"
        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit,
        ): JsonObjectRequest = getTlyCreateRequest(context, "ibit.ly", longURL, successCallback, errorCallback)
    }

    object Twtrto : Tly() {
        override val enabled = false // useless, only responds with t.ly??
        override val name = "t.ly (twtr.to)"
        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit,
        ): JsonObjectRequest = getTlyCreateRequest(context, "twtr.to", longURL, successCallback, errorCallback)
    }

    object Jpegly : Tly() {
        override val name = "t.ly (jpeg.ly)"
        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit,
        ): JsonObjectRequest = getTlyCreateRequest(context, "jpeg.ly", longURL, successCallback, errorCallback)
    }

    object Rebrandly : Tly() {
        override val enabled = false // useless, only responds with t.ly??
        override val name = "t.ly (rebrand.ly)"
        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit,
        ): JsonObjectRequest = getTlyCreateRequest(context, "rebrand.ly", longURL, successCallback, errorCallback)
    }

    object Bitly : Tly() {
        override val enabled = false // requires api key after some requests?
        override val name = "t.ly (bit.ly)"
        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit,
        ): JsonObjectRequest = getTlyCreateRequest(context, "bit.ly", longURL, successCallback, errorCallback)
    }
}