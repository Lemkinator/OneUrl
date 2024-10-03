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
import org.json.JSONObject

/*
https://tinu.be/en
header:
next-action: 1f5652ca890cba09fa370c02bd5123721da16fec
body: [{"longUrl":"https://example.com","urlCode":""}]

response:
0:["$@1",["RVMyonj2KmbCvJpjXHG0y",null]]
1:{"status":200,"data":{"urlCode":"Nx1ByyelU","longUrl":"https://example.com"}}

error:
0:["$@1",["RVMyonj2KmbCvJpjXHG0y",null]]
1:{"status":208,"data":"The suffix is already in use"}

stats:
https://api.tinu.be/Nx1ByyelU/stats
{
  "clicks": 0,
  "longUrl": "https://example.com",
  "shortUrl": "https://tinu.be/Nx1ByyelU",
  "urlCode": "Nx1ByyelU"
}
 */
val tinube = Tinube()

class Tinube : ShortURLProvider {
    override val name = "tinu.be"
    override val baseURL = "https://tinu.be"
    override val apiURL = "$baseURL/en"
    override val privacyURL = "$baseURL/terms"
    override val termsURL = "$baseURL/terms"
    override val aliasConfig = object : AliasConfig {
        override val minAliasLength = 0
        override val maxAliasLength = 100 //no info, tested up to 100
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

    override fun sanitizeLongURL(url: String) = url.withHttps().urlEncodeAmpersand().trim()

    override fun getURLClickCount(context: Context, url: URL, callback: (clicks: Int?) -> Unit) {
        val tag = "GetURLVisitCount_$name"
        val requestURL = "https://api.tinu.be/${url.alias}/stats"
        Log.d(tag, "start request: $url")
        RequestQueueSingleton.getInstance(context).addToRequestQueue(
            StringRequest(
                Request.Method.GET,
                requestURL,
                { response ->
                    try {
                        Log.d(tag, "response: $response")
                        val clicks = JSONObject(response).getInt("clicks")
                        Log.d(tag, "clicks: $clicks")
                        callback(clicks)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback(null)
                    }
                },
                { error ->
                    Log.e(tag, "error: $error")
                    callback(null)
                }
            )
        )
    }

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): StringRequest {
        val tag = "CreateRequest_$name"
        Log.d(tag, "start request: $apiURL [{longUrl: $longURL, urlCode: $alias}]")
        return object : StringRequest(
            Method.POST,
            apiURL,
            { response ->
                try {
                    Log.d(tag, "response: $response")
                    val data = response.split("1:")[1]
                    val status = data.split(",")[0].split(":")[1].toInt()
                    val urlCode = if (status == 200) data.split("urlCode\":\"")[1].split("\"")[0] else null
                    Log.d(tag, "status: $status, urlCode: $urlCode")
                    when {
                        status == 200 && urlCode != null -> successCallback("$baseURL/$urlCode")
                        status == 208 -> errorCallback(GenerateURLError.AliasAlreadyExists(context))
                        else -> errorCallback(GenerateURLError.Unknown(context, status))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
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
                        else -> errorCallback(GenerateURLError.Unknown(context, statusCode))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        ) {
            override fun getHeaders() = mapOf("next-action" to "1f5652ca890cba09fa370c02bd5123721da16fec")
            override fun getBody() = "[{\"longUrl\":\"$longURL\",\"urlCode\":\"$alias\"}]".toByteArray(Charsets.UTF_8)
        }
    }
}