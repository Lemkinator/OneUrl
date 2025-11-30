package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.NoConnectionError
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import de.lemke.commonutils.urlEncodeAmpersand
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError

/*
https://tinyurl.com/app
example: https://tinyurl.com/api-create.php?url=https://example.com&alias=example // json body: https://api.tinyurl.com/create

analytics require api token
 */
object Tinyurl : ShortURLProvider {
    override val enabled = false // https://tinyurl.com/blog/retiring-our-old-api-endpoint/
    override val name = "tinyurl.com"
    override val baseURL = "https://tinyurl.com"
    override val apiURL = "$baseURL/api-create.php"
    override val privacyURL = "$baseURL/app/privacy-policy"
    override val termsURL = "$baseURL/app/terms"
    override val aliasConfig = object : AliasConfig {
        override val minAliasLength = 5
        override val maxAliasLength = 30
        override val allowedAliasCharacters = "a-z, A-Z, 0-9, _"
        override fun isAliasValid(alias: String) = alias.matches(Regex("[a-zA-Z0-9_]+"))
    }

    override fun sanitizeLongURL(url: String) = url.urlEncodeAmpersand().trim()

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

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit,
    ): StringRequest {
        val tag = "CreateRequest_$name"
        val url = apiURL + "?url=" + longURL + if (alias.isBlank()) "" else "&alias=$alias"
        Log.d(tag, "start request: $url")
        return StringRequest(
            Request.Method.POST,
            url,
            { response ->
                Log.d(tag, "response: $response")
                if (response.startsWith("https://tinyurl.com/") || response.startsWith("http://tinyurl.com/")) {
                    val shortURL = response.trim()
                    Log.d(tag, "shortURL: $shortURL")
                    successCallback(shortURL)
                } else {
                    Log.e(tag, "error, response does not start with http(s)://tinyurl.com/, response: $response")
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
                        statusCode == 422 || statusCode == 400 -> {
                            if (alias.isBlank()) errorCallback(GenerateURLError.InvalidURL(context))
                            else errorCallback(GenerateURLError.InvalidURLOrAlias(context))
                        }

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