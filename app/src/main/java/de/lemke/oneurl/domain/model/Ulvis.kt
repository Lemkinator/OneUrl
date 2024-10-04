package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import de.lemke.oneurl.domain.urlEncodeAmpersand
import de.lemke.oneurl.domain.withHttps

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
val ulvis = Ulvis()

class Ulvis : ShortURLProvider {
    override val name = "ulvis.net"
    override val baseURL = "https://ulvis.net"
    override val apiURL = "$baseURL/API/write/get"

    override val privacyURL = "$baseURL/privacy.html"
    override val termsURL = "$baseURL/disclaimer.html"
    override val aliasConfig = object : AliasConfig {
        override val minAliasLength = 0
        override val maxAliasLength = 60
        override val allowedAliasCharacters = "a-z, A-Z, 0-9"
        override fun isAliasValid(alias: String) = alias.matches(Regex("[a-zA-Z0-9]+"))
    }

    override fun sanitizeLongURL(url: String) = url.withHttps().urlEncodeAmpersand().trim()

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

    override fun getURLClickCount(context: Context, url: URL, callback: (clicks: Int?) -> Unit) {
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
    ): JsonObjectRequest {
        val tag = "CreateRequest_$name"
        val url = "$apiURL?custom=$alias&url=$longURL"
        Log.d(tag, "start request: $url")
        return JsonObjectRequest(
            Request.Method.POST,
            url,
            null,
            { response ->
                try {
                    Log.d(tag, "response: $response")
                    val error = response.optJSONObject("error")
                    val data = response.optJSONObject("data")
                    val shortURL = data?.optString("url")?.trim()
                    Log.d(tag, "shortURL: $shortURL")
                    val status = data?.optString("status")
                    when {
                        status == "custom-taken" -> errorCallback(GenerateURLError.AliasAlreadyExists(context))
                        !shortURL.isNullOrBlank() -> successCallback(shortURL)
                        error != null && error.has("code") -> {
                            val code = error.getInt("code")
                            when {
                                code == 0 -> errorCallback(GenerateURLError.DomainNotAllowed(context))
                                code == 1 -> errorCallback(GenerateURLError.InvalidURL(context))
                                code == 2 -> errorCallback(GenerateURLError.InvalidAlias(context))
                                error.has("msg") -> errorCallback(GenerateURLError.Custom(context, code, error.optString("msg")))
                                else -> errorCallback(GenerateURLError.Unknown(context, 200))
                            }
                        }

                        else -> errorCallback(GenerateURLError.Unknown(context, 200))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context, 200))
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
                        statusCode == 403 -> errorCallback(GenerateURLError.ServiceTemporarilyUnavailable(context, this))
                        data.isNullOrBlank() -> errorCallback(GenerateURLError.Unknown(context, statusCode))
                        else -> errorCallback(GenerateURLError.Custom(context, statusCode, data))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        )
    }
}