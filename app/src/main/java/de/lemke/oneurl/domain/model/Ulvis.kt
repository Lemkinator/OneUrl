package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.withHttps

/*
https://ulvis.net/developer.html -> added cloudflare :/ -> removed
example:
https://ulvis.net/api.php?url=https://example.com&custom=alias&private=1
https://ulvis.net/API/write/get?url=https://example.com&custom=alias&private=1
 */
val ulvis = Ulvis()

class Ulvis : ShortURLProvider {
    override val enabled = false //added cloudflare :/
    override val name = "ulvis.net"
    override val baseURL = "https://ulvis.net"
    override val apiURL = "$baseURL/API/write/get"
    override val privacyURL = "$baseURL/privacy.html"
    override val termsURL = "$baseURL/disclaimer.html"
    override val aliasConfig = object : AliasConfig {
        override val minAliasLength = 0
        override val maxAliasLength = 60
        override val allowedAliasCharacters = "a-z, A-Z, 0-9"
        override fun isAliasValid(alias: String) = alias.matches(Regex("[a-zA-Z0-9]+"))
    }

    override fun sanitizeLongURL(url: String) = url.withHttps().trim()

    override fun getInfoContents(context: Context): List<ProviderInfo> = listOf(
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_block,
            context.getString(R.string.currently_disabled),
            context.getString(R.string.currently_disabled_ulvis)
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
        )
    )

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): JsonObjectRequest {
        val tag = "CreateRequest_$name"
        val url = apiURL + "?url=" + longURL + (if (alias.isBlank()) "" else "&custom=$alias&private=1")
        Log.d(tag, "start request: $url")
        return JsonObjectRequest(
            Request.Method.POST,
            url,
            null,
            { response ->
                Log.d(tag, "response: $response")
                /*
                success: response: {"success":true,"data":{"id":"EAZe","url":"https:\/\/ulvis.net\/EAZe","full":"https:\/\/t.com"}}
                alias already exists: response: {"success":true,"data":{"status":"custom-taken"}}
                code 0:
                {"success":false,"error":{"code":0,"msg":"domain not allowed"}}
                code 1:
                {"success":false,"error":{"code":1,"msg":"invalid url"}}
                code 2:
                {"success":false,"error":{"code":2,"msg":"custom name must be less than 60 chars"}}     //should not happen, checked before
                 */
                if (!response.optBoolean("success")) {
                    Log.e(tag, "error: ${response.optJSONObject("error")}")
                    val error = response.optJSONObject("error")
                    if (error != null) {
                        when (val code = error.optInt("code")) {
                            0 -> errorCallback(GenerateURLError.DomainNotAllowed(context))
                            1 -> errorCallback(GenerateURLError.InvalidURL(context))
                            else -> errorCallback(GenerateURLError.Custom(context, code, error.optString("msg")))
                        }
                        return@JsonObjectRequest
                    }
                    errorCallback(GenerateURLError.Unknown(context))
                    return@JsonObjectRequest
                }
                val data = response.optJSONObject("data")
                if (data == null) {
                    Log.e(tag, "error, response does not contain data")
                    errorCallback(GenerateURLError.Unknown(context, 200))
                    return@JsonObjectRequest
                }
                if (data.optString("status") == "custom-taken") {
                    Log.e(tag, "error, alias already exists")
                    errorCallback(GenerateURLError.AliasAlreadyExists(context))
                    return@JsonObjectRequest
                }
                if (!data.has("url")) {
                    Log.e(tag, "error, response does not contain url")
                    errorCallback(GenerateURLError.Unknown(context, 200))
                    return@JsonObjectRequest
                }
                val shortURL = data.getString("url").trim()
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
                        statusCode == 403 -> errorCallback(GenerateURLError.HumanVerificationRequired(context, this)) //not working: cookies
                        data.isNullOrBlank() -> errorCallback(GenerateURLError.Unknown(context, statusCode))
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
}