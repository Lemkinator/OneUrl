package de.lemke.oneurl.domain.model

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.withHttps
import org.json.JSONObject

/*
docs: https://zws.im/api-docs
https://zws.im/stats
example: https://api.zws.im/ {"url": "https://example.com"}

answer:
{
    "short": "󠁭󠁳󠁤󠁡󠁤󠁯󠁲",
    "url": "https://zws.im/%F3%A0%81%AD%F3%A0%81%B3%F3%A0%81%A4%F3%A0%81%A1%F3%A0%81%A4%F3%A0%81%AF%F3%A0%81%B2"
}

{
    "message": ["url: Invalid url"],
    "error": "Unprocessable Entity",
    "statusCode": 422
}
 */
val zwsim = Zwsim()
class Zwsim : ShortURLProvider {
    override val enabled = true
    override val name = "zws.im"
    override val group = name
    override val baseURL = "https://zws.im/"
    override val apiURL = "https://api.zws.im/"
    override val infoURL = baseURL
    override val privacyURL = null
    override val termsURL = null
    override val aliasConfig = null

    override fun getAnalyticsURL(alias: String) = null

    override fun sanitizeLongURL(url: String) = url.withHttps().trim()

    override fun getInfoContents(context: Context) = listOf(
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_keyboard_btn_space,
            "zws",
            context.getString(R.string.zwsim_zws)
        )
    )

    override fun getInfoButtons(context: Context) = listOf(
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_info_outline,
            context.getString(R.string.more_information),
            infoURL
        )
    )

    override fun getTipsCardTitleAndInfo(context: Context) = Pair(
        context.getString(R.string.info),
        context.getString(R.string.zwsim_zws)
    )

    override fun getCreateRequest(
        context: Context,
        longURL: String,
        alias: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): JsonObjectRequest {
        val tag = "CreateRequest_$name"
        Log.d(tag, "start request: $longURL")
        return JsonObjectRequest(
            Request.Method.POST,
            apiURL,
            JSONObject(mapOf("url" to longURL)),
            { response ->
                Log.d(tag, "response: $response")
                when {
                    response.has("short") -> successCallback("https://zws.im/${response.getString("short")}")
                    response.has("url") -> successCallback(response.getString("url"))
                    else -> {
                        Log.e(tag, "error, response does not contain short url")
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
                        statusCode == null -> errorCallback(GenerateURLError.Unknown(context))
                        data.isNullOrBlank() -> errorCallback(GenerateURLError.Unknown(context, statusCode))
                        statusCode == 422 && data.contains("Invalid url") -> errorCallback(GenerateURLError.InvalidURL(context))
                        statusCode == 503 -> errorCallback(GenerateURLError.ServiceTemporarilyUnavailable(context, this))
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