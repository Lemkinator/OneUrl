package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import de.lemke.oneurl.domain.urlEncodeAmpersand
import org.json.JSONObject

/*
https://tny.im/
https://tny.im/aboutapi.php
example:
https://tny.im/yourls-api.php?action=shorturl&url=example.com?a=1%26b=2&format=json

shows a hint before redirecting
request takes some time :/ (usually 4-5 seconds, timeout needs to be increased)

response:
{
    "url": {
        "keyword": "k1Ka",
        "url": "http:\/\/example.com?a=1&b=2",
        "title": "Example Domain",
        "date": "2024-09-23 08:15:47",
        "ip": "tny.im does not make public the IP address of the creator of a short url",
        "hitlimit": 0,
        "timelimit": 0,
        "price": 0,
        "payoutaddr": "",
        "redirection_type": "301",
        "passcode": "fNcU0Y"
    },
    "status": "success",
    "message": "http:\/\/example.com?a=1&b=2 added to database",
    "title": "Example Domain",
    "shorturl": "http:\/\/tny.im\/k1Ka",
    "statusCode": 200
}

already shortened:
{
    "status": "fail",
    "code": "error:url",
    "url": {
        "keyword": "k1Ka",
        "url": "http:\/\/example.com?a=1&b=2",
        "title": "Example Domain",
        "date": "2024-09-23 08:15:47",
        "ip": "tny.im does not make public the IP address of the creator of a short url",
        "clicks": "0",
        "hitlimit": "0",
        "timelimit": "0",
        "price": "0",
        "payoutaddr": "",
        "redirection_type": "301"
    },
    "message": "http:\/\/example.com?a=1&b=2 already exists in database",
    "title": "Example Domain",
    "shorturl": "http:\/\/tny.im\/k1Ka",
    "statusCode": 200
}

keyword: gives same response, have to check if keyword matches

keyword=mö gives error 500

fail:
{
    "status": "fail",
    "code": "error:nourl",
    "message": "Missing or malformed URL",
    "errorCode": "400"
}

stats:
https://tny.im/yourls-api.php?action=url-stats&format=json&shorturl=a54321
{
  "statusCode": 200,
  "message": "success",
  "link": {
    "shorturl": "http://tny.im/a54321",
    "url": "http://example.com?a=1&b=2&c=3a12345",
    "title": "Example Domain",
    "timestamp": "2024-10-01 12:40:34",
    "ip": "tny.im does not make public the IP address of the creator of a short url",
    "clicks": "1"
  }
}
{
  "statusCode": 404,
  "message": "Error: short URL not found"
}
*/
val tinyim = Tnyim()

class Tnyim : ShortURLProvider {
    override val enabled = false
    override val name = "tny.im"
    override val baseURL = "https://tny.im"
    override val apiURL = "$baseURL/yourls-api.php"
    override val termsURL = "$baseURL/rules.php"
    override val aliasConfig = object : AliasConfig {
        override val minAliasLength = 5
        override val maxAliasLength = 100 // no info, tested up to 100
        override val allowedAliasCharacters = "a-z, A-Z, 0-9, -"
        override fun isAliasValid(alias: String) = alias.matches(Regex("[a-zA-Z0-9-]+"))
    }

    override fun getInfoContents(context: Context): List<ProviderInfo> = listOf(
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_tool_outline,
            context.getString(R.string.alias),
            context.getString(
                R.string.alias_text,
                aliasConfig.minAliasLength,
                aliasConfig.maxAliasLength,
                aliasConfig.allowedAliasCharacters
            )
        ),
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_report,
            context.getString(R.string.analytics),
            context.getString(R.string.analytics_text)
        )
    )

    override fun sanitizeLongURL(url: String) = url.urlEncodeAmpersand().trim()

    override fun getURLClickCount(context: Context, url: URL, callback: (clicks: Int?) -> Unit) {
        val tag = "GetURLVisitCount_$name"
        val requestURL = "$apiURL?action=url-stats&format=json&shorturl=${url.alias}"
        Log.d(tag, "start request: $url")
        RequestQueueSingleton.getInstance(context).addToRequestQueue(
            JsonObjectRequest(
                Request.Method.POST,
                requestURL,
                null,
                { response ->
                    try {
                        Log.d(tag, "response: $response")
                        val visitCount = response.optJSONObject("link")?.optString("clicks")?.toIntOrNull()
                        Log.d(tag, "visitCount: $visitCount")
                        callback(visitCount)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback(null)
                    }
                },
                { error ->
                    Log.e(tag, "error: $error")
                    callback(null)
                }
            )
        )
    }

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): StringRequest {
        val tag = "CreateRequest_$name"
        val url = "$apiURL?action=shorturl&format=json&url=$longURL&keyword=$alias"
        Log.d(tag, "start request: $url")
        return object : StringRequest(
            Method.POST,
            url,
            { response ->
                try {
                    Log.d(tag, "response: $response")
                    val json = JSONObject(response)
                    if (json.has("shorturl")) {
                        val shortURL = json.getString("shorturl").trim()
                        Log.d(tag, "shortURL: $shortURL")
                        if (alias.isBlank() || shortURL == "$baseURL/alias") successCallback(shortURL)
                        else errorCallback(GenerateURLError.URLExistsWithDifferentAlias(context))
                    } else {
                        Log.d(tag, "error: response does not contain short url or errors")
                        errorCallback(GenerateURLError.Unknown(context, 200))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val networkResponse = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    val data = networkResponse?.data?.toString(Charsets.UTF_8)
                    Log.e(tag, "$statusCode: message: ${error.message} data: $data")
                    when {
                        statusCode == null -> errorCallback(GenerateURLError.Unknown(context))
                        data.isNullOrBlank() -> errorCallback(GenerateURLError.Unknown(context, statusCode))
                        statusCode == 500 -> errorCallback(GenerateURLError.Unknown(context, statusCode))
                        else -> errorCallback(GenerateURLError.Custom(context, statusCode, data))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        ) {
            override fun getRetryPolicy() = DefaultRetryPolicy(
                10000, // set timeout to 10 seconds
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
        }
    }
}