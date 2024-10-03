package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import de.lemke.oneurl.domain.urlEncodeAmpersand
import de.lemke.oneurl.domain.withHttps

/*
https://da.gd/help
example: https://da.gd/shorten?url=http://some_long_url&shorturl=slug

errors:
400: Long URL cannot be empty                           //should not happen, checked before
400: Long URL must have http:// or https:// scheme.     //should not happen, checked before
400: Long URL is not a valid URL.
400: Short URL already taken. Pick a different one.
400: Custom short URL contained invalid characters.     //should not happen, checked before
 */
val dagd = Dagd()

class Dagd : ShortURLProvider {
    override val name = "da.gd"
    override val baseURL = "https://da.gd"
    override val apiURL = "$baseURL/shorten"
    override val aliasConfig = object : AliasConfig {
        override val minAliasLength = 0
        override val maxAliasLength = 10
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
            context.getString(R.string.analytics_dagd)
        )
    )

    override fun getAnalyticsURL(alias: String) = "$baseURL/stats/$alias"

    override fun sanitizeLongURL(url: String) = url.withHttps().urlEncodeAmpersand().trim()

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): StringRequest {
        val tag = "CreateRequest_check_$name"
        if (alias.isBlank()) return requestCreateDAGD(context, longURL, "", successCallback, errorCallback)
        val checkUrlApi = "$baseURL/coshorten/$alias"
        Log.d(tag, "start request: $checkUrlApi")
        return StringRequest(
            Request.Method.GET,
            checkUrlApi,
            { response ->
                if (sanitizeLongURL(response) != longURL) {
                    Log.e(
                        tag,
                        "error, shortURL already exists, but has different longURL, longURL: $longURL, response: ${sanitizeLongURL(response)} ($response)"
                    )
                    errorCallback(GenerateURLError.AliasAlreadyExists(context))
                    return@StringRequest
                }
                Log.d(tag, "shortURL already exists (but is not in local db): $response")
                val shortURL = "$baseURL/$alias"
                Log.d(tag, "shortURL: $shortURL")
                successCallback(shortURL)
            },
            { error ->
                try {
                    Log.w(tag, "error: $error")
                    val message = error.message
                    val networkResponse = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    val data = networkResponse?.data?.toString(Charsets.UTF_8)
                    Log.e(tag, "$statusCode: message: $message data: $data")
                    when {
                        statusCode == null -> errorCallback(GenerateURLError.Unknown(context))
                        else -> {
                            if (statusCode == 404) Log.d(tag, "shortURL does not exist yet, creating it")
                            else Log.w(tag, "error, trying to create it anyway")
                            RequestQueueSingleton.getInstance(context).addToRequestQueue(
                                requestCreateDAGD(context, longURL, alias, successCallback, errorCallback)
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        )
    }

    private fun requestCreateDAGD(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): StringRequest {
        val tag = "CreateRequest_create_$name"
        val url = apiURL + "?url=" + longURL + (if (alias.isBlank()) "" else "&shorturl=$alias")
        Log.d(tag, "start request: $url")
        return StringRequest(
            Request.Method.GET,
            url,
            { response ->
                Log.d(tag, "response: $response")
                if (response.startsWith("https://da.gd")) {
                    val shortURL = response.trim()
                    Log.d(tag, "shortURL: $shortURL")
                    successCallback(shortURL)
                } else {
                    Log.e(tag, "error, response does not start with https://da.gd, response: $response")
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
                        data.isNullOrBlank() -> errorCallback(GenerateURLError.Unknown(context, statusCode))
                        data.contains("Long URL cannot be empty", true) -> errorCallback(GenerateURLError.InvalidURL(context))
                        data.contains("Long URL must have http:// or https://", true) -> errorCallback(GenerateURLError.InvalidURL(context))
                        data.contains("Long URL is not a valid URL", true) -> errorCallback(GenerateURLError.InvalidURL(context))
                        data.contains("Short URL already taken", true) -> errorCallback(GenerateURLError.AliasAlreadyExists(context))
                        data.contains("Custom short URL contained invalid", true) -> errorCallback(GenerateURLError.InvalidAlias(context))
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