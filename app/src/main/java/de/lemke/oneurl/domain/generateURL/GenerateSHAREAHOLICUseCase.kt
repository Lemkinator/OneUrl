package de.lemke.oneurl.domain.generateURL


import android.content.Context
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.ShortURLProvider
import org.json.JSONObject
import javax.inject.Inject


class GenerateSHAREAHOLICUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(
        provider: ShortURLProvider,
        longURL: String,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit = { },
    ): JsonObjectRequest {
        val tag = "GenerateURLUseCase_SHAREAHOLIC"
        val apiURL = provider.getCreateURLApi(longURL)
        Log.d(tag, "start request: $apiURL")
        return JsonObjectRequest(
            Request.Method.GET,
            apiURL,
            null,
            { response ->
                Log.d(tag, "response: $response")
                /*
                {
                    "status_code": "200",
                    "data": "https://go.shr.lc/2sZ8JZo"
                }

                {
                    "errors":[
                        {
                            "code":"140",
                            "source":{
                                "pointer":"/data/attributes/url"
                            },
                            "detail":"Missing URL. See https://www.shareaholic.com/api/shortener/ for usage examples."
                        }
                    ]
                }
                */
                if (response.has("data")) {
                    val shortURL = response.getString("data").trim()
                    Log.d(tag, "shortURL: $shortURL")
                    successCallback(shortURL)
                    return@JsonObjectRequest
                }
                if (response.has("errors")) {
                    val firstError = response.optJSONArray("errors")?.optJSONObject(0)
                    Log.e(tag, "error: $firstError")
                    when (firstError?.optString("code")) {
                        "100" -> errorCallback(GenerateURLError.Unknown(context)) //100	apikey not provided
                        "101" -> errorCallback(GenerateURLError.Unknown(context)) //101	apikey provided is invalid
                        "140" -> errorCallback(GenerateURLError.Unknown(context)) //140	Missing URL
                        "141" -> errorCallback(GenerateURLError.InvalidURL(context)) //141	Invalid URL
                        "145" -> errorCallback(GenerateURLError.InvalidURL(context)) //145	URL shortening problem or unsafe URL
                        "429" -> errorCallback(GenerateURLError.RateLimitExceeded(context)) //429	rate_limit_exceeded
                        else -> errorCallback(GenerateURLError.Custom(context, firstError?.optString("detail")))
                    }
                    return@JsonObjectRequest
                }
                errorCallback(GenerateURLError.Unknown(context))
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val networkResponse: NetworkResponse? = error.networkResponse
                    Log.e(tag, "networkResponse: $networkResponse")
                    val statusCode = networkResponse?.statusCode
                    Log.e(tag, "statusCode: $statusCode")
                    if (networkResponse == null || statusCode == null) {
                        errorCallback(GenerateURLError.Unknown(context))
                        return@JsonObjectRequest
                    }
                    val response = JSONObject(String(networkResponse.data))
                    if (response.has("errors")) {
                        val firstError = response.optJSONArray("errors")?.optJSONObject(0)
                        Log.e(tag, "error: $firstError")
                        when (firstError?.optString("code")) {
                            "100" -> errorCallback(GenerateURLError.Unknown(context)) //100	apikey not provided
                            "101" -> errorCallback(GenerateURLError.Unknown(context)) //101	apikey provided is invalid
                            "140" -> errorCallback(GenerateURLError.Unknown(context)) //140	Missing URL
                            "141" -> errorCallback(GenerateURLError.InvalidURL(context)) //141	Invalid URL
                            "145" -> errorCallback(GenerateURLError.InvalidURL(context)) //145	URL shortening problem or unsafe URL
                            "429" -> errorCallback(GenerateURLError.RateLimitExceeded(context)) //429	rate_limit_exceeded
                            else -> errorCallback(GenerateURLError.Custom(context, firstError?.optString("detail")))
                        }
                        return@JsonObjectRequest
                    }
                    Log.e(tag, "data: ${String(networkResponse.data)}")
                    errorCallback(GenerateURLError.Custom(context, context.getString(R.string.error_unknown) + " ($statusCode)"))
                } catch (e: Exception) {
                    Log.e(tag, "error: $e")
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        )
    }
}