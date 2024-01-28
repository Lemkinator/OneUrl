package de.lemke.oneurl.domain.generateURL


import android.content.Context
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import de.lemke.oneurl.R
import de.lemke.oneurl.domain.model.ShortURLProvider
import javax.inject.Inject


class GenerateONEPTCOUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(
        provider: ShortURLProvider,
        longURL: String,
        alias: String?,
        successCallback: (shortURL: String) -> Unit,
        errorCallback: (error: GenerateURLError) -> Unit = { },
    ): JsonObjectRequest {
        val tag = "GenerateONEPTCOUseCase"
        val apiURL = provider.getCreateURLApi(longURL, alias)
        Log.d(tag, "start request: $apiURL")
        return JsonObjectRequest(
            Request.Method.POST,
            apiURL,
            null,
            { response ->
                Log.d(tag, "response: $response")
                /*
                success: {
                    "message": "Added!",
                    "short": "ajodd",
                    "long": "t"
                }
                alias already exists: {
                    "message": "Added!",
                    "short": "asdfg",
                    "long": "asdfasdf",
                    "receivedRequestedShort": false
                }
                fail: {
                    "message": "Bad request"
                }
                */
                if (!response.has("message")) {
                    Log.e(tag, "error: no message")
                    errorCallback(GenerateURLError.Unknown(context))
                    return@JsonObjectRequest
                } else if (response.getString("message") != "Added!") {
                    Log.e(tag, "error: ${response.getString("message")}")
                    errorCallback(GenerateURLError.Custom(context, response.getString("message")))
                    return@JsonObjectRequest
                } else if (!response.has("short")) {
                    Log.e(tag, "error: no short")
                    errorCallback(GenerateURLError.Unknown(context))
                    return@JsonObjectRequest
                } else if (response.has("receivedRequestedShort") && !response.getBoolean("receivedRequestedShort")) {
                    Log.e(tag, "error: alias already exists")
                    errorCallback(GenerateURLError.AliasAlreadyExists(context))
                    return@JsonObjectRequest
                }
                val shortURL = provider.baseURL + response.getString("short").trim()
                Log.d(tag, "shortURL: $shortURL")
                successCallback(shortURL)
            },
            { error ->
                try {
                    Log.e(tag, "error: $error")
                    val networkResponse: NetworkResponse? = error.networkResponse
                    val statusCode = networkResponse?.statusCode
                    if (networkResponse == null || statusCode == null) {
                        Log.e(tag, "error.networkResponse == null")
                        errorCallback(GenerateURLError.Custom(context, error.message))
                        return@JsonObjectRequest
                    }
                    Log.e(tag, "statusCode: $statusCode")
                    val data = networkResponse.data
                    if (data == null) {
                        Log.e(tag, "error.networkResponse.data == null")
                        errorCallback(
                            GenerateURLError.Custom(
                                context, (error.message ?: context.getString(R.string.error_unknown)) + " ($statusCode)"
                            )
                        )
                        return@JsonObjectRequest
                    }
                    val message = data.toString(charset("UTF-8"))
                    if (statusCode == 500 && message.contains("Internal server error")) {
                        Log.e(tag, "error: Internal server error ($statusCode)")
                        errorCallback(GenerateURLError.InternalServerError(context))
                        return@JsonObjectRequest
                    }
                    Log.e(tag, "error: $message ($statusCode)")
                    errorCallback(GenerateURLError.Custom(context, "$message ($statusCode)"))
                } catch (e: Exception) {
                    Log.e(tag, "error: $e")
                    e.printStackTrace()
                    errorCallback(GenerateURLError.Unknown(context))
                }
            }
        )
    }
}