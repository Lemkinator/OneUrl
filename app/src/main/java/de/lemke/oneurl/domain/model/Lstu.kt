package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NoConnectionError
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import de.lemke.oneurl.domain.withHttps
import org.json.JSONObject

/*
https://lstu.fr/
https://lstu.fr/api
https://framagit.org/fiat-tux/hat-softwares/lstu/blob/master/README.md

https://lstu.fr/a {lsturl=https://example.com lsturl-custom=test format=json}
resonse:
{
  "qrcode": "iVBORw0KGgoAAAANSUhEUgAAAGMAAABjAQAAAACnQIM4AAAA7ElEQVQ4jc3Usa3EIAwAUEcUdJcF\nkFiDjpVggRAWuFuJjjUisUDoUljxkeL/pMkZ/eLrLApegWQbA9A14Iu1AkywhAQDp0oYNhVy23BK\nagIcMvgO+VSoU7mtDhFOstQzs1u1+o5zZ7W3arFbNZwdvNVql4eEQILVbnTMNBsYWUl0Ro25RE51\nKzXR04jKiY5O4JiI1Qp6lmI29OJUE46EDxCRFZWn1a+0eFYJBxIzqMCpxWrRkyZO7W6dbfOiK6c2\nL85QpA4lFbZLZh81Wb1Lih0KeXH2pxMfROg35eA3s1sd9R3vr7D675/ob3oDhzG5hss/eO8AAAAA\nSUVORK5CYII=\n",
  "short": "https://lstu.fr/test-273",
  "success": true,
  "url": "https://example.com"
}

fail:
{
  "msg": "example.com is not a valid URL.",
  "success": false
}

stats:
https://lstu.fr/stats/test
{
  "counter": 702,
  "created_at": 1401192086,
  "short": "https://lstu.fr/test",
  "success": true,
  "timestamp": 1727985341,
  "url": "https://linuxfr.org"
}
 */
val lstu = Lstu()

class Lstu : ShortURLProvider {
    override val enabled: Boolean = false // discontinued because of abuse: https://lstu.fr/
    override val name = "lstu.fr"
    override val baseURL = "https://lstu.fr"
    override val apiURL = "$baseURL/a"
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
        ),
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_report,
            context.getString(R.string.analytics),
            context.getString(R.string.analytics_text)
        )
    )

    override fun sanitizeLongURL(url: String) = url.withHttps().trim()

    override fun getURLClickCount(context: Context, url: URL, callback: (clicks: Int?) -> Unit) {
        val tag = "GetURLVisitCount_$name"
        val requestURL = "$baseURL/stats/${url.alias}"
        Log.d(tag, "start request: $url")
        RequestQueueSingleton.getInstance(context).addToRequestQueue(
            StringRequest(
                Request.Method.GET,
                requestURL,
                { response ->
                    try {
                        Log.d(tag, "response: $response")
                        val clicks = JSONObject(response).getInt("counter")
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
        Log.d(tag, "start request: $apiURL {lsturl=$longURL lsturl-custom=$alias format=json}")
        return object : StringRequest(
            Method.POST,
            apiURL,
            { response ->
                try {
                    Log.d(tag, "response: $response")
                    val json = JSONObject(response)
                    when {
                        json.optBoolean("success") && json.has("short") -> {
                            val shortURL = json.getString("short")
                            Log.d(tag, "shortURL: $shortURL")
                            if (alias.isEmpty() || shortURL.endsWith("/$alias")) successCallback(shortURL)
                            else errorCallback(GenerateURLError.AliasAlreadyExists(context))
                        }

                        json.has("msg") -> errorCallback(GenerateURLError.Custom(context, 200, json.getString("msg")))
                        else -> errorCallback(GenerateURLError.Unknown(context, 200))
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
                        error is NoConnectionError -> errorCallback(GenerateURLError.ServiceOffline(context))
                        statusCode == null -> errorCallback(GenerateURLError.Unknown(context))
                        else -> errorCallback(GenerateURLError.Unknown(context, statusCode))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        ) {
            override fun getParams() = mapOf("lsturl" to longURL, "lsturl-custom" to alias, "format" to "json")
            override fun getRetryPolicy() = DefaultRetryPolicy(
                10000, // set timeout to 10 seconds
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
        }
    }
}