package de.lemke.oneurl.domain.model

import android.content.Context
import android.net.Uri
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.generateURL.GenerateURLError
import de.lemke.oneurl.domain.generateURL.RequestQueueSingleton
import de.lemke.oneurl.domain.withHttps
import org.json.JSONObject

/*
docs: https://owo.vc/api.html
example: https://owo.vc/api/v2/link {"link": "https://example.com", "generator": "owo", "metadata": "IGNORE"}
 */

sealed class Owovz : ShortURLProvider {
    override val enabled = true
    final override val group = "owo.vc (zws, sketchy, gay)"
    final override val baseURL = "https://owo.vc/"
    final override val apiURL = "${baseURL}api/v2/link/"
    final override val infoURL = baseURL
    final override val privacyURL = null
    final override val termsURL = null
    final override val aliasConfig = null
    override fun getAnalyticsURL(alias: String) = null

    override fun sanitizeLongURL(url: String) = url.withHttps().trim()

    //Info
    override val infoIcons: List<Int> = listOf(
        dev.oneuiproject.oneui.R.drawable.ic_oui_emoji,
        dev.oneuiproject.oneui.R.drawable.ic_oui_report
    )

    override fun getInfoContents(context: Context): List<ProviderInfo> = listOf(
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_emoji,
            context.getString(R.string.owovc_fun),
            context.getString(R.string.owovc_fun_text)
        ),
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_report,
            context.getString(R.string.analytics),
            context.getString(R.string.analytics_owovc)
        ),
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_keyboard_btn_space,
            "zws",
            context.getString(R.string.owovc_zws)
        ),
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_basic,
            "sketchy",
            context.getString(R.string.owovc_sketchy)
        ),
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_long_legs,
            "gay",
            context.getString(R.string.owovc_gay)
        )
    )

    override fun getInfoButtons(context: Context): List<ProviderInfo> = listOf(
        ProviderInfo(
            dev.oneuiproject.oneui.R.drawable.ic_oui_info_outline,
            context.getString(R.string.more_information),
            infoURL
        )
    )

    fun getURLVisitCount(context: Context, shortURL: String, callback: (visitCount: Int?) -> Unit) {
        val tag = "GetURLVisitCount_$name"
        val url = apiURL + Uri.encode(shortURL)
        Log.d(tag, "start request: $url")
        RequestQueueSingleton.getInstance(context).addToRequestQueue(
            JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                { response ->
                    Log.d(tag, "response: $response")
                    /*
                    {
                        "id": "uwu.owo.vc/uwU/uwU_Ovo/uvu",
                        "destination": "https://example.com",
                        "method": "OWO_VC",
                        "metadata": "OWOIFY",
                        "visits": 0,
                        "scrapes": 0,
                        "createdAt": "2024-02-06T16:11:00.509Z",
                        "status": "ACTIVE",
                        "commentId": null,
                        "comment": null
                    }
                    fail:
                    {
                        "statusCode": 404,
                        "error": "Not Found",
                        "message": "link not found"
                    }
                     */
                    val visitCount = response.optInt("visits")
                    Log.d(tag, "visitCount: $visitCount")
                    callback(visitCount)
                },
                { error ->
                    Log.e(tag, "error: $error")
                    callback(null)
                }
            )
        )
    }

    fun getOwovzCreateRequest(
        context: Context,
        generator: String,
        longURL: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): JsonObjectRequest {
        val tag = "OwovzCreateRequest_$generator"
        Log.d(tag, "start request: $apiURL")
        return JsonObjectRequest(
            Request.Method.POST,
            apiURL,
            JSONObject(
                mapOf(
                    "link" to longURL,
                    "generator" to generator,
                    "metadata" to "IGNORE" //IGNORE, OWOIFY, PROXY
                )
            ),
            { response ->
                Log.d(tag, "response: $response")
                /*
                success:
                {
                    "id": "uvu.owo.vc/uwU-uvU.uwU-uwU",
                    "destination": "https://example.com",
                    "method": "OWO_VC",
                    "metadata": "OWOIFY",
                    "visits": 0,
                    "scrapes": 0,
                    "createdAt": "2023-10-24T20:41:21.597Z",
                    "status": "ACTIVE",
                    "commentId": null
                }
                fail:
                {
                    "statusCode": 400,
                    "code": "FST_ERR_VALIDATION",
                    "error": "Bad Request",
                    "message": "body/link must match pattern \"https?:\\/\\/.+\\..+\""
                }
                 */
                if (!response.has("id")) {
                    Log.e(tag, "error: no shortURL")
                    errorCallback(GenerateURLError.Unknown(context))
                    return@JsonObjectRequest
                }

                val shortURL = response.getString("id").trim()
                Log.d(tag, "shortURL: $shortURL")
                successCallback(shortURL)
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val networkResponse: NetworkResponse? = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    Log.e(tag, "statusCode: $statusCode")
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
                                context,
                                (error.message ?: context.getString(R.string.error_unknown)) + " ($statusCode)"
                            )
                        )
                        return@JsonObjectRequest
                    }
                    val message = data.toString(charset("UTF-8"))
                    Log.e(tag, "error: $message ($statusCode)")
                    if (statusCode == 400 && message.contains("link must match pattern")) {
                        errorCallback(GenerateURLError.InvalidURL(context))
                        return@JsonObjectRequest
                    } else if (statusCode == 503) {
                        errorCallback(GenerateURLError.ServiceTemporarilyUnavailable(context, this))
                        return@JsonObjectRequest
                    }
                    errorCallback(GenerateURLError.Custom(context, "$message ($statusCode)"))
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        )
    }

    class OwovzOwo : Owovz() {
        override val name = "owo.vc"

        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String?,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit
        ): JsonObjectRequest =
            getOwovzCreateRequest(context, "owo", longURL, successCallback, errorCallback)

    }

    class OwovzZws : Owovz() {
        override val name = "owo.vc (zws)"
        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String?,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit
        ): JsonObjectRequest =
            getOwovzCreateRequest(context, "zws", longURL, successCallback, errorCallback)
    }

    class OwovzSketchy : Owovz() {
        override val name = "owo.vc (sketchy)"
        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String?,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit
        ): JsonObjectRequest =
            getOwovzCreateRequest(context, "sketchy", longURL, successCallback, errorCallback)
    }

    class OwovzGay : Owovz() {
        override val name = "owo.vc (gay)"
        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String?,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit
        ): JsonObjectRequest =
            getOwovzCreateRequest(context, "gay", longURL, successCallback, errorCallback)
    }
}