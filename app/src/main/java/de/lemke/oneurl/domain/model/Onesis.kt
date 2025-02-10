package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.NoConnectionError
import com.android.volley.toolbox.StringRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.withHttps

/*
https://1s.is/
example: https://1s.is/?original_url=https://example.com&custom_short_url=

<span id="shortlink-url" style="color: #007bff; font-weight: bold;">https://1s.is/K5F8WO</span>

supports alias (1- tested up to 100 chars, on its website its limited to 30)
no spaces, no accents, only (lowercase???) letters and numbers, use "-", not at the beginning or end, and no consecutive hyphens.

if alias is taken -> <div class="shortlink-message">Short URL already exists. Please choose another one.</div>
if url already shortened -> return existing short url regardless of alias... thats bad

errors:
The custom short URL must follow the correct format: no spaces, no accents, only letters and numbers, use "-", not at the beginning or end, and no consecutive hyphens.
Short URL already exists. Please choose another one.
 */
object Onesis : ShortURLProvider {
    override val enabled = false // security check failed
    override val name = "1s.is"
    override val baseURL = "https://1s.is"
    override val aliasConfig = object : AliasConfig {
        override val minAliasLength = 1
        override val maxAliasLength = 100
        override val allowedAliasCharacters = "a-z, 0-9"
        override fun isAliasValid(alias: String) = alias.matches(Regex("[a-z0-9]+"))
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
        val tag = "CreateRequest_$name"
        Log.d(tag, "start request: $apiURL {original_url=$longURL, custom_short_url=$alias}")
        return object : StringRequest(
            Method.POST,
            apiURL,
            { response ->
                try {
                    //Log.d(tag, "response: $response")
                    val responseAlias = response.split("<span id=\"shortlink-url\"")[1].split("</span>")[0].split("https://1s.is/")[1]
                    val shortURL = "$baseURL/$responseAlias"
                    Log.d(tag, "shortURL: $shortURL")
                    if (alias.isBlank() || responseAlias == alias) successCallback(shortURL)
                    else errorCallback(GenerateURLError.URLExistsWithDifferentAlias(context))
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.w(tag, "could not find short URL in response")
                    when {
                        response.contains("Short URL already exists. Please choose another one.") ->
                            errorCallback(GenerateURLError.AliasAlreadyExists(context))

                        response.contains("The custom short URL must follow the correct format") ->
                            errorCallback(GenerateURLError.InvalidAlias(context))

                        else -> errorCallback(GenerateURLError.Unknown(context, 200))
                    }
                }
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val message = error.message
                    val networkResponse = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    val data = networkResponse?.data?.toString(Charsets.UTF_8)
                    Log.e(tag, "$statusCode: message: $message data: $data")
                    when {
                        error is NoConnectionError -> errorCallback(GenerateURLError.ServiceOffline(context))
                        statusCode == null -> errorCallback(GenerateURLError.Unknown(context))
                        statusCode == 503 -> errorCallback(GenerateURLError.ServiceTemporarilyUnavailable(context, this))
                        else -> errorCallback(GenerateURLError.Unknown(context, statusCode))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        ) {
            override fun getParams() = mapOf("original_url" to longURL, "custom_short_url" to alias)
        }
    }
}