package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.toolbox.StringRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import de.lemke.oneurl.domain.withHttps

/*
https://gg.gg/
https://gg.gg/check {long_url=https://example.com custom_path=test}

rseponse: 200: ok
fail: 200: Link with this path already exist. Choose another path.

https://gg.gg/create {long_url=https://example.com custom_path=1cbz0v}
response: 200: http://gg.gg/1cbz0v
fail: 200: http://gg.gg/
 */
val gg = Gg()

class Gg : ShortURLProvider {
    override val name = "gg.gg"
    override val baseURL = "https://gg.gg"
    val apiURLCheck = "$baseURL/check"
    override val apiURL = "$baseURL/create"
    override val aliasConfig = object : AliasConfig {
        override val minAliasLength = 0
        override val maxAliasLength = 200 //no info, tested up to 500
        override val allowedAliasCharacters = "a-z, A-Z, 0-9, -, _"
        override fun isAliasValid(alias: String) = alias.matches(Regex("[a-zA-Z0-9_-]+"))
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

    override fun sanitizeLongURL(url: String) = url.withHttps().trim()

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): StringRequest {
        val tag = "CreateRequest_check_$name"
        Log.d(tag, "start request: $apiURLCheck {long_url=$longURL custom_path=$alias}")
        return object : StringRequest(
            Method.POST,
            apiURLCheck,
            { response ->
                try {
                    Log.d(tag, "response: $response")
                    if (response.contains("Link with this path already exist", true)) {
                        errorCallback(GenerateURLError.AliasAlreadyExists(context))
                    } else {
                        RequestQueueSingleton.getInstance(context).addToRequestQueue(
                            requestCreateGg(context, longURL, alias, successCallback, errorCallback)
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    RequestQueueSingleton.getInstance(context).addToRequestQueue(
                        requestCreateGg(context, longURL, alias, successCallback, errorCallback)
                    )
                }
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val networkResponse = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    val data = networkResponse?.data?.toString(Charsets.UTF_8)
                    Log.e(tag, "$statusCode: message: ${error.message} data: $data")
                    RequestQueueSingleton.getInstance(context).addToRequestQueue(
                        requestCreateGg(context, longURL, alias, successCallback, errorCallback)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    RequestQueueSingleton.getInstance(context).addToRequestQueue(
                        requestCreateGg(context, longURL, alias, successCallback, errorCallback)
                    )
                }
            }
        ) {
            override fun getParams() = mapOf("long_url" to longURL, "custom_path" to alias)
        }
    }

    private fun requestCreateGg(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): StringRequest {
        val tag = "CreateRequest_create_$name"
        Log.d(tag, "start request: $apiURL {long_url=$longURL custom_path=$alias}")
        return object : StringRequest(
            Method.POST,
            apiURL,
            { response ->
                try {
                    Log.d(tag, "response: $response")
                    when {
                        response == "$baseURL/" -> errorCallback(GenerateURLError.Unknown(context, 2001))
                        alias.isNotBlank() && !response.endsWith("/$alias") -> errorCallback(GenerateURLError.Unknown(context, 2002))
                        else -> successCallback(response)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context, 2009))
                }
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val networkResponse = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    val data = networkResponse?.data?.toString(Charsets.UTF_8)
                    Log.e(tag, "$statusCode: message: ${error.message} data: $data")
                    if (statusCode == null) errorCallback(GenerateURLError.Unknown(context))
                    else errorCallback(GenerateURLError.Unknown(context, statusCode))
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        ) {
            override fun getParams() = mapOf("long_url" to longURL, "custom_path" to alias)
        }
    }
}