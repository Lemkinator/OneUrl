package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.NoConnectionError
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import de.lemke.oneurl.domain.urlEncodeAmpersand
import de.lemke.oneurl.domain.withHttps

/*
https://murl.com
example: https://murl.com/api/?url=https://example.com

errors:
Invalid URL
URL is too long
You are adding URLs too fast, please slow down
Enter your URL

 */
object Murl : ShortURLProvider {
    override val name = "murl.com"
    override val baseURL = "https://murl.com"
    override val apiURL = "$baseURL/api"

    override fun sanitizeLongURL(url: String) = url.withHttps().urlEncodeAmpersand().trim()

    override fun getTipsCardTitleAndInfo(context: Context) = Pair(
        context.getString(de.lemke.commonutils.R.string.info),
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
        )
    )

    override fun getURLClickCount(context: Context, url: URL, callback: (clicks: Int?) -> Unit) {
        val tag = "GetURLVisitCount_$name"
        val requestURL = "$baseURL/${url.alias}"
        Log.d(tag, "start request: $requestURL")
        RequestQueueSingleton.getInstance(context).apply {
            removeCacheEntry(requestURL) // this provider is weird
            addToRequestQueue(
                StringRequest(
                    Request.Method.GET,
                    requestURL,
                    { response ->
                        try {
                            //Log.d(tag, "response: $response")
                            //<span class="mu-mc count-480357" title="Views">1</span>
                            val clicks = response.substringAfter("\" title=\"Views\">").substringBefore("</span>").toIntOrNull()
                            Log.d(tag, response.substringAfter("entry-date").substringBefore("entry-links"))
                            Log.d(tag, "clicks: $clicks")
                            callback(clicks)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Log.e(tag, "error: ${e.message}")
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
    }

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): StringRequest {
        val tag = "CreateRequest_$name"
        val url = "$apiURL?url=$longURL"
        Log.d(tag, "start request: $url")
        return StringRequest(
            Request.Method.GET,
            url,
            { response ->
                Log.d(tag, "response: $response")
                when {
                    response.startsWith("https://murl.com/") -> {
                        val shortURL = response.trim()
                        Log.d(tag, "shortURL: $shortURL")
                        successCallback(shortURL)
                    }

                    response.contains("Invalid URL", true) ||
                            response.contains("URL is too long", true)
                        -> errorCallback(GenerateURLError.InvalidURL(context))

                    response.contains("adding URLs too fast", true) ||
                            response.contains("please slow down", true) -> errorCallback(GenerateURLError.RateLimitExceeded(context))

                    else -> {
                        Log.e(tag, "error, response does not start with https://murl.com/, response: $response")
                        errorCallback(GenerateURLError.Unknown(context, 200))
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
                        data.isNullOrBlank() -> errorCallback(GenerateURLError.Unknown(context, statusCode))
                        data.contains("Long URL cannot be empty", true) -> errorCallback(GenerateURLError.InvalidURL(context))
                        data.contains("Long URL must have http:// or https://", true) -> errorCallback(GenerateURLError.InvalidURL(context))
                        data.contains("Long URL is not a valid URL", true) -> errorCallback(GenerateURLError.InvalidURL(context))
                        data.contains("Short URL already taken", true) -> errorCallback(GenerateURLError.AliasAlreadyExists(context))
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