package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.NoConnectionError
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.urlEncodeAmpersand

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
val l4f = L4f()

class L4f : ShortURLProvider {
    override val name = "l4f.com"
    override val baseURL = "https://l4f.com"
    override val apiURL = "$baseURL/shorten"
    override val aliasConfig = object : AliasConfig {
        override val minAliasLength = 3
        override val maxAliasLength = 100
        override val allowedAliasCharacters = "a-z, A-Z, 0-9"
        override fun isAliasValid(alias: String) = alias.matches(Regex("[a-zA-Z0-9]+"))
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
        )
    )

    override fun sanitizeLongURL(url: String) = url.urlEncodeAmpersand().trim()

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): JsonObjectRequest {
        val tag = "CreateRequest_check_$name"
        val url = "$apiURL?url=$longURL&custom=$alias"
        Log.d(tag, "start request: $url")
        return JsonObjectRequest(
            Request.Method.POST,
            url,
            null,
            { response ->
                try {
                    Log.d(tag, "response: $response")
                    val error = response.optBoolean("error")
                    val message = response.optString("message")
                    val shortURL = response.optJSONObject("data")?.optString("shorturl")
                    Log.d(tag, "error: $error message: $message shortURL: $shortURL")
                    if (!error && shortURL != null) {
                        if (alias.isBlank() || shortURL == "$baseURL/$alias") successCallback(shortURL)
                        else errorCallback(GenerateURLError.URLExistsWithDifferentAlias(context))
                    } else when {
                        message.contains("alias is taken", true) -> errorCallback(GenerateURLError.AliasAlreadyExists(context))
                        message.contains("Inappropriate alias", true) -> errorCallback(GenerateURLError.InvalidAlias(context))
                        message.contains("Please enter a valid URL", true) -> errorCallback(GenerateURLError.InvalidURL(context))
                        message.contains("Too Many Requests", true) -> errorCallback(GenerateURLError.RateLimitExceeded(context))
                        else -> errorCallback(GenerateURLError.Custom(context, 200, message))
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
                        error is NoConnectionError -> errorCallback(GenerateURLError.ServiceOffline(context))
                        statusCode == null -> errorCallback(GenerateURLError.Unknown(context))
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