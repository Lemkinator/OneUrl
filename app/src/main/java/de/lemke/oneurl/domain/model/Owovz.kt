package de.lemke.oneurl.domain.model

import android.content.Context
import android.net.Uri
import android.util.Log
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
visit count:
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
val owovzOwo = Owovz.OwovzOwo()
val owovzZws = Owovz.OwovzZws()
val owovzSketchy = Owovz.OwovzSketchy()
val owovzGay = Owovz.OwovzGay()

sealed class Owovz : ShortURLProvider {
    final override val group = "owo.vc (zws, sketchy, gay)"
    final override val baseURL = "https://owo.vc"
    final override val apiURL = "$baseURL/api/v2/link"

    override fun sanitizeLongURL(url: String) = url.withHttps().trim()

    override fun getURLClickCount(context: Context, url: URL, callback: (clicks: Int?) -> Unit) {
        val tag = "GetURLVisitCount_$name"
        val requestURL = "$apiURL/${Uri.encode(url.shortURL)}"
        Log.d(tag, "start request: $url")
        RequestQueueSingleton.getInstance(context).addToRequestQueue(
            JsonObjectRequest(
                Request.Method.GET,
                requestURL,
                null,
                { response ->
                    try {
                        Log.d(tag, "response: $response")
                        val visitCount = response.optInt("visits")
                        Log.d(tag, "visitCount: $visitCount")
                        callback(visitCount)
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

    fun getOwovzCreateRequest(
        context: Context,
        generator: String,
        longURL: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit
    ): JsonObjectRequest {
        val tag = "CreateRequest_$name"
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
                if (response.has("id")) {
                    val shortURL = response.getString("id").trim()
                    Log.d(tag, "shortURL: $shortURL")
                    successCallback(shortURL)
                } else {
                    Log.e(tag, "error: no shortURL")
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
                        statusCode == 400 && data.contains("link must match pattern") -> errorCallback(GenerateURLError.InvalidURL(context))
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

    class OwovzOwo : Owovz() {
        override val name = "owo.vc"
        override fun getTipsCardTitleAndInfo(context: Context) = Pair(
            context.getString(R.string.info),
            context.getString(R.string.owovc_fun_text)
        )

        override fun getInfoContents(context: Context): List<ProviderInfo> = listOf(
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_report,
                context.getString(R.string.analytics),
                context.getString(R.string.analytics_text)
            )
        )

        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit
        ): JsonObjectRequest =
            getOwovzCreateRequest(context, "owo", longURL, successCallback, errorCallback)

    }

    class OwovzZws : Owovz() {
        override val name = "owo.vc (zws)"
        override fun getTipsCardTitleAndInfo(context: Context) = Pair(
            context.getString(R.string.info),
            context.getString(R.string.owovc_zws)
        )

        override fun getInfoContents(context: Context): List<ProviderInfo> = listOf(
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_keyboard_btn_space,
                "zws",
                context.getString(R.string.owovc_zws)
            ),
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_report,
                context.getString(R.string.analytics),
                context.getString(R.string.analytics_text)
            ),
        )

        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit
        ): JsonObjectRequest =
            getOwovzCreateRequest(context, "zws", longURL, successCallback, errorCallback)
    }

    class OwovzSketchy : Owovz() {
        override val name = "owo.vc (sketchy)"
        override fun getTipsCardTitleAndInfo(context: Context) = Pair(
            context.getString(R.string.info),
            context.getString(R.string.owovc_sketchy)
        )

        override fun getInfoContents(context: Context): List<ProviderInfo> = listOf(
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_basic,
                "sketchy",
                context.getString(R.string.owovc_sketchy)
            ),
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_report,
                context.getString(R.string.analytics),
                context.getString(R.string.analytics_text)
            )
        )

        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit
        ): JsonObjectRequest =
            getOwovzCreateRequest(context, "sketchy", longURL, successCallback, errorCallback)
    }

    class OwovzGay : Owovz() {
        override val name = "owo.vc (gay)"
        override fun getTipsCardTitleAndInfo(context: Context) = Pair(
            context.getString(R.string.warning),
            context.getString(R.string.owovc_gay)
        )

        override fun getInfoContents(context: Context): List<ProviderInfo> = listOf(
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_long_legs,
                "gay",
                context.getString(R.string.owovc_gay)
            ),
            ProviderInfo(
                dev.oneuiproject.oneui.R.drawable.ic_oui_report,
                context.getString(R.string.analytics),
                context.getString(R.string.analytics_text)
            )
        )

        override fun getCreateRequest(
            context: Context,
            longURL: String,
            alias: String,
            successCallback: (shortURL: String) -> Unit,
            errorCallback: (error: GenerateURLError) -> Unit
        ): JsonObjectRequest =
            getOwovzCreateRequest(context, "gay", longURL, successCallback, errorCallback)
    }
}