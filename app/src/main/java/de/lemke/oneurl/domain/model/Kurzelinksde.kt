package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.toolbox.StringRequest
import de.lemke.oneurl.BuildConfig
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.withoutHttps
import org.json.JSONObject

/*
docs: https://kurzelinks.de/ https://ogy.de/ https://t1p.de/ https://0cn.de/ -> pdf
example: https://kurzelinks.de/api?key=API_KEY&json=1&apiversion=22&url=example.com&servicedomain=kurzelinks.de&requesturl=example

response:
{"@attributes":{"api":"22"},"status":"ok","shorturl":{"url":"https:\/\/kurzelinks.de\/014q","originalurl":"http:\/\/example.com"}}

error:
400: Error occurred (please check the correctness of the request)
403: No permission for the operation is available (check API key)
423: the specified desired URL is not available
429: the maximum number of allowed requests has been exceeded
444: the API cannot be accessed at the moment (please try again later)

*/
val kurzelinksde = Kurzelinksde.Kurzelinks()
val kurzelinksdeOgy = Kurzelinksde.Ogy()
val kurzelinksdeT1p = Kurzelinksde.T1p()
val kurzelinksdeOcn = Kurzelinksde.Ocn()
sealed class Kurzelinksde : ShortURLProvider {
    override val enabled = true
    final override val group = "kurzelinks.de, 0cn.de, t1p.de, ogy.de"
    final override val aliasConfig = object : AliasConfig {
        override val minAliasLength = 5
        override val maxAliasLength = 100 //tested up to 250
        override val allowedAliasCharacters = "a-z, A-Z, 0-9, -, _"
        override fun isAliasValid(alias: String) = alias.matches(Regex("[a-zA-Z0-9_-]+"))
    }

    override fun getAnalyticsURL(alias: String) = null //no analytics

    override fun sanitizeLongURL(url: String) = url.trim()

    override fun getInfoContents(context: Context): List<ProviderInfo> = listOf(
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_privacy,
            context.getString(R.string.privacy_policy),
            context.getString(R.string.privacy_text)
        ),
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_tool_outline,
            context.getString(R.string.alias),
            context.getString(R.string.alias_text, aliasConfig.minAliasLength, aliasConfig.maxAliasLength, aliasConfig.allowedAliasCharacters)
        )
    )

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
        )
    )

    override fun getTipsCardTitleAndInfo(context: Context) = Pair(
        context.getString(R.string.info),
        context.getString(R.string.privacy_text)
    )

    fun getKurzelinksCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): StringRequest {
        val tag = "CreateRequest_$name"
        Log.d(tag, "start request: $apiURL")
        return object : StringRequest(
            Method.POST,
            apiURL,
            { response ->
                Log.d(tag, "response: $response")
                try {
                    val shortURL = JSONObject(response).optJSONObject("shorturl")?.optString("url")
                    if (shortURL == null) {
                        Log.e(tag, "error: shortURL not found")
                        errorCallback(GenerateURLError.Unknown(context))
                    } else {
                        Log.d(tag, "shortURL: $shortURL")
                        successCallback(shortURL)
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
                        statusCode == 400 || statusCode == 403 -> errorCallback(GenerateURLError.Unknown(context, statusCode))
                        statusCode == 423 -> errorCallback(GenerateURLError.AliasAlreadyExists(context))
                        statusCode == 429 -> errorCallback(GenerateURLError.RateLimitExceeded(context))
                        statusCode == 444 -> errorCallback(GenerateURLError.ServiceTemporarilyUnavailable(context, this))
                        else -> errorCallback(GenerateURLError.Custom(context, statusCode, data))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        ) {
            override fun getParams() = mapOf(
                "key" to BuildConfig.KURZELINKS_API_KEY,
                "json" to "1",
                "apiversion" to "22",
                "url" to longURL,
                "servicedomain" to baseURL.withoutHttps(),
                "requesturl" to alias
            )
        }
    }

    class Kurzelinks : Kurzelinksde() {
        override val name = "kurzelinks.de"
        override val baseURL = "https://kurzelinks.de/"
        override val apiURL = "${baseURL}api"
        override val infoURL = baseURL
        override val privacyURL = baseURL + "datenschutzerklaerung"
        override val termsURL = baseURL + "nutzungsbedingungen"

        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit
        ): StringRequest = getKurzelinksCreateRequest(context, longURL, alias, successCallback, errorCallback)
    }

    class Ocn : Kurzelinksde() { //o -> 0
        override val name = "0cn.de"
        override val baseURL = "https://0cn.de/"
        override val apiURL = "${baseURL}api"
        override val infoURL = baseURL
        override val privacyURL = baseURL + "datenschutzerklaerung"
        override val termsURL = baseURL + "nutzungsbedingungen"

        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit
        ): StringRequest = getKurzelinksCreateRequest(context, longURL, alias, successCallback, errorCallback)
    }

    class T1p : Kurzelinksde() {
        override val name = "t1p.de"
        override val baseURL = "https://t1p.de/"
        override val apiURL = "${baseURL}api"
        override val infoURL = baseURL
        override val privacyURL = baseURL + "datenschutzerklaerung"
        override val termsURL = baseURL + "nutzungsbedingungen"

        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit
        ): StringRequest = getKurzelinksCreateRequest(context, longURL, alias, successCallback, errorCallback)
    }

    class Ogy : Kurzelinksde() {
        override val name = "ogy.de"
        override val baseURL = "https://ogy.de/"
        override val apiURL = "${baseURL}api"
        override val infoURL = baseURL
        override val privacyURL = baseURL + "datenschutzerklaerung"
        override val termsURL = baseURL + "nutzungsbedingungen"

        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit
        ): StringRequest = getKurzelinksCreateRequest(context, longURL, alias, successCallback, errorCallback)
    }
}