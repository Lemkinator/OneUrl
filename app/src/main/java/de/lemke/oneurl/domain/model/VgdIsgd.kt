package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError

/*
docs:
https://v.gd/apishorteningreference.php
https://is.gd/apishorteningreference.php
example:
https://v.gd/create.php?format=json&url=www.example.com&shorturl=example
https://is.gd/create.php?format=json&url=www.example.com&shorturl=example
 */

sealed class VgdIsgd : ShortURLProvider {
    override val enabled = true
    final override val group = "v.gd, is.gd"
    final override val aliasConfig = object : AliasConfig {
        override val minAliasLength = 5
        override val maxAliasLength = 30
        override val allowedAliasCharacters = "a-z, A-Z, 0-9, _"
        override fun isAliasValid(alias: String) = alias.matches(Regex("[a-zA-Z0-9_]+"))
    }

    override fun getAnalyticsURL(alias: String) = "${baseURL}stats.php?url=$alias"

    override fun sanitizeLongURL(url: String) = url.trim()

    //Info
    override val infoIcons: List<Int> = listOf(
        dev.oneuiproject.oneui.R.drawable.ic_oui_confirm_before_next_action,
        dev.oneuiproject.oneui.R.drawable.ic_oui_report,
        dev.oneuiproject.oneui.R.drawable.ic_oui_tool_outline
    )

    override fun getInfoContents(context: Context): List<ProviderInfo> = listOf(
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_confirm_before_next_action,
            context.getString(R.string.redirect_hint),
            context.getString(R.string.redirect_hint_vgd)
        ),
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_report,
            context.getString(R.string.analytics),
            context.getString(R.string.analytics_vgd_isgd)
        ),
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_tool_outline,
            context.getString(R.string.alias),
            context.getString(R.string.alias_vgd_isgd_tinyurl)
        )
    )

    override fun getInfoButtons(context: Context): List<ProviderInfo> {
        val vgd = Vgd()
        val isgd = Isgd()
        return listOf(
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_privacy,
                context.getString(R.string.privacy_policy) + " (v.gd)",
                vgd.privacyURL
            ),
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_privacy,
                context.getString(R.string.privacy_policy) + " (is.gd)",
                isgd.privacyURL
            ),
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_memo_outline,
                context.getString(R.string.tos) + " (v.gd)",
                vgd.termsURL
            ),
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_memo_outline,
                context.getString(R.string.tos) + " (is.gd)",
                isgd.termsURL
            ),
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_info_outline,
                context.getString(R.string.more_information) + " (v.gd)",
                vgd.infoURL
            ),
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_info_outline,
                context.getString(R.string.more_information) + " (is.gd)",
                isgd.infoURL
            )
        )
    }

    fun getVgdIsgdCreateRequest(
        context: Context,
        longURL: String,
        alias: String?,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): JsonObjectRequest {
        val tag = "VgdIsgdCreateRequest_$name"
        val url = apiURL + "?format=json&url=" + longURL + (if (alias.isNullOrBlank()) "" else "&shorturl=$alias&logstats=1")
        Log.d(tag, "start request: $url")
        return JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                Log.d(tag, "response: $response")
                if (response.has("errorcode")) {
                    Log.e(tag, "errorcode: ${response.getString("errorcode")}")
                    Log.e(tag, "errormessage: ${response.optString("errormessage")}")
                    /*
                    Error code 1 - there was a problem with the original long URL provided
                    Please specify a URL to shorten.                                            //should not happen, checked before
                    Please enter a valid URL to shorten
                    Error code 2 - there was a problem with the short URL provided (for custom short URLs)
                    Short URLs must be at least 5 characters long                               //should not happen, checked before
                    Short URLs may only contain the characters a-z, 0-9 and underscore          //should not happen, checked before
                    The shortened URL you picked already exists, please choose another.
                    Error code 3 - our rate limit was exceeded (your app should wait before trying again)
                    Error code 4 - any other error (includes potential problems with our service such as a maintenance period)
                     */
                    when (response.getString("errorcode")) {
                        "1" -> errorCallback(GenerateURLError.InvalidURL(context))
                        "2" -> errorCallback(GenerateURLError.AliasAlreadyExists(context))
                        "3" -> errorCallback(GenerateURLError.RateLimitExceeded(context))
                        "4" -> errorCallback(GenerateURLError.ServiceTemporarilyUnavailable(context, this))
                        else -> errorCallback(
                            GenerateURLError.Custom(
                                context,
                                response.optString("errormessage") + " (${response.getString("errorcode")})"
                            )
                        )
                    }
                    return@JsonObjectRequest
                }
                if (!response.has("shorturl")) {
                    Log.e(tag, "error, response does not contain shorturl, response: $response")
                    errorCallback(GenerateURLError.Unknown(context))
                    return@JsonObjectRequest
                }
                val shortURL = response.getString("shorturl").trim()
                Log.d(tag, "shortURL: $shortURL")
                successCallback(shortURL)
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val networkResponse: NetworkResponse? = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    Log.e(tag, "statusCode: $statusCode")
                    if (error.message == "org.json.JSONException: Value Error of type java.lang.String cannot be converted to JSONObject") {
                        //https://v.gd/create.php?format=json&url=example.com?test&shorturl=test21 -> Error, database insert failed
                        Log.e(tag, "error.message == ${error.message} (probably error: database insert failed)")
                        errorCallback(GenerateURLError.Custom(context, context.getString(R.string.error_vgd_isgd)))
                        return@JsonObjectRequest
                    }
                    if (networkResponse == null || statusCode == null) {
                        Log.e(tag, "error.networkResponse == null")
                        errorCallback(GenerateURLError.Custom(context, error.message))
                        return@JsonObjectRequest
                    }
                    val data = networkResponse.data
                    if (data == null) {
                        Log.e(tag, "error.networkResponse.data == null")
                        errorCallback(
                            GenerateURLError.Custom(
                                context, (error.message ?: context.getString(R.string.error_unknown)) + " ($statusCode)"
                            )
                        )
                        return@JsonObjectRequest
                    }
                    val message = data.toString(charset("UTF-8"))
                    Log.e(tag, "error: $message ($statusCode)")
                    errorCallback(GenerateURLError.Custom(context, "$message ($statusCode)"))
                } catch (e: Exception) {
                    Log.e(tag, "error: $e")
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        )
    }

    class Vgd : VgdIsgd() {
        override val name = "v.gd"
        override val baseURL = "https://v.gd/"
        override val apiURL = "${baseURL}create.php"
        override val infoURL = baseURL
        override val privacyURL = "${baseURL}privacy.php"
        override val termsURL = "${baseURL}terms.php"

        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String?,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit
        ): JsonObjectRequest = getVgdIsgdCreateRequest(context, longURL, alias, successCallback, errorCallback)
    }

    class Isgd : VgdIsgd() {
        override val name = "is.gd"
        override val baseURL = "https://is.gd/"
        override val apiURL = "${baseURL}create.php"
        override val infoURL = baseURL
        override val privacyURL = "${baseURL}privacy.php"
        override val termsURL = "${baseURL}terms.php"

        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String?,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit
        ): JsonObjectRequest = getVgdIsgdCreateRequest(context, longURL, alias, successCallback, errorCallback)
    }
}