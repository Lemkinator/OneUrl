package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import de.lemke.oneurl.domain.withHttps

/*
https://da.gd/help
example: https://da.gd/shorten?url=http://some_long_url&shorturl=slug
 */

class Dagd : ShortURLProvider {
    override val enabled = true
    override val name = "da.gd"
    override val group = name
    override val baseURL = "https://da.gd/"
    override val apiURL = "${baseURL}shorten"
    override val infoURL = baseURL
    override val privacyURL = null
    override val termsURL = null
    override val aliasConfig = object : AliasConfig {
        override val minAliasLength = 0
        override val maxAliasLength = 10
        override val allowedAliasCharacters = "a-z, A-Z, 0-9, _"
        override fun isAliasValid(alias: String) = alias.matches(Regex("[a-zA-Z0-9_]+"))
    }

    //Info
    override val infoIcons: List<Int> = listOf(
        dev.oneuiproject.oneui.R.drawable.ic_oui_report,
        dev.oneuiproject.oneui.R.drawable.ic_oui_tool_outline
    )

    override fun getInfoContents(context: Context): List<ProviderInfo> = listOf(
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_report,
            context.getString(R.string.analytics),
            context.getString(R.string.analytics_dagd)
        ),
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_tool_outline,
            context.getString(R.string.alias),
            context.getString(R.string.alias_dagd)
        )
    )

    override fun getInfoButtons(context: Context): List<ProviderInfo> = listOf(
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_info_outline,
            context.getString(R.string.more_information),
            infoURL
        )
    )

    override fun getAnalyticsURL(alias: String) = "${baseURL}stats/$alias"

    override fun sanitizeLongURL(url: String) = url.withHttps().trim()

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String?,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): StringRequest {
        val tag = "DagdCreateRequest_check"
        if (alias == null) return requestCreateDAGD(context, longURL, null, successCallback, errorCallback)
        val checkUrlApi = "${baseURL}coshorten/$alias"
        Log.d(tag, "start request: $checkUrlApi")
        return StringRequest(
            Request.Method.GET,
            checkUrlApi,
            { response ->
                if (response.trim() != longURL) {
                    Log.e(tag, "error, shortURL already exists, but has different longURL, longURL: $longURL, response: $response")
                    errorCallback(GenerateURLError.AliasAlreadyExists(context))
                    return@StringRequest
                }
                Log.d(tag, "shortURL already exists (but is not in local db): $response")
                val shortURL = baseURL + alias
                Log.d(tag, "shortURL: $shortURL")
                successCallback(shortURL)
            },
            { error ->
                try {
                    Log.w(tag, "error: $error")
                    val networkResponse: NetworkResponse? = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    if (networkResponse == null || statusCode == null) {
                        Log.e(tag, "error.networkResponse == null")
                        errorCallback(GenerateURLError.Custom(context, error.message))
                        return@StringRequest
                    }
                    if (statusCode == 404) {
                        Log.d(tag, "shortURL does not exist yet, creating it")
                    } else {
                        Log.w(tag, "error, statusCode: ${error.networkResponse.statusCode}, trying to create it anyway")
                    }
                    RequestQueueSingleton.getInstance(context).addToRequestQueue(
                        requestCreateDAGD(context, longURL, alias, successCallback, errorCallback)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e(tag, "error: $e")
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        )
    }

    private fun requestCreateDAGD(
        context: Context,
        longURL: String,
        alias: String?,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): StringRequest {
        val tag = "DagdCreateRequest_create"
        val url = apiURL + "?url=" + longURL + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias")
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
                    errorCallback(GenerateURLError.Unknown(context))
                }
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val networkResponse: NetworkResponse? = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    Log.e(tag, "statusCode: $statusCode")
                    if (networkResponse == null || statusCode == null) {
                        Log.e(tag, "error.networkResponse == null")
                        errorCallback(GenerateURLError.Custom(context, error.message))
                        return@StringRequest
                    }
                    val data = networkResponse.data
                    if (data == null) {
                        Log.e(tag, "error.networkResponse.data == null")
                        errorCallback(
                            GenerateURLError.Custom(
                                context,
                                (error.message ?: context.getString(R.string.error_unknown)) + " ($statusCode)"
                            )
                        )
                        return@StringRequest
                    }
                    val message = data.toString(charset("UTF-8"))
                    Log.e(tag, "error: $message ($statusCode)")
                    /* possible error messages:
                    400: Long URL cannot be empty                           //should not happen, checked before
                    400: Long URL must have http:// or https:// scheme.     //should not happen, checked before
                    400: Long URL is not a valid URL.
                    400: Short URL already taken. Pick a different one.
                    400: Custom short URL contained invalid characters.     //should not happen, checked before
                     */
                    when {
                        message.contains("Long URL cannot be empty") -> errorCallback(GenerateURLError.InvalidURL(context))
                        message.contains("Long URL must have http:// or https:// scheme") ->
                            errorCallback(GenerateURLError.InvalidURL(context))

                        message.contains("Long URL is not a valid URL") -> errorCallback(GenerateURLError.InvalidURL(context))
                        message.contains("Short URL already taken") -> errorCallback(GenerateURLError.AliasAlreadyExists(context))
                        message.contains("Custom short URL contained invalid characters") ->
                            errorCallback(GenerateURLError.Custom(context, context.getString(R.string.error_invalid_alias)))

                        else -> errorCallback(GenerateURLError.Custom(context, "$message ($statusCode)"))
                    }
                } catch (e: Exception) {
                    Log.e(tag, "error: $e")
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        )
    }
}