package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.NoConnectionError
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import de.lemke.oneurl.domain.urlEncodeAmpersand
import de.lemke.oneurl.domain.withHttps
import org.json.JSONObject

/*
https://spoo.me/api
example:
https://spoo.me/ {url: "https://example.com"}

Spoo.me API has a rate limit of 5 requests per minute, 50 requests per hour and 500 requests per day for the POST /, POST /emoji endpoints.
If you exceed this limit, you will receive a 429 status code in response to your requests.

response:
{
    "short_url": "https://spoo.me/NSPBXZ"
}

Errors:
UrlError	    400	The request does not contain the long URL or contains an invalid url. The url must contain a valid protocol like https, http and must follow rfc-1034 & rfc-2727
AliasError	    400	The User requested Alias is invalid or already taken. The alias must be alphanumeric & must be under 15 chars, anything beyond 15 chars would be stripped by the API
PasswordError	400	The user entered password must be atleast 8 characters long, must contain atleast a letter and a number and a special character either '@' or '.' and cannot be consecutive.
MaxClicksError	400	The user entered max-clicks is not a positive integer.

{
    "AliasError": "Invalid Alias",
    "alias": "ö"
}
{
    "UrlError": "Invalid URL, URL must have a valid protocol and must follow rfc_1034 & rfc_2728 patterns"
}

emoji:
Errors:
UrlError	    400	The request does not contain the long URL or contains an invalid url. The url must contain a valid protocol like https, http and must follow rfc-1034 & rfc-2727
EmojiError	    400	The User requested Emoji sequence is invalid or already taken. The emoji sequence must contain only emojies, no other character is allowed.
PasswordError	400	The user entered password must be atleast 8 characters long, must contain atleast a letter and a number and a special character either '@' or '.' and cannot be consecutive.
MaxClicksError	400	The user entered max-clicks is not a positive integer.
{
  "EmojiError": "Invalid emoji"
}
{
  "EmojiError": "Emoji already exists"
}


 */
sealed class Spoome : ShortURLProvider {
    final override val group = "spoo.me, spoo.me (emoji)"
    final override val baseURL = "https://spoo.me"

    override fun getAnalyticsURL(alias: String) = "$baseURL/stats/$alias"

    override fun sanitizeLongURL(url: String) = url.urlEncodeAmpersand().withHttps().trim()

    override fun getURLClickCount(context: Context, url: URL, callback: (clicks: Int?) -> Unit) {
        val tag = "GetURLVisitCount_$name"
        val requestURL = getAnalyticsURL(url.alias)
        Log.d(tag, "start request: $url")
        RequestQueueSingleton.getInstance(context).addToRequestQueue(
            JsonObjectRequest(
                Request.Method.POST,
                requestURL,
                null,
                { response ->
                    try {
                        Log.d(tag, "response: $response")
                        val clicks = response.getInt("total-clicks")
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
        val tag = "SpoomeCreateRequest_$name"
        val url = if (this is Default) "$apiURL?alias=$alias&url=$longURL"
        else "$apiURL?emojies=$alias&url=$longURL"
        Log.d(tag, "start request: $url")
        return object : JsonObjectRequest(
            Method.POST,
            url,
            null,
            { response ->
                Log.d(tag, "response: $response")
                if (response.has("short_url")) {
                    val shortURL = response.getString("short_url").trim()
                    Log.d(tag, "shortURL: $shortURL")
                    successCallback(shortURL)
                } else {
                    Log.e(tag, "error: response does not contain short_url")
                    errorCallback(GenerateURLError.Unknown(context, 200))
                }
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val networkResponse = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    val data = networkResponse?.data?.toString(Charsets.UTF_8)
                    val response = data?.let { JSONObject(it) }
                    Log.e(tag, "$statusCode: message: ${error.message} data: $data")
                    when {
                        error is NoConnectionError -> errorCallback(GenerateURLError.ServiceOffline(context))
                        statusCode == null -> errorCallback(GenerateURLError.Unknown(context))
                        data.isNullOrBlank() -> errorCallback(GenerateURLError.Unknown(context, statusCode))
                        response?.has("UrlError") == true -> errorCallback(GenerateURLError.InvalidURL(context))
                        response?.has("AliasError") == true -> {
                            val aliasError = response.getString("AliasError")
                            when {
                                aliasError.contains("already exists", true) -> errorCallback(GenerateURLError.AliasAlreadyExists(context))
                                aliasError.contains("invalid", true) -> errorCallback(GenerateURLError.InvalidAlias(context))
                                else -> errorCallback(GenerateURLError.Custom(context, statusCode, aliasError))
                            }
                        }

                        response?.has("EmojiError") == true -> {
                            val emojiError = response.getString("EmojiError")
                            when {
                                emojiError.contains("already exists", true) -> errorCallback(GenerateURLError.AliasAlreadyExists(context))
                                emojiError.contains("invalid", true) -> errorCallback(GenerateURLError.InvalidAlias(context))
                                else -> errorCallback(GenerateURLError.Custom(context, statusCode, emojiError))
                            }
                        }

                        statusCode == 429 -> errorCallback(GenerateURLError.RateLimitExceeded(context))
                        else -> errorCallback(GenerateURLError.Custom(context, statusCode, data))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        ) {
            override fun getHeaders() = mapOf("Accept" to "application/json")
        }
    }

    object Default : Spoome() {
        override val name = "spoo.me"
        override val apiURL = "https://spoo.me/"
        override val aliasConfig = object : AliasConfig {
            override val minAliasLength = 0
            override val maxAliasLength = 15
            override val allowedAliasCharacters = "a-z, A-Z, 0-9, _"
            override fun isAliasValid(alias: String) = alias.matches(Regex("[a-zA-Z0-9_]+"))
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
    }

    object Emoji : Spoome() {
        override val name = "spoo.me (emoji)"
        override val apiURL = "https://spoo.me/emoji"
        override val aliasConfig = object : AliasConfig {
            override val minAliasLength = 0
            override val maxAliasLength = 30 //returns invalid alias if more than 30
            override val allowedAliasCharacters = "Emojis"

            //one or more characters that belong to the "Symbol, Other" Unicode category, which includes emoji characters
            override fun isAliasValid(alias: String) = alias.matches(Regex("\\p{So}+"))
        }

        override fun getTipsCardTitleAndInfo(context: Context) = Pair(
            context.getString(de.lemke.commonutils.R.string.info),
            context.getString(R.string.emoji_text)
        )

        override fun getInfoContents(context: Context): List<ProviderInfo> = listOf(
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_emoji,
                context.getString(R.string.emoji),
                context.getString(R.string.emoji_text)
            ),
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
    }
}