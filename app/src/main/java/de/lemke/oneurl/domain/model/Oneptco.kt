package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.urlEncodeAmpersand

/*
https://github.com/1pt-co/api
example: https://csclub.uwaterloo.ca/~phthakka/1pt-express/addurl?long=test.com&short=test

success: {
    "message": "Added!",
    "short": "ajodd",
    "long": "t"
}
alias already exists: {
    "message": "Added!",
    "short": "asdfg",
    "long": "asdfasdf",
    "receivedRequestedShort": false
}
fail: {
    "message": "Bad request"
}
*/
val oneptco = Oneptco()

class Oneptco : ShortURLProvider {
    override val enabled = true
    override val name = "1pt.co"
    override val group = name
    override val baseURL = "https://1pt.co/"
    override val apiURL = "https://csclub.uwaterloo.ca/~phthakka/1pt-express/addurl"
    override val infoURL = baseURL
    override val privacyURL = null
    override val termsURL = null
    override val aliasConfig = object : AliasConfig {
        override val minAliasLength = 0
        override val maxAliasLength = AliasConfig.NO_MAX_ALIAS_SPECIFIED
        override val allowedAliasCharacters = "a-z, A-Z, 0-9, _"
        override fun isAliasValid(alias: String) = alias.matches(Regex("[a-zA-Z0-9_]+"))
    }

    override fun getAnalyticsURL(alias: String) = null

    override fun sanitizeLongURL(url: String) = url.urlEncodeAmpersand().trim()

    override fun getInfoContents(context: Context): List<ProviderInfo> = listOf(
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_tool_outline,
            context.getString(R.string.alias),
            context.getString(R.string.alias_text, aliasConfig.minAliasLength, aliasConfig.maxAliasLength, aliasConfig.allowedAliasCharacters)
        )
    )

    override fun getInfoButtons(context: Context): List<ProviderInfo> = listOf(
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
        val tag = "CreateRequest_$name"
        val url = apiURL + "?long=$longURL" + (if (alias.isBlank()) "" else "&short=$alias")
        Log.d(tag, "start request: $url")
        return JsonObjectRequest(
            Request.Method.POST,
            url,
            null,
            { response ->
                Log.d(tag, "response: $response")
                when {
                    !response.has("message") -> {
                        Log.e(tag, "error: no message")
                        errorCallback(GenerateURLError.Unknown(context, 200))
                    }

                    response.getString("message") != "Added!" -> {
                        Log.e(tag, "error: ${response.getString("message")}")
                        errorCallback(GenerateURLError.Custom(context, 200, response.getString("message")))
                    }

                    !response.has("short") -> {
                        Log.e(tag, "error: no short")
                        errorCallback(GenerateURLError.Unknown(context, 200))
                    }

                    response.has("receivedRequestedShort") && !response.getBoolean("receivedRequestedShort") -> {
                        Log.e(tag, "error: alias already exists")
                        errorCallback(GenerateURLError.AliasAlreadyExists(context))
                    }

                    else -> {
                        val shortURL = baseURL + response.getString("short").trim()
                        Log.d(tag, "shortURL: $shortURL")
                        successCallback(shortURL)
                    }
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
                        statusCode == 500 && data.contains("Internal server error") -> errorCallback(GenerateURLError.InternalServerError(context))
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