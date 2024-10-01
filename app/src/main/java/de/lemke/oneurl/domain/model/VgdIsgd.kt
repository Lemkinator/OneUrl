package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.urlEncodeAmpersand

/*
docs:
https://v.gd/apishorteningreference.php
https://is.gd/apishorteningreference.php
example:
https://v.gd/create.php?format=json&url=www.example.com&shorturl=example
https://is.gd/create.php?format=json&url=www.example.com&shorturl=example
 */
val vgd = VgdIsgd.Vgd()
val isgd = VgdIsgd.Isgd()
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

    override fun sanitizeLongURL(url: String) = url.urlEncodeAmpersand().trim()

    override fun getInfoButtons(context: Context): List<ProviderInfo> = listOf(
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_privacy,
            context.getString(R.string.privacy_policy),
            privacyURL!!
        ),
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_memo_outline,
            context.getString(R.string.tos),
            termsURL!!
        ),
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_info_outline,
            context.getString(R.string.more_information),
            infoURL
        ),
    )

    fun getVgdIsgdCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): JsonObjectRequest {
        val tag = "CreateRequest_$name"
        val url = apiURL + "?format=json&url=" + longURL + (if (alias.isBlank()) "" else "&shorturl=$alias&logstats=1")
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
                    Sorry, the URL you entered is on our internal blacklist. It may have been used abusively in the past, or it may link to another URL redirection service.
                    Error code 2 - there was a problem with the short URL provided (for custom short URLs)
                    Short URLs must be at least 5 characters long                               //should not happen, checked before
                    Short URLs may only contain the characters a-z, 0-9 and underscore          //should not happen, checked before
                    The shortened URL you picked already exists, please choose another.
                    Error code 3 - our rate limit was exceeded (your app should wait before trying again)
                    Error code 4 - any other error (includes potential problems with our service such as a maintenance period)
                     */
                    when (response.getString("errorcode")) {
                        "1" -> if (response.optString("errormessage").contains("blacklist", ignoreCase = true)) {
                            errorCallback(GenerateURLError.BlacklistedURL(context))
                        } else {
                            errorCallback(GenerateURLError.InvalidURL(context))
                        }

                        "2" -> errorCallback(GenerateURLError.AliasAlreadyExists(context))
                        "3" -> errorCallback(GenerateURLError.RateLimitExceeded(context))
                        "4" -> errorCallback(GenerateURLError.ServiceTemporarilyUnavailable(context, this))
                        else -> errorCallback(
                            GenerateURLError.Custom(
                                context,
                                200,
                                response.optString("errormessage") + " (${response.getString("errorcode")})"
                            )
                        )
                    }
                    return@JsonObjectRequest
                }
                if (!response.has("shorturl")) {
                    Log.e(tag, "error, response does not contain shorturl, response: $response")
                    errorCallback(GenerateURLError.Unknown(context, 200))
                    return@JsonObjectRequest
                }
                val shortURL = response.getString("shorturl").trim()
                Log.d(tag, "shortURL: $shortURL")
                successCallback(shortURL)
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
                        error.message?.contains("JSONException", true) == true -> {
                            //https://v.gd/create.php?format=json&url=example.com?test&shorturl=test21 -> Error, database insert failed
                            //Update: Works fine now?
                            Log.e(tag, "error.message == ${error.message} (probably error: database insert failed)")
                            errorCallback(GenerateURLError.Custom(context, statusCode, context.getString(R.string.error_vgd_isgd)))
                        }
                        else -> errorCallback(GenerateURLError.Custom(context, statusCode, data))
                    }
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

        override fun getTipsCardTitleAndInfo(context: Context) = Pair(
            context.getString(R.string.info),
            context.getString(R.string.redirect_hint_text)
        )

        override fun getInfoContents(context: Context): List<ProviderInfo> = listOf(
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_confirm_before_next_action,
                context.getString(R.string.redirect_hint),
                context.getString(R.string.redirect_hint_text)
            ),
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_report,
                context.getString(R.string.analytics),
                context.getString(R.string.analytics_text)
            ),
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_tool_outline,
                context.getString(R.string.alias),
                context.getString(R.string.alias_text, aliasConfig.minAliasLength, aliasConfig.maxAliasLength, aliasConfig.allowedAliasCharacters)
            )
        )

        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String,
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

        override fun getTipsCardTitleAndInfo(context: Context) = null

        override fun getInfoContents(context: Context): List<ProviderInfo> = listOf(
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_report,
                context.getString(R.string.analytics),
                context.getString(R.string.analytics_text)
            ),
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_tool_outline,
                context.getString(R.string.alias),
                context.getString(R.string.alias_text, aliasConfig.minAliasLength, aliasConfig.maxAliasLength, aliasConfig.allowedAliasCharacters)
            )
        )

        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit
        ): JsonObjectRequest = getVgdIsgdCreateRequest(context, longURL, alias, successCallback, errorCallback)
    }
}